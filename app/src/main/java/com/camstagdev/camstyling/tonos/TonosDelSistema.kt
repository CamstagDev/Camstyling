package com.camstagdev.camstyling.tonos

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns

/** Los tres tonos del sistema, cada uno con su carpeta de tonos del teléfono. */
enum class TipoDeTono(val titulo: String, val tipo: Int, val carpeta: String, val columna: String) {
    LLAMADA("Llamada", RingtoneManager.TYPE_RINGTONE, Environment.DIRECTORY_RINGTONES, MediaStore.Audio.AudioColumns.IS_RINGTONE),
    NOTIFICACION("Notificación", RingtoneManager.TYPE_NOTIFICATION, Environment.DIRECTORY_NOTIFICATIONS, MediaStore.Audio.AudioColumns.IS_NOTIFICATION),
    ALARMA("Alarma", RingtoneManager.TYPE_ALARM, Environment.DIRECTORY_ALARMS, MediaStore.Audio.AudioColumns.IS_ALARM),
}

/** Leer y cambiar el tono de llamada, notificación y alarma (necesita "Modificar ajustes del sistema"). */
object TonosDelSistema {

    fun actual(contexto: Context, tipo: TipoDeTono): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(contexto, tipo.tipo)

    fun nombre(contexto: Context, tono: Uri?): String {
        if (tono == null) return "Silencio"
        return runCatching { RingtoneManager.getRingtone(contexto, tono)?.getTitle(contexto) }.getOrNull() ?: "Tono sin nombre"
    }

    /** null = silencio. */
    fun poner(contexto: Context, tipo: TipoDeTono, tono: Uri?) =
        RingtoneManager.setActualDefaultRingtoneUri(contexto, tipo.tipo, tono)

    /**
     * Copia un archivo de audio a la carpeta de tonos del teléfono (Ringtones,
     * Notifications o Alarms) y devuelve la copia. Así el tono sigue sonando
     * aunque se borre o se mueva el archivo original.
     */
    fun copiar(contexto: Context, origen: Uri, tipo: TipoDeTono): Uri {
        val resolver = contexto.contentResolver
        val nombre = nombreDeArchivo(contexto, origen) ?: "Tono de Camstyling"
        val valores = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Audio.Media.MIME_TYPE, resolver.getType(origen) ?: "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, tipo.carpeta)
            put(tipo.columna, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val coleccion = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val destino = resolver.insert(coleccion, valores) ?: error("No se pudo crear el tono")
        try {
            resolver.openInputStream(origen).use { entrada ->
                resolver.openOutputStream(destino).use { salida ->
                    requireNotNull(entrada) { "No se pudo leer el archivo" }.copyTo(requireNotNull(salida))
                }
            }
            resolver.update(destino, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(destino, null, null)
            throw e
        }
        return destino
    }

    /** El nombre con que se ve un archivo elegido ("mi tono.mp3"), si lo dice. */
    fun nombreDeArchivo(contexto: Context, archivo: Uri): String? =
        contexto.contentResolver.query(archivo, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
}
