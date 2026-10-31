package com.camstagdev.camstyling.tonos

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Cuándo suena una regla. Si varias coinciden, gana la más precisa (la primera de esta lista). */
enum class Cuando(val texto: String) {
    PALABRA("Una palabra"),
    CHAT("Una persona o grupo"),
    CANAL("Una categoría"),
    APP("Toda la app"),
}

/**
 * Un sonido para las notificaciones de una app. [valor] es lo que se busca
 * (el id de la categoría, el nombre de la persona o grupo, o la palabra) y
 * [etiqueta], cómo se muestra (el nombre de la categoría, por ejemplo).
 */
data class ReglaDeSonido(
    val id: String,
    val paquete: String,
    val cuando: Cuando,
    val valor: String,
    val etiqueta: String,
    val tono: String,
) {
    val descripcion: String
        get() = when (cuando) {
            Cuando.APP -> "Toda la app"
            Cuando.CANAL -> "Categoría \"$etiqueta\""
            Cuando.CHAT -> "De \"$etiqueta\""
            Cuando.PALABRA -> "Con la palabra \"$etiqueta\""
        }

    fun aJson(): JSONObject = JSONObject()
        .put("id", id).put("paquete", paquete).put("cuando", cuando.name)
        .put("valor", valor).put("etiqueta", etiqueta).put("tono", tono)

    companion object {
        fun deJson(json: JSONObject) = ReglaDeSonido(
            id = json.getString("id"),
            paquete = json.getString("paquete"),
            cuando = Cuando.valueOf(json.getString("cuando")),
            valor = json.getString("valor"),
            etiqueta = json.optString("etiqueta", json.getString("valor")),
            tono = json.getString("tono"),
        )
    }
}

/** Lo que importa de una notificación para elegir su sonido. */
data class DatosDeAviso(val paquete: String, val canal: String?, val chats: List<String>, val texto: String) {

    companion object {
        /** "Grupo (3 mensajes)" -> "Grupo": WhatsApp y otras le suman la cuenta al título. */
        private val CUENTA = Regex("""\s*\(\d+[^)]*\)\s*$""")

        fun de(aviso: StatusBarNotification): DatosDeAviso {
            val notificacion = aviso.notification
            val extras = notificacion.extras
            val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val estilo = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notificacion)
            val conversacion = estilo?.conversationTitle?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            val remitente = estilo?.messages?.lastOrNull()?.person?.name?.toString()

            // En los grupos el título suele ser "Grupo: Persona" o "Persona @ Grupo": vale cada parte.
            val chats = listOfNotNull(conversacion, remitente, titulo)
                .flatMap { listOf(it) + it.split(": ", " @ ") }
                .map { it.replace(CUENTA, "").trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val texto = listOfNotNull(
                titulo,
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            ) + estilo?.messages?.takeLast(3)?.mapNotNull { it.text?.toString() }.orEmpty()

            return DatosDeAviso(aviso.packageName, notificacion.channelId, chats, texto.joinToString("\n"))
        }
    }
}

/**
 * Las reglas de sonido por app. Se guardan en el teléfono (SharedPreferences);
 * la pantalla Tonos y el servicio de notificaciones leen lo mismo.
 */
