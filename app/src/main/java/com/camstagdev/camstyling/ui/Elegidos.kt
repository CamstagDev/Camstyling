package com.camstagdev.camstyling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Una lista de elegidos: apps enteras ("app:paquete"), personas, grupos o
 * palabras, con lo necesario para agregar y quitar.
 */
@Composable
fun Elegidos(elegidos: List<String>, alCambiar: (List<String>) -> Unit) {
    val contexto = LocalContext.current
    var escrito by remember { mutableStateOf("") }
    var eligiendoApp by remember { mutableStateOf(false) }

    if (elegidos.isEmpty()) Explicacion("Todavía no elegiste nada.")
    elegidos.forEach { e ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (e.startsWith("app:")) "App: " + nombreDeApp(contexto, e.removePrefix("app:")) else e, Modifier.weight(1f))
            IconButton(onClick = { alCambiar(elegidos - e) }) { Icon(Icons.Outlined.Close, contentDescription = "Quitar") }
        }
    }
    OutlinedTextField(escrito, { escrito = it }, label = { Text("Persona, grupo o palabra") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { alCambiar((elegidos + escrito.trim()).distinct()); escrito = "" }, enabled = escrito.isNotBlank()) {
            Text("Agregar")
        }
        OutlinedButton(onClick = { eligiendoApp = true }) { Text("Una app entera") }
    }

    if (eligiendoApp) {
        SelectorDeApp(
            yaTienen = elegidos.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }.toSet(),
            alElegir = { alCambiar(elegidos + "app:$it"); eligiendoApp = false },
            alCerrar = { eligiendoApp = false },
        )
    }
}
