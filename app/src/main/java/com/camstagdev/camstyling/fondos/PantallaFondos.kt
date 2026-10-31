@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.camstagdev.camstyling.fondos

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.Titulo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PantallaFondos(alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val ajustes = remember { AjustesFondos(contexto) }

    // Sube con cada cambio de los ajustes, para volver a leerlos.
    var cambios by remember { mutableIntStateOf(0) }
    val pantalla = remember(cambios) { ajustes.pantalla }
    val activo = remember(cambios) { ajustes.activo }
    val carpeta = remember(cambios) { ajustes.carpeta }
    val orden = remember(cambios) { ajustes.orden }
    val horas = remember(cambios) { ajustes.horas }
    val noche = remember(cambios) { ajustes.noche }
    val carpetaNoche = remember(cambios) { ajustes.carpetaNoche }
    val desde = remember(cambios) { ajustes.desde }
    val hasta = remember(cambios) { ajustes.hasta }

    var poniendo by remember { mutableStateOf(false) }

    /** Pone un fondo en segundo plano (leer una foto grande tarda) y avisa cómo le fue. */
    fun ponerFondo(accion: () -> String?) {
        poniendo = true
        alcance.launch {
            val resultado = withContext(Dispatchers.IO) { runCatching(accion) }
            poniendo = false
            val texto = when {
                resultado.isFailure -> "No se pudo poner esa imagen"
                resultado.getOrNull() == null -> "No hay imágenes en la carpeta"
                else -> "Listo: ${resultado.getOrNull()}"
            }
            Toast.makeText(contexto, texto, Toast.LENGTH_SHORT).show()
        }
    }

    /** Tras cambiar algo de la rotación: pone la que toque (si toca) y rearma el trabajo periódico. */
    fun reprogramar(forzar: Boolean = false) {
        cambios++
        if (!ajustes.activo) {
            Rotacion.programar(contexto)
            return
        }
        if (forzar) {
            ponerFondo { Rotacion.tocar(contexto, forzar = true) }
        } else {
            alcance.launch(Dispatchers.IO) { runCatching { Rotacion.tocar(contexto, forzar = false) } }
        }
        Rotacion.programar(contexto)
    }

    val elegirImagen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { imagen: Uri? ->
        if (imagen == null) return@rememberLauncherForActivityResult
        ponerFondo { Fondos.poner(contexto, imagen, ajustes.pantalla); "imagen puesta" }
    }

    val elegirCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { elegida: Uri? ->
        if (elegida == null) return@rememberLauncherForActivityResult
        ajustes.carpeta = elegida
        // Si es la que se usa ahora, el cambio se ve enseguida.
        reprogramar(forzar = ajustes.activo && Rotacion.franjaActual(ajustes) == Franja.DIA)
    }

    val elegirCarpetaNoche = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { elegida: Uri? ->
        if (elegida == null) return@rememberLauncherForActivityResult
        ajustes.carpetaNoche = elegida
        reprogramar(forzar = ajustes.activo && Rotacion.franjaActual(ajustes) == Franja.NOCHE)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondos") },
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
            item {
                Tarjeta {
                    Text("Dónde se pone", style = MaterialTheme.typography.titleMedium)
                    Opciones(Pantalla.entries, pantalla, { it.texto }) { ajustes.pantalla = it; cambios++ }
                    Explicacion("Vale para la imagen suelta y para el cambio automático.")
                }
            }

            item { Titulo("Una imagen") }
            item {
                Tarjeta {
                    Explicacion("Elige una imagen y queda de fondo. Si el cambio automático está prendido, la reemplaza en su próxima vuelta.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { elegirImagen.launch(arrayOf("image/*")) }, enabled = !poniendo) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Text("  Elegir imagen")
                        }
                        if (poniendo) CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }

            item { Titulo("Cambio automático") }
            item {
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cambiar solo", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = activo,
                            onCheckedChange = { prender ->
                                if (prender && carpeta == null) {
                                    Toast.makeText(contexto, "Primero elige una carpeta", Toast.LENGTH_SHORT).show()
                                } else {
                                    ajustes.activo = prender
                                    reprogramar(forzar = prender)
                                }
                            },
                        )
                    }
                    Carpeta(carpeta, alElegir = { elegirCarpeta.launch(carpeta) })
                    Text("Orden", style = MaterialTheme.typography.bodyLarge)
                    Opciones(Orden.entries, orden, { it.texto }) { ajustes.orden = it; cambios++ }
                    Text("Cada cuánto", style = MaterialTheme.typography.bodyLarge)
                    Opciones(INTERVALOS, horas, ::textoDeIntervalo) { ajustes.horas = it; reprogramar() }
                    OutlinedButton(onClick = { ponerFondo { Rotacion.tocar(contexto, forzar = true) } }, enabled = activo && !poniendo) {
                        Text("Cambiar ahora")
                    }
                }
            }

            item { Titulo("De noche") }
            item {
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Otro fondo de noche", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = noche, onCheckedChange = { ajustes.noche = it; reprogramar() })
                    }
                    Carpeta(carpetaNoche, alElegir = { elegirCarpetaNoche.launch(carpetaNoche) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { elegirHora(contexto, desde) { ajustes.desde = it; reprogramar() } }) {
                            Text("Desde ${textoDeHora(desde)}")
                        }
                        OutlinedButton(onClick = { elegirHora(contexto, hasta) { ajustes.hasta = it; reprogramar() } }) {
                            Text("Hasta ${textoDeHora(hasta)}")
                        }
                    }
                    Explicacion(
                        "Entre esas horas usa las imágenes de esta carpeta, con el mismo orden y cada cuánto. " +
                            "Para tener siempre el mismo fondo de noche, deja una sola imagen en ella.",
                    )
                }
            }

            item {
                Explicacion(
                    "Para no gastar batería no hay nada corriendo todo el tiempo: Android despierta a la app " +
                        "más o menos a la hora que toca (puede atrasarse un rato si el teléfono está quieto) y " +
                        "cambia la imagen. Con el fondo de noche prendido revisa una vez por hora, así que el " +
                        "paso de día a noche puede tardar hasta una hora.",
                )
            }
            item {
                Explicacion(
                    "En Android 12 o más nuevo, los colores del sistema (Material You) se toman del fondo: " +
                        "cambian con cada imagen nueva.",
                )
            }
        }
    }
}

/** Una fila de opciones en la que se elige una. */
@Composable
private fun <T> Opciones(opciones: List<T>, elegida: T, texto: (T) -> String, alElegir: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { opcion ->
            FilterChip(selected = opcion == elegida, onClick = { alElegir(opcion) }, label = { Text(texto(opcion)) })
        }
    }
}

/** La carpeta elegida (nombre y cuántas imágenes tiene) con su botón para cambiarla. */
@Composable
private fun Carpeta(carpeta: Uri?, alElegir: () -> Unit) {
    val contexto = LocalContext.current
    var detalle by remember(carpeta) { mutableStateOf<String?>(null) }
    LaunchedEffect(carpeta) {
        if (carpeta == null) return@LaunchedEffect
        detalle = withContext(Dispatchers.IO) {
            val nombre = Fondos.nombreDeCarpeta(contexto, carpeta) ?: "Carpeta"
            val cuantas = Fondos.imagenes(contexto, carpeta).size
            "$nombre · " + if (cuantas == 1) "1 imagen" else "$cuantas imágenes"
        }
    }
    Text(
        if (carpeta == null) "Sin carpeta" else detalle ?: "…",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    FilledTonalButton(onClick = alElegir) {
        Icon(Icons.Outlined.Folder, contentDescription = null)
        Text(if (carpeta == null) "  Elegir carpeta" else "  Cambiar carpeta")
    }
}

private fun textoDeHora(minutos: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutos / 60, minutos % 60)

private fun elegirHora(contexto: Context, minutos: Int, alElegir: (Int) -> Unit) {
    TimePickerDialog(contexto, { _, hora, minuto -> alElegir(hora * 60 + minuto) }, minutos / 60, minutos % 60, true).show()
}
