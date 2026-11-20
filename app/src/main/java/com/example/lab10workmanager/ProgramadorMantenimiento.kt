package com.example.lab10workmanager

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Crea y encola las solicitudes de trabajo del mantenimiento de la biblioteca.
 */
object ProgramadorMantenimiento {
    const val TRABAJO_UNICO = "mantenimiento_biblioteca"
    const val TRABAJO_PERIODICO = "mantenimiento_biblioteca_periodico"
    const val ETIQUETA = "mantenimiento"
    const val RETARDO_INICIAL_SEG = 5L

    /**
     * Restricciones: la tarea es local (no usa red), pero recorre todo el catálogo
     * y escribe en disco, así que se pospone si la batería o el almacenamiento están bajos.
     */
    val restricciones: Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    /** Encola un OneTimeWorkRequest con retardo corto para comprobar el funcionamiento. */
    fun encolarUnico(contexto: Context): UUID {
        val solicitud = OneTimeWorkRequestBuilder<MantenimientoBibliotecaWorker>()
            .setConstraints(restricciones)
            .setInitialDelay(RETARDO_INICIAL_SEG, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf(MantenimientoBibliotecaWorker.CLAVE_ORIGEN to "manual"))
            .addTag(ETIQUETA)
            .build()

        WorkManager.getInstance(contexto)
            .enqueueUniqueWork(TRABAJO_UNICO, ExistingWorkPolicy.REPLACE, solicitud)
        Log.i(MantenimientoBibliotecaWorker.TAG, "ENCOLADO OneTimeWorkRequest id=${solicitud.id} retardo=${RETARDO_INICIAL_SEG}s")
        return solicitud.id
    }

    /**
     * Extensión: mantenimiento periódico. Android exige un intervalo mínimo de
     * 15 minutos (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS); se usa 6 horas
     * con una ventana flexible de 1 hora para que el sistema agrupe trabajos.
     */
    fun programarPeriodico(contexto: Context): UUID {
        val solicitud = PeriodicWorkRequestBuilder<MantenimientoBibliotecaWorker>(
            6, TimeUnit.HOURS,
            1, TimeUnit.HOURS,
        )
            .setConstraints(restricciones)
            .setInputData(workDataOf(MantenimientoBibliotecaWorker.CLAVE_ORIGEN to "periodico"))
            .addTag(ETIQUETA)
            .build()

        WorkManager.getInstance(contexto)
            .enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, solicitud)
        Log.i(
            MantenimientoBibliotecaWorker.TAG,
            "PROGRAMADO PeriodicWorkRequest cada 6 h (minimo permitido: " +
                "${TimeUnit.MILLISECONDS.toMinutes(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)} min)",
        )
        return solicitud.id
    }

    fun cancelarPeriodico(contexto: Context) {
        WorkManager.getInstance(contexto).cancelUniqueWork(TRABAJO_PERIODICO)
        Log.i(MantenimientoBibliotecaWorker.TAG, "CANCELADO trabajo periodico")
    }
}
