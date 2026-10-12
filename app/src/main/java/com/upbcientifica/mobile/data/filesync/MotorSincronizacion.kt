package com.upbcientifica.mobile.data.filesync

import android.content.Context
import android.net.Network
import com.google.protobuf.ByteString
import com.upbcientifica.filesync.Accion
import com.upbcientifica.filesync.AccionArchivo
import com.upbcientifica.filesync.DownloadRequest
import com.upbcientifica.filesync.EliminarRequest
import com.upbcientifica.filesync.EstadoRequest
import com.upbcientifica.filesync.FileChunk
import com.upbcientifica.filesync.FileMeta
import com.upbcientifica.filesync.FileSyncGrpcKt.FileSyncCoroutineStub
import com.upbcientifica.filesync.SyncRequest
import com.upbcientifica.mobile.network.GrpcClient
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fallo que detiene toda la sincronización (no solo un archivo). */
class SyncAbortada(mensaje: String, val sinRed: Boolean = false) : Exception(mensaje)

/**
 * Ejecuta UNA sincronización entre la carpeta del teléfono y su carpeta del
 * Home, hablando gRPC con node-sync. Es el mismo algoritmo que el cliente de
 * escritorio:
 *
 *  1. Recorre la carpeta y calcula el SHA-256 de cada archivo (con caché por
 *     tamaño+fecha para no releer lo que no cambió).
 *  2. IniciarSync con cada archivo, su hash y su hash BASE (el de la última
 *     sincronización correcta), más los que estaban y ya no están (eliminado).
 *     El servidor decide; el teléfono no reimplementa las reglas.
 *  3. Aplica las acciones: subir, bajar, borrar aquí, borrar allá, o
 *     CONFLICTO -> MANTENER_AMBOS (la copia del teléfono se renombra y sube,
 *     y se baja la del servidor). Nunca se descarta contenido en silencio.
 *  4. Guarda la nueva base. Si se corta a mitad, lo ya hecho queda bien
 *     registrado y el resto se retoma en la siguiente (contrato, §3.7).
 */
class MotorSincronizacion(private val context: Context, private val almacen: AlmacenSync) {

    /** Nunca dos sincronizaciones a la vez (contrato: ni en paralelo ni solapadas). */
    private val enCurso = Mutex()

    val ocupado: Boolean get() = enCurso.isLocked

    /**
     * Comprueba que el servidor responde por [red] en pocos segundos. Es la
     * forma de saber que el Wi-Fi actual es "la red local donde se encuentra
     * el servidor" sin pedir permisos de ubicación para leer el SSID.
     */
    suspend fun servidorAlcanzable(token: String, red: Network?): Boolean = withContext(Dispatchers.IO) {
        val canal = GrpcClient.canal(red)
        try {
            FileSyncCoroutineStub(canal).withDeadlineAfter(4, TimeUnit.SECONDS)
                .estadoSync(EstadoRequest.newBuilder().setDispositivo(dispositivo()).build(), GrpcClient.autorizacion(token))
            true
        } catch (e: StatusException) {
            // Responde pero rechaza el token: el servidor SÍ está en esta red.
            e.status.code == Status.Code.UNAUTHENTICATED
        } catch (e: Exception) {
            false
        } finally {
            canal.shutdownNow()
        }
    }

    /** Devuelve null si ya había otra sincronización en marcha. */
    suspend fun ejecutar(
        uid: String,
        token: String,
        modo: ModoDisparo,
        red: Network?,
        progreso: (EstadoSincronizacion) -> Unit,
    ): Resumen? {
        if (!enCurso.tryLock()) return null
        try {
            return withContext(Dispatchers.IO) { sincronizar(uid, token, modo, red, progreso) }
        } finally {
            enCurso.unlock()
        }
    }

