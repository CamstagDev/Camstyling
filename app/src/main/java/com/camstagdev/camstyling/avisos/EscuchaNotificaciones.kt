package com.camstagdev.camstyling.avisos

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.camstagdev.camstyling.tonos.Reproductor
import com.camstagdev.camstyling.tonos.SonidosPorApp
import com.camstagdev.camstyling.vibracion.Reglas
import com.camstagdev.camstyling.vibracion.Vibrador

/**
 * El sistema la despierta solo cuando llega una notificación; no corre en
 * segundo plano el resto del tiempo, así que no gasta batería.
 *
 * Toca el sonido de cada app (módulo Tonos) y la hace vibrar con su patrón
 * (módulo Vibración). Las reglas por categoría, persona y palabra llegan en las
 * siguientes versiones.
 */
class EscuchaNotificaciones : NotificationListenerService() {

    /** Última vez que vibró cada app: un mensaje suele llegar como 2 o 3 notificaciones juntas. */
    private val ultimaVez = mutableMapOf<String, Long>()

    /** Notificaciones que ya vibraron y piden avisar una sola vez (FLAG_ONLY_ALERT_ONCE). */
    private val yaAvisadas = mutableSetOf<String>()

    override fun onNotificationPosted(aviso: StatusBarNotification, clasificacion: RankingMap?) {
        if (aviso.packageName == packageName) return
        val sonido = SonidosPorApp(this).sonidoDeApp(aviso.packageName)
        val patron = Reglas(this).patronDeApp(aviso.packageName)
        if (sonido == null && patron == null) return
        val notificacion = aviso.notification

        // Lo que no avisa: lo fijo (música, descargas), los resúmenes de grupo y las actualizaciones silenciosas.
        if (aviso.isOngoing) return
        if (notificacion.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notificacion.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && !yaAvisadas.add(aviso.key)) return

        // Lo que el sistema tampoco dejaría sonar: canal silencioso, "No molestar", modo silencio.
        val ranking = Ranking()
        if (clasificacion?.getRanking(aviso.key, ranking) == true) {
            if (!ranking.matchesInterruptionFilter()) return
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        }
        val modo = getSystemService(AudioManager::class.java).ringerMode
        if (modo == AudioManager.RINGER_MODE_SILENT) return

        val ahora = System.currentTimeMillis()
        if (ahora - (ultimaVez[aviso.packageName] ?: 0L) < SEPARACION_MS) return
        ultimaVez[aviso.packageName] = ahora

        // En modo vibración no suena, pero sí vibra.
        if (sonido != null && modo == AudioManager.RINGER_MODE_NORMAL) Reproductor.tocar(this, sonido)
        if (patron != null) Vibrador.vibrar(this, patron, Vibrador.Uso.NOTIFICACION)
    }

    override fun onNotificationRemoved(aviso: StatusBarNotification) {
        yaAvisadas.remove(aviso.key)
    }

    override fun onListenerDisconnected() {
        // Si el sistema la desconecta (p. ej. al actualizar la app), pedir que la vuelva a conectar.
        requestRebind(ComponentName(this, EscuchaNotificaciones::class.java))
    }

    private companion object {
        const val SEPARACION_MS = 1500L
    }
}
