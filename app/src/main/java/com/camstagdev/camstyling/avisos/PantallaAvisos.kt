@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.camstagdev.camstyling.avisos

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.ui.Elegidos
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Modulo
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo
import com.camstagdev.camstyling.ui.nombreDeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaAvisos(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val ajustes = remember { AjustesAvisos(contexto) }
    val historial = remember { Historial(contexto) }
    // Sube con cada cambio, para volver a leer los ajustes y el historial.
    var cambios by remember { mutableIntStateOf(0) }
    val notificaciones = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }
    var busqueda by remember { mutableStateOf("") }
    var guardados by remember { mutableStateOf<List<Guardado>>(emptyList()) }
    LaunchedEffect(busqueda, cambios, vecesAlFrente) {
        guardados = withContext(Dispatchers.IO) { runCatching { historial.recientes(busqueda) }.getOrDefault(emptyList()) }
    }

    // Lee el ajuste en cada recomposición tras un cambio.
    val conHistorial = remember(cambios) { ajustes.historial }
    val dias = remember(cambios) { ajustes.diasDeHistorial }
    val conRecordatorio = remember(cambios) { ajustes.recordatorio }
    val minutos = remember(cambios) { ajustes.minutosDeRecordatorio }
    val repeticiones = remember(cambios) { ajustes.repeticiones }
    val elegidosRecordatorio = remember(cambios) { ajustes.elegidosRecordatorio }
    val conLinterna = remember(cambios) { ajustes.linterna }
    val soloApagada = remember(cambios) { ajustes.linternaSoloApagada }
    val elegidosLinterna = remember(cambios) { ajustes.elegidosLinterna }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Avisos") },
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
            item { Explicacion(Modulo.AVISOS.descripcion) }
            if (!notificaciones) {
                item {
                    TarjetaPermiso(
                        titulo = "Acceso a notificaciones",
                        explicacion = "Todo este módulo funciona con las notificaciones que llegan.",
                        concedido = false,
                        alConceder = { Permisos.abrirLeerNotificaciones(contexto) },
                    )
                }
            }

            item { Titulo("Recordatorio") }
            item {
                Tarjeta {
                    Interruptor("Volver a avisar", conRecordatorio) { ajustes.recordatorio = it; cambios++ }
                    Explicacion(
                        "Si una notificación de lo elegido sigue sin abrir, vuelve a sonar y vibrar. Para cuando la " +
                            "abres o la descartas. Con la pantalla apagada Android puede atrasarlo unos minutos.",
                    )
                    Text("Cada", style = MaterialTheme.typography.bodyLarge)
                    Opciones(MINUTOS_DE_RECORDATORIO, minutos, { "$it min" }) { ajustes.minutosDeRecordatorio = it; cambios++ }
                    Text("Cuántas veces", style = MaterialTheme.typography.bodyLarge)
                    Opciones(listOf(1, 2, 3, 5, 10), repeticiones, { "$it" }) { ajustes.repeticiones = it; cambios++ }
                    Text("Para", style = MaterialTheme.typography.bodyLarge)
                    Elegidos(elegidosRecordatorio) { ajustes.elegidosRecordatorio = it; cambios++ }
                }
            }

            item { Titulo("Linterna") }
            item {
                Tarjeta {
                    Interruptor("Parpadear la linterna", conLinterna) { ajustes.linterna = it; cambios++ }
                    Interruptor("Solo con la pantalla apagada", soloApagada) { ajustes.linternaSoloApagada = it; cambios++ }
                    Explicacion("Tres destellos cortos cuando llega algo de lo elegido. Respeta \"No molestar\".")
                    Elegidos(elegidosLinterna) { ajustes.elegidosLinterna = it; cambios++ }
                    OutlinedButton(onClick = { Linterna.parpadear(contexto) }) { Text("Probar") }
                }
            }

            item { Titulo("Historial") }
            item {
                Tarjeta {
                    Interruptor("Guardar las notificaciones", conHistorial) { ajustes.historial = it; cambios++ }
                    Explicacion(
                        "Guarda el título y el texto de lo que llega, aunque lo descartes o la app lo borre después " +
                            "(un mensaje eliminado de WhatsApp, por ejemplo). Queda solo en este teléfono.",
                    )
                    Text("Guardar por", style = MaterialTheme.typography.bodyLarge)
                    Opciones(DIAS_DE_HISTORIAL, dias, { if (it == 1) "1 día" else "$it días" }) { ajustes.diasDeHistorial = it; cambios++ }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(busqueda, { busqueda = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            alcance.launch {
                                withContext(Dispatchers.IO) { runCatching { historial.borrarTodo() } }
                                cambios++
                            }
                        }) { Text("Borrar") }
                    }
                }
            }
            if (guardados.isEmpty()) {
                item { Explicacion(if (conHistorial) "Nada guardado todavía." else "El historial está apagado.") }
            }
            items(guardados) { g ->
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(nombreDeApp(contexto, g.paquete), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text(
                            DateUtils.getRelativeTimeSpanString(g.cuando).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (g.titulo.isNotBlank()) Text(g.titulo, style = MaterialTheme.typography.titleSmall)
                    if (g.texto.isNotBlank()) Text(g.texto, style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun <T> Opciones(opciones: List<T>, elegida: T, texto: (T) -> String, alElegir: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { opcion ->
            FilterChip(selected = opcion == elegida, onClick = { alElegir(opcion) }, label = { Text(texto(opcion)) })
        }
    }
}
