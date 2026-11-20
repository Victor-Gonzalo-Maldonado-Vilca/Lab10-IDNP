package com.example.lab10workmanager

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.lab10workmanager.MantenimientoBibliotecaWorker.Companion.CLAVE_AVANCE
import com.example.lab10workmanager.MantenimientoBibliotecaWorker.Companion.CLAVE_CANCIONES
import com.example.lab10workmanager.MantenimientoBibliotecaWorker.Companion.CLAVE_DURACION
import com.example.lab10workmanager.MantenimientoBibliotecaWorker.Companion.CLAVE_MARCA
import com.example.lab10workmanager.MantenimientoBibliotecaWorker.Companion.CLAVE_PISTA
import com.example.lab10workmanager.MantenimientoBibliotecaWorker.Companion.TAG
import com.example.lab10workmanager.ui.theme.NocturneCian
import com.example.lab10workmanager.ui.theme.NocturneFondo
import com.example.lab10workmanager.ui.theme.NocturneSuperficie
import com.example.lab10workmanager.ui.theme.NocturneTexto
import com.example.lab10workmanager.ui.theme.NocturneTextoSuave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla "Biblioteca" de Nocturne: programa el mantenimiento con WorkManager
 * y muestra el estado del trabajo observando su WorkInfo como un Flow.
 */
