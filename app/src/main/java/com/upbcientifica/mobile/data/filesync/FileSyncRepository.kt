package com.upbcientifica.mobile.data.filesync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.upbcientifica.mobile.session.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * File Sync en el teléfono: una carpeta local se respalda de forma síncrona en
 * una carpeta del Home (node-sync, gRPC), y se sincroniza sola al llegar al
 * Wi-Fi de la red local del servidor o en el horario programado.
 *
 * ANTES este repositorio SIMULABA el ciclo (progreso aleatorio en un bucle) y
 * gRPC estaba desactivado. AHORA habla con node-sync de verdad y el estado que
 * ve la UI es el de la última sincronización real.
 *
 * Hay una sola instancia por proceso (ver [FileSyncModulo]): la usan la UI y
 * el trabajo en segundo plano, así que si la sincronización la dispara el
 * Wi-Fi con la app abierta, el indicador de la barra superior la muestra.
 */
class FileSyncRepository(
    private val context: Context,
    private val almacen: AlmacenSync,
    private val tokens: TokenManager,
    private val red: RedLocal,
    private val motor: MotorSincronizacion,
) {
    private val _estado = MutableStateFlow<EstadoSincronizacion>(EstadoSincronizacion.SinConfigurar)
    val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()

    private val _config = MutableStateFlow(ConfigSync())
    val config: StateFlow<ConfigSync> = _config.asStateFlow()

    // --- Sesión ----------------------------------------------------------------

    /** Al iniciar sesión: carga su configuración y activa los disparadores. */
    fun iniciarSesion(uid: String) {
        almacen.uidActivo = uid
        val c = almacen.cargar(uid)
        _config.value = c
        if (!motor.ocupado) _estado.value = estadoEnReposo(c)
        aplicarDisparadores(c)
        // Si se abre la app ya con Wi-Fi, cuenta como "alcanzar la conexión".
        if (c.carpetaUri != null && c.alConectarWifi) TrabajoSincronizacion.encolarAlConectar(context)
    }

    /** Al cerrar sesión no se sigue sincronizando con el token de nadie. */
    fun cerrarSesion() {
        almacen.uidActivo = null
        TrabajoSincronizacion.cancelarTodo(context)
        programacionAplicada = null
        red.dejarDeVigilar()
        _config.value = ConfigSync()
        _estado.value = EstadoSincronizacion.SinConfigurar
    }

    fun renovarVigilancia() {
        val uid = almacen.uidActivo ?: return
        if (almacen.cargar(uid).alConectarWifi) red.vigilarConexionWifi()
    }

    /** Última programación enviada a WorkManager, para no reencolarla sin cambios. */
    private var programacionAplicada: Triple<Programacion, Int, Int>? = null

    private fun aplicarDisparadores(c: ConfigSync) {
        val configurada = c.carpetaUri != null
        if (configurada && c.alConectarWifi) red.vigilarConexionWifi() else red.dejarDeVigilar()
        TrabajoSincronizacion.programarVigilancia(context, configurada && c.alConectarWifi)
        val programacion = if (configurada) c.programacion else Programacion.NINGUNA
        val clave = Triple(programacion, c.horaDiaria, c.minutoDiario)
        if (clave != programacionAplicada) {
            TrabajoSincronizacion.programar(context, c.copy(programacion = programacion))
            programacionAplicada = clave
        }
    }

    private fun estadoEnReposo(c: ConfigSync): EstadoSincronizacion {
        val error = c.ultimoResumen?.mensajeError
        return when {
            c.carpetaUri == null -> EstadoSincronizacion.SinConfigurar
            error != null -> EstadoSincronizacion.Error(error, c.ultimaSync)
            c.ultimaSync == 0L && red.wifi() == null -> EstadoSincronizacion.SinConexion
            else -> EstadoSincronizacion.AlDia(c.ultimaSync, c.ultimoResumen?.conflictos ?: 0)
        }
    }

    // --- Configuración (ProgramarSincronizacion / ConfigurarPolitica...) --------

    private fun modificar(cambio: (ConfigSync) -> ConfigSync): ConfigSync? {
        val uid = almacen.uidActivo ?: return null
        val c = almacen.modificar(uid, cambio)
        _config.value = c
        if (!motor.ocupado) _estado.value = estadoEnReposo(c)
        aplicarDisparadores(c)
        return c
    }

    /**
     * Carpeta del teléfono elegida con ACTION_OPEN_DOCUMENT_TREE. Si es otra
     * carpeta distinta de la anterior se empieza de cero: la base (lo que se
     * sincronizó) era de la otra carpeta y usarla haría creer que se borró todo.
     */
    fun elegirCarpeta(arbol: Uri) {
        val permisos = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(arbol, permisos)
        val nombre = runCatching { CarpetaLocal(context, arbol).nombre() }.getOrNull()
        val anterior = _config.value.carpetaUri
        modificar { c ->
            if (c.carpetaUri == arbol.toString()) c
            else c.copy(
                carpetaUri = arbol.toString(), carpetaNombre = nombre, base = emptyMap(), huellas = emptyMap(),
                ultimaSync = 0, redUltimaSync = 0, ultimoResumen = null,
            )
        }
        if (anterior != null && anterior != arbol.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(anterior), permisos) }
        }
        sincronizarAhora()
    }

    /** Cambiar la carpeta del Home es empezar una copia nueva allí. */
    fun cambiarCarpetaRemota(nombre: String) {
        val limpio = nombre.trim().trim('/').replace(Regex("[\\\\:*?\"<>|]"), "-")
        if (limpio.isEmpty() || limpio.split('/').any { it == ".." || it == "." || it.isBlank() }) return
        modificar { c ->
            if (c.carpetaRemota == limpio) c
            else c.copy(carpetaRemota = limpio, base = emptyMap(), ultimaSync = 0, redUltimaSync = 0, ultimoResumen = null)
        }
    }

    fun alConectarWifi(activo: Boolean) {
        modificar { it.copy(alConectarWifi = activo) }
    }

    fun programar(programacion: Programacion, hora: Int, minuto: Int) {
        modificar { it.copy(programacion = programacion, horaDiaria = hora, minutoDiario = minuto) }
    }

    fun sincronizarAhora() {
        if (_config.value.carpetaUri == null) return
        if (!motor.ocupado) _estado.value = EstadoSincronizacion.Sincronizando(0f, 0, "En cola")
        TrabajoSincronizacion.encolarManual(context)
    }

    // --- Ejecución (la llama TrabajoSincronizacion) -----------------------------

    suspend fun ejecutar(modo: ModoDisparo) {
        val uid = almacen.uidActivo ?: return
        val token = tokens.currentToken() ?: return
        val c = almacen.cargar(uid)
        _config.value = c
        if (c.carpetaUri == null) { _estado.value = EstadoSincronizacion.SinConfigurar; return }
        if (modo == ModoDisparo.AUTOMATICO_AL_CONECTAR && !c.alConectarWifi) return

        val wifi = red.wifi()
        if (modo != ModoDisparo.MANUAL) {
            if (wifi == null) {
                if (!motor.ocupado) _estado.value = EstadoSincronizacion.SinConexion
                return
            }
            // "Una vez alcance una conexión WIFI": una vez por conexión. Si ya
            // se sincronizó en esta misma conexión, el aviso repetido no hace nada.
            if (modo == ModoDisparo.AUTOMATICO_AL_CONECTAR && c.redUltimaSync == wifi.networkHandle) return
            // ...y que sea la red local DONDE ESTÁ EL SERVIDOR, no cualquier Wi-Fi.
            if (!motor.servidorAlcanzable(token, wifi)) {
                if (!motor.ocupado) _estado.value = EstadoSincronizacion.SinConexion
                return
            }
        }

        val inicio = System.currentTimeMillis()
        val resumen = try {
            motor.ejecutar(uid, token, modo, wifi) { _estado.value = it } ?: return // ya había una en curso
        } catch (e: Exception) {
            val mensaje = e.message ?: e.javaClass.simpleName
            val guardada = almacen.modificar(uid) { it.copy(ultimoResumen = Resumen(fecha = inicio, modo = modo, mensajeError = mensaje)) }
            _config.value = guardada
            _estado.value = if (e is SyncAbortada && e.sinRed && modo != ModoDisparo.MANUAL) EstadoSincronizacion.SinConexion
            else EstadoSincronizacion.Error(mensaje, guardada.ultimaSync)
            return
        }

        val guardada = almacen.modificar(uid) {
            it.copy(ultimaSync = resumen.fecha, ultimoResumen = resumen, redUltimaSync = wifi?.networkHandle ?: it.redUltimaSync)
        }
        _config.value = guardada
        _estado.value = EstadoSincronizacion.AlDia(resumen.fecha, resumen.conflictos)
    }
}

/**
 * Raíz de composición de File Sync. MainActivity no puede ser la única: el
 * trabajo en segundo plano lo crea WorkManager, a veces sin que la app esté
 * abierta, y necesita el MISMO repositorio que la UI.
 */
object FileSyncModulo {
    @Volatile private var instancia: FileSyncRepository? = null

    fun repositorio(context: Context): FileSyncRepository =
        instancia ?: synchronized(this) {
            instancia ?: run {
                val app = context.applicationContext
                val almacen = AlmacenSync(app)
                FileSyncRepository(app, almacen, TokenManager(app), RedLocal(app), MotorSincronizacion(app, almacen))
                    .also { instancia = it }
            }
        }
}
