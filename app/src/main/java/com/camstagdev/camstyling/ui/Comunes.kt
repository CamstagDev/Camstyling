package com.camstagdev.camstyling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Piezas que comparten las pantallas de los módulos.

@Composable
fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun Explicacion(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Tarjeta(contenido: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
    }
}

@Composable
fun TarjetaPermiso(titulo: String, explicacion: String, concedido: Boolean, alConceder: () -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (concedido) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = if (concedido) "Concedido" else "Falta",
                tint = if (concedido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Text(titulo, style = MaterialTheme.typography.titleMedium)
        }
        Explicacion(explicacion)
        if (!concedido) {
            FilledTonalButton(onClick = alConceder) { Text("Dar permiso") }
        }
    }
}

@Composable
fun Pendiente(titulo: String, explicacion: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Explicacion(explicacion)
    }
}
