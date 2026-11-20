package com.camstagdev.camstyling.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.sistema.Respaldo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cada herramienta de la app. Una nueva se agrega aquí y en MainActivity.
 * En el inicio solo se ven el ícono y el título; la descripción va arriba de su pantalla.
 */
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
    LLAMADAS(
        "Llamadas",
        "Tono que empieza bajito y sube, y una voz que dice quién te llama (a todos, a algunos o solo en ciertos perfiles).",
        Icons.Outlined.Call,
    ),
    FONDOS(
        "Fondos",
        "Fondo de inicio y de bloqueo: una imagen, o que cambie solo con las fotos de una carpeta, con otra de noche.",
        Icons.Outlined.Wallpaper,
    ),
    PERFILES(
        "Perfiles",
        "Tonos, vibración y volumen por horario (trabajo, noche, fin de semana) y horas de silencio con excepciones.",
        Icons.Outlined.Schedule,
    ),
    AVISOS(
        "Avisos",
        "Historial de notificaciones, recordatorio de lo que sigue sin abrir y linterna al llegar algo importante.",
        Icons.Outlined.NotificationsActive,
    ),
    ADICIONALES(
        "Adicionales",
        "Detalles extra que se prenden aparte: animación al cargar y atajos de colores, íconos y letra.",
        Icons.Outlined.AutoAwesome,
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
            item { TarjetaRespaldo() }
        }
    }
}

/** Guardar todos los ajustes en un archivo y volver a ponerlos desde él. */
@Composable
private fun TarjetaRespaldo() {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    fun hacer(accion: () -> Unit, listo: String) {
        alcance.launch {
            val resultado = withContext(Dispatchers.IO) { runCatching(accion) }
            Toast.makeText(
                contexto,
                if (resultado.isSuccess) listo else resultado.exceptionOrNull()?.message ?: "No se pudo",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { destino: Uri? ->
        if (destino != null) hacer({ Respaldo.guardar(contexto, destino) }, "Respaldo guardado")
    }
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { origen: Uri? ->
        if (origen != null) hacer({ Respaldo.restaurar(contexto, origen) }, "Ajustes restaurados")
    }

    Tarjeta {
        Text("Respaldo", style = MaterialTheme.typography.titleMedium)
        Explicacion(
            "Guarda en un archivo todas tus reglas, patrones, perfiles y ajustes de fondos, avisos y " +
                "adicionales. Restaurar reemplaza los de ahora. Los tonos y las carpetas elegidas siguen " +
                "dependiendo de que existan en el teléfono.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { guardar.launch("camstyling-respaldo.json") }) { Text("Guardar") }
            OutlinedButton(onClick = { restaurar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text("Restaurar")
            }
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
            Text(modulo.titulo, style = MaterialTheme.typography.titleMedium)
        }
    }
}
