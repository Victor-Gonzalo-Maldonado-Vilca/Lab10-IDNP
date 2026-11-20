package com.example.lab10workmanager

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Pruebas instrumentadas del Worker y de su programación con WorkManager.
 * WorkManagerTestInitHelper reemplaza WorkManager por una versión de prueba
 * cuyo TestDriver permite simular el retardo y el cumplimiento de restricciones.
 */
@RunWith(AndroidJUnit4::class)
class MantenimientoWorkerTest {

    private lateinit var contexto: Context

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        RegistroMantenimiento.borrar(contexto)
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(contexto, config)
    }

    @Test
    fun doWork_devuelveExitoYGuardaRegistro() = runBlocking {
        val worker = TestListenableWorkerBuilder<MantenimientoBibliotecaWorker>(contexto)
            .setInputData(workDataOf(MantenimientoBibliotecaWorker.CLAVE_ORIGEN to "prueba"))
            .build()

        val resultado = worker.doWork()

        assertTrue(resultado is ListenableWorker.Result.Success)
        val salida = (resultado as ListenableWorker.Result.Success).outputData
        assertEquals(Catalogo.canciones.size, salida.getInt(MantenimientoBibliotecaWorker.CLAVE_CANCIONES, 0))
        assertEquals(Catalogo.canciones.sumOf { it.duracionSeg }, salida.getInt(MantenimientoBibliotecaWorker.CLAVE_DURACION, 0))
        val registro = RegistroMantenimiento.leer(contexto)
        assertEquals(1, registro.size)
        assertEquals("prueba", registro.first().origen)
    }

    @Test
    fun solicitudUnica_esperaRetardoYRestricciones_yLuegoTermina() {
        val workManager = WorkManager.getInstance(contexto)
        val solicitud = OneTimeWorkRequestBuilder<MantenimientoBibliotecaWorker>()
            .setConstraints(ProgramadorMantenimiento.restricciones)
            .setInitialDelay(ProgramadorMantenimiento.RETARDO_INICIAL_SEG, TimeUnit.SECONDS)
            .build()
        workManager.enqueue(solicitud).result.get()
        val driver = WorkManagerTestInitHelper.getTestDriver(contexto)!!

        // Sin cumplir retardo ni restricciones, la tarea queda en cola.
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(solicitud.id).get()!!.state)

        driver.setInitialDelayMet(solicitud.id)
        // Retardo cumplido, pero restricciones (batería/almacenamiento) aún no: sigue en cola.
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(solicitud.id).get()!!.state)

        driver.setAllConstraintsMet(solicitud.id)
        // Con todo cumplido pasa a RUNNING y, al terminar doWork(), a SUCCEEDED.
        val info = esperar(solicitud.id) { it.state.isFinished }
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        assertEquals(Catalogo.canciones.size, info.outputData.getInt(MantenimientoBibliotecaWorker.CLAVE_CANCIONES, 0))
    }

    @Test
    fun solicitudPeriodica_seEjecutaAlCumplirseElPeriodo() {
        val workManager = WorkManager.getInstance(contexto)
        val solicitud = PeriodicWorkRequestBuilder<MantenimientoBibliotecaWorker>(6, TimeUnit.HOURS)
            .setConstraints(ProgramadorMantenimiento.restricciones)
            .build()
        workManager.enqueue(solicitud).result.get()
        val driver = WorkManagerTestInitHelper.getTestDriver(contexto)!!

        driver.setAllConstraintsMet(solicitud.id)
        driver.setPeriodDelayMet(solicitud.id)

        // Un trabajo periódico nunca queda SUCCEEDED: tras ejecutarse vuelve a ENQUEUED
        // para el siguiente periodo. Se espera a que el Worker deje su registro.
        runBlocking {
            withTimeout(15_000) { while (RegistroMantenimiento.leer(contexto).isEmpty()) delay(200) }
        }
        val info = esperar(solicitud.id) { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(1, RegistroMantenimiento.leer(contexto).size)
    }

    /** Espera (máx. 15 s) a que el WorkInfo del trabajo cumpla la condición. */
    private fun esperar(id: UUID, condicion: (WorkInfo) -> Boolean): WorkInfo = runBlocking {
        withTimeout(15_000) {
            WorkManager.getInstance(contexto).getWorkInfoByIdFlow(id).first { it != null && condicion(it) }!!
        }
    }
}
