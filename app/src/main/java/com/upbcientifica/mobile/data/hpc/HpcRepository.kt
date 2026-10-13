package com.upbcientifica.mobile.data.hpc

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/** Estados de Contrato_HPC. GUARDANDO = COMPLETADO_PENDIENTE_ALMACENAMIENTO. */
enum class EstadoTrabajo { PENDIENTE, EN_COLA, EJECUTANDO, GUARDANDO, COMPLETADO, FALLIDO, FALLIDO_TIMEOUT, CANCELADO }

data class TrabajoHpc(
    val id: String,
    val nombre: String,
    /** Código fuente en el Home ("/Proyectos/pi.c"). */
    val rutaCodigo: String,
    val rutaDataset: String?,
    val procesos: Int,
    val tiempoMaximoS: Int,
    /** BAJA, NORMAL o ALTA. */
    val prioridad: String,
    val estado: EstadoTrabajo,
    val enviadoEn: Long,
    val posicionCola: Int = 0,
    val nodos: List<String> = emptyList(),
    /** Carpeta del Home con la salida ("/Resultados HPC/pi-1a2b3c4d"). */
    val rutaResultado: String? = null,
    /** Salida parcial de un FALLIDO_TIMEOUT. */
    val incompleto: Boolean = false,
    val mensajeError: String? = null,
    val originalId: String? = null,
)

data class NodoCluster(val id: String, val estado: String, val capacidad: Int, val carga: Int, val externo: Boolean)

data class EstadoCluster(
    val nodosTotales: Int,
    val nodosDisponibles: Int,
    val nodosEnFalla: Int,
    val procesosTotales: Int,
    val procesosEnUso: Int,
    val trabajosEnCola: Int,
    val trabajosEjecutando: Int,
    /** job-runner (hpc-master) responde. Sin él los trabajos esperan en cola. */
    val ejecutorConectado: Boolean,
    val nodos: List<NodoCluster>,
) {
    val uso: Float get() = if (procesosTotales == 0) 0f else procesosEnUso / procesosTotales.toFloat()
}

/** EnviarTrabajo (Contrato_HPC §3.1): código y dataset del Home, procesos, tiempo y prioridad. */
data class SolicitudTrabajo(
    val nombre: String,
    val rutaCodigo: String,
    val rutaDataset: String?,
    val procesos: Int,
    val tiempoMaximoS: Int,
    val prioridad: String,
    val parametros: Map<String, String> = emptyMap(),
)

/**
 * HPC Service.
 *
 * Con [api] habla con job-api por REST (ver [HpcApi]). El trabajo es código
 * fuente y dataset del Home: el gestor los valida contra Storage, los ejecuta
 * con mpirun en el clúster y deja el resultado en "Resultados HPC/" del Home.
 *
 * Sin [api] SIMULA las operaciones con la misma forma, para trabajar la
 * interfaz sin backend.
 */
class HpcRepository(private val api: HpcApi? = null) {

    suspend fun enviarTrabajo(s: SolicitudTrabajo): TrabajoHpc {
        if (api == null) return simulador.enviar(s)
        return llamar {
            val creado = api.enviarTrabajo(EnviarTrabajoDto(
                s.nombre, s.rutaCodigo, s.rutaDataset?.takeIf { it.isNotBlank() }, s.procesos,
                s.tiempoMaximoS, s.prioridad, s.parametros))
            api.obtenerTrabajo(creado.trabajoId.orEmpty()).aModelo()
        }
    }

    /** Más recientes primero. */
    suspend fun consultarTrabajos(): List<TrabajoHpc> {
        if (api == null) return simulador.trabajos()
        return llamar { api.listarTrabajos().map { it.aModelo() } }.sortedByDescending { it.enviadoEn }
    }

    suspend fun cancelarTrabajo(id: String) {
        if (api == null) return simulador.cancelar(id)
        llamar { api.cancelarTrabajo(id) }
    }

    /** Devuelve el id del trabajo nuevo y la advertencia del contrato (usa el código ACTUAL). */
    suspend fun reintentarTrabajo(id: String): Pair<String, String?> {
        if (api == null) return simulador.reintentar(id)
        return llamar { api.reintentarTrabajo(id, emptyMap()).let { it.trabajoId.orEmpty() to it.advertencia } }
    }

    suspend fun logs(id: String): String {
        if (api == null) return "=== salida estándar ===\nrango 0/4: listo\nrango 1/4: listo\n"
        return llamar { api.logsTrabajo(id).logsEjecucion.orEmpty() }
    }

