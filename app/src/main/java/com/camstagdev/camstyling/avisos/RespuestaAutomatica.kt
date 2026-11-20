package com.camstagdev.camstyling.avisos

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.camstagdev.camstyling.perfiles.Perfiles
import com.camstagdev.camstyling.tonos.DatosDeAviso

/**
 * Responde sola durante un perfil ("Manejando", "Dormido"): usa el botón
 * "Responder" de la propia notificación, como si escribieras ahí. Solo chats
 * uno a uno elegidos en el perfil, y una vez por chat mientras dura el perfil.
 */
object RespuestaAutomatica {

    fun intentar(contexto: Context, aviso: StatusBarNotification, datos: DatosDeAviso) {
        val perfiles = Perfiles(contexto)
        val perfil = perfiles.aplicado ?: return
        val texto = perfil.respuesta?.takeIf { it.isNotBlank() } ?: return
        if (perfil.responderA.none { datos.coincideCon(it) }) return

        val notificacion = aviso.notification
        val estilo = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notificacion)
        // Nunca a grupos.
        if (estilo?.isGroupConversation == true) return
        if (notificacion.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return
        // Si el último mensaje es tuyo (por ejemplo, nuestra propia respuesta), no hay nada que responder.
        if (estilo != null && estilo.messages.lastOrNull()?.person == null && estilo.messages.isNotEmpty()) return

        val accion = notificacion.actions.orEmpty()
            .filter { a -> a.remoteInputs.orEmpty().any { it.allowFreeFormInput } }
            .let { acciones -> acciones.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY } ?: acciones.firstOrNull() }
            ?: return

        val chat = estilo?.conversationTitle?.toString()
            ?: notificacion.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return
        if (!perfiles.marcarRespondido("${aviso.packageName}|$chat")) return

        val entradas = accion.remoteInputs
        val resultados = Bundle()
        entradas.filter { it.allowFreeFormInput }.forEach { resultados.putCharSequence(it.resultKey, texto) }
        val intento = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(entradas, intento, resultados)
        runCatching { accion.actionIntent.send(contexto, 0, intento) }
    }
}