@Composable
fun PantallaMantenimiento() {
    val contexto = LocalContext.current
    val workManager = remember { WorkManager.getInstance(contexto) }

    // Estado del trabajo: WorkManager publica cada cambio de WorkInfo en un Flow.
    val infosUnico by workManager
        .getWorkInfosForUniqueWorkFlow(ProgramadorMantenimiento.TRABAJO_UNICO)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val infosPeriodico by workManager
        .getWorkInfosForUniqueWorkFlow(ProgramadorMantenimiento.TRABAJO_PERIODICO)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val unico = infosUnico.firstOrNull()
    val periodico = infosPeriodico.firstOrNull { !it.state.isFinished }

    // Deja constancia en Logcat de cada transición de estado observada por la UI.
    LaunchedEffect(unico?.id, unico?.state) {
        unico?.let { Log.i(TAG, "UI observa estado=${it.state} id=${it.id}") }
    }

    // Registro guardado por el Worker: se lee en Dispatchers.IO, nunca en el hilo principal.
    var registro by remember { mutableStateOf<List<RegistroMantenimiento.Entrada>>(emptyList()) }
    LaunchedEffect(unico?.state) {
        registro = withContext(Dispatchers.IO) { RegistroMantenimiento.leer(contexto) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF231C45), NocturneFondo)))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("NOCTURNE · BIBLIOTECA", color = NocturneTextoSuave, fontSize = 13.sp, letterSpacing = 2.sp)
        Text(
            "Mantenimiento en segundo plano",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        LatidoInterfaz()

        // ---------- Trabajo único ----------
        Tarjeta {
            Text("Verificación de la biblioteca (OneTimeWorkRequest)", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estado: ", color = NocturneTextoSuave)
                ChipEstado(unico?.state)
            }
            unico?.let {
                Text("id ${it.id.toString().take(8)}…  ·  intentos ${it.runAttemptCount}", color = NocturneTextoSuave, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(10.dp))

            val avance = unico?.progress?.getInt(CLAVE_AVANCE, 0) ?: 0
            val pista = unico?.progress?.getString(CLAVE_PISTA)
            val progresoMostrado = when (unico?.state) {
                WorkInfo.State.SUCCEEDED -> 1f
                WorkInfo.State.RUNNING -> avance / 100f
                else -> 0f
            }
            LinearProgressIndicator(
                progress = { progresoMostrado },
                modifier = Modifier.fillMaxWidth().height(8.dp).testTag("progreso"),
                color = NocturneCian,
                trackColor = Color(0x33FFFFFF),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (unico?.state) {
                    WorkInfo.State.ENQUEUED -> "En cola: espera el retardo de ${ProgramadorMantenimiento.RETARDO_INICIAL_SEG} s y las restricciones."
                    WorkInfo.State.RUNNING -> "Verificando: ${pista ?: "…"} ($avance %)"
                    WorkInfo.State.SUCCEEDED -> {
                        val d = unico.outputData
                        val seg = d.getInt(CLAVE_DURACION, 0)
                        "${d.getInt(CLAVE_CANCIONES, 0)} pistas verificadas · ${seg / 60} min ${seg % 60} s · ${d.getString(CLAVE_MARCA)}"
                    }
                    WorkInfo.State.FAILED -> "La tarea falló."
                    WorkInfo.State.CANCELLED -> "La tarea fue cancelada."
                    else -> "Aún no se ha programado ninguna verificación."
                },
                color = NocturneTextoSuave,
                fontSize = 13.sp,
                modifier = Modifier.testTag("detalle"),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { ProgramadorMantenimiento.encolarUnico(contexto) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("botonSincronizar"),
            ) { Text("Sincronizar biblioteca ahora") }
            Spacer(Modifier.height(6.dp))
            Text(
                "Restricciones: batería no baja · almacenamiento no bajo · sin red (tarea local)",
                color = NocturneTextoSuave,
                fontSize = 11.sp,
            )
        }

        // ---------- Trabajo periódico ----------
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mantenimiento periódico", fontWeight = FontWeight.SemiBold)
                    Text("Cada 6 h (flex 1 h) · mínimo Android: 15 min", color = NocturneTextoSuave, fontSize = 12.sp)
                }
                Switch(
                    checked = periodico != null,
                    onCheckedChange = { activo ->
                        if (activo) ProgramadorMantenimiento.programarPeriodico(contexto)
                        else ProgramadorMantenimiento.cancelarPeriodico(contexto)
                    },
                    modifier = Modifier.testTag("switchPeriodico"),
                )
            }
            if (periodico != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado: ", color = NocturneTextoSuave)
                    ChipEstado(periodico.state)
                }
                val proxima = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(periodico.nextScheduleTimeMillis))
                Text("Próxima ejecución estimada: $proxima", color = NocturneTextoSuave, fontSize = 12.sp)
            }
        }

        // ---------- Registro persistido por el Worker ----------
        Tarjeta {
            Text("Registro de ejecuciones (${registro.size})", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (registro.isEmpty()) {
                Text("Sin registros todavía.", color = NocturneTextoSuave, fontSize = 13.sp)
            } else {
                registro.takeLast(4).reversed().forEach {
                    Text("• $it", color = NocturneTextoSuave, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/**
 * Animación continua y contador de cuadros: si el hilo principal se bloqueara,
 * el disco dejaría de girar y el contador se detendría.
 */
@Composable
private fun LatidoInterfaz() {
    val transicion = rememberInfiniteTransition(label = "latido")
    val angulo by transicion.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "angulo",
    )
    var ticks by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(100); ticks++ }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(28.dp)) {
            drawCircle(Color(0x33FFFFFF), style = Stroke(5f))
            drawArc(NocturneCian, startAngle = angulo, sweepAngle = 90f, useCenter = false, style = Stroke(5f))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Hilo principal libre · latido $ticks",
            color = NocturneTextoSuave,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("latido"),
        )
    }
}

@Composable
private fun ChipEstado(estado: WorkInfo.State?) {
    val color by animateColorAsState(
        when (estado) {
            WorkInfo.State.ENQUEUED -> Color(0xFFF2B33D)
            WorkInfo.State.RUNNING -> NocturneCian
            WorkInfo.State.SUCCEEDED -> Color(0xFF4ADE80)
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Color(0xFFF87171)
            else -> NocturneTextoSuave
        },
        label = "colorEstado",
    )
    Text(
        estado?.name ?: "SIN PROGRAMAR",
        color = NocturneFondo,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .testTag("estado"),
    )
}

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = NocturneSuperficie.copy(alpha = 0.92f),
            contentColor = NocturneTexto,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { contenido() }
    }
}