    suspend fun estadoCluster(): EstadoCluster {
        if (api == null) return simulador.estadoCluster()
        return llamar {
            val c = api.estadoCluster()
            EstadoCluster(
                nodosTotales = c.nodosTotales ?: 0, nodosDisponibles = c.nodosDisponibles ?: 0,
                nodosEnFalla = c.nodosEnFalla ?: 0, procesosTotales = c.procesosTotales ?: 0,
                procesosEnUso = c.procesosEnUso ?: 0, trabajosEnCola = c.trabajosEnCola ?: 0,
                trabajosEjecutando = c.trabajosEjecutando ?: 0, ejecutorConectado = c.ejecutorConectado == true,
                nodos = c.nodos.orEmpty().map {
                    NodoCluster(it.id.orEmpty(), it.estado.orEmpty(), it.capacidadProcesos ?: 0,
                        it.cargaActual ?: 0, it.origen == "EXTERNO")
                }
            )
        }
    }

    /**
     * Convierte los errores de red en mensajes que se pueden mostrar tal cual.
     * job-api responde `{ status, error, codigo, mensaje }` (codigo = HPC-xxx).
     */
    private suspend fun <T> llamar(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: HttpException) {
        val cuerpo = runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()) }.getOrNull()
        val mensaje = cuerpo?.optString("mensaje")
        val codigo = cuerpo?.optString("codigo")?.takeIf { it.isNotBlank() && it != "null" }
        throw IllegalStateException(
            when {
                !mensaje.isNullOrBlank() -> if (codigo != null) "$mensaje ($codigo)" else mensaje
                e.code() == 401 -> "La sesión no es válida. Vuelve a iniciar sesión."
                e.code() == 403 -> "Tu perfil no tiene acceso al servicio HPC."
                else -> "El servicio HPC respondió ${e.code()}"
            }
        )
    } catch (e: IOException) {
        throw IllegalStateException("No hay conexión con el servicio HPC (job-api). ¿Está encendido?")
    }

    private fun TrabajoDto.aModelo() = TrabajoHpc(
        id = id,
        nombre = nombreTrabajo.orEmpty(),
        rutaCodigo = rutaCodigo.orEmpty(),
        rutaDataset = rutaDataset,
        procesos = numeroProcesos ?: 1,
        tiempoMaximoS = tiempoMaximoEjecucion ?: 0,
        prioridad = prioridad ?: "NORMAL",
        estado = when (estado) {
            "PENDIENTE" -> EstadoTrabajo.PENDIENTE
            "EN_COLA" -> EstadoTrabajo.EN_COLA
            "EJECUTANDO" -> EstadoTrabajo.EJECUTANDO
            "COMPLETADO_PENDIENTE_ALMACENAMIENTO" -> EstadoTrabajo.GUARDANDO
            "COMPLETADO" -> EstadoTrabajo.COMPLETADO
            "FALLIDO_TIMEOUT" -> EstadoTrabajo.FALLIDO_TIMEOUT
            "CANCELADO" -> EstadoTrabajo.CANCELADO
            else -> EstadoTrabajo.FALLIDO
        },
        enviadoEn = fechaEnvio?.let(::leerFechaIso) ?: 0L,
        posicionCola = posicionCola ?: 0,
        nodos = nodosAsignados.orEmpty(),
        rutaResultado = rutaResultado,
        incompleto = resultadoIncompleto == true,
        mensajeError = mensajeError,
        originalId = trabajoOriginalId,
    )

    private val simulador = Simulador()

    private companion object {
        /**
         * "2026-10-11T03:40:17.716941500Z" -> milisegundos. Sin java.time porque
         * minSdk es 24: se leen los segundos con SimpleDateFormat y la fracción a mano.
         */
        fun leerFechaIso(texto: String): Long? = runCatching {
            val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val base = formato.parse(texto.take(19))!!.time
            val fraccion = texto.drop(19).removePrefix(".").takeWhile { it.isDigit() }
            base + (fraccion.padEnd(3, '0').take(3).toLongOrNull() ?: 0L)
        }.getOrNull()
    }
}

/** Trabajos y clúster de mentira, para usar la pantalla sin backend. */
private class Simulador {

    private data class Registro(
        val trabajo: TrabajoHpc,
        val esperaMs: Long,
        val duracionMs: Long,
        val falla: Boolean = false,
        val cancelado: Boolean = false,
    )

