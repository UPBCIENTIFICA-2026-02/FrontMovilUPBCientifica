package com.upbcientifica.mobile.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.streaming.ContenidoDto
import com.upbcientifica.mobile.data.streaming.FotoDto
import com.upbcientifica.mobile.data.streaming.OrdenMedios
import com.upbcientifica.mobile.data.streaming.StreamingRepository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Streaming (videos) y Photo Album (fotos): los dos los sirve node-media. */
class StreamingViewModel(private val repo: StreamingRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ContenidoDto>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ContenidoDto>>> = _state

    private val _fotos = MutableStateFlow<UiState<List<FotoDto>>>(UiState.Idle)
    val fotos: StateFlow<UiState<List<FotoDto>>> = _fotos

    private val _visor = MutableStateFlow<EstadoVisor?>(null)
    val visor: StateFlow<EstadoVisor?> = _visor

    /** Orden del catálogo y del álbum; lo aplica node-media (ordenPor). */
    private val _ordenVideos = MutableStateFlow(OrdenMedios.RECIENTES)
    val ordenVideos: StateFlow<OrdenMedios> = _ordenVideos
    private val _ordenFotos = MutableStateFlow(OrdenMedios.RECIENTES)
    val ordenFotos: StateFlow<OrdenMedios> = _ordenFotos

    private val _indexando = MutableStateFlow(false)
    val indexando: StateFlow<Boolean> = _indexando

    private val _avisos = Channel<String>(Channel.BUFFERED)
    val avisos: Flow<String> = _avisos.receiveAsFlow()

    // Una descarga por miniatura mientras dure la sesión, como en el portal.
    private val miniaturas = mutableMapOf<String, ImageBitmap?>()
    private val candado = Mutex()
    private var cargaVisor: Job? = null

    /** Carga solo si todavía no hay nada (al volver a la pestaña). */
    fun cargarSiHaceFalta() {
        if (_state.value is UiState.Idle) cargar()
        if (_fotos.value is UiState.Idle) cargarFotos()
    }

    fun cargar() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repo.catalogo(orden = _ordenVideos.value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.mensajeLegible("Streaming"))
            }
        }
    }

    fun cargarFotos() {
        viewModelScope.launch {
            _fotos.value = UiState.Loading
            _fotos.value = try {
                UiState.Success(repo.fotos(orden = _ordenFotos.value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.mensajeLegible("Photo Album"))
            }
        }
    }

    fun ordenarVideos(orden: OrdenMedios) {
        if (_ordenVideos.value == orden) return
        _ordenVideos.value = orden
        cargar()
    }

    fun ordenarFotos(orden: OrdenMedios) {
        if (_ordenFotos.value == orden || orden == OrdenMedios.MAS_VISTOS) return
        _ordenFotos.value = orden
        cargarFotos()
    }

    /**
     * Busca ya mismo fotos y videos nuevos en Mis archivos y en lo compartido
     * contigo. node-media también lo hace solo al cargar (como mucho una vez
     * por minuto).
     */
    fun indexar() {
        if (_indexando.value) return
        viewModelScope.launch {
            _indexando.value = true
            val aviso = try {
                val r = repo.indexar()
                if (r.indexados == 0 && r.compartidos == 0) "No hay fotos ni videos nuevos"
                else "${r.indexados} tuyos y ${r.compartidos} compartidos contigo en el catálogo"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo buscar contenido nuevo: ${e.mensajeLegible("node-media")}"
            }
            _indexando.value = false
            _avisos.send(aviso)
            cargar()
            cargarFotos()
        }
    }

    /** Miniatura ya decodificada, o null si la foto no tiene o falló. */
    suspend fun miniatura(foto: FotoDto): ImageBitmap? =
        if (!foto.tieneMiniatura) null else miniaturaEnCache(foto.id) { repo.miniatura(foto.id) }

    /** Vista previa del video (FFmpeg en node-media), o null si no la hay. */
    suspend fun miniatura(video: ContenidoDto): ImageBitmap? =
        if (!video.tieneMiniatura) null else miniaturaEnCache("v:${video.id}") { repo.miniaturaVideo(video.id) }

    private suspend fun miniaturaEnCache(clave: String, descargar: suspend () -> ByteArray): ImageBitmap? {
        candado.withLock { if (miniaturas.containsKey(clave)) return miniaturas[clave] }
        val imagen = try {
            val bytes = withContext(Dispatchers.IO) { descargar() }
            withContext(Dispatchers.Default) { decodificarImagen(bytes, ladoMaximo = 512) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        candado.withLock { miniaturas[clave] = imagen }
        return imagen
    }

    fun reproducir(video: ContenidoDto) {
        cargaVisor?.cancel()
        _visor.value = EstadoVisor.Cargando(video.titulo)
        cargaVisor = viewModelScope.launch {
            _visor.value = try {
                EstadoVisor.Video(video.titulo, repo.reproducir(video.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoVisor.Error(video.titulo, e.mensajeLegible("Streaming"))
            }
        }
    }

    fun verFoto(foto: FotoDto) {
        cargaVisor?.cancel()
        _visor.value = EstadoVisor.Cargando(foto.titulo)
        cargaVisor = viewModelScope.launch {
            _visor.value = try {
                val bytes = withContext(Dispatchers.IO) { repo.contenidoFoto(foto.id) }
                val imagen = withContext(Dispatchers.Default) { decodificarImagen(bytes) }
                if (imagen != null) EstadoVisor.Imagen(foto.titulo, imagen)
                else EstadoVisor.Error(foto.titulo, "El teléfono no puede mostrar este formato de imagen.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoVisor.Error(foto.titulo, e.mensajeLegible("Photo Album"))
            }
        }
    }

    /** Borra el video de Streaming y también de Mis archivos (node-media lo pide a Storage). */
    fun eliminarVideo(video: ContenidoDto) {
        viewModelScope.launch {
            val aviso = try {
                repo.eliminarVideo(video.id)
                if ((_visor.value as? EstadoVisor.Video)?.titulo == video.titulo) cerrarVisor()
                "\"${video.titulo}\" eliminado"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo eliminar: ${e.mensajeLegible("Streaming")}"
            }
            _avisos.send(aviso)
            cargar()
        }
    }

    /** Borra la foto del álbum y también de Mis archivos (node-media lo pide a Storage). */
    fun eliminarFoto(foto: FotoDto) {
        viewModelScope.launch {
            val aviso = try {
                repo.eliminarFoto(foto.id)
                candado.withLock { miniaturas.remove(foto.id) }
                if ((_visor.value as? EstadoVisor.Imagen)?.titulo == foto.titulo) cerrarVisor()
                "\"${foto.titulo}\" eliminada"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo eliminar: ${e.mensajeLegible("Photo Album")}"
            }
            _avisos.send(aviso)
            cargarFotos()
        }
    }

    fun cerrarVisor() {
        cargaVisor?.cancel()
        _visor.value = null
    }
}
