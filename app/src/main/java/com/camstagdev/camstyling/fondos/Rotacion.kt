package com.camstagdev.camstyling.fondos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * El cambio automático de fondo. Para no gastar batería no hay alarmas
 * exactas ni servicio prendido: un trabajo periódico de WorkManager despierta
 * cada tanto (Android lo junta con otros y lo atrasa si el teléfono duerme),
 * mira si ya toca y, solo entonces, lee una imagen y la pone.
 */
object Rotacion {

    private const val NOMBRE = "fondos"
    private const val HORA = 60 * 60 * 1000L

    fun franjaActual(ajustes: AjustesFondos, ahora: LocalTime = LocalTime.now()): Franja {
        if (!ajustes.usaNoche) return Franja.DIA
        val minuto = ahora.hour * 60 + ahora.minute
        val desde = ajustes.desde
        val hasta = ajustes.hasta
        // La noche suele cruzar la medianoche (20:00 a 07:00).
        val deNoche = if (desde <= hasta) minuto in desde until hasta else minuto >= desde || minuto < hasta
        return if (deNoche) Franja.NOCHE else Franja.DIA
    }

    /**
     * Pone la imagen que sigue si ya pasó el intervalo o si cambió de día a
     * noche (o al revés); con [forzar], la pone siempre. Devuelve el nombre de
     * la imagen puesta, o null si no tocaba o no había imágenes.
     */
    fun tocar(contexto: Context, forzar: Boolean): String? {
        val ajustes = AjustesFondos(contexto)
        val franja = franjaActual(ajustes)
        val carpeta = ajustes.carpetaDe(franja) ?: return null
        val ahora = System.currentTimeMillis()
        // Un 10 % de margen: el trabajo periódico nunca cae justo a la hora.
        val yaPaso = ahora - ajustes.ultimoCambio >= ajustes.horas * HORA * 9 / 10
        if (!forzar && franja == ajustes.ultimaFranja && !yaPaso) return null

        val imagenes = Fondos.imagenes(contexto, carpeta)
        if (imagenes.isEmpty()) return null
        val ultima = ajustes.ultimaImagen(franja)
        val siguiente = when (ajustes.orden) {
            Orden.AL_AZAR -> imagenes.filter { it.nombre != ultima }.ifEmpty { imagenes }.random()
            // Si la última ya no está (o no hay), empieza por la primera.
            Orden.EN_ORDEN -> imagenes[(imagenes.indexOfFirst { it.nombre == ultima } + 1) % imagenes.size]
        }
        Fondos.poner(contexto, siguiente.uri, ajustes.pantalla)
        ajustes.ponerUltimaImagen(franja, siguiente.nombre)
        ajustes.ultimaFranja = franja
        ajustes.ultimoCambio = ahora
        return siguiente.nombre
    }

    /**
     * Arma (o quita) el trabajo periódico según los ajustes. Con la noche
     * prendida mira cada hora, para no tardar mucho en pasar de día a noche;
     * sin ella, solo una vez por intervalo.
     */
    fun programar(contexto: Context) {
        val trabajos = WorkManager.getInstance(contexto)
        val ajustes = AjustesFondos(contexto)
        if (!ajustes.activo || ajustes.carpeta == null) {
            trabajos.cancelUniqueWork(NOMBRE)
            return
        }
        val cada = if (ajustes.usaNoche) 1 else ajustes.horas
        // Corre en los últimos 15 minutos de cada vuelta, así las vueltas quedan parejas.
        val pedido = PeriodicWorkRequestBuilder<CambioDeFondo>(cada.toLong(), TimeUnit.HOURS, 15, TimeUnit.MINUTES).build()
        trabajos.enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, pedido)
    }
}

/** El trabajo periódico: solo llama a [Rotacion.tocar]. WorkManager lo vuelve a armar tras reiniciar. */
class CambioDeFondo(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (AjustesFondos(applicationContext).activo) {
            // Si falla (carpeta borrada, imagen dañada) se intenta en la próxima vuelta.
            runCatching { Rotacion.tocar(applicationContext, forzar = false) }
        }
        Result.success()
    }
}
