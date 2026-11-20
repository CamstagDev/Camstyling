package com.camstagdev.camstyling.avisos

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import com.camstagdev.camstyling.llamadas.Timbre
import com.camstagdev.camstyling.vibracion.Reglas
import com.camstagdev.camstyling.vibracion.Vibrador

/**
 * Vibra con el patrón de llamadas mientras el teléfono suena, y para al
 * contestar o colgar. El sistema la despierta solo cuando cambia el estado del
 * teléfono (necesita el permiso "Teléfono"); no corre el resto del tiempo.
 *
 * El tono sigue siendo el del sistema: Camstyling solo pone la vibración. Para
 * que no se mezclen, la vibración propia del sistema en llamadas se apaga en
 * Ajustes > Sonido (la pantalla Vibración abre esa pantalla).
 */
class LlamadaEntrante : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intento: Intent) {
        // PHONE_STATE solo lo puede mandar el sistema: ninguna otra app puede hacerla vibrar.
        if (intento.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val patron = Reglas(contexto).patronDeLlamada()

        if (intento.getStringExtra(TelephonyManager.EXTRA_STATE) == TelephonyManager.EXTRA_STATE_RINGING) {
            val audio = contexto.getSystemService(AudioManager::class.java)
            val noMolestar = contexto.getSystemService(NotificationManager::class.java).currentInterruptionFilter
            // En silencio total o con "No molestar" solo para alarmas, el teléfono no avisa: nosotros tampoco.
            if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
            if (noMolestar == NotificationManager.INTERRUPTION_FILTER_NONE ||
                noMolestar == NotificationManager.INTERRUPTION_FILTER_ALARMS
            ) return
            if (patron != null) Vibrador.vibrar(contexto, patron, Vibrador.Uso.LLAMADA)
            // Módulo Llamadas: tono que sube y voz que dice quién llama. El número solo llega con "Registro de llamadas".
            @Suppress("DEPRECATION")
            Timbre.alSonar(contexto, intento.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER))
        } else {
            // Contestaron (OFFHOOK) o colgaron (IDLE).
            if (patron != null) Vibrador.detener(contexto)
            Timbre.alTerminar(contexto)
        }
    }
}
