@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.camstagdev.camstyling.llamadas

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.camstagdev.camstyling.perfiles.Perfiles
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.tonos.TonosPorContacto
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Modulo
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo

@Composable
fun PantallaLlamadas(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val ajustes = remember { AjustesLlamadas(contexto) }
    var cambios by remember { mutableIntStateOf(0) }
    val creciente = remember(cambios) { ajustes.creciente }
    val segundos = remember(cambios) { ajustes.segundosDeSubida }
    val anunciar = remember(cambios) { ajustes.anunciar }
    val contactos = remember(cambios) { ajustes.contactos }
    val enPerfiles = remember(cambios) { ajustes.perfiles }
    val perfiles = remember(cambios) { Perfiles(contexto).todos() }
    val telefono = remember(vecesAlFrente, cambios) { Permisos.puedeVerLlamadas(contexto) }
    val numero = remember(vecesAlFrente, cambios) { Permisos.puedeVerNumero(contexto) }
    val agenda = remember(vecesAlFrente, cambios) { Permisos.puedeUsarContactos(contexto) }
    val escucha = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { respuesta ->
        cambios++
        val actividad = contexto as? Activity
        // Negado "para siempre": Android ya no muestra el diálogo, queda Ajustes.
        val negado = respuesta.filterValues { !it }.keys.firstOrNull()
        if (negado != null && actividad != null && !ActivityCompat.shouldShowRequestPermissionRationale(actividad, negado)) {
            Permisos.abrirAjustesDeLaApp(contexto)
        }
    }
    val elegirContacto = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contacto: Uri? ->
        val nombre = contacto?.let { TonosPorContacto.nombre(contexto, it) } ?: return@rememberLauncherForActivityResult
        ajustes.contactos = (contactos + nombre).distinct()
        cambios++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Llamadas") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { margen ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(margen),
        ) {
            item { Explicacion(Modulo.LLAMADAS.descripcion) }
            item {
                TarjetaPermiso(
                    titulo = "Teléfono",
                    explicacion = "Para saber cuándo suena una llamada.",
                    concedido = telefono,
                    alConceder = { pedir.launch(arrayOf(Permisos.VER_LLAMADAS)) },
                )
            }
            if (!escucha) {
                item {
                    Explicacion(
                        "Con el \"Acceso a notificaciones\" (módulo Tonos) prendido, Android mantiene la app lista " +
                            "y el tono que sube y la voz arrancan sin demora.",
                    )
                }
            }

            item { Titulo("Tono que sube") }
            item {
                Tarjeta {
                    Interruptor("Empezar bajito", creciente) { ajustes.creciente = it; cambios++ }
                    Explicacion(
                        "La llamada empieza casi en silencio y sube hasta tu volumen de siempre. Al contestar o " +
                            "colgar, el volumen vuelve a como estaba.",
                    )
                    Text("Tarda", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20, 30).forEach { s ->
                            FilterChip(selected = segundos == s, onClick = { ajustes.segundosDeSubida = s; cambios++ }, label = { Text("$s s") })
                        }
                    }
                }
            }

            item { Titulo("Decir quién llama") }
            item {
                Tarjeta {
                    Interruptor("Anunciar con voz", anunciar) { ajustes.anunciar = it; cambios++ }
                    Explicacion(
                        "Dice \"Te llama …\" con la voz del teléfono, sin internet (si falta la voz en español, " +
                            "se baja una vez en Ajustes > Sistema > Idiomas > Salida de texto a voz).",
                    )
                    if (!numero || !agenda) {
                        OutlinedButton(onClick = { pedir.launch(Permisos.VER_NUMERO + Permisos.CONTACTOS) }) {
                            Text("Dar permisos de registro de llamadas y contactos")
                        }
                        Explicacion("Android solo le dice a la app el número de quien llama con \"Registro de llamadas\"; con \"Contactos\" saca el nombre.")
                    }
                    Text("Contactos", style = MaterialTheme.typography.bodyLarge)
                    if (contactos.isEmpty()) Explicacion("Todos (los que no están en tus contactos se anuncian como número desconocido).")
                    contactos.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c, Modifier.weight(1f))
                            IconButton(onClick = { ajustes.contactos = contactos - c; cambios++ }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Quitar")
                            }
                        }
                    }
                    OutlinedButton(onClick = { elegirContacto.launch(null) }) { Text("Solo algunos contactos") }
                    if (perfiles.isNotEmpty()) {
                        Text("Cuándo", style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = enPerfiles.isEmpty(), onClick = { ajustes.perfiles = emptyList(); cambios++ }, label = { Text("Siempre") })
                            perfiles.forEach { p ->
                                FilterChip(
                                    selected = p.id in enPerfiles,
                                    onClick = { ajustes.perfiles = if (p.id in enPerfiles) enPerfiles - p.id else enPerfiles + p.id; cambios++ },
                                    label = { Text(p.nombre) },
                                )
                            }
                        }
                        Explicacion("Por ejemplo, solo con el perfil \"Manejando\".")
                    }
                }
            }
        }
    }
}

@Composable
private fun Interruptor(texto: String, prendido: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = prendido, onCheckedChange = alCambiar)
    }
}
