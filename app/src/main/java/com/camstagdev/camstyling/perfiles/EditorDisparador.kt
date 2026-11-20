@file:OptIn(ExperimentalLayoutApi::class)

package com.camstagdev.camstyling.perfiles

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.camstagdev.camstyling.ui.Explicacion

/** Elegir un aparato y qué pasa al conectarlo. */
@Composable
fun EditorDisparador(perfiles: List<Perfil>, alGuardar: (Disparador) -> Unit, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    var permiso by remember { mutableIntStateOf(0) }
    val puedeBluetooth = remember(permiso) { Conexiones.puedeVerBluetooth(contexto) }
    val aparatos = remember(permiso) { listOf(Disparador.CABLE to "Audífonos con cable") + Conexiones.vinculados(contexto) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso++ }

    var aparato by remember { mutableStateOf<Pair<String, String>?>(null) }
    var perfil by remember { mutableStateOf<String?>(perfiles.firstOrNull()?.id) }
    var volumen by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Al conectar") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aparato", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    aparatos.forEach { a ->
                        FilterChip(selected = aparato?.first == a.first, onClick = { aparato = a }, label = { Text(a.second) })
                    }
                }
                if (!puedeBluetooth) {
                    Explicacion("Para ver tus aparatos Bluetooth (audífonos, el auto) hace falta el permiso de dispositivos cercanos.")
                    OutlinedButton(onClick = { pedir.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Dar permiso") }
                } else if (aparatos.size == 1) {
                    Explicacion("No hay aparatos Bluetooth vinculados. Vincúlalos primero en los ajustes del teléfono.")
                }

                Text("Perfil", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = perfil == null, onClick = { perfil = null }, label = { Text("Sin cambiar") })
                    perfiles.forEach { p ->
                        FilterChip(selected = perfil == p.id, onClick = { perfil = p.id }, label = { Text(p.nombre) })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = volumen != null, onCheckedChange = { volumen = if (it) 50 else null })
                    Text(volumen?.let { "Volumen de la música: $it %" } ?: "Volumen de la música: sin cambiar")
                }
                volumen?.let { v ->
                    Slider(value = v.toFloat(), onValueChange = { volumen = it.toInt() }, valueRange = 0f..100f, steps = 9)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { aparato?.let { (id, nombre) -> alGuardar(Disparador(id, nombre, perfil, volumen)) } },
                enabled = aparato != null && (perfil != null || volumen != null),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
