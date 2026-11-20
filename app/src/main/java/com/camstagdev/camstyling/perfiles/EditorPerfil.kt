@file:OptIn(ExperimentalLayoutApi::class)

package com.camstagdev.camstyling.perfiles

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.tonos.TonosDelSistema
import com.camstagdev.camstyling.ui.Elegidos
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.SelectorDeApp
import com.camstagdev.camstyling.ui.nombreDeApp
import com.camstagdev.camstyling.vibracion.Reglas
import com.camstagdev.camstyling.vibracion.SelectorPatron

private val LETRAS = listOf("L", "M", "X", "J", "V", "S", "D")

/** Crear o cambiar un perfil. Lo que se deja en "Sin cambiar" no se toca. */
@Composable
fun EditorPerfil(inicial: Perfil, alGuardar: (Perfil) -> Unit, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    var p by remember { mutableStateOf(inicial) }
    var excepcion by remember { mutableStateOf("") }
    var eligiendoVibracion by remember { mutableStateOf(false) }
    var eligiendoApp by remember { mutableStateOf(false) }
    // Qué tono se elige en la lista del teléfono: de llamada (true) o de notificación (false).
    var tonoDeLlamada by remember { mutableStateOf(true) }

    val lista = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val datos = resultado.data ?: return@rememberLauncherForActivityResult
        if (resultado.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val elegido = if (Build.VERSION.SDK_INT >= 33) {
            datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        } ?: return@rememberLauncherForActivityResult
        p = if (tonoDeLlamada) p.copy(tonoLlamada = elegido.toString()) else p.copy(tonoNotificacion = elegido.toString())
    }

    fun elegirTono(llamada: Boolean) {
        tonoDeLlamada = llamada
        lista.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (llamada) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false),
        )
    }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (inicial.id.isEmpty()) "Nuevo perfil" else "Cambiar perfil") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(p.nombre, { p = p.copy(nombre = it) }, label = { Text("Nombre") }, singleLine = true)

                Text("Días", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LETRAS.forEachIndexed { i, letra ->
                        val dia = i + 1
                        FilterChip(
                            selected = dia in p.dias,
                            onClick = { p = p.copy(dias = if (dia in p.dias) p.dias - dia else p.dias + dia) },
                            label = { Text(letra) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { elegirHora(contexto, p.desde) { p = p.copy(desde = it) } }) { Text("Desde ${textoDeHora(p.desde)}") }
                    OutlinedButton(onClick = { elegirHora(contexto, p.hasta) { p = p.copy(hasta = it) } }) { Text("Hasta ${textoDeHora(p.hasta)}") }
                }
                Explicacion("Si \"hasta\" es más temprano, termina al día siguiente. Si son iguales, dura todo el día.")

                Text("Timbre", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = p.modo == null, onClick = { p = p.copy(modo = null) }, label = { Text("Sin cambiar") })
                    Modo.entries.forEach { modo ->
                        FilterChip(selected = p.modo == modo, onClick = { p = p.copy(modo = modo) }, label = { Text(modo.texto) })
                    }
                }

                Volumen("Volumen de llamada", p.volumenLlamada) { p = p.copy(volumenLlamada = it) }
                Volumen("Volumen de notificaciones", p.volumenNotificaciones) { p = p.copy(volumenNotificaciones = it) }

                Text("Tonos", style = MaterialTheme.typography.titleSmall)
                Eleccion("Llamada", p.tonoLlamada?.let { TonosDelSistema.nombre(contexto, Uri.parse(it)) }, { elegirTono(true) }) {
                    p = p.copy(tonoLlamada = null)
                }
                Eleccion("Notificación", p.tonoNotificacion?.let { TonosDelSistema.nombre(contexto, Uri.parse(it)) }, { elegirTono(false) }) {
                    p = p.copy(tonoNotificacion = null)
                }
                Eleccion("Vibración de llamada", p.vibracionLlamada?.let { id -> Reglas(contexto).buscar(id)?.nombre ?: "—" }, { eligiendoVibracion = true }) {
                    p = p.copy(vibracionLlamada = null)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Horas de silencio", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = p.silencio, onCheckedChange = { p = p.copy(silencio = it) })
                }
                if (p.silencio) {
                    Explicacion(
                        "Pone \"No molestar\": solo suenan las alarmas, las llamadas de tus contactos favoritos " +
                            "(con estrella) y las notificaciones de estas excepciones, que Camstyling toca con el " +
                            "volumen de alarma.",
                    )
                    p.excepciones.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (e.startsWith("app:")) "App: " + nombreDeApp(contexto, e.removePrefix("app:")) else e,
                                Modifier.weight(1f),
                            )
                            IconButton(onClick = { p = p.copy(excepciones = p.excepciones - e) }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Quitar")
                            }
                        }
                    }
                    OutlinedTextField(
                        excepcion, { excepcion = it },
                        label = { Text("Persona, grupo o palabra") }, singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { p = p.copy(excepciones = (p.excepciones + excepcion.trim()).distinct()); excepcion = "" },
                            enabled = excepcion.isNotBlank(),
                        ) { Text("Agregar") }
                        OutlinedButton(onClick = { eligiendoApp = true }) { Text("Una app entera") }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Responder solo", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = p.respuesta != null, onCheckedChange = { p = p.copy(respuesta = if (it) "Ahora no puedo, te escribo después." else null) })
                }
                if (p.respuesta != null) {
                    Explicacion(
                        "Mientras dura el perfil, contesta una vez a cada chat elegido con el botón \"Responder\" " +
                            "de la notificación (WhatsApp, Telegram, Mensajes...). Nunca a grupos. Elige personas o " +
                            "una app entera.",
                    )
                    OutlinedTextField(p.respuesta.orEmpty(), { p = p.copy(respuesta = it) }, label = { Text("Mensaje") })
                    Elegidos(p.responderA) { p = p.copy(responderA = it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(p.copy(nombre = p.nombre.trim().ifEmpty { "Perfil" })) }, enabled = p.dias.isNotEmpty()) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )

    if (eligiendoVibracion) {
        SelectorPatron(
            titulo = "Vibración de llamada",
            patrones = Reglas(contexto).todos(),
            elegido = p.vibracionLlamada,
            opcionNula = "Sin cambiar",
            alElegir = { p = p.copy(vibracionLlamada = it); eligiendoVibracion = false },
            alCerrar = { eligiendoVibracion = false },
        )
    }
    if (eligiendoApp) {
        SelectorDeApp(
            yaTienen = p.excepciones.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }.toSet(),
            alElegir = { p = p.copy(excepciones = p.excepciones + "app:$it"); eligiendoApp = false },
            alCerrar = { eligiendoApp = false },
        )
    }
}

/** Un volumen que se puede dejar sin cambiar (casilla apagada). */
@Composable
private fun Volumen(titulo: String, valor: Int?, alCambiar: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = valor != null, onCheckedChange = { alCambiar(if (it) 50 else null) })
        Text(if (valor == null) "$titulo: sin cambiar" else "$titulo: $valor %")
    }
    if (valor != null) {
        Slider(value = valor.toFloat(), onValueChange = { alCambiar(it.toInt()) }, valueRange = 0f..100f, steps = 9)
    }
}

/** Algo que se elige de una lista, con "Sin cambiar" para volver a no tocarlo. */
@Composable
private fun Eleccion(titulo: String, elegido: String?, alElegir: () -> Unit, alQuitar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(elegido ?: "Sin cambiar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = alElegir) { Text("Elegir") }
        if (elegido != null) IconButton(onClick = alQuitar) { Icon(Icons.Outlined.Close, contentDescription = "Sin cambiar") }
    }
}

private fun elegirHora(contexto: Context, minutos: Int, alElegir: (Int) -> Unit) {
    TimePickerDialog(contexto, { _, hora, minuto -> alElegir(hora * 60 + minuto) }, minutos / 60, minutos % 60, true).show()
}