    private suspend fun sincronizar(
        uid: String, token: String, modo: ModoDisparo, red: Network?,
        progreso: (EstadoSincronizacion) -> Unit,
    ): Resumen {
        val config = almacen.cargar(uid)
        val uri = config.carpetaUri ?: throw SyncAbortada("Elige la carpeta del teléfono que quieres respaldar")
        val carpeta = CarpetaLocal(context, android.net.Uri.parse(uri))
        if (!carpeta.accesible()) {
            throw SyncAbortada("No se puede abrir la carpeta del teléfono. Vuelve a elegirla.")
        }
        val remota = config.carpetaRemota.trim('/', ' ')
        fun aHome(rel: String) = "$remota/$rel"
        fun aLocal(ruta: String) = ruta.removePrefix("$remota/")

        // --- 1. Estado local, con la caché de hashes ---------------------------
        progreso(EstadoSincronizacion.Sincronizando(0f, 0, "Revisando la carpeta del teléfono"))
        val entradas = carpeta.listar()
        val huellas = HashMap<String, Huella>(entradas.size)
        val locales = HashMap<String, String>(entradas.size) // ruta en el Home -> hash
        for (e in entradas) {
            val previa = config.huellas[e.rel]
            val hash = if (previa != null && previa.tamano == e.tamano && previa.modificado == e.modificado && e.modificado != 0L) {
                previa.hash
            } else {
                CarpetaLocal.sha256(carpeta.abrir(e.documentoId))
            }
            huellas[e.rel] = Huella(e.tamano, e.modificado, hash)
            locales[aHome(e.rel)] = hash
        }

        val base = HashMap(config.base.filterKeys { it.startsWith("$remota/") })
        val metas = locales.map { (ruta, hash) ->
            FileMeta.newBuilder().setRuta(ruta).setHash(hash).setHashBase(base[ruta] ?: "").build()
        } + base.filterKeys { it !in locales }.map { (ruta, hashBase) ->
            FileMeta.newBuilder().setRuta(ruta).setHashBase(hashBase).setEliminado(true).build()
        }

        // --- 2. El servidor decide -------------------------------------------
        val canal = GrpcClient.canal(red)
        val md = GrpcClient.autorizacion(token)
        val stub = FileSyncCoroutineStub(canal)
        val eventos = mutableListOf<Evento>()
        var subidos = 0; var bajados = 0; var borrados = 0; var conflictos = 0; var errores = 0
        try {
            val resp = llamar {
                stub.withDeadlineAfter(60, TimeUnit.SECONDS).iniciarSync(
                    SyncRequest.newBuilder()
                        .setDispositivo(dispositivo())
                        .setCarpeta(remota)
                        .setModoDisparo(modo.name)
                        .addAllArchivos(metas)
                        .build(),
                    md,
                )
            }
            val pendientes = resp.accionesList.filter { it.accion != Accion.IGUAL }
            for (a in resp.accionesList) if (a.accion == Accion.IGUAL) base[a.ruta] = a.hashServidor

            // --- 3. Aplicar ----------------------------------------------------
            val transferencias = stub.withDeadlineAfter(30, TimeUnit.MINUTES)
            for ((i, a) in pendientes.withIndex()) {
                progreso(EstadoSincronizacion.Sincronizando(i / pendientes.size.toFloat(), pendientes.size - i, a.ruta.substringAfterLast('/')))
                try {
                    when (a.accion) {
                        Accion.SUBIR -> {
                            subir(transferencias, md, carpeta, a.ruta, aLocal(a.ruta))
                            base[a.ruta] = locales.getValue(a.ruta)
                            subidos++; eventos += Evento(a.ruta, TipoEvento.SUBIDO)
                        }
                        Accion.BAJAR -> {
                            bajar(transferencias, md, carpeta, a, aLocal(a.ruta))
                            base[a.ruta] = a.hashServidor
                            bajados++; eventos += Evento(a.ruta, TipoEvento.BAJADO)
                        }
                        Accion.BORRAR -> {
                            carpeta.borrar(aLocal(a.ruta))
                            base.remove(a.ruta); huellas.remove(aLocal(a.ruta))
                            borrados++; eventos += Evento(a.ruta, TipoEvento.BORRADO_EN_TELEFONO)
                        }
                        Accion.BORRAR_SERVIDOR -> {
                            llamar { transferencias.eliminarArchivo(EliminarRequest.newBuilder().setRuta(a.ruta).build(), md) }
                            base.remove(a.ruta)
                            borrados++; eventos += Evento(a.ruta, TipoEvento.BORRADO_EN_SERVIDOR)
                        }
                        Accion.CONFLICTO -> {
                            // MANTENER_AMBOS: la del teléfono pasa a ser la "copia de conflicto".
                            val local = aLocal(a.ruta)
                            if (carpeta.existe(local)) {
                                val conflicto = aLocal(a.rutaConflicto)
                                carpeta.renombrar(local, conflicto.substringAfterLast('/'))
                                subir(transferencias, md, carpeta, a.rutaConflicto, conflicto)
                                base[a.rutaConflicto] = CarpetaLocal.sha256(carpeta.abrirRuta(conflicto))
                            }
                            bajar(transferencias, md, carpeta, a, local)
                            base[a.ruta] = a.hashServidor
                            conflictos++
                            eventos += Evento(a.ruta, TipoEvento.CONFLICTO, a.rutaConflicto.substringAfterLast('/'))
                        }
                        else -> Unit
                    }
                } catch (e: SyncAbortada) {
                    throw e
                } catch (e: Exception) {
                    // Un archivo que falla no detiene los demás; su base no cambia
                    // y se reintenta en la próxima sincronización.
                    errores++
                    eventos += Evento(a.ruta, TipoEvento.ERROR, e.message)
                }
            }
            progreso(EstadoSincronizacion.Sincronizando(1f, 0, "Terminando"))
        } finally {
            canal.shutdownNow()
            // --- 4. Lo hecho queda registrado aunque se haya cortado a mitad. --
            val vivos = base.filterKeys { ruta -> ruta in locales || carpeta.existe(aLocal(ruta)) }
            almacen.modificar(uid) { c ->
                c.copy(
                    base = c.base.filterKeys { !it.startsWith("$remota/") } + vivos,
                    // Los archivos que se bajaron o renombraron tienen otra fecha:
                    // se vuelven a medir la próxima vez (una sola lectura).
                    huellas = huellas.filterKeys { rel -> eventos.none { aLocal(it.ruta) == rel && it.tipo != TipoEvento.SUBIDO } },
                )
            }
        }

        return Resumen(
            fecha = System.currentTimeMillis(), modo = modo,
            subidos = subidos, bajados = bajados, borrados = borrados, conflictos = conflictos, errores = errores,
            eventos = eventos.takeLast(MAX_EVENTOS),
        )
    }

