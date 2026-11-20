package com.camstagdev.camstyling.tonos

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Recorta un pedazo de una canción (el coro, por ejemplo) con una entrada
 * suave y lo guarda como tono en la carpeta de tonos del teléfono. Se guarda
 * en WAV: no hace falta volver a comprimir y cualquier teléfono lo toca.
 */
object Recorte {

    /** Un tono más largo no tiene sentido y el WAV ocuparía mucho. */
    const val MAXIMO_MS = 40_000L

    fun duracionMs(contexto: Context, cancion: Uri): Long {
        val lector = MediaMetadataRetriever()
        return try {
            lector.setDataSource(contexto, cancion)
            lector.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            lector.release()
        }
    }

    /**
     * Decodifica de [desdeMs] a [hastaMs], sube el volumen de a poco durante
     * [entradaMs] y guarda el resultado como tono de [tipo]. Tarda unos
     * segundos: llamar fuera del hilo principal.
     */
    fun guardar(contexto: Context, cancion: Uri, desdeMs: Long, hastaMs: Long, entradaMs: Long, nombre: String, tipo: TipoDeTono): Uri {
        val audio = decodificar(contexto, cancion, desdeMs, hastaMs.coerceAtMost(desdeMs + MAXIMO_MS))
        suavizarEntrada(audio, entradaMs)
        // Un final cortado de golpe suena a "clic": medio segundo de salida suave.
        suavizarSalida(audio, 500)

        val resolver = contexto.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$nombre.wav")
            put(MediaStore.Audio.Media.TITLE, nombre)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, tipo.carpeta)
            put(tipo.columna, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val coleccion = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val destino = resolver.insert(coleccion, valores) ?: error("No se pudo crear el tono")
        try {
            resolver.openOutputStream(destino).use { salida -> escribirWav(requireNotNull(salida), audio) }
            resolver.update(destino, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(destino, null, null)
            throw e
        }
        return destino
    }

    private class Audio(val muestras: ShortArray, val frecuencia: Int, val canales: Int)

    private fun decodificar(contexto: Context, cancion: Uri, desdeMs: Long, hastaMs: Long): Audio {
        val extractor = MediaExtractor()
        var decodificador: MediaCodec? = null
        try {
            extractor.setDataSource(contexto, cancion, null)
            val pista = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("El archivo no tiene audio")
            val formato = extractor.getTrackFormat(pista)
            extractor.selectTrack(pista)
            extractor.seekTo(desdeMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
            decodificador = codec
            codec.configure(formato, null, null, 0)
            codec.start()

            var frecuencia = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var canales = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var enFlotante = false
            val salida = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var finEntrada = false
            var listo = false

            while (!listo) {
                if (!finEntrada) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val leidos = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (leidos < 0 || extractor.sampleTime > hastaMs * 1000) {
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
                    val f = codec.outputFormat
                    frecuencia = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    canales = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    enFlotante = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val datos = codec.getOutputBuffer(o)!!.apply {
                        position(info.offset)
                        limit(info.offset + info.size)
                        order(ByteOrder.nativeOrder())
                    }
                    val valores = if (enFlotante) {
                        datos.asFloatBuffer().let { b -> ShortArray(b.remaining()) { (b.get().coerceIn(-1f, 1f) * 32767).toInt().toShort() } }
                    } else {
                        datos.asShortBuffer().let { b -> ShortArray(b.remaining()) { b.get() } }
                    }
                    // Cada muestra tiene su momento: se guarda solo lo que cae entre "desde" y "hasta".
                    val porSegundo = frecuencia.toLong() * canales
                    for ((n, v) in valores.withIndex()) {
                        val us = info.presentationTimeUs + n / canales * 1_000_000L / frecuencia
                        if (us < desdeMs * 1000) continue
                        if (us >= hastaMs * 1000) {
                            listo = true
                            break
                        }
                        salida.write(v.toInt() and 0xFF)
                        salida.write(v.toInt() shr 8 and 0xFF)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) listo = true
                    if (salida.size() / 2 > porSegundo * (MAXIMO_MS / 1000 + 1)) listo = true
                }
            }
            val bytes = salida.toByteArray()
            val muestras = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(muestras)
            if (muestras.isEmpty()) error("No se pudo leer ese pedazo")
            return Audio(muestras, frecuencia, canales)
        } finally {
            runCatching { decodificador?.stop() }
            decodificador?.release()
            extractor.release()
        }
    }

    private fun suavizarEntrada(audio: Audio, ms: Long) {
        val m = audio.muestras
        val total = (audio.frecuencia.toLong() * audio.canales * ms / 1000).toInt().coerceAtMost(m.size)
        for (i in 0 until total) m[i] = (m[i] * (i.toFloat() / total)).toInt().toShort()
    }

    private fun suavizarSalida(audio: Audio, ms: Long) {
        val m = audio.muestras
        val total = (audio.frecuencia.toLong() * audio.canales * ms / 1000).toInt().coerceAtMost(m.size)
        for (i in 0 until total) {
            val j = m.size - 1 - i
            m[j] = (m[j] * (i.toFloat() / total)).toInt().toShort()
        }
    }

    private fun escribirWav(salida: OutputStream, audio: Audio) {
        val frecuencia = audio.frecuencia
        val canales = audio.canales
        val datos = audio.muestras.size * 2
        val cabecera = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + datos); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(canales.toShort())
            putInt(frecuencia); putInt(frecuencia * canales * 2); putShort((canales * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(datos)
        }
        salida.write(cabecera.array())
        val cuerpo = ByteBuffer.allocate(datos).order(ByteOrder.LITTLE_ENDIAN)
        cuerpo.asShortBuffer().put(audio.muestras)
        salida.write(cuerpo.array())
    }
}
