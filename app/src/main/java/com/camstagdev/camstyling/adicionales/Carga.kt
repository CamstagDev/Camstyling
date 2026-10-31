package com.camstagdev.camstyling.adicionales

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.camstagdev.camstyling.R

/** Los ajustes de Adicionales: cada uno con su interruptor, apagados de entrada. */
class AjustesAdicionales(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("adicionales", Context.MODE_PRIVATE)

    /** La animación al conectar o desconectar el cargador. */
    var carga: Boolean
        get() = prefs.getBoolean("carga", false)
        set(valor) = prefs.edit { putBoolean("carga", valor) }
}

/**
 * La animación de carga. Android no despierta a las apps por el cargador
 * desde el manifiesto, así que el servicio de notificaciones (que ya está
 * conectado) escucha el cargador mientras vive: no corre nada aparte.
 *
 * Desde segundo plano una app no puede abrir pantallas; la forma permitida
 * es una notificación "de pantalla completa", que Android muestra como
 * pantalla solo si el teléfono está bloqueado. Por eso la animación sale
 * con el teléfono bloqueado y nunca encima de lo que estés usando.
 */
object Carga {

    private const val CANAL = "carga"
    private const val ID = 4001

    /** Lo registra el servicio de notificaciones. */
    val receptor = object : BroadcastReceiver() {
        override fun onReceive(contexto: Context, intento: Intent) {
            when (intento.action) {
                Intent.ACTION_POWER_CONNECTED -> alCambiar(contexto, conectado = true)
                Intent.ACTION_POWER_DISCONNECTED -> alCambiar(contexto, conectado = false)
            }
        }
    }

    fun registrar(contexto: Context) {
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(contexto, receptor, filtro, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun desregistrar(contexto: Context) {
        runCatching { contexto.unregisterReceiver(receptor) }
    }

    fun nivel(contexto: Context): Int =
        contexto.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

    private fun alCambiar(contexto: Context, conectado: Boolean) {
        if (!AjustesAdicionales(contexto).carga) return
        val bloqueado = contexto.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !contexto.getSystemService(PowerManager::class.java).isInteractive
        if (bloqueado) mostrar(contexto, conectado)
    }

    /** Si Android la deja salir sola: notificaciones permitidas y, desde Android 14, "pantalla completa". */
    fun puedeMostrarse(contexto: Context): Boolean =
        NotificationManagerCompat.from(contexto).areNotificationsEnabled() && pantallaCompleta(contexto)

    fun pantallaCompleta(contexto: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || contexto.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun mostrar(contexto: Context, conectado: Boolean) {
        val avisos = contexto.getSystemService(NotificationManager::class.java)
        // Sin sonido ni vibración: solo sirve para abrir la animación.
        avisos.createNotificationChannel(
            NotificationChannel(CANAL, "Animación de carga", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        val pantalla = PendingIntent.getActivity(
            contexto, 0,
            Intent(contexto, PantallaCarga::class.java)
                .putExtra(PantallaCarga.CONECTADO, conectado)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_frente)
            .setContentTitle(if (conectado) "Cargando" else "Cargador desconectado")
            .setContentText("${nivel(contexto)} %")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setFullScreenIntent(pantalla, true)
            .setAutoCancel(true)
            .setTimeoutAfter(PantallaCarga.DURACION_MS + 1000)
            .build()
        runCatching { avisos.notify(ID, aviso) }
    }

    fun quitarAviso(contexto: Context) {
        contexto.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
