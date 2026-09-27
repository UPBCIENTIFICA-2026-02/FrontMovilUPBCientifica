package com.upbcientifica.mobile.network

import com.upbcientifica.mobile.session.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Interceptor que agrega el token de sesión (emitido por Authentication Service)
 * a cada llamada REST, tal como espera Storage/SharedFile/Streaming.
 */
class AuthHeaderInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.currentToken() ?: return chain.proceed(original)
        val authenticated = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authenticated)
    }
}

/**
 * Construye un Retrofit configurado para un servicio REST específico.
 * Un mismo patrón sirve para Storage, Shared File y Streaming — cada uno
 * solo cambia [baseUrl] y su propia interfaz de API (ver data/<servicio>).
 */
object RestClient {

    /**
     * @param baseUrl tiene que terminar en "/" o Retrofit lanza
     *        IllegalArgumentException al construirse. Las URLs de
     *        [Configuracion] ya vienen así.
     */
    fun build(baseUrl: String, tokenManager: TokenManager): Retrofit {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(AuthHeaderInterceptor(tokenManager))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // BASIC y no BODY: aquí viajan archivos completos, y volcar
                    // el cuerpo de una descarga a Logcat lo haría inservible.
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            // Subir o bajar un archivo grande tarda más que una llamada normal,
            // pero sin límite una VM caída deja la pantalla colgada.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
