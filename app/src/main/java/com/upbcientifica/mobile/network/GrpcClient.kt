package com.upbcientifica.mobile.network

import android.net.Network
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * Canales gRPC hacia File Sync (node-sync).
 *
 * ANTES era un marcador vacío: gRPC estaba desactivado en build.gradle.kts a la
 * espera del .proto. AHORA el .proto vive en src/main/proto (copia del de
 * node-sync) y Gradle genera FileSyncGrpcKt.FileSyncCoroutineStub.
 *
 * El canal se crea por sesión de sincronización y se cierra al terminar: así
 * se puede atar a una red concreta. Con [red] (el Wi-Fi), el tráfico va por esa
 * red aunque Android tenga los datos móviles como red por defecto (pasa cuando
 * el Wi-Fi del laboratorio no tiene salida a Internet): el requisito es
 * sincronizar por la red LOCAL donde está el servidor, no por cualquier red.
 */
object GrpcClient {

    fun canal(red: Network? = null): ManagedChannel =
        OkHttpChannelBuilder.forTarget(Configuracion.direccionSync)
            .apply {
                if (!Configuracion.SYNC_CON_TLS) usePlaintext()
                red?.let { socketFactory(it.socketFactory) }
            }
            .keepAliveTime(30, TimeUnit.SECONDS)
            .build()

    /** Metadata con el token: el servidor saca el uid de aquí, nunca del mensaje. */
    fun autorizacion(token: String): Metadata = Metadata().apply {
        put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $token")
    }
}
