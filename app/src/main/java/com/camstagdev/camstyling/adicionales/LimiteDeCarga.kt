package com.camstagdev.camstyling.adicionales

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.camstagdev.camstyling.R

/**
 * Avisa cuando la batería llega al límite elegido (80 % de entrada) mientras
 * carga, para desconectar y cuidar la batería. Solo escucha el nivel entre
 * que se conecta el cargador y que avisa o se desconecta; el resto del
 * tiempo no hace nada.
 */
object LimiteDeCarga {

    private const val CANAL = "limite"
    private const val ID = 4002
    private var escuchando = false

    private val nivel = object : BroadcastReceiver() {
        override fun onReceive(contexto: Context, intento: Intent) {
            val escala = intento.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val porcentaje = intento.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / escala
            if (porcentaje >= AjustesAdicionales(contexto).porcentajeLimite) {
                avisar(contexto, porcentaje)
                dejar(contexto)
            }
        }
    }

    /** Al conectar el cargador (o al arrancar el servicio si ya estaba cargando). */
    fun empezar(contexto: Context) {
        val app = contexto.applicationContext
        if (escuchando || !AjustesAdicionales(app).limite) return
        if (Carga.nivel(app) >= AjustesAdicionales(app).porcentajeLimite) return
        escuchando = true
        ContextCompat.registerReceiver(app, nivel, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Al desconectar o después de avisar. */
    fun dejar(contexto: Context) {
        if (!escuchando) return
        escuchando = false
        runCatching { contexto.applicationContext.unregisterReceiver(nivel) }
    }

    fun estaCargando(contexto: Context): Boolean = contexto.getSystemService(BatteryManager::class.java).isCharging

    private fun avisar(contexto: Context, porcentaje: Int) {
        val avisos = contexto.getSystemService(NotificationManager::class.java)
        // El canal suena y vibra como cualquier notificación (respeta silencio y "No molestar").
        avisos.createNotificationChannel(NotificationChannel(CANAL, "Límite de carga", NotificationManager.IMPORTANCE_DEFAULT))
        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_frente)
            .setContentTitle("Batería al $porcentaje %")
            .setContentText("Ya puedes desconectar el cargador.")
            .setAutoCancel(true)
            .build()
        runCatching { avisos.notify(ID, aviso) }
    }
}
