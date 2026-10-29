package com.camstagdev.camstyling.sistema

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.camstagdev.camstyling.avisos.EscuchaNotificaciones

/**
 * Los permisos de Camstyling. Los especiales no se pueden pedir con un diálogo:
 * el usuario los activa en una pantalla de Ajustes que la app le abre. El de
 * "Teléfono" sí es un diálogo (lo pide la pantalla Vibración).
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

    /** "Teléfono": saber cuándo suena una llamada para vibrar con su patrón. */
    const val VER_LLAMADAS = android.Manifest.permission.READ_PHONE_STATE

    fun puedeVerLlamadas(contexto: Context): Boolean =
        ContextCompat.checkSelfPermission(contexto, VER_LLAMADAS) == PackageManager.PERMISSION_GRANTED

    /** Si el usuario lo negó dos veces, Android ya no muestra el diálogo: queda la pantalla de la app en Ajustes. */
    fun abrirAjustesDeLaApp(contexto: Context) {
        contexto.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Ajustes > Sonido: ahí se apaga la vibración del sistema en llamadas. */
    fun abrirSonido(contexto: Context) {
        contexto.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Las notificaciones de otra app, para apagarles la vibración original. */
    fun abrirNotificacionesDeApp(contexto: Context, paquete: String) {
        contexto.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, paquete)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
