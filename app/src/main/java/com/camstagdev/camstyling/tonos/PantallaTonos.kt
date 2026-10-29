package com.camstagdev.camstyling.tonos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.sistema.Permisos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaTonos(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    // Se releen cada vez que la app vuelve al frente (el usuario viene de Ajustes).
    val tonos = remember(vecesAlFrente) { Permisos.puedeCambiarTonos(contexto) }
    val notificaciones = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tonos") },
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
            item { Titulo("Permisos") }
            item {
                TarjetaPermiso(
                    titulo = "Cambiar tonos del sistema",
                    explicacion = "Para poner tu tono de llamada, de notificación y de alarma.",
                    concedido = tonos,
                    alConceder = { Permisos.abrirCambiarTonos(contexto) },
                )
            }
            item {
                TarjetaPermiso(
                    titulo = "Acceso a notificaciones",
                    explicacion = "Para sonar con tu tono cuando llega una notificación de una app, persona o grupo.",
                    concedido = notificaciones,
                    alConceder = { Permisos.abrirLeerNotificaciones(contexto) },
                )
            }
            item { Titulo("Próximamente") }
            item { Pendiente("Tonos del sistema", "Llamada, notificación y alarma con cualquier archivo de audio.") }
            item { Pendiente("Sonidos por app", "WhatsApp, Telegram, Mensajes y Discord, por categoría, persona, grupo o palabra.") }
            item { Pendiente("Tonos por contacto", "Un tono de llamada distinto para cada contacto.") }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun TarjetaPermiso(titulo: String, explicacion: String, concedido: Boolean, alConceder: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (concedido) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = if (concedido) "Concedido" else "Falta",
                    tint = if (concedido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                Text(titulo, style = MaterialTheme.typography.titleMedium)
            }
            Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!concedido) {
                FilledTonalButton(onClick = alConceder) { Text("Dar permiso") }
            }
        }
    }
}

@Composable
private fun Pendiente(titulo: String, explicacion: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
