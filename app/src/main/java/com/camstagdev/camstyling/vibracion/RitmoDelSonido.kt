package com.camstagdev.camstyling.vibracion

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Saca una vibración del ritmo de un sonido: decodifica el audio, mide qué tan
 * fuerte suena cada pedacito de [VENTANA_MS] y vibra más fuerte donde el sonido
 * es más fuerte. Así el teléfono "vibra con el tono".
 *
 * Se calcula una vez, al elegir el sonido, y se guarda como un patrón normal:
 * al llegar la llamada no hay que decodificar nada.
 */
object RitmoDelSonido {

    private const val VENTANA_MS = 40
    private const val MAXIMO_MS = 30_000

    /** Tarda un poco (decodifica el audio): llamar fuera del hilo principal. */
    fun calcular(contexto: Context, sonido: Uri, controlaFuerza: Boolean): List<Tramo> =
        aTramos(energias(contexto, sonido), controlaFuerza)

    /** Volumen (RMS) de cada ventana del sonido, hasta [MAXIMO_MS]. */
    private fun energias(contexto: Context, sonido: Uri): List<Double> {
        val extractor = MediaExtractor()
        var decodificador: MediaCodec? = null
        try {
            extractor.setDataSource(contexto, sonido, null)
            val pista = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("El archivo no tiene audio")
            val formato = extractor.getTrackFormat(pista)
            extractor.selectTrack(pista)
            val codec = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
            decodificador = codec
            codec.configure(formato, null, null, 0)
            codec.start()

            var muestrasPorVentana = muestrasPorVentana(formato)
            var enFlotante = false
            val energias = mutableListOf<Double>()
            var suma = 0.0
            var muestras = 0
            val info = MediaCodec.BufferInfo()
            var finEntrada = false
            var finSalida = false

            while (!finSalida && energias.size * VENTANA_MS < MAXIMO_MS) {
                if (!finEntrada) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val leidos = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (leidos < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            finEntrada = true
                        } else {
                            codec.queueInputBuffer(i, 0, leidos, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val salida = codec.outputFormat
                    muestrasPorVentana = muestrasPorVentana(salida)
                    enFlotante = salida.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        salida.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val datos = codec.getOutputBuffer(o)!!.apply {
                        position(info.offset)
                        limit(info.offset + info.size)
                        order(ByteOrder.nativeOrder())
                    }
                    val valores = if (enFlotante) datos.asFloatBuffer().let { b -> DoubleArray(b.remaining()) { b.get().toDouble() } }
                    else datos.asShortBuffer().let { b -> DoubleArray(b.remaining()) { b.get() / 32768.0 } }
                    for (v in valores) {
                        suma += v * v
                        if (++muestras >= muestrasPorVentana) {
                            energias += sqrt(suma / muestras)
                            suma = 0.0
                            muestras = 0
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) finSalida = true
                }
            }
            return energias
        } finally {
            runCatching { decodificador?.stop() }
            decodificador?.release()
            extractor.release()
        }
    }

    private fun muestrasPorVentana(formato: MediaFormat): Int {
        val frecuencia = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val canales = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        return (frecuencia * canales * VENTANA_MS / 1000).coerceAtLeast(1)
    }

    /**
     * Volumen -> fuerza. Se compara con el propio sonido (entre lo más bajo y lo
     * más alto de ese tono), no con el silencio, para que un tono que siempre
     * suena fuerte igual marque su ritmo. Sin control de fuerza, solo vibra en los
     * golpes más fuertes.
     */
    private fun aTramos(energias: List<Double>, controlaFuerza: Boolean): List<Tramo> {
        if (energias.isEmpty()) return emptyList()
        val ordenadas = energias.sorted()
        val bajo = ordenadas[(ordenadas.size * 0.2).toInt()]
        val alto = ordenadas[((ordenadas.size - 1) * 0.95).toInt()]
        if (alto <= bajo) return emptyList()

        val tramos = mutableListOf<Tramo>()
        for (e in energias) {
            val r = ((e - bajo) / (alto - bajo)).coerceIn(0.0, 1.0)
            // 4 niveles de fuerza: con más, el motor no los distingue y el patrón se llena de pedacitos.
            val fuerza = when {
                !controlaFuerza -> if (r >= 0.55) 255 else 0
                r < 0.25 -> 0
                else -> ((r * 4).roundToInt().coerceIn(1, 4) * 255 / 4)
            }
            val ultimo = tramos.lastOrNull()
            if (ultimo != null && ultimo.fuerza == fuerza) {
                tramos[tramos.lastIndex] = ultimo.copy(ms = ultimo.ms + VENTANA_MS)
            } else {
                tramos += Tramo(VENTANA_MS.toLong(), fuerza)
            }
        }
        return tramos
    }
}
