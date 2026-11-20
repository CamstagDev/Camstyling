@file:OptIn(ExperimentalLayoutApi::class)

package com.camstagdev.camstyling.tonos

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.ui.Explicacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Elegir el pedazo de una canción (desde, hasta y entrada suave) y guardarlo como tono. */
@Composable
fun DialogoRecorte(cancion: Uri, alListo: () -> Unit, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var duracion by remember { mutableLongStateOf(0L) }
    var rango by remember { mutableStateOf(0f..30f) }
    var entrada by remember { mutableLongStateOf(2000L) }
    var nombre by remember { mutableStateOf(TonosDelSistema.nombreDeArchivo(contexto, cancion)?.substringBeforeLast('.') ?: "Mi tono") }
    var guardando by remember { mutableStateOf(false) }
    val reproductor = remember { MediaPlayer() }
    val hilo = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(cancion) {
        duracion = withContext(Dispatchers.IO) { runCatching { Recorte.duracionMs(contexto, cancion) }.getOrDefault(0L) }
        val total = duracion / 1000f
        rango = 0f..minOf(total, 30f)
    }
    DisposableEffect(Unit) {
        onDispose {
            hilo.removeCallbacksAndMessages(null)
            runCatching { reproductor.release() }
        }
    }

    fun escuchar() {
        hilo.removeCallbacksAndMessages(null)
        runCatching {
            reproductor.reset()
            reproductor.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            reproductor.setDataSource(contexto, cancion)
            reproductor.prepare()
            reproductor.seekTo((rango.start * 1000).toInt())
            reproductor.start()
            hilo.postDelayed({ runCatching { reproductor.pause() } }, ((rango.endInclusive - rango.start) * 1000).toLong())
        }
    }

    fun guardar(tipo: TipoDeTono) {
        guardando = true
        runCatching { reproductor.pause() }
        alcance.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching {
                    val tono = Recorte.guardar(
                        contexto, cancion,
                        desdeMs = (rango.start * 1000).toLong(),
                        hastaMs = (rango.endInclusive * 1000).toLong(),
                        entradaMs = entrada,
                        nombre = nombre.trim().ifEmpty { "Mi tono" },
                        tipo = tipo,
                    )
                    if (Settings.System.canWrite(contexto)) TonosDelSistema.poner(contexto, tipo, tono)
                }
            }
            guardando = false
            Toast.makeText(
                contexto,
                if (resultado.isSuccess) "Listo: guardado en la carpeta de tonos" else "No se pudo recortar esa canción",
                Toast.LENGTH_LONG,
            ).show()
            if (resultado.isSuccess) alListo()
        }
    }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Recortar") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre del tono") }, singleLine = true)
                if (duracion <= 0) {
                    CircularProgressIndicator()
                } else {
                    Text("De ${minutos(rango.start)} a ${minutos(rango.endInclusive)}", style = MaterialTheme.typography.bodyLarge)
                    RangeSlider(
                        value = rango,
                        onValueChange = { nuevo ->
                            // Como mucho 40 s: si se pasa, se mueve el otro extremo.
                            val largo = Recorte.MAXIMO_MS / 1000f
                            rango = if (nuevo.endInclusive - nuevo.start <= largo) nuevo
                            else if (nuevo.start != rango.start) nuevo.start..(nuevo.start + largo)
                            else (nuevo.endInclusive - largo)..nuevo.endInclusive
                        },
                        valueRange = 0f..(duracion / 1000f),
                    )
                    Explicacion("Hasta 40 segundos. Busca el coro con \"Escuchar\".")
                    Text("Entrada suave", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0L, 1000L, 2000L, 3000L, 5000L).forEach { ms ->
                            FilterChip(selected = entrada == ms, onClick = { entrada = ms }, label = { Text(if (ms == 0L) "No" else "${ms / 1000} s") })
                        }
                    }
                    OutlinedButton(onClick = { escuchar() }) { Text("Escuchar") }
                    if (guardando) CircularProgressIndicator()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { guardar(TipoDeTono.LLAMADA) }, enabled = !guardando) { Text("Tono de llamada") }
                        TextButton(onClick = { guardar(TipoDeTono.NOTIFICACION) }, enabled = !guardando) { Text("Notificación") }
                    }
                    Explicacion("Se guarda en la carpeta de tonos y, si diste el permiso, queda puesto.")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}

private fun minutos(segundos: Float): String {
    val s = segundos.toInt()
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}
