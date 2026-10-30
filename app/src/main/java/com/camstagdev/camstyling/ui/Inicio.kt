package com.camstagdev.camstyling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Cada herramienta de la app. Una nueva se agrega aquí y en MainActivity. */
enum class Modulo(val titulo: String, val descripcion: String, val icono: ImageVector) {
    TONOS(
        "Tonos",
        "Tono de llamada, notificaciones y alarma; sonidos por app, por contacto y por persona.",
        Icons.Outlined.MusicNote,
    ),
    VIBRACION(
        "Vibración",
        "Patrones de vibración para llamadas y para cada app: los de siempre, los tuyos o al ritmo de un sonido.",
        Icons.Outlined.Vibration,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(alAbrir: (Modulo) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Camstyling") }) }) { margen ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(margen),
        ) {
            items(Modulo.entries) { modulo -> TarjetaModulo(modulo, alAbrir) }
        }
    }
}

@Composable
private fun TarjetaModulo(modulo: Modulo, alAbrir: (Modulo) -> Unit) {
    Card(
        onClick = { alAbrir(modulo) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(modulo.icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(modulo.titulo, style = MaterialTheme.typography.titleMedium)
                Text(modulo.descripcion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
