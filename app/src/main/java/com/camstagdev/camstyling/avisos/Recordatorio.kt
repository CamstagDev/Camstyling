package com.camstagdev.camstyling.avisos

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import com.camstagdev.camstyling.tonos.DatosDeAviso
import com.camstagdev.camstyling.tonos.Reproductor
import com.camstagdev.camstyling.tonos.ReglasDeSonido
import com.camstagdev.camstyling.vibracion.Reglas
import com.camstagdev.camstyling.vibracion.Vibrador

/**
 * Vuelve a avisar de una notificación elegida que sigue sin abrir (sigue en
 * la barra) cada tantos minutos, hasta un máximo de veces. Cada repetición
 * es una alarma inexacta: con la pantalla apagada Android puede atrasarla.
 */
class Recordatorio : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intento: Intent) {
        if (intento.action != ACCION) return
        val clave = intento.getStringExtra(CLAVE) ?: return
        val vez = intento.getIntExtra(VEZ, 1)
        val ajustes = AjustesAvisos(contexto)
        if (!ajustes.recordatorio) return

        // Si ya no está en la barra (la abriste o la descartaste), no hay nada que recordar.
        val aviso = EscuchaNotificaciones.activa?.activeNotifications?.firstOrNull { it.key == clave } ?: return

        val audio = contexto.getSystemService(AudioManager::class.java)
        val filtro = contexto.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        // Con "No molestar" o en silencio no suena; se vuelve a intentar en la próxima vuelta.
        if (filtro == NotificationManager.INTERRUPTION_FILTER_ALL && audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val tono = ReglasDeSonido(contexto).buscar(DatosDeAviso.de(aviso))?.let { Uri.parse(it.tono) }
                    ?: RingtoneManager.getActualDefaultRingtoneUri(contexto, RingtoneManager.TYPE_NOTIFICATION)
                if (tono != null) runCatching { Reproductor.tocar(contexto, tono) }
            }
            Reglas(contexto).patronDeApp(aviso.packageName)?.let { Vibrador.vibrar(contexto, it, Vibrador.Uso.NOTIFICACION) }
        }
        if (vez < ajustes.repeticiones) programar(contexto, clave, vez + 1)
    }

    companion object {
        private const val ACCION = "com.camstagdev.camstyling.RECORDATORIO"
        private const val CLAVE = "clave"
        private const val VEZ = "vez"

        private fun pendiente(contexto: Context, clave: String, vez: Int): PendingIntent = PendingIntent.getBroadcast(
            contexto,
            clave.hashCode(),
            Intent(contexto, Recordatorio::class.java).setAction(ACCION).putExtra(CLAVE, clave).putExtra(VEZ, vez),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun programar(contexto: Context, clave: String, vez: Int = 1) {
            val minutos = AjustesAvisos(contexto).minutosDeRecordatorio
            contexto.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + minutos * 60_000L,
                pendiente(contexto, clave, vez),
            )
        }

        fun cancelar(contexto: Context, clave: String) {
            contexto.getSystemService(AlarmManager::class.java).cancel(pendiente(contexto, clave, 1))
        }
    }
}
