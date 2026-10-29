package com.camstagdev.camstyling.sistema

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.camstagdev.camstyling.avisos.EscuchaNotificaciones

/**
 * Los permisos especiales de Camstyling. Ninguno se puede pedir con un diálogo:
 * el usuario los activa en una pantalla de Ajustes que la app le abre.
 */
object Permisos {

    /** "Modificar ajustes del sistema": cambiar el tono de llamada, notificación y alarma. */
    fun puedeCambiarTonos(contexto: Context): Boolean = Settings.System.canWrite(contexto)

    fun abrirCambiarTonos(contexto: Context) {
        contexto.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${contexto.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** "Acceso a notificaciones": ver lo que llega para sonar con el tono de cada regla. */
    fun puedeLeerNotificaciones(contexto: Context): Boolean =
        contexto.packageName in NotificationManagerCompat.getEnabledListenerPackages(contexto)

    fun abrirLeerNotificaciones(contexto: Context) {
        // En Android 11+ se puede abrir directo el interruptor de esta app; en 10, la lista.
        val intento = if (android.os.Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(contexto, EscuchaNotificaciones::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        contexto.startActivity(intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
