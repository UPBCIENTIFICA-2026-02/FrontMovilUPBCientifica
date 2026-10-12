package com.upbcientifica.mobile.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.sharedfile.NivelPermiso
import com.upbcientifica.mobile.data.sharedfile.SharedFileRepository
import com.upbcientifica.mobile.data.sharedfile.TipoDestinatario
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.data.storage.ArchivosDelDispositivo
import com.upbcientifica.mobile.data.storage.CuotaDto
import com.upbcientifica.mobile.data.storage.StorageRepository
import com.upbcientifica.mobile.network.mensajeLegible
import com.upbcientifica.mobile.ui.components.EstadoVisor
import com.upbcientifica.mobile.ui.components.decodificarImagen
import com.upbcientifica.mobile.ui.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un nivel de la ruta de carpetas que se muestra como migas de pan. */
data class Carpeta(val id: String, val nombre: String)

/** Qué se puede abrir en el visor de la app, según la extensión. */
enum class TipoVisible { IMAGEN, VIDEO }

fun tipoVisible(nombre: String): TipoVisible? = when (nombre.substringAfterLast('.', "").lowercase()) {
    "png", "jpg", "jpeg", "gif", "webp", "bmp" -> TipoVisible.IMAGEN
    "mp4", "m4v", "mov", "webm", "mkv", "3gp" -> TipoVisible.VIDEO
    else -> null
}

