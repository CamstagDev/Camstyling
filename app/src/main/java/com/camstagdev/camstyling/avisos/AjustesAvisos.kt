package com.camstagdev.camstyling.avisos

import android.content.Context
import androidx.core.content.edit
import com.camstagdev.camstyling.tonos.DatosDeAviso
import org.json.JSONArray
import org.json.JSONObject

/** Días que se guarda el historial. */
val DIAS_DE_HISTORIAL = listOf(1, 3, 7, 30)

/** Cada cuántos minutos se repite un recordatorio. */
val MINUTOS_DE_RECORDATORIO = listOf(5, 10, 15, 30)

/**
 * Los ajustes del módulo Avisos: historial, recordatorio y linterna. Los
 * "elegidos" son las apps ("app:paquete"), personas, grupos o palabras a
 * los que se aplica cada cosa.
 */
class AjustesAvisos(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("avisos", Context.MODE_PRIVATE)

    var historial: Boolean
        get() = prefs.getBoolean("historial", false)
        set(valor) = prefs.edit { putBoolean("historial", valor) }

    var diasDeHistorial: Int
        get() = prefs.getInt("dias", 7).takeIf { it in DIAS_DE_HISTORIAL } ?: 7
        set(valor) = prefs.edit { putInt("dias", valor) }

    var recordatorio: Boolean
        get() = prefs.getBoolean("recordatorio", false)
        set(valor) = prefs.edit { putBoolean("recordatorio", valor) }

    var minutosDeRecordatorio: Int
        get() = prefs.getInt("minutos", 10).takeIf { it in MINUTOS_DE_RECORDATORIO } ?: 10
        set(valor) = prefs.edit { putInt("minutos", valor) }

    /** Cuántas veces se repite como máximo. */
    var repeticiones: Int
        get() = prefs.getInt("repeticiones", 3).coerceIn(1, 10)
        set(valor) = prefs.edit { putInt("repeticiones", valor) }

    var elegidosRecordatorio: List<String>
        get() = lista("elegidos_recordatorio")
        set(valor) = guardarLista("elegidos_recordatorio", valor)

    var linterna: Boolean
        get() = prefs.getBoolean("linterna", false)
        set(valor) = prefs.edit { putBoolean("linterna", valor) }

    /** La linterna solo parpadea con la pantalla apagada (boca abajo sobre la mesa, por ejemplo). */
    var linternaSoloApagada: Boolean
        get() = prefs.getBoolean("linterna_apagada", true)
        set(valor) = prefs.edit { putBoolean("linterna_apagada", valor) }

    var elegidosLinterna: List<String>
        get() = lista("elegidos_linterna")
        set(valor) = guardarLista("elegidos_linterna", valor)

    /**
     * Lo pospuesto: cada elección ("app:paquete", una persona o grupo, o una
     * palabra) con la hora (en milisegundos) hasta la que se aparta lo que
     * llega de ella. Lo vencido se quita al leer.
     */
    var pospuestos: Map<String, Long>
        get() {
            val json = runCatching { JSONObject(prefs.getString("pospuestos", "{}")!!) }.getOrDefault(JSONObject())
            val ahora = System.currentTimeMillis()
            return json.keys().asSequence().associateWith { json.getLong(it) }.filterValues { it > ahora }
        }
        set(valor) = prefs.edit { putString("pospuestos", JSONObject(valor).toString()) }

    fun posponer(eleccion: String, hasta: Long) {
        pospuestos = pospuestos + (eleccion to hasta)
    }

    fun quitarPospuesto(eleccion: String) {
        pospuestos = pospuestos - eleccion
    }

    private fun lista(clave: String): List<String> {
        val json = JSONArray(prefs.getString(clave, "[]"))
        return (0 until json.length()).map { json.getString(it) }
    }

    private fun guardarLista(clave: String, valor: List<String>) = prefs.edit { putString(clave, JSONArray(valor).toString()) }
}

/** Dentro de una hora. */
fun enUnaHora(): Long = System.currentTimeMillis() + 60 * 60 * 1000L

/** Mañana a las 8:00. */
fun mananaTemprano(): Long = java.time.LocalDate.now().plusDays(1).atTime(8, 0)
    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Si la notificación es de algo elegido: "app:paquete", el nombre de una persona o grupo, o una palabra del mensaje. */
fun DatosDeAviso.coincideCon(eleccion: String): Boolean = when {
    eleccion.startsWith("app:") -> eleccion.removePrefix("app:") == paquete
    else -> chats.any { it.equals(eleccion, ignoreCase = true) } || eleccion.lowercase() in texto.lowercase()
}
