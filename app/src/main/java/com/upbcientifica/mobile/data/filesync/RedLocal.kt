package com.upbcientifica.mobile.data.filesync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Detecta el Wi-Fi. La lógica de "detección de reconexión" vive en el cliente,
 * no en node-sync (Contrato_FileSync §3.1): el servicio solo recibe la
 * solicitud resultante con modoDisparo = AUTOMATICO_AL_CONECTAR.
 */
class RedLocal(private val context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    /**
     * La red Wi-Fi conectada, o null. Se busca entre TODAS las redes y no solo
     * la activa: si el Wi-Fi del laboratorio no tiene Internet, Android deja
     * los datos móviles como red por defecto aunque el Wi-Fi siga conectado.
     */
    @Suppress("DEPRECATION") // allNetworks: la alternativa exige registrar un callback y esperar
    fun wifi(): Network? = cm.allNetworks.firstOrNull { red ->
        cm.getNetworkCapabilities(red)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun intentConexion(): PendingIntent {
        // MUTABLE es obligatorio aquí: el sistema añade la red al Intent.
        val banderas = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(
            context, 0, Intent(context, ReceptorWifi::class.java).setPackage(context.packageName), banderas
        )
    }

    /**
     * Pide al sistema que avise a [ReceptorWifi] cada vez que se conecte un
     * Wi-Fi, AUNQUE la app esté cerrada (Android 7+ ya no entrega
     * CONNECTIVITY_ACTION a receptores del manifiesto; esta es la vía que
     * queda). Registrarlo de nuevo con el mismo PendingIntent lo reemplaza.
     * Dura hasta reiniciar el teléfono; el trabajo periódico lo vuelve a
     * registrar.
     */
    fun vigilarConexionWifi() {
        val peticion = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Una red local sin salida a Internet también vale.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(peticion, intentConexion())
        } catch (e: RuntimeException) {
            // Límite de callbacks por app alcanzado: queda el trabajo periódico.
        }
    }

    fun dejarDeVigilar() {
        try {
            cm.unregisterNetworkCallback(intentConexion())
        } catch (e: RuntimeException) {
            // No estaba registrado.
        }
    }
}
