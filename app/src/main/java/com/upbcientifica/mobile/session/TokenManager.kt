package com.upbcientifica.mobile.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda el token emitido por Authentication Service (operación Login) y lo
 * expone a los demás repositorios (Storage, Shared File, Streaming, Sync).
 * Cada protocolo lo inyecta a su manera:
 *  - SOAP: como parámetro `token` en cada operación (según el contrato).
 *  - REST: header Authorization (ver AuthHeaderInterceptor).
 *  - gRPC: como metadata en cada llamada.
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "upbcientifica_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()

    fun currentToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clear() = prefs.edit().remove(KEY_TOKEN).apply()

    private companion object {
        const val KEY_TOKEN = "session_token"
    }
}
