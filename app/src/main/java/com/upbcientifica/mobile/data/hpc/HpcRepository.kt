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
import kotlin.math.sin
import kotlin.random.Random

enum class EstadoTrabajo { EN_COLA, EJECUTANDO, COMPLETADO, FALLIDO, CANCELADO }

data class TrabajoHpc(
    val id: String,
    val nombre: String,
    val comando: String,
    val prioridad: Int,
    val estado: EstadoTrabajo,
    /**
     * 0..1 en EJECUTANDO. null si el servidor no lo informa: job-api no da
     * porcentaje de avance, y la pantalla muestra entonces una barra indeterminada.
     */
    val progreso: Float?,
    val enviadoEn: Long,
    /** Salida del comando (solo COMPLETADO). */
    val resultado: String? = null,
    /** Motivo del fallo (solo FALLIDO). */
    val mensajeError: String? = null,
    /** Recursos pedidos. Solo los tiene el simulador: job-api no los recibe. */
    val recursos: RecursosTrabajo? = null,
)

data class RecursosTrabajo(val nodos: Int, val cpusPorNodo: Int, val memoriaGb: Int, val tiempoMaximoMin: Int)

data class EstadoCluster(
    val nodosActivos: Int,
    val nodosTotales: Int,
    /** 0..1 */
    val usoCpu: Float,
    /** 0..1 */
    val usoGpu: Float,
    val trabajosEnCola: Int,
)

/** Lo que pide job-api para encolar un trabajo (SubmitJobRequest.java). */
data class SolicitudTrabajo(
    val nombre: String,
    val comando: String,
    /** 1 (baja) a 10 (alta). */
    val prioridad: Int,
)

/**
 * HPC Service.
 *
 * Con [api] habla con job-api por REST (ver [HpcApi]): enviar, listar y
 * cancelar son reales y los ejecuta el gestor del clúster.
 *
 * Sin [api] SIMULA las operaciones con la misma forma, para trabajar la
 * interfaz sin backend: el estado se deduce del tiempo transcurrido desde el
 * envío (en cola unos segundos, luego ejecutando, luego terminado).
 *
 * El estado del clúster ([estadoCluster]) solo existe en el simulador: job-api
 * todavía no expone `GET /cluster/estado` (Contrato_HPC). Con el backend real
 * devuelve null y la pantalla lo dice en vez de inventar cifras.
 */
class HpcRepository(private val api: HpcApi? = null) {

