package com.camstagdev.camstyling.adicionales

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.camstagdev.camstyling.sistema.Permisos
import com.camstagdev.camstyling.ui.Explicacion
import com.camstagdev.camstyling.ui.Modulo
import com.camstagdev.camstyling.ui.Tarjeta
import com.camstagdev.camstyling.ui.TarjetaPermiso
import com.camstagdev.camstyling.ui.Titulo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAdicionales(vecesAlFrente: Int, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val ajustes = remember { AjustesAdicionales(contexto) }
    var cambios by remember { mutableIntStateOf(0) }
    val carga = remember(cambios) { ajustes.carga }
    val escucha = remember(vecesAlFrente) { Permisos.puedeLeerNotificaciones(contexto) }
    val notificaciones = remember(vecesAlFrente, cambios) { NotificationManagerCompat.from(contexto).areNotificationsEnabled() }
    val pantallaCompleta = remember(vecesAlFrente) { Carga.pantallaCompleta(contexto) }

    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        cambios++
        if (!concedido) Permisos.abrirAjustesDeLaApp(contexto)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionales") },
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
            item { Explicacion(Modulo.ADICIONALES.descripcion) }
            item {
                Explicacion(
                    "Detalles que no hacen falta pero quedan lindos. Cada uno se prende aparte y gastan un poco " +
                        "más de batería que el resto de la app (poco), así que vienen apagados.",
                )
            }

            item { Titulo("Animación al cargar") }
            item {
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mostrar al conectar o desconectar", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = carga, onCheckedChange = { ajustes.carga = it; cambios++ })
                    }
                    Explicacion(
                        "Con el teléfono bloqueado, al conectar el cargador sale una batería animada con el " +
                            "porcentaje durante 5 segundos (y \"Desconectado\" al sacarlo). Si estás usando el " +
                            "teléfono no aparece, y al desbloquear sigue tu fondo de siempre. Solo despierta con " +
                            "el cargador.",
                    )
                    OutlinedButton(onClick = {
                        contexto.startActivity(Intent(contexto, PantallaCarga::class.java).putExtra(PantallaCarga.CONECTADO, true))
                    }) { Text("Probar") }
                }
            }
            if (!escucha) {
                item {
                    TarjetaPermiso(
                        titulo = "Acceso a notificaciones",
                        explicacion = "El aviso del cargador llega por el mismo servicio que escucha las notificaciones.",
                        concedido = false,
                        alConceder = { Permisos.abrirLeerNotificaciones(contexto) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                item {
                    TarjetaPermiso(
                        titulo = "Notificaciones",
                        explicacion = "La animación se abre con una notificación sin sonido que se quita sola.",
                        concedido = notificaciones,
                        alConceder = { pedirNotificaciones.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= 34) {
                item {
                    TarjetaPermiso(
                        titulo = "Pantalla completa",
                        explicacion = "Para que la animación salga sobre la pantalla de bloqueo.",
                        concedido = pantallaCompleta,
                        alConceder = {
                            contexto.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${contexto.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }

            item { Titulo("Colores, íconos y letra") }
            item {
                Tarjeta {
                    Explicacion(
                        "Android no deja que una app cambie la letra, la forma de los íconos ni los colores del " +
                            "sistema sin root. Lo que sí se puede está en los ajustes del teléfono: los colores salen " +
                            "del fondo (Material You, cambia solo con el módulo Fondos) y Motorola permite elegir " +
                            "letra, forma de íconos y colores en \"Fondo de pantalla y estilo\" o en la app " +
                            "Personalizar. El tamaño de letra está en Pantalla.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runCatching {
                                contexto.startActivity(
                                    Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo y estilo")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text("Fondo y estilo") }
                        OutlinedButton(onClick = {
                            contexto.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Pantalla") }
                    }
                }
            }
        }
    }
}