    private val mutex = Mutex()
    private var siguienteId = 1043
    private val registros = mutableListOf<Registro>().apply {
        val ahora = System.currentTimeMillis()
        add(Registro(nuevo("Dinámica molecular · 4HHB", "/Proyectos/md.c", "/Datos/4hhb.pdb", 8, ahora - 3 * HORA), 20_000, 2 * HORA))
        add(Registro(nuevo("Monte Carlo · π", "/Proyectos/pi.c", null, 4, ahora - HORA), 10_000, 10 * MIN, falla = true))
        add(Registro(nuevo("Simulación CFD · túnel de viento", "/Proyectos/cfd.cpp", "/Datos/malla.dat", 8, ahora - 40_000), 5_000, 150_000))
        add(Registro(nuevo("Análisis genómico · lote 12", "/Proyectos/genoma.py", "/Datos/lote12.csv", 4, ahora - 2_000), 25_000, 90_000))
    }

    private fun nuevo(nombre: String, codigo: String, dataset: String?, procesos: Int, enviado: Long, prioridad: String = "NORMAL") =
        TrabajoHpc((siguienteId++).toString() + "-demo", nombre, codigo, dataset, procesos, 3600, prioridad, EstadoTrabajo.EN_COLA, enviado)

    suspend fun enviar(s: SolicitudTrabajo): TrabajoHpc {
        delay(700) // latencia de red simulada
        val r = mutex.withLock {
            Registro(nuevo(s.nombre, s.rutaCodigo, s.rutaDataset, s.procesos, System.currentTimeMillis(), s.prioridad),
                Random.nextLong(5_000, 12_000), Random.nextLong(45_000, 90_000)).also { registros.add(it) }
        }
        return r.avanzar(System.currentTimeMillis())
    }

    suspend fun trabajos(): List<TrabajoHpc> {
        delay(250)
        val ahora = System.currentTimeMillis()
        return mutex.withLock { registros.map { it.avanzar(ahora) } }.sortedByDescending { it.enviadoEn }
    }

    suspend fun cancelar(id: String) {
        delay(300)
        mutex.withLock {
            val i = registros.indexOfFirst { it.trabajo.id == id }
            if (i >= 0) registros[i] = registros[i].copy(cancelado = true)
        }
    }

    suspend fun reintentar(id: String): Pair<String, String?> {
        delay(300)
        return mutex.withLock {
            val o = registros.first { it.trabajo.id == id }.trabajo
            val t = nuevo(o.nombre, o.rutaCodigo, o.rutaDataset, o.procesos, System.currentTimeMillis(), o.prioridad)
                .copy(originalId = o.id)
            registros.add(Registro(t, 4_000, 40_000))
            t.id to "El reintento usa la versión actual del código."
        }
    }

    suspend fun estadoCluster(): EstadoCluster {
        val trabajos = trabajos()
        val enUso = trabajos.filter { it.estado == EstadoTrabajo.EJECUTANDO }.sumOf { it.procesos }.coerceAtMost(PROCESOS)
        val nodos = (1..4).map { i ->
            val carga = (enUso - (i - 1) * 4).coerceIn(0, 4)
            NodoCluster("hpc-worker$i", if (carga >= 4) "OCUPADO" else "DISPONIBLE", 4, carga, false)
        }
        return EstadoCluster(4, nodos.count { it.estado == "DISPONIBLE" }, 0, PROCESOS, enUso,
            trabajos.count { it.estado == EstadoTrabajo.EN_COLA }, trabajos.count { it.estado == EstadoTrabajo.EJECUTANDO },
            true, nodos)
    }

    private fun Registro.avanzar(ahora: Long): TrabajoHpc {
        val enEjecucion = ahora - trabajo.enviadoEn - esperaMs
        return when {
            cancelado -> trabajo.copy(estado = EstadoTrabajo.CANCELADO)
            enEjecucion < 0 -> trabajo.copy(estado = EstadoTrabajo.EN_COLA, posicionCola = 1)
            enEjecucion < duracionMs -> trabajo.copy(estado = EstadoTrabajo.EJECUTANDO, nodos = listOf("hpc-worker1", "hpc-worker2"))
            falla -> trabajo.copy(estado = EstadoTrabajo.FALLIDO, mensajeError = "mpirun terminó con código 1")
            else -> trabajo.copy(estado = EstadoTrabajo.COMPLETADO,
                rutaResultado = "/Resultados HPC/${trabajo.nombre.substringBefore(' ')}-${trabajo.id.take(8)}")
        }
    }

    private companion object {
        const val PROCESOS = 16
        const val MIN = 60_000L
        const val HORA = 60 * MIN
    }
}
