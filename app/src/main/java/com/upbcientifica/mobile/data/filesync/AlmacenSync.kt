package com.upbcientifica.mobile.data.filesync

import android.content.Context
import com.google.gson.Gson
import java.io.File

/** IniciarSincronizacion.modoDisparo del Contrato_FileSync. */
enum class ModoDisparo { MANUAL, AUTOMATICO_AL_CONECTAR, PROGRAMADO }

/** Frecuencia de la sincronización programada (ProgramarSincronizacion). */
enum class Programacion { NINGUNA, CADA_HORA, DIARIA }

/** Lo que la UI necesita saber de File Sync para pintar el indicador superior. */
sealed class EstadoSincronizacion {
    /** Aún no se eligió qué carpeta del teléfono respaldar. */
    data object SinConfigurar : EstadoSincronizacion()

    /** [progreso] va de 0 a 1; [pendientes] son los cambios que faltan. */
    data class Sincronizando(val progreso: Float, val pendientes: Int, val detalle: String = "") :
        EstadoSincronizacion()

    /** [ultimaSincronizacion] = 0 si nunca se completó una. */
    data class AlDia(val ultimaSincronizacion: Long, val conflictos: Int = 0) : EstadoSincronizacion()

    /** Sin Wi-Fi o el Wi-Fi actual no llega al servidor: se espera a la red local. */
    data object SinConexion : EstadoSincronizacion()

    data class Error(val mensaje: String, val ultimaSincronizacion: Long) : EstadoSincronizacion()
}

enum class TipoEvento { SUBIDO, BAJADO, BORRADO_EN_TELEFONO, BORRADO_EN_SERVIDOR, CONFLICTO, ERROR }

data class Evento(val ruta: String = "", val tipo: TipoEvento = TipoEvento.SUBIDO, val detalle: String? = null)

/** Resultado de una sincronización, para la pantalla "Último resultado". */
data class Resumen(
    val fecha: Long = 0,
    val modo: ModoDisparo = ModoDisparo.MANUAL,
    val subidos: Int = 0,
    val bajados: Int = 0,
    val borrados: Int = 0,
    val conflictos: Int = 0,
    val errores: Int = 0,
    val eventos: List<Evento> = emptyList(),
    val mensajeError: String? = null,
)

/** Hash ya calculado de un archivo local, válido mientras no cambien tamaño ni fecha. */
data class Huella(val tamano: Long = 0, val modificado: Long = 0, val hash: String = "")

/**
 * Configuración y estado de la sincronización de UN usuario en este teléfono.
 * Todos los campos tienen valor por defecto: Gson usa el constructor sin
 * argumentos y un JSON antiguo al que le falte un campo no rompe nada.
 */
data class ConfigSync(
    /** Carpeta del teléfono (Uri del árbol SAF), o null si no se eligió. */
    val carpetaUri: String? = null,
    val carpetaNombre: String? = null,
    /** Carpeta del Home donde queda la copia (la que se ve en "Mis archivos"). */
    val carpetaRemota: String = CARPETA_REMOTA_POR_DEFECTO,
    val alConectarWifi: Boolean = true,
    val programacion: Programacion = Programacion.NINGUNA,
    val horaDiaria: Int = 22,
    val minutoDiario: Int = 0,
    /** Ruta (relativa al Home) -> hash en la última sincronización correcta. */
    val base: Map<String, String> = emptyMap(),
    /** Ruta relativa a la carpeta local -> huella, para no recalcular hashes. */
    val huellas: Map<String, Huella> = emptyMap(),
    val ultimaSync: Long = 0,
    /** Red Wi-Fi (Network.networkHandle) en la que se hizo la última. */
    val redUltimaSync: Long = 0,
    val ultimoResumen: Resumen? = null,
) {
    companion object {
        const val CARPETA_REMOTA_POR_DEFECTO = "Respaldo celular"
    }
}

/**
 * Persiste [ConfigSync] en un JSON por usuario (filesDir/filesync/<uid>.json)
 * y recuerda qué usuario tiene la sesión abierta, para que el trabajo en
 * segundo plano sepa de quién es la carpeta aunque la app esté cerrada.
 */
class AlmacenSync(context: Context) {

    private val dir = File(context.filesDir, "filesync").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("filesync", Context.MODE_PRIVATE)
    private val gson = Gson()

    var uidActivo: String?
        get() = prefs.getString("uid", null)
        set(valor) = prefs.edit().putString("uid", valor).apply()

    private fun archivo(uid: String) = File(dir, uid.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    @Synchronized
    fun cargar(uid: String): ConfigSync {
        val f = archivo(uid)
        if (!f.exists()) return ConfigSync()
        return try {
            gson.fromJson(f.readText(), ConfigSync::class.java) ?: ConfigSync()
        } catch (e: Exception) {
            ConfigSync() // archivo dañado: se empieza de cero (como una primera sincronización)
        }
    }

    @Synchronized
    fun guardar(uid: String, config: ConfigSync) {
        val f = archivo(uid)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(gson.toJson(config))
        if (!tmp.renameTo(f)) {
            f.delete(); tmp.renameTo(f)
        }
    }

    @Synchronized
    fun modificar(uid: String, cambio: (ConfigSync) -> ConfigSync): ConfigSync =
        cambio(cargar(uid)).also { guardar(uid, it) }
}
