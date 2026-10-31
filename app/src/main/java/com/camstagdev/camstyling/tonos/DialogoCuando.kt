package com.camstagdev.camstyling.tonos

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.ui.Explicacion

/**
 * Elegir cuándo suena una regla nueva de [paquete]: toda la app, una
 * categoría, una persona o grupo, o una palabra. Devuelve el tipo, lo que
 * se busca y cómo mostrarlo.
 */
@Composable
fun DialogoCuando(
    nombreApp: String,
    paquete: String,
    alElegir: (cuando: Cuando, valor: String, etiqueta: String) -> Unit,
    alCerrar: () -> Unit,
) {
    val contexto = LocalContext.current
    val vistos = remember { Vistos(contexto) }
    var modo by remember { mutableStateOf<Cuando?>(null) }
    var escrito by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(modo?.texto ?: nombreApp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (modo) {
                    null -> {
                        Explicacion("¿Cuándo suena tu tono?")
                        Opciones(Cuando.entries.reversed()) { opcion ->
                            if (opcion == Cuando.APP) alElegir(Cuando.APP, "", "Toda la app") else modo = opcion
                        }
                    }
                    Cuando.CANAL -> {
                        val canales = remember { vistos.canales(paquete) }
                        if (canales.isEmpty()) {
                            Explicacion(
                                "Todavía no llegó ninguna notificación de esta app desde que diste el acceso. " +
                                    "Cuando llegue una, su categoría aparece aquí.",
                            )
                        } else {
                            Explicacion("Las categorías que ya se vieron de esta app (mensajes, grupos, llamadas...).")
                            Lista(canales.map { it.nombre }) { i -> alElegir(Cuando.CANAL, canales[i].id, canales[i].nombre) }
                        }
                    }
                    Cuando.CHAT -> {
                        val chats = remember { vistos.chats(paquete) }
                        OutlinedTextField(escrito, { escrito = it }, label = { Text("Nombre, como sale en la notificación") }, singleLine = true)
                        if (chats.isNotEmpty()) {
                            Explicacion("O elige uno de los que ya llegaron:")
                            Lista(chats) { i -> alElegir(Cuando.CHAT, chats[i], chats[i]) }
                        }
                    }
                    Cuando.PALABRA -> {
                        OutlinedTextField(escrito, { escrito = it }, label = { Text("Palabra o frase") }, singleLine = true)
                        Explicacion("Suena cuando el título o el mensaje la contienen (sin importar mayúsculas).")
                    }
                    Cuando.APP -> {}
                }
            }
        },
        confirmButton = {
            if (modo == Cuando.CHAT || modo == Cuando.PALABRA) {
                TextButton(onClick = { alElegir(modo!!, escrito.trim(), escrito.trim()) }, enabled = escrito.isNotBlank()) {
                    Text("Aceptar")
                }
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/**
 * Las opciones de cuándo suena, pegadas una debajo de otra. Con el teléfono
 * acostado van de a dos por renglón para que quepan todas sin desplazar; si
 * aun así no caben (letra muy grande), se desplazan dentro del diálogo.
 */
@Composable
private fun Opciones(opciones: List<Cuando>, alTocar: (Cuando) -> Unit) {
    val acostado = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.verticalScroll(rememberScrollState())) {
        opciones.chunked(if (acostado) 2 else 1).forEach { renglon ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                renglon.forEach { opcion -> Fila(opcion.texto, Modifier.weight(1f)) { alTocar(opcion) } }
            }
        }
    }
}

@Composable
private fun Fila(texto: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth().clickable(onClick = alTocar).padding(vertical = 12.dp),
    )
}

@Composable
private fun Lista(textos: List<String>, alElegir: (Int) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 320.dp)) {
        items(textos.size) { i -> Fila(textos[i]) { alElegir(i) } }
    }
}

/** De dónde sale el tono: la lista del teléfono o un archivo. */
@Composable
fun DialogoFuente(titulo: String, alDelTelefono: () -> Unit, alDeArchivo: () -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column {
                Fila("De la lista del teléfono", alTocar = alDelTelefono)
                Fila("Desde un archivo de audio", alTocar = alDeArchivo)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