    private suspend fun subir(stub: FileSyncCoroutineStub, md: io.grpc.Metadata, carpeta: CarpetaLocal, ruta: String, rel: String) {
        val trozos = flow {
            carpeta.abrirRuta(rel).use { entrada ->
                val buf = ByteArray(TROZO)
                var primero = true
                while (true) {
                    val n = entrada.read(buf)
                    if (n < 0) break
                    emit(chunk(if (primero) ruta else "", ByteString.copyFrom(buf, 0, n)))
                    primero = false
                }
                if (primero) emit(chunk(ruta, ByteString.EMPTY)) // archivo vacío: al menos la ruta
            }
        }.flowOn(Dispatchers.IO)
        llamar { stub.subirArchivo(trozos, md) }
    }

    /**
     * Descarga a un temporal, comprueba el SHA-256 contra el que anunció el
     * servidor y solo entonces reemplaza el archivo del usuario.
     */
    private suspend fun bajar(stub: FileSyncCoroutineStub, md: io.grpc.Metadata, carpeta: CarpetaLocal, a: AccionArchivo, rel: String) {
        val tmp = File.createTempFile("sync-", ".part", context.cacheDir)
        try {
            llamar {
                tmp.outputStream().use { salida ->
                    stub.descargarArchivo(DownloadRequest.newBuilder().setRuta(a.ruta).build(), md)
                        .collect { salida.write(it.contenido.toByteArray()) }
                }
            }
            if (a.hashServidor.isNotEmpty() && CarpetaLocal.sha256(tmp.inputStream()) != a.hashServidor) {
                // El archivo cambió en el servidor durante la descarga (SYNC-006).
                throw IOException("cambió en el servidor durante la descarga; se reintentará")
            }
            carpeta.escribir(rel, tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun chunk(ruta: String, datos: ByteString) =
        FileChunk.newBuilder().setRuta(ruta).setContenido(datos).build()

    /** Traduce los errores gRPC que detienen la sincronización entera. */
    private suspend fun <T> llamar(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: StatusException) {
        when (e.status.code) {
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED ->
                throw SyncAbortada("No se alcanza el servidor de sincronización", sinRed = true)
            Status.Code.UNAUTHENTICATED ->
                throw SyncAbortada("Tu sesión ya no es válida. Vuelve a iniciar sesión para sincronizar.")
            Status.Code.RESOURCE_EXHAUSTED ->
                throw SyncAbortada("No queda espacio en tu cuota de almacenamiento")
            else -> throw IOException(e.status.description ?: e.status.code.name)
        }
    }

    private fun dispositivo(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()

    private companion object {
        const val TROZO = 64 * 1024
        const val MAX_EVENTOS = 100
    }
}
