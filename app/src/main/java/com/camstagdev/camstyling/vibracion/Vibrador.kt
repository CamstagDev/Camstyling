package com.camstagdev.camstyling.vibracion

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Hace vibrar el teléfono con un [Patron]. */
object Vibrador {

    enum class Uso { PRUEBA, NOTIFICACION, LLAMADA }

    /** En llamadas, la pausa antes de repetir un patrón (los que salen de un sonido no la llevan). */
    private const val PAUSA_LLAMADA_MS = 1000L

    private fun motor(contexto: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            contexto.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            contexto.getSystemService(Vibrator::class.java)
        }

    fun tieneMotor(contexto: Context): Boolean = motor(contexto).hasVibrator()

    /**
     * Si el motor puede vibrar con distintas fuerzas. Muchos teléfonos económicos
     * (varios Moto G) solo prenden y apagan: ahí la fuerza no cambia nada, pero
     * los tiempos sí.
     */
    fun controlaFuerza(contexto: Context): Boolean = motor(contexto).hasAmplitudeControl()

    fun vibrar(contexto: Context, patron: Patron, uso: Uso) {
        val tramos = patron.tramos.filter { it.ms > 0 }
        if (tramos.none { it.fuerza > 0 }) return
        // En llamadas se repite hasta que contestan o cuelgan (ver avisos/LlamadaEntrante).
        val repite = uso == Uso.LLAMADA
        val lista = if (repite && !patron.delSonido) tramos + Tramo(PAUSA_LLAMADA_MS, 0) else tramos
        val efecto = VibrationEffect.createWaveform(
            lista.map { it.ms }.toLongArray(),
            lista.map { it.fuerza.coerceIn(0, 255) }.toIntArray(),
            if (repite) 0 else -1,
        )
        val motor = motor(contexto)
        if (uso == Uso.PRUEBA) {
            motor.vibrate(efecto)
        } else if (Build.VERSION.SDK_INT >= 33) {
            // El uso le dice al sistema que es una llamada o una notificación: así la
            // deja vibrar aunque la app esté en segundo plano y respeta el modo silencio.
            val tipo = if (uso == Uso.LLAMADA) VibrationAttributes.USAGE_RINGTONE else VibrationAttributes.USAGE_NOTIFICATION
            motor.vibrate(efecto, VibrationAttributes.createForUsage(tipo))
        } else {
            val atributos = AudioAttributes.Builder()
                .setUsage(if (uso == Uso.LLAMADA) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            motor.vibrate(efecto, atributos)
        }
    }

    /** Para lo que esté vibrando por esta app (solo la de esta app, no la del sistema). */
    fun detener(contexto: Context) = motor(contexto).cancel()
}