class StorageViewModel(
    private val repo: StorageRepository,
    private val dispositivo: ArchivosDelDispositivo,
    private val compartidos: SharedFileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ArchivoDto>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ArchivoDto>>> = _state

    /** Ruta desde la raíz hasta la carpeta abierta; nunca vacía. */
    private val _ruta = MutableStateFlow(listOf(Carpeta("root", "Mis archivos")))
    val ruta: StateFlow<List<Carpeta>> = _ruta

    /** Texto de la operación en curso ("Subiendo 2 de 5…"), o null si no hay ninguna. */
    private val _operacion = MutableStateFlow<String?>(null)
    val operacion: StateFlow<String?> = _operacion

    private val _visor = MutableStateFlow<EstadoVisor?>(null)
    val visor: StateFlow<EstadoVisor?> = _visor

    private val _cuota = MutableStateFlow<CuotaDto?>(null)
    val cuota: StateFlow<CuotaDto?> = _cuota

    /** Avisos de una sola vez para la barra inferior (snackbar). */
    private val _avisos = Channel<String>(Channel.BUFFERED)
    val avisos: Flow<String> = _avisos.receiveAsFlow()

    private var cargaActual: Job? = null
    private var cargaVisor: Job? = null

    private val carpetaActual get() = _ruta.value.last()

    /** Carga la carpeta actual solo si todavía no hay nada (al volver a la pestaña). */
    fun cargarSiHaceFalta() {
        if (_state.value is UiState.Idle) recargar()
    }

    fun recargar() {
        cargar(carpetaActual.id)
        actualizarCuota()
    }

    fun abrirCarpeta(carpeta: ArchivoDto) {
        _ruta.value = _ruta.value + Carpeta(carpeta.id, carpeta.nombre)
        recargar()
    }

    /** Sube un nivel. Devuelve false si ya estaba en la raíz. */
    fun subirNivel(): Boolean {
        if (_ruta.value.size <= 1) return false
        _ruta.value = _ruta.value.dropLast(1)
        recargar()
        return true
    }

    /** Salta a un nivel concreto de las migas de pan. */
    fun irANivel(indice: Int) {
        if (indice >= _ruta.value.lastIndex) return
        _ruta.value = _ruta.value.take(indice + 1)
        recargar()
    }

    /** Sube a la carpeta abierta los archivos elegidos en el selector del sistema. */
    fun subir(uris: List<Uri>) {
        if (uris.isEmpty() || _operacion.value != null) return
        val destino = carpetaActual
        viewModelScope.launch {
            var subidos = 0
            val fallos = mutableListOf<String>()
            uris.forEachIndexed { i, uri ->
                val nombre = runCatching { dispositivo.nombre(uri) }.getOrDefault("archivo")
                _operacion.value = if (uris.size == 1) "Subiendo $nombre…" else "Subiendo ${i + 1} de ${uris.size}: $nombre"
                try {
                    repo.subir(nombre, dispositivo.cuerpo(uri), destino.id)
                    subidos++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fallos += "$nombre: ${e.mensajeLegible("Storage")}"
                }
            }
            _operacion.value = null
            _avisos.send(
                when {
                    fallos.isEmpty() -> if (subidos == 1) "Archivo subido a ${destino.nombre}" else "$subidos archivos subidos a ${destino.nombre}"
                    subidos == 0 -> "No se pudo subir. ${fallos.first()}"
                    else -> "$subidos subidos, ${fallos.size} con error. ${fallos.first()}"
                }
            )
            if (carpetaActual.id == destino.id) recargar()
        }
    }

    fun crearCarpeta(nombre: String) {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        viewModelScope.launch {
            _operacion.value = "Creando carpeta…"
            val aviso = try {
                repo.crearCarpeta(limpio, carpetaActual.id)
                "Carpeta \"$limpio\" creada"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo crear la carpeta: ${e.mensajeLegible("Storage")}"
            }
            _operacion.value = null
            _avisos.send(aviso)
            recargar()
        }
    }

    fun eliminar(archivo: ArchivoDto) {
        viewModelScope.launch {
            _operacion.value = "Eliminando ${archivo.nombre}…"
            val aviso = try {
                repo.eliminar(archivo.id)
                "\"${archivo.nombre}\" eliminado"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo eliminar: ${e.mensajeLegible("Storage")}"
            }
            _operacion.value = null
            _avisos.send(aviso)
            recargar()
        }
    }

    /**
     * CompartirArchivo o CompartirDirectorio (Contrato_SharedFile), según lo
     * que sea. Una carpeta compartida comparte también todo lo que se meta
     * dentro después.
     */
    fun compartir(archivo: ArchivoDto, tipo: TipoDestinatario, destinatario: String, nivel: NivelPermiso) {
        val id = destinatario.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            _operacion.value = "Compartiendo ${archivo.nombre}…"
            val aviso = try {
                val r = if (archivo.tipo == "directorio") compartidos.compartirDirectorio(archivo.id, tipo, id, nivel)
                        else compartidos.compartirArchivo(archivo.id, tipo, id, nivel)
                if (r.estadoInvitacion == "PENDIENTE") "Invitación enviada a $id: tendrá acceso cuando la acepte"
                else "\"${archivo.nombre}\" compartido con $id"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo compartir: ${e.mensajeLegible("Shared File")}"
            }
            _operacion.value = null
            _avisos.send(aviso)
        }
    }

    /** Guarda una copia en el teléfono, en el sitio que el usuario eligió en "Guardar como". */
    fun descargar(archivo: ArchivoDto, destino: Uri) {
        viewModelScope.launch {
            _operacion.value = "Descargando ${archivo.nombre}…"
            val aviso = try {
                val flujo = repo.descargar(archivo.id)
                withContext(Dispatchers.IO) { dispositivo.guardar(destino, flujo) }
                "\"${archivo.nombre}\" guardado en el teléfono"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo descargar: ${e.mensajeLegible("Storage")}"
            }
            _operacion.value = null
            _avisos.send(aviso)
        }
    }

    /** Abre una imagen o un video en el visor de la app. */
    fun abrir(archivo: ArchivoDto) {
        val tipo = tipoVisible(archivo.nombre) ?: return
        cargaVisor?.cancel()
        when (tipo) {
            TipoVisible.VIDEO -> _visor.value = try {
                EstadoVisor.Video(archivo.nombre, repo.fuenteVideo(archivo.id))
            } catch (e: Exception) {
                EstadoVisor.Error(archivo.nombre, e.mensajeLegible("Storage"))
            }
            TipoVisible.IMAGEN -> {
                _visor.value = EstadoVisor.Cargando(archivo.nombre)
                cargaVisor = viewModelScope.launch {
                    _visor.value = try {
                        val bytes = withContext(Dispatchers.IO) { repo.bytes(archivo.id) }
                        val imagen = withContext(Dispatchers.Default) { decodificarImagen(bytes) }
                        if (imagen != null) EstadoVisor.Imagen(archivo.nombre, imagen)
                        else EstadoVisor.Error(archivo.nombre, "El archivo no es una imagen que el teléfono pueda mostrar.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        EstadoVisor.Error(archivo.nombre, e.mensajeLegible("Storage"))
                    }
                }
            }
        }
    }

    fun cerrarVisor() {
        cargaVisor?.cancel()
        _visor.value = null
    }

    private fun actualizarCuota() {
        viewModelScope.launch {
            _cuota.value = try {
                repo.cuota() ?: _cuota.value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _cuota.value
            }
        }
    }

    private fun cargar(directorioId: String) {
        // Si el usuario navega rápido, la respuesta de una carpeta anterior no
        // debe pisar la de la carpeta que está viendo ahora.
        cargaActual?.cancel()
        cargaActual = viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repo.listarHome(directorioId))
            } catch (e: CancellationException) {
                throw e // cancelada por otra navegación: no es un error que mostrar
            } catch (e: Exception) {
                UiState.Error(e.mensajeLegible("Storage"))
            }
        }
    }
}
