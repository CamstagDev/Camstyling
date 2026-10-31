@file:OptIn(ExperimentalMaterial3Api::class)

package com.camstagdev.camstyling.vibracion

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.tonos.TonosDelSistema
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.SelectorDeApp
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo
import com.camstagdev.camstyling.ui.nombreDeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Para qué se está eligiendo un patrón: las llamadas o una app (por su paquete). */
private sealed interface Destino {
    data object Llamada : Destino
    data class App(val paquete: String) : Destino
}

@Composable
fun PantallaVibracion(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val reglas = remember { Reglas(contexto) }
    val tieneMotor = remember { Vibrador.tieneMotor(contexto) }
    val controlaFuerza = remember { Vibrador.controlaFuerza(contexto) }

    // Sube con cada cambio de las reglas, para volver a leerlas.
    var cambios by remember { mutableIntStateOf(0) }
    val patrones = remember(cambios) { reglas.todos() }
    val propios = remember(cambios) { reglas.propios() }
    val apps = remember(cambios) { reglas.apps().toList().sortedBy { nombreDeApp(contexto, it.first) } }
    val idLlamada = remember(cambios) { reglas.idDeLlamada }
    val telefono = remember(vecesAlFrente, cambios) { Permisos.puedeVerLlamadas(contexto) }
    val notificaciones = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }

    var eligiendo by remember { mutableStateOf<Destino?>(null) }
    var editando by remember { mutableStateOf<Patron?>(null) }
    var creando by remember { mutableStateOf(false) }
    var agregandoApp by remember { mutableStateOf(false) }
    var calculando by remember { mutableStateOf(false) }
    // Al crear desde un sonido desde la elección de llamadas, el nuevo patrón queda puesto ahí.
    var sonidoParaLlamada by remember { mutableStateOf(false) }

    val pedirTelefono = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        cambios++
        val actividad = contexto as? Activity
        // Negado "para siempre": Android ya no muestra el diálogo, queda Ajustes.
        if (!concedido && actividad != null && !ActivityCompat.shouldShowRequestPermissionRationale(actividad, Permisos.VER_LLAMADAS)) {
            Permisos.abrirAjustesDeLaApp(contexto)
        }
    }

    val elegirSonido = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { sonido: Uri? ->
        if (sonido == null) return@rememberLauncherForActivityResult
        val paraLlamada = sonidoParaLlamada
        calculando = true
        alcance.launch {
            val resultado = withContext(Dispatchers.Default) {
                runCatching { RitmoDelSonido.calcular(contexto, sonido, controlaFuerza) }
            }
            calculando = false
            val tramos = resultado.getOrNull()
            if (tramos.isNullOrEmpty() || tramos.none { it.fuerza > 0 }) {
                Toast.makeText(contexto, "No se pudo sacar un ritmo de ese sonido", Toast.LENGTH_LONG).show()
                return@launch
            }
            val nombre = TonosDelSistema.nombreDeArchivo(contexto, sonido)?.substringBeforeLast('.') ?: "sonido"
            val guardado = reglas.guardarPropio(Patron("", "Ritmo de $nombre", tramos, delSonido = true))
            if (paraLlamada) reglas.idDeLlamada = guardado.id
            cambios++
            Toast.makeText(contexto, "Listo: \"${guardado.nombre}\"", Toast.LENGTH_SHORT).show()
        }
    }

    fun desdeUnSonido(paraLlamada: Boolean) {
        sonidoParaLlamada = paraLlamada
        elegirSonido.launch(arrayOf("audio/*"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vibración") },
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
                    Text("Tu teléfono", style = MaterialTheme.typography.titleMedium)
                    Explicacion(
                        when {
                            !tieneMotor -> "Este teléfono no tiene motor de vibración."
                            controlaFuerza -> "Puede vibrar con distintas fuerzas: la fuerza de cada patrón sí se nota."
                            else -> "Su motor solo prende y apaga: los patrones cambian por sus tiempos, no por su fuerza."
                        },
                    )
                }
            }

            item { Titulo("Llamadas") }
            item {
                TarjetaPermiso(
                    titulo = "Teléfono",
                    explicacion = "Para saber cuándo suena una llamada y vibrar con tu patrón. No lee números ni contactos.",
                    concedido = telefono,
                    alConceder = { pedirTelefono.launch(Permisos.VER_LLAMADAS) },
                )
            }
            item {
                Tarjeta {
                    Text("Al sonar una llamada", style = MaterialTheme.typography.titleMedium)
                    Text(
                        reglas.buscar(idLlamada)?.nombre ?: "La del sistema",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Explicacion(
                        "Para que no se mezcle con la del sistema, apaga \"Vibrar en llamadas\" en Ajustes de sonido. " +
                            "El tono de llamada se cambia en el módulo Tonos.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { eligiendo = Destino.Llamada }, enabled = tieneMotor) { Text("Cambiar") }
                        OutlinedButton(onClick = { Permisos.abrirSonido(contexto) }) { Text("Ajustes de sonido") }
                    }
                }
            }

            item { Titulo("Notificaciones por app") }
            if (!notificaciones) {
                item {
                    TarjetaPermiso(
                        titulo = "Acceso a notificaciones",
                        explicacion = "Para vibrar con el patrón de cada app cuando te llega algo.",
                        concedido = false,
                        alConceder = { Permisos.abrirLeerNotificaciones(contexto) },
                    )
                }
            }
            items(apps, key = { it.first }) { (paquete, id) ->
                Tarjeta {
                    Text(nombreDeApp(contexto, paquete), style = MaterialTheme.typography.titleMedium)
                    Text(
                        reglas.buscar(id)?.nombre ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { eligiendo = Destino.App(paquete) }) { Text("Cambiar") }
                        OutlinedButton(onClick = { reglas.ponerApp(paquete, null); cambios++ }) { Text("Quitar") }
                    }
                    TextButton(onClick = { Permisos.abrirNotificacionesDeApp(contexto, paquete) }) {
                        Text("Apagar su vibración original")
                    }
                }
            }
            item {
                Explicacion(
                    "Elige la app y su patrón. Después apaga la vibración de esa app en sus ajustes de " +
                        "notificación (botón de cada tarjeta), para que solo vibre la tuya.",
                )
            }
            item {
                FilledTonalButton(onClick = { agregandoApp = true }, enabled = tieneMotor) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Agregar app")
                }
            }

            item { Titulo("Mis patrones") }
            items(propios, key = { it.id }) { p ->
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { Vibrador.vibrar(contexto, p, Vibrador.Uso.PRUEBA) }) {
                            Icon(Icons.Outlined.Vibration, contentDescription = "Probar")
                        }
                        if (p.receta != null) {
                            IconButton(onClick = { editando = p }) { Icon(Icons.Outlined.Edit, contentDescription = "Cambiar") }
                        }
                        IconButton(onClick = { reglas.borrarPropio(p.id); cambios++ }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Borrar")
                        }
                    }
                    Grafica(p.tramos)
                    if (p.delSonido) Explicacion("Sigue el ritmo de un sonido (${p.duracionMs / 1000.0} s).")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { creando = true }, enabled = tieneMotor) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("  Crear")
                    }
                    OutlinedButton(onClick = { desdeUnSonido(paraLlamada = false) }, enabled = tieneMotor && !calculando) {
                        Icon(Icons.Outlined.GraphicEq, contentDescription = null)
                        Text("  Desde un sonido")
                    }
                    if (calculando) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
            item {
                Explicacion(
                    "\"Desde un sonido\" escucha un archivo de audio (tu tono de llamada, por ejemplo) y arma una " +
                        "vibración que sigue su ritmo: más fuerte donde suena más fuerte. En llamadas se repite " +
                        "al compás del tono.",
                )
            }

            item { Titulo("Predeterminados") }
            items(PREDETERMINADOS.filter { it.vibra }, key = { it.id }) { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.nombre, style = MaterialTheme.typography.bodyLarge)
                        Grafica(p.tramos)
                    }
                    IconButton(onClick = { Vibrador.vibrar(contexto, p, Vibrador.Uso.PRUEBA) }) {
                        Icon(Icons.Outlined.Vibration, contentDescription = "Probar")
                    }
                }
            }
        }
    }

    when (val destino = eligiendo) {
        null -> {}
        Destino.Llamada -> SelectorPatron(
            titulo = "Vibración de llamadas",
            patrones = patrones,
            elegido = idLlamada,
            opcionNula = "La del sistema",
            alElegir = { reglas.idDeLlamada = it; cambios++; eligiendo = null },
            alDesdeSonido = { eligiendo = null; desdeUnSonido(paraLlamada = true) },
            alCerrar = { eligiendo = null },
        )
        is Destino.App -> SelectorPatron(
            titulo = nombreDeApp(contexto, destino.paquete),
            patrones = patrones,
            elegido = reglas.apps()[destino.paquete],
            opcionNula = "La de la app (sin regla)",
            alElegir = { reglas.ponerApp(destino.paquete, it); cambios++; eligiendo = null },
            alCerrar = { eligiendo = null },
        )
    }

    if (creando || editando != null) {
        EditorPatron(
            inicial = editando,
            controlaFuerza = controlaFuerza,
            alGuardar = { reglas.guardarPropio(it); cambios++; creando = false; editando = null },
            alCerrar = { creando = false; editando = null },
        )
    }

    if (agregandoApp) {
        SelectorDeApp(
            yaTienen = apps.map { it.first }.toSet(),
            alElegir = { paquete -> agregandoApp = false; eligiendo = Destino.App(paquete) },
            alCerrar = { agregandoApp = false },
        )
    }
}
