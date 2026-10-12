package com.upbcientifica.mobile.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.sharedfile.NivelPermiso
import com.upbcientifica.mobile.data.sharedfile.RelacionCompartidoDto
import com.upbcientifica.mobile.data.sharedfile.SharedFileRepository
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.data.storage.ArchivosDelDispositivo
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

/**
 * Pestaña Compartidos.
 *
 * Las LISTAS ("conmigo" y "que comparto") salen de Shared File. Lo que hay
 * DENTRO de una carpeta compartida, y los bytes de los archivos, se piden a
 * Storage: Storage ya aplica la compartición en sus permisos, así que el
 * destinatario puede listar y descargar con su propio token.
 */
class SharedFileViewModel(
    private val repo: SharedFileRepository,
    private val storage: StorageRepository,
    private val dispositivo: ArchivosDelDispositivo,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<RelacionCompartidoDto>>>(UiState.Idle)
    val state: StateFlow<UiState<List<RelacionCompartidoDto>>> = _state

    private val _queComparto = MutableStateFlow<UiState<List<RelacionCompartidoDto>>>(UiState.Idle)
    val queComparto: StateFlow<UiState<List<RelacionCompartidoDto>>> = _queComparto

    /** Carpetas compartidas abiertas, desde la que aparece en la lista hasta la actual. */
    private val _ruta = MutableStateFlow<List<Carpeta>>(emptyList())
    val ruta: StateFlow<List<Carpeta>> = _ruta

    private val _contenido = MutableStateFlow<UiState<List<ArchivoDto>>>(UiState.Idle)
    val contenido: StateFlow<UiState<List<ArchivoDto>>> = _contenido

    private val _visor = MutableStateFlow<EstadoVisor?>(null)
    val visor: StateFlow<EstadoVisor?> = _visor

    private val _avisos = Channel<String>(Channel.BUFFERED)
    val avisos: Flow<String> = _avisos.receiveAsFlow()

    private var cargaVisor: Job? = null
    private var cargaCarpeta: Job? = null

    /** Carga solo si todavía no hay nada (al volver a la pestaña). */
    fun cargarSiHaceFalta() {
        if (_state.value is UiState.Idle) cargar()
        if (_queComparto.value is UiState.Idle) cargarQueComparto()
    }

    fun cargar() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repo.compartidosConmigo())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.mensajeLegible("Shared File"))
            }
        }
    }

    fun cargarQueComparto() {
        viewModelScope.launch {
            _queComparto.value = UiState.Loading
            _queComparto.value = try {
                UiState.Success(repo.queYoComparto())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.mensajeLegible("Shared File"))
            }
        }
    }

    // ------------------------------------------------------------ carpetas compartidas

    fun abrirCarpeta(id: String, nombre: String) {
        _ruta.value = _ruta.value + Carpeta(id, nombre)
        cargarCarpeta()
    }

    /** Sube un nivel; al salir de la primera carpeta se vuelve a la lista. Devuelve false si ya estaba en la lista. */
    fun subirNivel(): Boolean {
        if (_ruta.value.isEmpty()) return false
        _ruta.value = _ruta.value.dropLast(1)
        if (_ruta.value.isNotEmpty()) cargarCarpeta() else _contenido.value = UiState.Idle
        return true
    }

    fun irANivel(indice: Int) {
        if (indice < 0) {
            _ruta.value = emptyList()
            _contenido.value = UiState.Idle
            return
        }
        if (indice >= _ruta.value.lastIndex) return
        _ruta.value = _ruta.value.take(indice + 1)
        cargarCarpeta()
    }

    fun cargarCarpeta() {
        val actual = _ruta.value.lastOrNull() ?: return
        cargaCarpeta?.cancel()
        cargaCarpeta = viewModelScope.launch {
            _contenido.value = UiState.Loading
            _contenido.value = try {
                UiState.Success(storage.listarHome(actual.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.mensajeLegible("Storage"))
            }
        }
    }

    // ------------------------------------------------------------ archivos

    /** Imágenes y videos se abren en el visor de la app, como en Mis archivos. */
    fun abrir(id: String, nombre: String) {
        val tipo = tipoVisible(nombre) ?: return
        cargaVisor?.cancel()
        when (tipo) {
            TipoVisible.VIDEO -> _visor.value = try {
                EstadoVisor.Video(nombre, storage.fuenteVideo(id))
            } catch (e: Exception) {
                EstadoVisor.Error(nombre, e.mensajeLegible("Storage"))
            }
            TipoVisible.IMAGEN -> {
                _visor.value = EstadoVisor.Cargando(nombre)
                cargaVisor = viewModelScope.launch {
                    _visor.value = try {
                        val bytes = withContext(Dispatchers.IO) { storage.bytes(id) }
                        val imagen = withContext(Dispatchers.Default) { decodificarImagen(bytes) }
                        if (imagen != null) EstadoVisor.Imagen(nombre, imagen)
                        else EstadoVisor.Error(nombre, "El archivo no es una imagen que el teléfono pueda mostrar.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        EstadoVisor.Error(nombre, e.mensajeLegible("Storage"))
                    }
                }
            }
        }
    }

    fun cerrarVisor() {
        cargaVisor?.cancel()
        _visor.value = null
    }

    fun descargar(id: String, nombre: String, destino: Uri) {
        viewModelScope.launch {
            val aviso = try {
                val flujo = storage.descargar(id)
                withContext(Dispatchers.IO) { dispositivo.guardar(destino, flujo) }
                "\"$nombre\" guardado en el teléfono"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo descargar: ${e.mensajeLegible("Storage")}"
            }
            _avisos.send(aviso)
        }
    }

    // ------------------------------------------------------------ gestionar lo que comparto

    fun revocar(relacion: RelacionCompartidoDto) {
        viewModelScope.launch {
            val aviso = try {
                repo.revocar(relacion.compartidoId)
                "${relacion.destinatarioId} ya no tiene acceso a \"${relacion.nombre}\""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo dejar de compartir: ${e.mensajeLegible("Shared File")}"
            }
            _avisos.send(aviso)
            cargarQueComparto()
        }
    }

    fun cambiarNivel(relacion: RelacionCompartidoDto, nivel: NivelPermiso) {
        if (nivel == relacion.nivelPermiso) return
        viewModelScope.launch {
            val aviso = try {
                val r = repo.cambiarNivel(relacion.compartidoId, nivel)
                r.advertencia?.mensaje ?: "${relacion.destinatarioId} ahora puede: ${nivel.etiqueta.lowercase()}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo cambiar el permiso: ${e.mensajeLegible("Shared File")}"
            }
            _avisos.send(aviso)
            cargarQueComparto()
        }
    }
}
