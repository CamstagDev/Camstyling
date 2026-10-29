@file:OptIn(ExperimentalMaterial3Api::class)

package com.camstagdev.camstyling.vibracion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Dibujo del patrón: cada barra es un tramo que vibra, más alta mientras más fuerte. */
@Composable
fun Grafica(tramos: List<Tramo>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val fondo = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        drawRect(fondo)
        val total = tramos.sumOf { it.ms }.coerceAtLeast(1)
        var inicio = 0L
        for (t in tramos) {
            if (t.fuerza > 0) {
                val alto = size.height * (0.25f + 0.75f * t.fuerza / 255f)
                drawRect(
                    color,
                    topLeft = Offset(size.width * inicio / total, size.height - alto),
                    size = Size((size.width * t.ms / total).coerceAtLeast(1f), alto),
                )
            }
            inicio += t.ms
        }
    }
}

/** Crear o cambiar un patrón propio con deslizadores: pulsos, duración, pausa, fuerza... */
@Composable
fun EditorPatron(
    inicial: Patron?,
    controlaFuerza: Boolean,
    alGuardar: (Patron) -> Unit,
    alCerrar: () -> Unit,
) {
    val contexto = LocalContext.current
    var nombre by remember { mutableStateOf(inicial?.nombre ?: "Mi vibración") }
    var receta by remember { mutableStateOf(inicial?.receta ?: Receta()) }
    val patron = Patron(inicial?.id ?: "", nombre.ifBlank { "Mi vibración" }, receta.aTramos(), receta)

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (inicial == null) "Nuevo patrón" else "Cambiar patrón") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Grafica(patron.tramos, Modifier.padding(vertical = 8.dp))
                Text("Dura ${patron.duracionMs} ms", style = MaterialTheme.typography.bodySmall)
                Deslizador("Pulsos", receta.pulsos, 1..8, 1) { receta = receta.copy(pulsos = it) }
                Deslizador("Duración de cada pulso", receta.duracionMs, 20..800, 20, { "$it ms" }) { receta = receta.copy(duracionMs = it) }
                Deslizador("Pausa entre pulsos", receta.pausaMs, 20..1000, 20, { "$it ms" }) { receta = receta.copy(pausaMs = it) }
                Deslizador("Fuerza", receta.fuerza, 10..100, 10, { "$it %" }) { receta = receta.copy(fuerza = it) }
                if (!controlaFuerza) {
                    Text(
                        "Tu teléfono no cambia la fuerza de vibración: aquí solo cuentan los tiempos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Forma", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Forma.entries.forEach { forma ->
                        FilterChip(selected = receta.forma == forma, onClick = { receta = receta.copy(forma = forma) }, label = { Text(forma.titulo) })
                    }
                }
                Deslizador("Repetir", receta.repeticiones, 1..5, 1, { if (it == 1) "1 vez" else "$it veces" }) { receta = receta.copy(repeticiones = it) }
                if (receta.repeticiones > 1) {
                    Deslizador("Pausa entre repeticiones", receta.pausaEntreMs, 100..2000, 100, { "$it ms" }) { receta = receta.copy(pausaEntreMs = it) }
                }
                OutlinedButton(onClick = { Vibrador.vibrar(contexto, patron, Vibrador.Uso.PRUEBA) }) {
                    Icon(Icons.Outlined.Vibration, contentDescription = null)
                    Text("  Probar")
                }
            }
        },
        confirmButton = { TextButton(onClick = { alGuardar(patron) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun Deslizador(
    etiqueta: String,
    valor: Int,
    rango: IntRange,
    paso: Int,
    formato: (Int) -> String = { "$it" },
    alCambiar: (Int) -> Unit,
) {
    Column {
        Text("$etiqueta: ${formato(valor)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = valor.toFloat(),
            onValueChange = { alCambiar(((it / paso).roundToInt() * paso).coerceIn(rango.first, rango.last)) },
            valueRange = rango.first.toFloat()..rango.last.toFloat(),
            steps = ((rango.last - rango.first) / paso - 1).coerceAtLeast(0),
        )
    }
}

/**
 * Elegir un patrón de la lista (predeterminados y propios), con un botón para
 * probar cada uno. [opcionNula] es el texto de "no cambiar nada" (vuelve al sistema).
 * Con [alDesdeSonido], ofrece también crear uno que siga el ritmo de un sonido.
 */
@Composable
fun SelectorPatron(
    titulo: String,
    patrones: List<Patron>,
    elegido: String?,
    opcionNula: String,
    alElegir: (String?) -> Unit,
    alCerrar: () -> Unit,
    alDesdeSonido: (() -> Unit)? = null,
) {
    val contexto = LocalContext.current
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Opcion(opcionNula, elegido == null, alElegir = { alElegir(null) }, alProbar = null)
                patrones.forEach { p ->
                    Opcion(
                        p.nombre,
                        elegido == p.id,
                        alElegir = { alElegir(p.id) },
                        alProbar = if (p.vibra) ({ Vibrador.vibrar(contexto, p, Vibrador.Uso.PRUEBA) }) else null,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        dismissButton = alDesdeSonido?.let { { TextButton(onClick = it) { Text("Desde un sonido") } } },
    )
}

@Composable
private fun Opcion(texto: String, marcada: Boolean, alElegir: () -> Unit, alProbar: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = alElegir), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = marcada, onClick = alElegir)
        Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (alProbar != null) {
            IconButton(onClick = alProbar) { Icon(Icons.Outlined.Vibration, contentDescription = "Probar") }
        }
    }
}
