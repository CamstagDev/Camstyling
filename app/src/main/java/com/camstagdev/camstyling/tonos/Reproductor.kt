package com.camstagdev.camstyling.tonos

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** Toca un sonido como notificación: suena con el volumen de notificaciones y respeta "No molestar". */
object Reproductor {

    /** Un sonido largo (una canción entera) se corta a los 10 s. */
    private const val MAXIMO_MS = 10_000L

    // Uno solo a la vez: si llegan dos avisos seguidos, el segundo corta al primero.
    private var sonando: Ringtone? = null
    private val hilo = Handler(Looper.getMainLooper())

    private val NOTIFICACION = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun tocar(contexto: Context, sonido: Uri) {
        detener()
        val nuevo = RingtoneManager.getRingtone(contexto, sonido) ?: return
        nuevo.audioAttributes = NOTIFICACION
        nuevo.play()
        sonando = nuevo
        hilo.postDelayed({ if (sonando === nuevo) detener() }, MAXIMO_MS)
    }

    fun detener() {
        sonando?.stop()
        sonando = null
    }
}
