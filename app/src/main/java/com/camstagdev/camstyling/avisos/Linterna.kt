package com.camstagdev.camstyling.avisos

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Parpadea la linterna del teléfono. No necesita el permiso de cámara. */
object Linterna {

    private val hilo = Handler(Looper.getMainLooper())

    /** Tres destellos cortos (menos de un segundo en total). */
    fun parpadear(contexto: Context) {
        val camaras = contexto.getSystemService(CameraManager::class.java)
        val id = runCatching {
            camaras.cameraIdList.firstOrNull { camaras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return
        repeat(VECES) { i ->
            hilo.postDelayed({ runCatching { camaras.setTorchMode(id, true) } }, i * 2 * DESTELLO_MS)
            hilo.postDelayed({ runCatching { camaras.setTorchMode(id, false) } }, (i * 2 + 1) * DESTELLO_MS)
        }
    }

    private const val VECES = 3
    private const val DESTELLO_MS = 140L
}