class ReglasDeSonido(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("sonidos", Context.MODE_PRIVATE)

    fun todas(): List<ReglaDeSonido> {
        migrar()
        val json = JSONArray(prefs.getString(REGLAS, "[]"))
        return (0 until json.length()).mapNotNull { runCatching { ReglaDeSonido.deJson(json.getJSONObject(it)) }.getOrNull() }
    }

    /** Guarda una nueva o reemplaza la que tenga su id. */
    fun guardar(regla: ReglaDeSonido): ReglaDeSonido {
        val conId = if (regla.id.isNotEmpty()) regla else regla.copy(id = UUID.randomUUID().toString())
        val lista = todas().filter { it.id != conId.id } + conId
        prefs.edit { putString(REGLAS, JSONArray(lista.map { it.aJson() }).toString()) }
        return conId
    }

    fun borrar(id: String) = prefs.edit {
        putString(REGLAS, JSONArray(todas().filter { it.id != id }.map { it.aJson() }).toString())
    }

    /** La regla que le toca a esta notificación, o null si su app no tiene ninguna que coincida. */
    fun buscar(datos: DatosDeAviso): ReglaDeSonido? {
        val texto = datos.texto.lowercase()
        return todas()
            .filter { it.paquete == datos.paquete }
            .sortedBy { it.cuando.ordinal }
            .firstOrNull { regla ->
                when (regla.cuando) {
                    Cuando.PALABRA -> regla.valor.isNotBlank() && regla.valor.lowercase() in texto
                    Cuando.CHAT -> datos.chats.any { it.equals(regla.valor, ignoreCase = true) }
                    Cuando.CANAL -> regla.valor == datos.canal
                    Cuando.APP -> true
                }
            }
    }

    /**
     * La primera versión guardaba un sonido por app ("app:paquete" -> sonido).
     * Cada uno pasa a ser una regla "Toda la app".
     */
    private fun migrar() {
        val viejos = prefs.all.filterKeys { it.startsWith(VIEJO) }
        if (viejos.isEmpty()) return
        val json = JSONArray(prefs.getString(REGLAS, "[]"))
        viejos.forEach { (clave, valor) ->
            val tono = valor as? String ?: return@forEach
            json.put(ReglaDeSonido(UUID.randomUUID().toString(), clave.removePrefix(VIEJO), Cuando.APP, "", "Toda la app", tono).aJson())
        }
        prefs.edit {
            putString(REGLAS, json.toString())
            viejos.keys.forEach { remove(it) }
        }
    }

    private companion object {
        const val REGLAS = "reglas"
        const val VIEJO = "app:"
    }
}

/** Una categoría de notificaciones de otra app, como la vio el servicio. */
data class CanalVisto(val id: String, val nombre: String, val conSonido: Boolean)

/**
 * Las categorías y los chats que el servicio ya vio de cada app. Android no
 * deja leer la lista de categorías de otra app, así que se van anotando a
 * medida que llegan notificaciones; con eso se arman las opciones al crear
 * una regla. Todo queda en el teléfono.
 */
class Vistos(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("vistos", Context.MODE_PRIVATE)

    fun canales(paquete: String): List<CanalVisto> {
        val json = runCatching { JSONObject(prefs.getString(CANALES + paquete, "{}")!!) }.getOrDefault(JSONObject())
        return json.keys().asSequence().map { id ->
            val canal = json.getJSONObject(id)
            CanalVisto(id, canal.optString("nombre", id), canal.optBoolean("sonido", true))
        }.sortedBy { it.nombre.lowercase() }.toList()
    }

    fun chats(paquete: String): List<String> {
        val json = JSONArray(prefs.getString(CHATS + paquete, "[]"))
        return (0 until json.length()).map { json.getString(it) }
    }

    /** Anota la categoría y los chats de una notificación. Solo escribe si hay algo nuevo. */
    fun anotar(paquete: String, canal: NotificationChannel?, chats: List<String>) {
        val canales = runCatching { JSONObject(prefs.getString(CANALES + paquete, "{}")!!) }.getOrDefault(JSONObject())
        var nuevoCanal = false
        if (canal != null) {
            val anterior = canales.optJSONObject(canal.id)
            val nombre = canal.name?.toString() ?: canal.id
            val conSonido = canal.sound != null
            if (anterior == null || anterior.optString("nombre") != nombre || anterior.optBoolean("sonido") != conSonido) {
                canales.put(canal.id, JSONObject().put("nombre", nombre).put("sonido", conSonido))
                nuevoCanal = true
            }
        }
        val anteriores = chats(paquete)
        // Los más recientes primero; se guardan hasta 40 por app.
        val recientes = (chats + anteriores).distinct().take(MAX_CHATS)
        val nuevosChats = recientes != anteriores
        if (!nuevoCanal && !nuevosChats) return
        prefs.edit {
            if (nuevoCanal) putString(CANALES + paquete, canales.toString())
            if (nuevosChats) putString(CHATS + paquete, JSONArray(recientes).toString())
        }
    }

    private companion object {
        const val CANALES = "canales:"
        const val CHATS = "chats:"
        const val MAX_CHATS = 40
    }
}
