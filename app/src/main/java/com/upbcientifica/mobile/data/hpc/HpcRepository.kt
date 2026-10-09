package com.upbcientifica.mobile.data.hpc

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sin
import kotlin.random.Random

enum class EstadoTrabajo { EN_COLA, EJECUTANDO, COMPLETADO, FALLIDO }

data class TrabajoHpc(
    val id: String,
    val nombre: String,
    val comando: String,
    val nodos: Int,
    val cpusPorNodo: Int,
    val memoriaGb: Int,
    val tiempoMaximoMin: Int,
    val estado: EstadoTrabajo,
    /** 0..1; solo tiene sentido en EJECUTANDO (1 si terminó). */
    val progreso: Float,
    val enviadoEn: Long,
)

data class EstadoCluster(
    val nodosActivos: Int,
    val nodosTotales: Int,
    /** 0..1 */
    val usoCpu: Float,
    /** 0..1 */
    val usoGpu: Float,
    val trabajosEnCola: Int,
)

data class SolicitudTrabajo(
    val nombre: String,
    val comando: String,
    val nodos: Int,
    val cpusPorNodo: Int,
    val memoriaGb: Int,
    val tiempoMaximoMin: Int,
)

/**
 * ⚠️ RMI BLOQUEADO — ver README.md, sección "HPC Service (RMI) no es viable
 * tal cual en móvil": `java.rmi` no existe en el subconjunto de OpenJDK que
 * usa Android.
 *
 * Para que la interfaz quede lista, este repositorio SIMULA las tres
 * operaciones del contrato (Contrato_HPC) con la misma forma que tendrán:
 *   EnviarTrabajo          -> [enviarTrabajo]
 *   ConsultarEstadoTrabajo -> [consultarTrabajos]
 *   ObtenerEstadoCluster   -> [estadoCluster]
 *
 * El estado de cada trabajo se deduce del tiempo transcurrido desde que se
 * envió (en cola unos segundos, luego ejecutando, luego terminado), así que la
 * pantalla "ve avanzar" los trabajos al consultar periódicamente, igual que
 * hará con el servicio real. Cuando el equipo decida con Dev C el protocolo
 * (gRPC/REST), solo hay que cambiar el cuerpo de estas funciones.
 */
class HpcRepository {

    /** Un trabajo simulado: lo pedido más el guion de cómo va a evolucionar. */
    private data class Registro(
        val id: String,
        val solicitud: SolicitudTrabajo,
        val enviadoEn: Long,
        val esperaEnColaMs: Long,
        val duracionMs: Long,
        val falla: Boolean,
    )

    private val mutex = Mutex()
    private var siguienteId = 1043
    private val registros = mutableListOf<Registro>().apply {
        val ahora = System.currentTimeMillis()
        add(Registro("1039", SolicitudTrabajo("Dinámica molecular · proteína 4HHB",
            "gromacs mdrun -deffnm md_4hhb", 4, 32, 64, 240), ahora - 3 * HORA, 20_000, 2 * HORA, false))
        add(Registro("1040", SolicitudTrabajo("Entrenamiento CNN · imágenes satelitales",
            "python train.py --epochs 50 --gpu", 2, 16, 32, 120), ahora - HORA, 10_000, 25 * MIN, true))
        add(Registro("1041", SolicitudTrabajo("Simulación CFD · túnel de viento",
            "mpirun -np 64 ./openfoam_run.sh", 2, 32, 64, 180), ahora - 40_000, 5_000, 150_000, false))
        add(Registro("1042", SolicitudTrabajo("Análisis genómico · lote 12",
            "snakemake --cores 16 -s pipeline.smk", 1, 16, 32, 60), ahora - 2_000, 25_000, 90_000, false))
    }

    suspend fun enviarTrabajo(solicitud: SolicitudTrabajo): TrabajoHpc {
        delay(700) // latencia de red simulada
        val registro = mutex.withLock {
            Registro(
                id = (siguienteId++).toString(),
                solicitud = solicitud,
                enviadoEn = System.currentTimeMillis(),
                esperaEnColaMs = Random.nextLong(5_000, 12_000),
                duracionMs = Random.nextLong(45_000, 90_000),
                falla = false,
            ).also { registros.add(it) }
        }
        return registro.aTrabajo(System.currentTimeMillis())
    }

    /** Más recientes primero. */
    suspend fun consultarTrabajos(): List<TrabajoHpc> {
        delay(250)
        val ahora = System.currentTimeMillis()
        return mutex.withLock { registros.map { it.aTrabajo(ahora) } }.sortedByDescending { it.enviadoEn }
    }

    suspend fun estadoCluster(): EstadoCluster {
        val trabajos = consultarTrabajos()
        val propios = trabajos.filter { it.estado == EstadoTrabajo.EJECUTANDO }.sumOf { it.nodos }
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
            enEjecucion < 0 -> EstadoTrabajo.EN_COLA to 0f
            enEjecucion < duracionMs -> EstadoTrabajo.EJECUTANDO to enEjecucion / duracionMs.toFloat()
            falla -> EstadoTrabajo.FALLIDO to 1f
            else -> EstadoTrabajo.COMPLETADO to 1f
        }
        return TrabajoHpc(
            id = id,
            nombre = solicitud.nombre,
            comando = solicitud.comando,
            nodos = solicitud.nodos,
            cpusPorNodo = solicitud.cpusPorNodo,
            memoriaGb = solicitud.memoriaGb,
            tiempoMaximoMin = solicitud.tiempoMaximoMin,
            estado = estado,
            progreso = progreso,
            enviadoEn = enviadoEn,
        )
    }

    private companion object {
        const val NODOS_TOTALES = 16
        const val MIN = 60_000L
        const val HORA = 60 * MIN
    }
}
