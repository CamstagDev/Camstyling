package com.camstagdev.camstyling.perfiles

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo

@OptIn(ExperimentalMaterial3Api::class)
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
            item {
                Tarjeta {
                    Text("Ahora", style = MaterialTheme.typography.titleMedium)
                    Text(
                        manda?.nombre ?: "Normal (ningún perfil)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
            if (!notificaciones) {
                item {
                    Explicacion(
                        "Las excepciones de las horas de silencio necesitan el \"Acceso a notificaciones\" (módulo Tonos).",
                    )
                }
            }
        }
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
