package com.camstagdev.camstyling.tonos

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Pendiente
import com.camstagdev.camstyling.ui.SelectorDeApp
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo
import com.camstagdev.camstyling.ui.nombreDeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaTonos(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    // Sube al cambiar un tono, para volver a leer los nombres.
    var cambios by remember { mutableIntStateOf(0) }
    // Se releen cada vez que la app vuelve al frente (el usuario viene de Ajustes).
    val tonos = remember(vecesAlFrente) { Permisos.puedeCambiarTonos(contexto) }
    val notificaciones = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }
    val actuales = remember(vecesAlFrente, cambios) {
        TipoDeTono.entries.associateWith { TonosDelSistema.nombre(contexto, TonosDelSistema.actual(contexto, it)) }
    }
    // Qué tono se está cambiando mientras está abierta la lista del sistema o el selector de archivos.
    var cambiando by remember { mutableStateOf<TipoDeTono?>(null) }

    val sonidos = remember { SonidosPorApp(contexto) }
    val apps = remember(cambios) {
        sonidos.apps().map { (paquete, sonido) -> Triple(paquete, nombreDeApp(contexto, paquete), TonosDelSistema.nombre(contexto, sonido)) }
            .sortedBy { it.second.lowercase() }
    }
    var agregandoApp by remember { mutableStateOf(false) }
    // A qué app se le está eligiendo sonido mientras está abierta la lista del sistema o el selector de archivos.
    var paraApp by remember { mutableStateOf<String?>(null) }

    val sonidoDelTelefono = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val paquete = paraApp ?: return@rememberLauncherForActivityResult
        val elegido = resultado.data?.let { datos ->
            if (Build.VERSION.SDK_INT >= 33) {
                datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
        }
        if (resultado.resultCode == android.app.Activity.RESULT_OK && elegido != null) {
            sonidos.ponerApp(paquete, elegido)
            cambios++
        }
    }

    val archivoParaApp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { origen: Uri? ->
        val paquete = paraApp
        if (origen == null || paquete == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching { TonosDelSistema.copiar(contexto, origen, TipoDeTono.NOTIFICACION) }
            }
            resultado.getOrNull()?.let { sonidos.ponerApp(paquete, it) }
            cambios++
            Toast.makeText(
                contexto,
                if (resultado.isSuccess) "Sonido de ${nombreDeApp(contexto, paquete)} cambiado" else "No se pudo usar ese archivo",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun elegirSonidoDelTelefono(paquete: String) {
        paraApp = paquete
        sonidoDelTelefono.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sonidos.sonidoDeApp(paquete))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de ${nombreDeApp(contexto, paquete)}"),
        )
    }

    val listaDelSistema = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val tipo = cambiando ?: return@rememberLauncherForActivityResult
        val datos = resultado.data ?: return@rememberLauncherForActivityResult
        val elegido = if (Build.VERSION.SDK_INT >= 33) {
            datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        // Sin tono elegido = "Ninguno" (silencio). Solo con "Aceptar": al cancelar no llega nada.
        if (resultado.resultCode == android.app.Activity.RESULT_OK) {
            TonosDelSistema.poner(contexto, tipo, elegido)
            cambios++
        }
    }

    val archivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { origen: Uri? ->
        val tipo = cambiando
        if (origen == null || tipo == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching { TonosDelSistema.copiar(contexto, origen, tipo).also { TonosDelSistema.poner(contexto, tipo, it) } }
            }
            cambios++
            Toast.makeText(
                contexto,
                if (resultado.isSuccess) "Tono de ${tipo.titulo.lowercase()} cambiado" else "No se pudo usar ese archivo",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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

            item { Titulo("Tonos del sistema") }
            items(TipoDeTono.entries) { tipo ->
                Tarjeta {
                    Text(tipo.titulo, style = MaterialTheme.typography.titleMedium)
                    Text(actuales[tipo] ?: "", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            enabled = tonos,
                            onClick = {
                                cambiando = tipo
                                listaDelSistema.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, tipo.tipo)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, TonosDelSistema.actual(contexto, tipo))
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono de ${tipo.titulo.lowercase()}"),
                                )
                            },
                        ) { Text("Del teléfono") }
                        OutlinedButton(
                            enabled = tonos,
                            onClick = {
                                cambiando = tipo
                                archivo.launch(arrayOf("audio/*"))
                            },
                        ) { Text("Desde un archivo") }
                    }
                }
            }
            item {
                Explicacion(
                    "Un archivo elegido se copia a la carpeta de tonos del teléfono: sigue sonando aunque borres el " +
                        "original. La vibración de las llamadas se elige en el módulo Vibración. Algunas apps de reloj " +
                        "usan su propio sonido de alarma en vez del del sistema.",
                )
            }

            item { Titulo("Sonidos por app") }
            if (!notificaciones) {
                item {
                    TarjetaPermiso(
                        titulo = "Acceso a notificaciones",
                        explicacion = "Para sonar con el sonido de cada app cuando te llega algo.",
                        concedido = false,
                        alConceder = { Permisos.abrirLeerNotificaciones(contexto) },
                    )
                }
            }
            items(apps, key = { it.first }) { (paquete, nombre, sonido) ->
                Tarjeta {
                    Text(nombre, style = MaterialTheme.typography.titleMedium)
                    Text(sonido, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { elegirSonidoDelTelefono(paquete) }) { Text("Del teléfono") }
                        OutlinedButton(onClick = { paraApp = paquete; archivoParaApp.launch(arrayOf("audio/*")) }) { Text("Archivo") }
                        OutlinedButton(onClick = { sonidos.ponerApp(paquete, null); cambios++ }) { Text("Quitar") }
                    }
                    TextButton(onClick = { Permisos.abrirNotificacionesDeApp(contexto, paquete) }) {
                        Text("Apagar su sonido original")
                    }
                }
            }
            item {
                Explicacion(
                    "Elige la app y su sonido. Después, en los ajustes de notificación de esa app (botón de cada " +
                        "tarjeta), pon su sonido en \"Ninguno\" para que solo suene el tuyo. No la marques como " +
                        "\"Silenciosa\": así Camstyling tampoco suena. En modo vibración solo vibra.",
                )
            }
            item {
                FilledTonalButton(onClick = { agregandoApp = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Agregar app")
                }
            }

            item { Titulo("Próximamente") }
            item { Pendiente("Sonidos por categoría, persona o palabra", "Un sonido para un chat, un grupo o los mensajes que digan algo.") }
            item { Pendiente("Tonos por contacto", "Un tono de llamada distinto para cada contacto.") }
        }
    }

    if (agregandoApp) {
        SelectorDeApp(
            yaTienen = apps.map { it.first }.toSet(),
            alElegir = { paquete -> agregandoApp = false; elegirSonidoDelTelefono(paquete) },
            alCerrar = { agregandoApp = false },
        )
    }
}
