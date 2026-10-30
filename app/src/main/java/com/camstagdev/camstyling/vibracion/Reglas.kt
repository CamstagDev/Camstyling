package com.camstagdev.camstyling.vibracion

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import java.util.UUID

/**
 * Qué vibración usa cada cosa: las llamadas y cada app. Se guarda en el
 * teléfono (SharedPreferences); la pantalla, el servicio de notificaciones y
 * el aviso de llamadas leen lo mismo.
 */
class Reglas(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("vibracion", Context.MODE_PRIVATE)

    fun propios(): List<Patron> {
        val json = JSONArray(prefs.getString(PROPIOS, "[]"))
        return (0 until json.length()).mapNotNull { runCatching { Patron.deJson(json.getJSONObject(it)) }.getOrNull() }
    }

    fun todos(): List<Patron> = PREDETERMINADOS + propios()

    fun buscar(id: String?): Patron? = id?.let { buscado -> todos().firstOrNull { it.id == buscado } }

    /** Guarda uno nuevo o reemplaza el que tenga su id. Devuelve el guardado (con id). */
    fun guardarPropio(patron: Patron): Patron {
        val conId = if (patron.esPropio) patron else patron.copy(id = "c:" + UUID.randomUUID())
        val lista = propios().filter { it.id != conId.id } + conId
        prefs.edit { putString(PROPIOS, JSONArray(lista.map { it.aJson() }).toString()) }
        return conId
    }

    /** Lo borra y quita las reglas que lo usaban (vuelven a la vibración del sistema). */
    fun borrarPropio(id: String) {
        prefs.edit {
            putString(PROPIOS, JSONArray(propios().filter { it.id != id }.map { it.aJson() }).toString())
            prefs.all.filter { it.value == id }.keys.forEach { remove(it) }
        }
    }

    /** La vibración de las llamadas, o null si la maneja el sistema. */
    var idDeLlamada: String?
        get() = prefs.getString(LLAMADA, null)
        set(valor) = prefs.edit { if (valor == null) remove(LLAMADA) else putString(LLAMADA, valor) }

    fun patronDeLlamada(): Patron? = buscar(idDeLlamada)

    /** Paquete de cada app con regla -> id de su patrón. */
    fun apps(): Map<String, String> = prefs.all
        .filterKeys { it.startsWith(APP) }
        .mapNotNull { (clave, valor) -> (valor as? String)?.let { clave.removePrefix(APP) to it } }
        .toMap()

    fun patronDeApp(paquete: String): Patron? = buscar(prefs.getString(APP + paquete, null))

    fun ponerApp(paquete: String, idPatron: String?) = prefs.edit {
        if (idPatron == null) remove(APP + paquete) else putString(APP + paquete, idPatron)
    }

    private companion object {
        const val PROPIOS = "propios"
        const val LLAMADA = "llamada"
        const val APP = "app:"
    }
}
