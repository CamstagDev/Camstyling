package com.camstagdev.camstyling.tonos

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.edit

/**
 * Qué sonido usa cada app al llegar una notificación. Se guarda en el teléfono
 * (SharedPreferences); la pantalla Tonos y el servicio de notificaciones leen lo mismo.
 */
class SonidosPorApp(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("sonidos", Context.MODE_PRIVATE)

    /** Paquete de cada app con regla -> su sonido. */
    fun apps(): Map<String, Uri> = prefs.all
        .filterKeys { it.startsWith(APP) }
        .mapNotNull { (clave, valor) -> (valor as? String)?.let { clave.removePrefix(APP) to Uri.parse(it) } }
        .toMap()

    fun sonidoDeApp(paquete: String): Uri? = prefs.getString(APP + paquete, null)?.let(Uri::parse)

    /** null = quitar la regla (vuelve a sonar el sonido de la app). */
    fun ponerApp(paquete: String, sonido: Uri?) = prefs.edit {
        if (sonido == null) remove(APP + paquete) else putString(APP + paquete, sonido.toString())
    }

    private companion object {
        const val APP = "app:"
    }
}

/** Toca un sonido como notificación: suena con el volumen de notificaciones y respeta "No molestar". */
object Reproductor {

    // Uno solo a la vez: si llegan dos avisos seguidos, el segundo corta al primero.
    private var sonando: Ringtone? = null

    private val NOTIFICACION = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun tocar(contexto: Context, sonido: Uri) {
        detener()
        sonando = RingtoneManager.getRingtone(contexto, sonido)?.apply {
            audioAttributes = NOTIFICACION
            play()
        }
    }

    fun detener() {
        sonando?.stop()
        sonando = null
    }
}
