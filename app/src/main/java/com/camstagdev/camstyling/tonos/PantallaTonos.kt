package com.camstagdev.camstyling.tonos

import android.app.Activity
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
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.core.app.ActivityCompat
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Modulo
import com.camstagdev.camstyling.ui.SelectorDeApp
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo
import com.camstagdev.camstyling.ui.nombreDeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A qué se le está eligiendo un tono: un tono del sistema, una regla por app o un contacto. */
private sealed interface Destino {
    data class Sistema(val tipo: TipoDeTono) : Destino
    data class Regla(val regla: ReglaDeSonido) : Destino
    data class Contacto(val uri: Uri, val nombre: String) : Destino

    /** El tipo de tono que se ofrece en la lista del teléfono y la carpeta a la que se copia un archivo. */
    val tipoDeTono: TipoDeTono
        get() = when (this) {
            is Sistema -> tipo
            is Regla -> TipoDeTono.NOTIFICACION
            is Contacto -> TipoDeTono.LLAMADA
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaTonos(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val reglasDeSonido = remember { ReglasDeSonido(contexto) }
    val vistos = remember { Vistos(contexto) }
    // Sube al cambiar un tono o una regla, para volver a leerlos.
    var cambios by remember { mutableIntStateOf(0) }
    // Se releen cada vez que la app vuelve al frente (el usuario viene de Ajustes).
    val tonos = remember(vecesAlFrente) { Permisos.puedeCambiarTonos(contexto) }
    val notificaciones = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }
    val contactos = remember(vecesAlFrente, cambios) { Permisos.puedeUsarContactos(contexto) }
    val actuales = remember(vecesAlFrente, cambios) {
        TipoDeTono.entries.associateWith { TonosDelSistema.nombre(contexto, TonosDelSistema.actual(contexto, it)) }
    }
    val reglas = remember(cambios) {
        reglasDeSonido.todas()
            .map { Triple(it, nombreDeApp(contexto, it.paquete), TonosDelSistema.nombre(contexto, Uri.parse(it.tono))) }
            .sortedWith(compareBy({ it.second.lowercase() }, { it.first.cuando.ordinal }))
    }
    var conTono by remember { mutableStateOf<List<Pair<ContactoConTono, String>>>(emptyList()) }
    LaunchedEffect(contactos, cambios) {
        conTono = if (!contactos) emptyList() else withContext(Dispatchers.IO) {
            runCatching { TonosPorContacto.lista(contexto).map { it to TonosDelSistema.nombre(contexto, it.tono) } }.getOrDefault(emptyList())
        }
    }

    // Qué se está cambiando mientras está abierta la lista del sistema o el selector de archivos.
    var destino by remember { mutableStateOf<Destino?>(null) }
    var eligiendoFuente by remember { mutableStateOf(false) }
    var agregandoApp by remember { mutableStateOf(false) }
    var appNueva by remember { mutableStateOf<String?>(null) }
    var recortando by remember { mutableStateOf<Uri?>(null) }
    val elegirCancion = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { cancion: Uri? ->
        recortando = cancion
    }

    /** Pone el tono elegido donde corresponde. */
    fun aplicar(a: Destino, tono: Uri?) {
        when (a) {
            is Destino.Sistema -> TonosDelSistema.poner(contexto, a.tipo, tono)
            is Destino.Regla -> if (tono != null) reglasDeSonido.guardar(a.regla.copy(tono = tono.toString()))
            is Destino.Contacto -> TonosPorContacto.poner(contexto, a.uri, tono)
        }
    }

    val listaDelSistema = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val a = destino ?: return@rememberLauncherForActivityResult
        val datos = resultado.data ?: return@rememberLauncherForActivityResult
        val elegido = if (Build.VERSION.SDK_INT >= 33) {
            datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            datos.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        // Sin tono elegido = "Ninguno" (silencio). Solo con "Aceptar": al cancelar no llega nada.
        if (resultado.resultCode == Activity.RESULT_OK) {
            runCatching { aplicar(a, elegido) }
            cambios++
        }
    }

    val archivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { origen: Uri? ->
        val a = destino
        if (origen == null || a == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching { TonosDelSistema.copiar(contexto, origen, a.tipoDeTono).also { aplicar(a, it) } }
            }
            cambios++
            Toast.makeText(
                contexto,
                if (resultado.isSuccess) "Tono cambiado" else "No se pudo usar ese archivo",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun abrirLista(a: Destino) {
        destino = a
        val actual = when (a) {
            is Destino.Sistema -> TonosDelSistema.actual(contexto, a.tipo)
            is Destino.Regla -> a.regla.tono.takeIf { it.isNotEmpty() }?.let(Uri::parse)
            is Destino.Contacto -> null
        }
        listaDelSistema.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, a.tipoDeTono.tipo)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, actual)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, a is Destino.Sistema)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Elige el tono"),
        )
    }

    fun abrirArchivo(a: Destino) {
        destino = a
        archivo.launch(arrayOf("audio/*"))
    }

    val pedirContactos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { respuesta ->
        cambios++
        val actividad = contexto as? Activity
        // Negado "para siempre": Android ya no muestra el diálogo, queda Ajustes.
        if (respuesta.values.any { !it } && actividad != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(actividad, Permisos.CONTACTOS[0])
        ) {
            Permisos.abrirAjustesDeLaApp(contexto)
        }
    }

    val elegirContacto = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contacto: Uri? ->
        if (contacto == null) return@rememberLauncherForActivityResult
        destino = Destino.Contacto(contacto, TonosPorContacto.nombre(contexto, contacto) ?: "Contacto")
        eligiendoFuente = true
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
            item { Explicacion(Modulo.TONOS.descripcion) }
            item { Titulo("Tonos del sistema") }
            item {
                TarjetaPermiso(
                    titulo = "Cambiar tonos del sistema",
                    explicacion = "Para poner tu tono de llamada, de notificación y de alarma.",
                    concedido = tonos,
                    alConceder = { Permisos.abrirCambiarTonos(contexto) },
                )
            }
            items(TipoDeTono.entries) { tipo ->
                Tarjeta {
                    Text(tipo.titulo, style = MaterialTheme.typography.titleMedium)
                    Text(actuales[tipo] ?: "", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(enabled = tonos, onClick = { abrirLista(Destino.Sistema(tipo)) }) { Text("Del teléfono") }
                        OutlinedButton(enabled = tonos, onClick = { abrirArchivo(Destino.Sistema(tipo)) }) { Text("Desde un archivo") }
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

            item {
                Tarjeta {
                    Text("Recortar una canción", style = MaterialTheme.typography.titleMedium)
                    Explicacion("Elige el pedazo que quieres (el coro, por ejemplo), con una entrada suave, y queda como tono.")
                    OutlinedButton(onClick = { elegirCancion.launch(arrayOf("audio/*")) }) { Text("Elegir canción") }
                }
            }

            item { Titulo("Sonidos por app") }
            item {
                TarjetaPermiso(
                    titulo = "Acceso a notificaciones",
                    explicacion = "Para sonar con tu tono cuando llega una notificación de una app, persona o grupo.",
                    concedido = notificaciones,
                    alConceder = { Permisos.abrirLeerNotificaciones(contexto) },
                )
            }
            items(reglas, key = { it.first.id }) { (regla, app, tono) ->
                Tarjeta {
                    Text(app, style = MaterialTheme.typography.titleMedium)
                    Text(regla.descripcion, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tono, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { runCatching { Reproductor.tocar(contexto, Uri.parse(regla.tono)) } }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Probar")
                        }
                    }
                    if (regla.cuando == Cuando.CANAL && vistos.canales(regla.paquete).any { it.id == regla.valor && it.conSonido }) {
                        Explicacion("Esa categoría todavía tiene su sonido original: quítalo para que no suenen los dos.")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { destino = Destino.Regla(regla); eligiendoFuente = true }) { Text("Cambiar tono") }
                        OutlinedButton(onClick = { reglasDeSonido.borrar(regla.id); cambios++ }) { Text("Quitar") }
                    }
                    TextButton(
                        onClick = {
                            if (regla.cuando == Cuando.CANAL) {
                                Permisos.abrirCanalDeApp(contexto, regla.paquete, regla.valor)
                            } else {
                                Permisos.abrirNotificacionesDeApp(contexto, regla.paquete)
                            }
                        },
                    ) { Text("Quitar su sonido original") }
                }
            }
            item {
                Explicacion(
                    "Cada regla toca tu tono cuando llega una notificación de esa app: de toda la app, de una " +
                        "categoría (mensajes, grupos...), de una persona o grupo, o con una palabra. Si coinciden " +
                        "varias, gana la más precisa. Después, en los ajustes de notificación de la app, pon el " +
                        "sonido de esa categoría en \"Ninguno\" para que no suenen los dos. No la pongas en " +
                        "\"Silencio\": así Android la calla del todo y tu tono tampoco suena. Respeta el modo " +
                        "silencio, el modo vibración y \"No molestar\".",
                )
            }
            item {
                FilledTonalButton(onClick = { agregandoApp = true }, enabled = notificaciones) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Agregar regla")
                }
            }

            item { Titulo("Tonos por contacto") }
            item {
                TarjetaPermiso(
                    titulo = "Contactos",
                    explicacion = "Para ponerle su tono de llamada a cada contacto. Solo cambia el tono, nada más del contacto.",
                    concedido = contactos,
                    alConceder = { pedirContactos.launch(Permisos.CONTACTOS) },
                )
            }
            items(conTono, key = { it.first.uri.toString() }) { (contacto, tono) ->
                Tarjeta {
                    Text(contacto.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(tono, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { destino = Destino.Contacto(contacto.uri, contacto.nombre); eligiendoFuente = true }) {
                            Text("Cambiar")
                        }
                        OutlinedButton(onClick = { runCatching { TonosPorContacto.poner(contexto, contacto.uri, null) }; cambios++ }) {
                            Text("Quitar")
                        }
                    }
                }
            }
            item {
                Explicacion(
                    "El tono queda guardado en el contacto: lo toca la app de Teléfono cuando te llama, sin que " +
                        "Camstyling tenga que estar despierta.",
                )
            }
            item {
                FilledTonalButton(onClick = { elegirContacto.launch(null) }, enabled = contactos) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Elegir contacto")
                }
            }
        }
    }

    if (agregandoApp) {
        SelectorDeApp(
            yaTienen = emptySet(),
            alElegir = { paquete -> agregandoApp = false; appNueva = paquete },
            alCerrar = { agregandoApp = false },
        )
    }

    appNueva?.let { paquete ->
        DialogoCuando(
            nombreApp = nombreDeApp(contexto, paquete),
            paquete = paquete,
            alElegir = { cuando, valor, etiqueta ->
                appNueva = null
                destino = Destino.Regla(ReglaDeSonido("", paquete, cuando, valor, etiqueta, ""))
                eligiendoFuente = true
            },
            alCerrar = { appNueva = null },
        )
    }

    val elegido = destino
    if (eligiendoFuente && elegido != null) {
        DialogoFuente(
            titulo = when (elegido) {
                is Destino.Contacto -> "Tono para ${elegido.nombre}"
                else -> "Elige el tono"
            },
            alDelTelefono = { eligiendoFuente = false; abrirLista(elegido) },
            alDeArchivo = { eligiendoFuente = false; abrirArchivo(elegido) },
            alCerrar = { eligiendoFuente = false },
        )
    }

    recortando?.let { cancion ->
        DialogoRecorte(cancion, alListo = { recortando = null; cambios++ }, alCerrar = { recortando = null })
    }
}
