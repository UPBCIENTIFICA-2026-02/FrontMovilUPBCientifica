package com.upbcientifica.mobile.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda el token emitido por Authentication Service (operación Login) y lo
 * expone a los demás repositorios (Storage, Shared File, Streaming, Sync).
 * Cada protocolo lo inyecta a su manera:
 *  - SOAP: como parámetro `token` en cada operación (según el contrato).
 *  - REST: header Authorization (ver AuthHeaderInterceptor).
 *  - gRPC: como metadata en cada llamada.
 *
 * ---------------------------------------------------------------------------
 * POR QUÉ ESTO NO ES UN SIMPLE `EncryptedSharedPreferences.create(...)`
 *
 * El archivo de preferencias va cifrado con una clave del almacén de claves de
 * Android. Si el archivo sobrevive pero la clave ya no sirve para verificarlo
 * —cosa que pasa al REINSTALAR la app, al restaurar un respaldo, o cuando el
 * almacén de claves del dispositivo se regenera— cualquier acceso revienta con:
 *
 *     Signature/MAC verification failed (internal Keystore code: -30 ...
 *     Error::Km(r#VERIFICATION_FAILED)
 *
 * Y como `prefs` es `by lazy`, el estallido ocurre en el primer `saveToken`,
 * es decir JUSTO DESPUÉS de un login correcto: el servidor devuelve el token,
 * la app lo recibe bien, y aun así la pantalla muestra un error. Depurarlo
 * mirando el login es perder el tiempo, porque el login no tiene nada que ver.
 *
 * La solución es tratar el archivo como lo que es —una caché descartable— y
 * rehacerlo si no se puede leer. El token es de sesión: perderlo solo obliga a
 * iniciar sesión otra vez.
 * ---------------------------------------------------------------------------
 */
class TokenManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy { abrirPreferencias() }

    private fun abrirPreferencias(): SharedPreferences =
        try {
            crearCifradas()
        } catch (e: Exception) {
            /*
             * El archivo existe pero no se puede descifrar. Se borra y se
             * reintenta: al no existir, se genera un juego de claves nuevo.
             */
            Log.w(ETIQUETA, "Preferencias cifradas ilegibles; se descartan y se rehacen", e)
            context.deleteSharedPreferences(NOMBRE)

            try {
                crearCifradas()
            } catch (e2: Exception) {
                /*
                 * El cifrado no está disponible en este dispositivo. Antes que
                 * dejar la app inutilizable, se guarda sin cifrar y se avisa.
                 *
                 * PENDIENTE: en producción esto no debería pasar inadvertido.
                 * Lo correcto sería no persistir el token y obligar a iniciar
                 * sesión en cada arranque.
                 */
                Log.e(ETIQUETA, "Sin almacenamiento cifrado; el token queda SIN CIFRAR", e2)
                context.getSharedPreferences(NOMBRE_SIN_CIFRAR, Context.MODE_PRIVATE)
            }
        }

    private fun crearCifradas(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            NOMBRE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        try {
            prefs.edit().putString(KEY_TOKEN, token).apply()
        } catch (e: Exception) {
            /*
             * No poder recordar el token no invalida la sesión recién abierta:
             * el token ya está en memoria y las llamadas de esta ejecución
             * funcionan. Solo significa que habrá que entrar de nuevo al
             * reabrir la app.
             */
            Log.e(ETIQUETA, "No se pudo guardar el token; la sesión no sobrevivirá al reinicio", e)
        }
    }

    fun currentToken(): String? =
        try {
            prefs.getString(KEY_TOKEN, null)
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se pudo leer el token guardado", e)
            null
        }

    fun clear() {
        try {
            prefs.edit().remove(KEY_TOKEN).apply()
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se pudo borrar el token", e)
        }
    }

    private companion object {
        const val ETIQUETA = "TokenManager"
        const val KEY_TOKEN = "session_token"
        const val NOMBRE = "upbcientifica_session"
        const val NOMBRE_SIN_CIFRAR = "upbcientifica_session_plano"
    }
}
