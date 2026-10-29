package com.camstagdev.camstyling.avisos

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * El sistema la despierta solo cuando llega una notificación; no corre en
 * segundo plano el resto del tiempo, así que no gasta batería.
 *
 * Por ahora solo se conecta. Las reglas (por app, categoría, persona y
 * palabra) y el sonido llegan en las siguientes versiones.
 */
class EscuchaNotificaciones : NotificationListenerService() {

    override fun onNotificationPosted(notificacion: StatusBarNotification) {
        // Aquí se buscará la regla que aplica y se tocará su sonido.
    }

    override fun onListenerDisconnected() {
        // Si el sistema la desconecta (p. ej. al actualizar la app), pedir que la vuelva a conectar.
        requestRebind(ComponentName(this, EscuchaNotificaciones::class.java))
    }
}
