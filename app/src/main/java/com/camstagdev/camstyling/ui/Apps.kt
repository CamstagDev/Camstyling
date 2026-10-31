package com.camstagdev.camstyling.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Elegir otra app del teléfono (Vibración y Tonos le ponen reglas a cada app).

/** Lista de las apps instaladas (las que tienen ícono), con buscador. */
@Composable
fun SelectorDeApp(yaTienen: Set<String>, alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    var lista by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var busqueda by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { lista = withContext(Dispatchers.Default) { appsInstaladas(contexto) } }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Elige la app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(busqueda, { busqueda = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val visibles = lista?.filter { (paquete, nombre) ->
                    paquete !in yaTienen && (busqueda.isBlank() || nombre.contains(busqueda, ignoreCase = true))
                }
                if (visibles == null) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(visibles, key = { it.first }) { (paquete, nombre) ->
                            Text(
                                nombre,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable { alElegir(paquete) }.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/** Paquete y nombre de cada app con ícono en el cajón de apps, por nombre. */
private fun appsInstaladas(contexto: Context): List<Pair<String, String>> {
    val pm = contexto.packageManager
    val conIcono = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(conIcono, 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != contexto.packageName }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}

fun nombreDeApp(contexto: Context, paquete: String): String = try {
    val pm = contexto.packageManager
    @Suppress("DEPRECATION")
    pm.getApplicationInfo(paquete, 0).loadLabel(pm).toString()
} catch (_: PackageManager.NameNotFoundException) {
    paquete
}
