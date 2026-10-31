package com.camstagdev.camstyling.tonos

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.Contacts

/** Un contacto con tono propio: dónde está, su nombre y su tono. */
data class ContactoConTono(val uri: Uri, val nombre: String, val tono: Uri)

/**
 * El tono de llamada de cada contacto. Se guarda en el mismo contacto
 * (CUSTOM_RINGTONE), así lo toca la app de Teléfono sin que Camstyling haga
 * nada al sonar. Necesita el permiso "Contactos".
 */
object TonosPorContacto {

    fun lista(contexto: Context): List<ContactoConTono> {
        val columnas = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME, Contacts.CUSTOM_RINGTONE)
        val lista = mutableListOf<ContactoConTono>()
        contexto.contentResolver.query(Contacts.CONTENT_URI, columnas, "${Contacts.CUSTOM_RINGTONE} IS NOT NULL", null, Contacts.DISPLAY_NAME)
            ?.use { c ->
                while (c.moveToNext()) {
                    val tono = c.getString(3)?.takeIf { it.isNotBlank() } ?: continue
                    val uri = Contacts.getLookupUri(c.getLong(0), c.getString(1)) ?: continue
                    lista += ContactoConTono(uri, c.getString(2) ?: "Sin nombre", Uri.parse(tono))
                }
            }
        return lista
    }

    fun nombre(contexto: Context, contacto: Uri): String? =
        contexto.contentResolver.query(contacto, arrayOf(Contacts.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    /** null = vuelve al tono de llamada del teléfono. */
    fun poner(contexto: Context, contacto: Uri, tono: Uri?) {
        val valores = ContentValues().apply {
            if (tono == null) putNull(Contacts.CUSTOM_RINGTONE) else put(Contacts.CUSTOM_RINGTONE, tono.toString())
        }
        contexto.contentResolver.update(contacto, valores, null, null)
    }
}
