package com.camstagdev.camstyling.perfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Modulo
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantallaPerfiles(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val perfiles = remember { Perfiles(contexto) }
    // Sube con cada cambio, para volver a leer los perfiles.
    var cambios by remember { mutableIntStateOf(0) }
    val lista = remember(cambios) { perfiles.todos() }
    val manda = remember(cambios, vecesAlFrente) { perfiles.queManda() }
    val noMolestar = remember(vecesAlFrente) { Permisos.puedeCambiarNoMolestar(contexto) }
    val notificaciones = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }
    var editando by remember { mutableStateOf<Perfil?>(null) }
    val forzado = remember(cambios, vecesAlFrente) { perfiles.forzado }
    val disparadores = remember { Disparadores(contexto) }
    val listaDisparadores = remember(cambios) { disparadores.todos() }
    var agregandoDisparador by remember { mutableStateOf(false) }

    fun cambiado() {
        perfiles.aplicar(forzar = true)
        cambios++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfiles") },
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
            item { Explicacion(Modulo.PERFILES.descripcion) }
            item {
                Tarjeta {
                    Text("Ahora", style = MaterialTheme.typography.titleMedium)
                    Text(
                        manda?.nombre ?: "Normal (ningún perfil)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (forzado != null) {
                        Explicacion("Elegido a mano (mosaico, widget o un aparato conectado): manda hasta que vuelvas al horario.")
                        OutlinedButton(onClick = { perfiles.forzar(null); cambios++ }) { Text("Volver al horario") }
                    }
                    if (lista.isNotEmpty()) {
                        Text("Elegir a mano", style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lista.forEach { perfil ->
                                FilterChip(
                                    selected = forzado == perfil.id,
                                    onClick = { perfiles.forzar(if (forzado == perfil.id) null else perfil.id); cambios++ },
                                    label = { Text(perfil.nombre) },
                                )
                            }
                        }
                        Explicacion(
                            "También desde el mosaico \"Perfil\" de los ajustes rápidos (edítalos y arrástralo) o " +
                                "el widget \"Perfil\" de la pantalla de inicio: cada toque pasa al siguiente.",
                        )
                    }
                }
            }
            item {
                TarjetaPermiso(
                    titulo = "Acceso a No molestar",
                    explicacion = "Para poner el teléfono en silencio y para las horas de silencio. Sin él, los perfiles solo cambian volúmenes, tonos y vibración.",
                    concedido = noMolestar,
                    alConceder = { Permisos.abrirNoMolestar(contexto) },
                )
            }

            item { Titulo("Mis perfiles") }
            items(lista, key = { it.id }) { perfil ->
                Tarjeta {
                    Text(perfil.nombre, style = MaterialTheme.typography.titleMedium)
                    Text("${perfil.textoDias}, ${perfil.textoHoras}", style = MaterialTheme.typography.bodyMedium)
                    Explicacion(resumen(perfil))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { editando = perfil }) { Text("Cambiar") }
                        OutlinedButton(onClick = { perfiles.borrar(perfil.id); cambiado() }) { Text("Borrar") }
                    }
                }
            }
            item {
                FilledTonalButton(onClick = { editando = Perfil.nuevo() }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Nuevo perfil")
                }
            }
            item {
                Explicacion(
                    "Cada perfil manda en sus días y horas; si dos se pisan, gana el que está más arriba. Cuando " +
                        "no manda ninguno, el teléfono vuelve a como estaba antes del primero. El cambio lo hace " +
                        "una alarma por cada horario (sin nada corriendo en medio), así que puede tardar unos minutos.",
                )
            }
            item { Titulo("Al conectar") }
            item {
                Tarjeta {
                    Explicacion(
                        "Al conectar unos audífonos o el auto, pone un perfil (hasta que lo desconectes) y el " +
                            "volumen de la música. No busca nada: Android avisa al conectar.",
                    )
                    if (listaDisparadores.isEmpty()) Explicacion("Todavía no hay ninguno.")
                    listaDisparadores.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.nombre, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    listOfNotNull(
                                        d.perfil?.let { id -> lista.firstOrNull { it.id == id }?.nombre?.let { "perfil $it" } },
                                        d.volumenMusica?.let { "música al $it %" },
                                    ).joinToString(", ").ifEmpty { "No cambia nada" },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            OutlinedButton(onClick = { disparadores.borrar(d.aparato); cambios++ }) { Text("Quitar") }
                        }
                    }
                    FilledTonalButton(onClick = { agregandoDisparador = true }) { Text("Agregar") }
                    if (!notificaciones) Explicacion("Necesita el \"Acceso a notificaciones\" (módulo Tonos) para enterarse de las conexiones.")
                }
            }
            if (!notificaciones) {
                item {
                    Explicacion(
                        "Las excepciones de las horas de silencio necesitan el \"Acceso a notificaciones\" (módulo Tonos).",
                    )
                }
            }
        }
    }

    if (agregandoDisparador) {
        EditorDisparador(
            perfiles = lista,
            alGuardar = { disparadores.guardar(it); agregandoDisparador = false; cambios++ },
            alCerrar = { agregandoDisparador = false },
        )
    }

    editando?.let { perfil ->
        EditorPerfil(
            inicial = perfil,
            alGuardar = { perfiles.guardar(it); editando = null; cambiado() },
            alCerrar = { editando = null },
        )
    }
}

private fun resumen(perfil: Perfil): String = listOfNotNull(
    perfil.modo?.texto,
    perfil.volumenLlamada?.let { "llamada al $it %" },
    perfil.volumenNotificaciones?.let { "notificaciones al $it %" },
    perfil.tonoLlamada?.let { "otro tono de llamada" },
    perfil.tonoNotificacion?.let { "otro tono de notificación" },
    perfil.vibracionLlamada?.let { "otra vibración de llamada" },
    if (perfil.silencio) "horas de silencio" + if (perfil.excepciones.isEmpty()) "" else " (${perfil.excepciones.size} excepciones)" else null,
).joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "No cambia nada" }
