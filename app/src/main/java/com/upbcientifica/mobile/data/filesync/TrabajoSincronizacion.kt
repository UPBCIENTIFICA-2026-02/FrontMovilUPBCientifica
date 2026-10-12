package com.upbcientifica.mobile.data.filesync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Sincronización en segundo plano (WorkManager): sobrevive a que se cierre la
 * app y Android la ejecuta cuando se cumplen las condiciones de red.
 */
class TrabajoSincronizacion(contexto: Context, parametros: WorkerParameters) :
    CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val modo = runCatching { ModoDisparo.valueOf(inputData.getString(MODO) ?: "") }
            .getOrDefault(ModoDisparo.AUTOMATICO_AL_CONECTAR)
        val repo = FileSyncModulo.repositorio(applicationContext)
        // Re-registrar el aviso de Wi-Fi: se pierde al reiniciar el teléfono.
        if (modo != ModoDisparo.MANUAL) repo.renovarVigilancia()
        repo.ejecutar(modo)
        // Siempre "éxito": los reintentos los dan el aviso de Wi-Fi y la
        // programación; reintentar aquí con backoff solo gastaría batería.
        return Result.success()
    }

    companion object {
        const val MODO = "modo"
        private const val AL_CONECTAR = "filesync-al-conectar"
        private const val VIGILANCIA = "filesync-vigilancia-wifi"
        private const val PROGRAMADA = "filesync-programada"
        private const val MANUAL = "filesync-manual"

        private fun soloWifi() = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

        /** "Sincronizar ahora": cualquier red; el usuario lo pidió explícitamente. */
        fun encolarManual(context: Context) {
            val trabajo = OneTimeWorkRequestBuilder<TrabajoSincronizacion>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(MODO to ModoDisparo.MANUAL.name))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, trabajo)
        }

        /** Se acaba de conectar un Wi-Fi (o se abrió la app con Wi-Fi). */
        fun encolarAlConectar(context: Context) {
            val trabajo = OneTimeWorkRequestBuilder<TrabajoSincronizacion>()
                .setConstraints(soloWifi())
                .setInputData(workDataOf(MODO to ModoDisparo.AUTOMATICO_AL_CONECTAR.name))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(AL_CONECTAR, ExistingWorkPolicy.KEEP, trabajo)
        }

        /**
         * Red de seguridad del aviso de Wi-Fi: cada 15 min (el mínimo de
         * Android) y SOLO con Wi-Fi. Si el aviso se perdió (reinicio, límite de
         * callbacks), este trabajo detecta la llegada al Wi-Fi como mucho 15
         * min después. Si ya se sincronizó en esa misma conexión, no hace nada.
         */
        fun programarVigilancia(context: Context, activa: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!activa) {
                wm.cancelUniqueWork(VIGILANCIA); wm.cancelUniqueWork(AL_CONECTAR); return
            }
            val trabajo = PeriodicWorkRequestBuilder<TrabajoSincronizacion>(15, TimeUnit.MINUTES)
                .setConstraints(soloWifi())
                .setInputData(workDataOf(MODO to ModoDisparo.AUTOMATICO_AL_CONECTAR.name))
                .build()
            wm.enqueueUniquePeriodicWork(VIGILANCIA, ExistingPeriodicWorkPolicy.KEEP, trabajo)
        }

        /**
         * ProgramarSincronizacion: cada hora o diaria a una hora fija. En el
         * teléfono exige Wi-Fi (no gasta datos móviles) y el trabajo comprueba
         * además que ese Wi-Fi llegue al servidor. Si toca la hora sin Wi-Fi,
         * Android la retrasa hasta que lo haya.
         */
        fun programar(context: Context, config: ConfigSync) {
            val wm = WorkManager.getInstance(context)
            val periodo = when (config.programacion) {
                Programacion.NINGUNA -> { wm.cancelUniqueWork(PROGRAMADA); return }
                Programacion.CADA_HORA -> 1L to TimeUnit.HOURS
                Programacion.DIARIA -> 24L to TimeUnit.HOURS
            }
            val retraso = if (config.programacion == Programacion.DIARIA) {
                milisHasta(config.horaDiaria, config.minutoDiario)
            } else 0L
            val trabajo = PeriodicWorkRequestBuilder<TrabajoSincronizacion>(periodo.first, periodo.second)
                .setConstraints(soloWifi())
                .setInitialDelay(retraso, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(MODO to ModoDisparo.PROGRAMADO.name))
                .build()
            // Reemplazar: si cambió la hora, el retraso inicial también.
            wm.enqueueUniquePeriodicWork(PROGRAMADA, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, trabajo)
        }

        fun cancelarTodo(context: Context) {
            val wm = WorkManager.getInstance(context)
            listOf(AL_CONECTAR, VIGILANCIA, PROGRAMADA, MANUAL).forEach(wm::cancelUniqueWork)
        }

        private fun milisHasta(hora: Int, minuto: Int): Long {
            val ahora = Calendar.getInstance()
            val objetivo = (ahora.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hora); set(Calendar.MINUTE, minuto)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (!after(ahora)) add(Calendar.DAY_OF_MONTH, 1)
            }
            return objetivo.timeInMillis - ahora.timeInMillis
        }
    }
}

/** Lo despierta el sistema al conectarse un Wi-Fi (ver RedLocal.vigilarConexionWifi). */
class ReceptorWifi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TrabajoSincronizacion.encolarAlConectar(context)
    }
}
