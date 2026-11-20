package com.camstagdev.camstyling.llamadas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.PhoneLookup
import android.speech.tts.TextToSpeech
import androidx.core.content.edit
import com.camstagdev.camstyling.perfiles.Perfiles
import com.camstagdev.camstyling.sistema.Permisos
import org.json.JSONArray
import java.util.Locale

/** Ajustes del módulo Llamadas. Se guardan en "llamadas" (también van al respaldo). */
class AjustesLlamadas(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("llamadas", Context.MODE_PRIVATE)

    /** El tono empieza bajito y sube hasta el volumen de siempre. */
    var creciente: Boolean
        get() = prefs.getBoolean("creciente", false)
        set(valor) = prefs.edit { putBoolean("creciente", valor) }

    var segundosDeSubida: Int
        get() = prefs.getInt("segundos", 10).coerceIn(3, 30)
        set(valor) = prefs.edit { putInt("segundos", valor) }

    /** Decir en voz alta quién llama. */
    var anunciar: Boolean
        get() = prefs.getBoolean("anunciar", false)
        set(valor) = prefs.edit { putBoolean("anunciar", valor) }

    /** Solo estos contactos (por nombre); vacío = todos. */
    var contactos: List<String>
        get() = lista("contactos")
        set(valor) = prefs.edit { putString("contactos", JSONArray(valor).toString()) }

    /** Solo durante estos perfiles (por id); vacío = siempre. */
    var perfiles: List<String>
        get() = lista("perfiles")
        set(valor) = prefs.edit { putString("perfiles", JSONArray(valor).toString()) }

    /** El volumen de llamada de antes de la subida, por si la app se cerró en medio. */
    var volumenOriginal: Int
        get() = prefs.getInt("volumen_original", -1)
        set(valor) = prefs.edit { if (valor < 0) remove("volumen_original") else putInt("volumen_original", valor) }

    private fun lista(clave: String): List<String> {
        val json = JSONArray(prefs.getString(clave, "[]"))
        return (0 until json.length()).map { json.getString(it) }
    }
}

/**
 * Lo que pasa mientras suena una llamada: el tono que sube de a poco y la
 * voz que dice quién llama. Lo llama LlamadaEntrante al empezar y al
 * terminar de sonar; no corre nada fuera de eso.
 */
object Timbre {

    private const val PASO_MS = 250L
    private const val REPETIR_VOZ_MS = 6000L
    private const val VECES_VOZ = 3

    private val hilo = Handler(Looper.getMainLooper())
    private var sonando = false
    private var anunciado = false
    private var voz: TextToSpeech? = null

    /** Puede llegar dos veces por llamada (con y sin número): solo hace cada cosa una vez. */
    fun alSonar(contexto: Context, numero: String?) {
        val app = contexto.applicationContext
        val ajustes = AjustesLlamadas(app)
        if (!sonando) {
            sonando = true
            anunciado = false
            if (ajustes.creciente) subir(app, ajustes)
        }
        if (numero != null && !anunciado && ajustes.anunciar) {
            anunciado = true
            nombreParaAnunciar(app, ajustes, numero)?.let { anunciar(app, it) }
        }
    }

    fun alTerminar(contexto: Context) {
        val app = contexto.applicationContext
        sonando = false
        hilo.removeCallbacksAndMessages(null)
        voz?.stop()
        voz?.shutdown()
        voz = null
        val ajustes = AjustesLlamadas(app)
        val original = ajustes.volumenOriginal
        if (original >= 0) {
            runCatching { app.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_RING, original, 0) }
            ajustes.volumenOriginal = -1
        }
    }

    private fun subir(contexto: Context, ajustes: AjustesLlamadas) {
        val audio = contexto.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        // Si quedó guardado de una llamada que se cortó a mitad de subida, ese es el de verdad.
        val objetivo = ajustes.volumenOriginal.takeIf { it >= 0 } ?: audio.getStreamVolume(AudioManager.STREAM_RING)
        if (objetivo <= 1) return
        ajustes.volumenOriginal = objetivo
        // Nunca a 0: eso pasaría el teléfono a modo vibrar.
        runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, 1, 0) }
        val pasos = (ajustes.segundosDeSubida * 1000 / PASO_MS).toInt().coerceAtLeast(1)
        for (i in 1..pasos) {
            hilo.postDelayed({
                if (sonando) {
                    val volumen = (1 + (objetivo - 1) * i / pasos).coerceIn(1, objetivo)
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, volumen, 0) }
                }
            }, i * PASO_MS)
        }
    }

    /** El nombre a decir, o null si esta llamada no se anuncia (otro perfil, contacto no elegido, sin permiso). */
    private fun nombreParaAnunciar(contexto: Context, ajustes: AjustesLlamadas, numero: String): String? {
        val perfiles = ajustes.perfiles
        if (perfiles.isNotEmpty() && Perfiles(contexto).aplicado?.id !in perfiles) return null
        val nombre = if (Permisos.puedeUsarContactos(contexto)) runCatching {
            contexto.contentResolver.query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numero)),
                arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() else null
        val elegidos = ajustes.contactos
        if (elegidos.isNotEmpty()) return nombre?.takeIf { n -> elegidos.any { it.equals(n, ignoreCase = true) } }
        return nombre ?: "un número desconocido"
    }

    private fun anunciar(contexto: Context, nombre: String) {
        voz?.shutdown()
        var nueva: TextToSpeech? = null
        nueva = TextToSpeech(contexto) { estado ->
            val lista = nueva ?: return@TextToSpeech
            if (estado != TextToSpeech.SUCCESS || !sonando) return@TextToSpeech
            lista.language = Locale.forLanguageTag("es")
            lista.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            repeat(VECES_VOZ) { i ->
                hilo.postDelayed({
                    if (sonando) lista.speak("Te llama $nombre", TextToSpeech.QUEUE_FLUSH, null, "llamada")
                }, 1000 + i * REPETIR_VOZ_MS)
            }
        }
        voz = nueva
    }
}
