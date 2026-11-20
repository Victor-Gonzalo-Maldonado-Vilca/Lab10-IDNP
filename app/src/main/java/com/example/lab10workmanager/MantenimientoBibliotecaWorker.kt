package com.example.lab10workmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Tarea de mantenimiento de la biblioteca de Nocturne (proyecto final).
 *
 * Recorre el catálogo de canciones, verifica cada pista, calcula la duración total
 * y guarda un registro con marca de tiempo en el almacenamiento interno.
 * Al ser un CoroutineWorker, doWork() es una función suspendida que WorkManager
 * ejecuta fuera del hilo principal; el trabajo de E/S se hace en Dispatchers.IO.
 */
class MantenimientoBibliotecaWorker(
    contexto: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val origen = inputData.getString(CLAVE_ORIGEN) ?: "desconocido"
        Log.i(TAG, "INICIO id=$id origen=$origen intento=${runAttemptCount + 1} hilo=${Thread.currentThread().name}")

        try {
            val canciones = Catalogo.canciones
            var segundos = 0
            canciones.forEachIndexed { i, cancion ->
                delay(PAUSA_POR_PISTA_MS)               // simula leer y verificar la pista
                segundos += cancion.duracionSeg
                val avance = (i + 1) * 100 / canciones.size
                setProgress(workDataOf(CLAVE_AVANCE to avance, CLAVE_PISTA to cancion.titulo))
                Log.d(TAG, "Verificada ${i + 1}/${canciones.size}: ${cancion.titulo} ($avance %)")
            }

            val registro = RegistroMantenimiento.agregar(
                applicationContext,
                origen = origen,
                canciones = canciones.size,
                duracionSeg = segundos,
            )
            Log.i(TAG, "FIN OK id=$id -> $registro")

            Result.success(
                workDataOf(
                    CLAVE_CANCIONES to canciones.size,
                    CLAVE_DURACION to segundos,
                    CLAVE_MARCA to registro.marca,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "FALLO id=$id: ${e.message}", e)
            // Reintento con la política de backoff definida en la solicitud (máximo 3 intentos).
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "NocturneWorker"
        const val CLAVE_ORIGEN = "origen"
        const val CLAVE_AVANCE = "avance"
        const val CLAVE_PISTA = "pista"
        const val CLAVE_CANCIONES = "canciones"
        const val CLAVE_DURACION = "duracionSeg"
        const val CLAVE_MARCA = "marca"
        const val PAUSA_POR_PISTA_MS = 400L
    }
}

/** Catálogo local de prueba del reproductor (en el proyecto final proviene de MediaStore). */
object Catalogo {
    data class Cancion(val titulo: String, val artista: String, val duracionSeg: Int)

    val canciones = listOf(
        Cancion("Luz de medianoche", "Aurora Sur", 214),
        Cancion("Ciudad dormida", "Los Faroles", 187),
        Cancion("Neón", "Vera Cian", 242),
        Cancion("Marea lenta", "Puerto Azul", 263),
        Cancion("Constelaciones", "Aurora Sur", 198),
        Cancion("Último tren", "Los Faroles", 231),
        Cancion("Brisa del Misti", "Andes Lo-Fi", 176),
        Cancion("Eclipse", "Vera Cian", 254),
        Cancion("Calles de sillar", "Andes Lo-Fi", 205),
        Cancion("Amanecer", "Puerto Azul", 221),
    )
}