    suspend fun enviarTrabajo(solicitud: SolicitudTrabajo): TrabajoHpc {
        if (api == null) return simulador.enviar(solicitud)
        return llamar {
            val creado = api.enviarTrabajo(EnviarTrabajoDto(solicitud.nombre, solicitud.comando, solicitud.prioridad))
            api.obtenerTrabajo(creado.id).aModelo()
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

    suspend fun estadoCluster(): EstadoCluster? {
        if (api == null) return simulador.estadoCluster()
        return null
    }

    /**
     * Convierte los errores de red en mensajes que se pueden mostrar tal cual.
     * job-api responde `{ status, error, mensaje }`; sin esto el usuario vería
     * "HTTP 503" en vez de "El servicio gestor-cluster no está disponible...".
     */
    private suspend fun <T> llamar(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: HttpException) {
        val mensaje = runCatching {
            JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("mensaje")
        }.getOrNull()
        throw IllegalStateException(
            when {
                !mensaje.isNullOrBlank() -> mensaje
                e.code() == 401 -> "La sesión no es válida. Vuelve a iniciar sesión."
                else -> "El servicio HPC respondió ${e.code()}"
            }
        )
    } catch (e: IOException) {
        throw IllegalStateException("No hay conexión con el servicio HPC (job-api). ¿Está encendido?")
    }

    private fun TrabajoDto.aModelo() = TrabajoHpc(
        id = id,
        nombre = nombre,
        comando = comando,
        prioridad = prioridad,
        estado = when (estado) {
            "QUEUED" -> EstadoTrabajo.EN_COLA
            "RUNNING" -> EstadoTrabajo.EJECUTANDO
            "COMPLETED" -> EstadoTrabajo.COMPLETADO
            "CANCELLED" -> EstadoTrabajo.CANCELADO
            else -> EstadoTrabajo.FALLIDO
        },
        progreso = null,
        enviadoEn = fechaCreacion?.let(::leerFechaIso) ?: 0L,
        resultado = resultado,
        mensajeError = mensajeError,
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

    /** Un trabajo simulado: lo pedido más el guion de cómo va a evolucionar. */
    private data class Registro(
        val id: String,
        val solicitud: SolicitudTrabajo,
        val recursos: RecursosTrabajo,
        val enviadoEn: Long,
        val esperaEnColaMs: Long,
        val duracionMs: Long,
        val falla: Boolean,
        val cancelado: Boolean = false,
    )

    private val mutex = Mutex()
    private var siguienteId = 1043
    private val registros = mutableListOf<Registro>().apply {
        val ahora = System.currentTimeMillis()
        add(Registro("1039", SolicitudTrabajo("Dinámica molecular · proteína 4HHB", "gromacs mdrun -deffnm md_4hhb", 7),
            RecursosTrabajo(4, 32, 64, 240), ahora - 3 * HORA, 20_000, 2 * HORA, false))
        add(Registro("1040", SolicitudTrabajo("Entrenamiento CNN · imágenes satelitales", "python train.py --epochs 50 --gpu", 5),
            RecursosTrabajo(2, 16, 32, 120), ahora - HORA, 10_000, 25 * MIN, true))
        add(Registro("1041", SolicitudTrabajo("Simulación CFD · túnel de viento", "mpirun -np 64 ./openfoam_run.sh", 8),
            RecursosTrabajo(2, 32, 64, 180), ahora - 40_000, 5_000, 150_000, false))
        add(Registro("1042", SolicitudTrabajo("Análisis genómico · lote 12", "snakemake --cores 16 -s pipeline.smk", 3),
            RecursosTrabajo(1, 16, 32, 60), ahora - 2_000, 25_000, 90_000, false))
    }

    suspend fun enviar(solicitud: SolicitudTrabajo): TrabajoHpc {
        delay(700) // latencia de red simulada
        val registro = mutex.withLock {
            Registro(
                id = (siguienteId++).toString(),
                solicitud = solicitud,
                recursos = RecursosTrabajo(1, 16, 32, 60),
                enviadoEn = System.currentTimeMillis(),
                esperaEnColaMs = Random.nextLong(5_000, 12_000),
                duracionMs = Random.nextLong(45_000, 90_000),
                falla = false,
            ).also { registros.add(it) }
        }
        return registro.aTrabajo(System.currentTimeMillis())
    }

    suspend fun trabajos(): List<TrabajoHpc> {
        delay(250)
        val ahora = System.currentTimeMillis()
        return mutex.withLock { registros.map { it.aTrabajo(ahora) } }.sortedByDescending { it.enviadoEn }
    }

    suspend fun cancelar(id: String) {
        delay(300)
        mutex.withLock {
            val i = registros.indexOfFirst { it.id == id }
            if (i >= 0) registros[i] = registros[i].copy(cancelado = true)
        }
    }

    suspend fun estadoCluster(): EstadoCluster {
        val trabajos = trabajos()
        val propios = trabajos.filter { it.estado == EstadoTrabajo.EJECUTANDO }.sumOf { it.recursos?.nodos ?: 0 }
        // Carga de otros usuarios del clúster, oscilando suavemente.
        val t = System.currentTimeMillis() / 20_000.0
        val ajenos = (7 + 2 * sin(t)).toInt()
        val activos = (propios + ajenos).coerceAtMost(NODOS_TOTALES)
        return EstadoCluster(
            nodosActivos = activos,
            nodosTotales = NODOS_TOTALES,
            usoCpu = (activos / NODOS_TOTALES.toFloat() * 0.92f).coerceIn(0f, 1f),
            usoGpu = (0.45f + 0.2f * sin(t * 1.7).toFloat()).coerceIn(0f, 1f),
            trabajosEnCola = 3 + trabajos.count { it.estado == EstadoTrabajo.EN_COLA },
        )
    }

    private fun Registro.aTrabajo(ahora: Long): TrabajoHpc {
        val transcurrido = ahora - enviadoEn
        val enEjecucion = transcurrido - esperaEnColaMs
        val (estado, progreso) = when {
            cancelado -> EstadoTrabajo.CANCELADO to 0f
            enEjecucion < 0 -> EstadoTrabajo.EN_COLA to 0f
            enEjecucion < duracionMs -> EstadoTrabajo.EJECUTANDO to enEjecucion / duracionMs.toFloat()
            falla -> EstadoTrabajo.FALLIDO to 1f
            else -> EstadoTrabajo.COMPLETADO to 1f
        }
        return TrabajoHpc(
            id = id,
            nombre = solicitud.nombre,
            comando = solicitud.comando,
            prioridad = solicitud.prioridad,
            estado = estado,
            progreso = progreso,
            enviadoEn = enviadoEn,
            resultado = if (estado == EstadoTrabajo.COMPLETADO) "Terminado sin errores" else null,
            mensajeError = if (estado == EstadoTrabajo.FALLIDO) "Código de salida 1: memoria insuficiente" else null,
            recursos = recursos,
        )
    }

    private companion object {
        const val NODOS_TOTALES = 16
        const val MIN = 60_000L
        const val HORA = 60 * MIN
    }
}
