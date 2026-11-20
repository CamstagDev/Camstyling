package com.camstagdev.camstyling.perfiles

import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Cómo queda el timbre durante el perfil. */
enum class Modo(val texto: String, val timbre: Int) {
    SONIDO("Sonido", AudioManager.RINGER_MODE_NORMAL),
    VIBRAR("Vibrar", AudioManager.RINGER_MODE_VIBRATE),
    SILENCIO("Silencio", AudioManager.RINGER_MODE_SILENT),
}

/**
 * Un perfil de sonido: los días y las horas en que manda, y lo que cambia.
 * Lo que queda en null no se toca. Con [silencio], son horas de silencio:
 * solo suenan las [excepciones] (nombres de personas o grupos, palabras o
 * apps, estas como "app:paquete") y las llamadas de los favoritos.
 */
data class Perfil(
    val id: String,
    val nombre: String,
    /** Días en que empieza (1 = lunes ... 7 = domingo). */
    val dias: Set<Int>,
    /** Minutos desde la medianoche. Si [hasta] es menor, termina al día siguiente; si son iguales, dura todo el día. */
    val desde: Int,
    val hasta: Int,
    val modo: Modo?,
    /** Porcentaje del volumen máximo. */
    val volumenLlamada: Int?,
    val volumenNotificaciones: Int?,
    val tonoLlamada: String?,
    val tonoNotificacion: String?,
    /** Id del patrón de vibración de llamadas. */
    val vibracionLlamada: String?,
    val silencio: Boolean,
    val excepciones: List<String>,
    /** Respuesta automática a los chats (uno a uno, no grupos) de [responderA], una vez por chat mientras dura el perfil. */
    val respuesta: String? = null,
    val responderA: List<String> = emptyList(),
) {

    /** Si el perfil manda en ese momento. */
    fun manda(momento: LocalDateTime): Boolean {
        val minuto = momento.hour * 60 + momento.minute
        val hoy = momento.dayOfWeek.value
        val ayer = momento.minusDays(1).dayOfWeek.value
        return when {
            desde == hasta -> hoy in dias
            desde < hasta -> hoy in dias && minuto >= desde && minuto < hasta
            else -> (hoy in dias && minuto >= desde) || (ayer in dias && minuto < hasta)
        }
    }

    val textoDias: String
        get() = when (dias) {
            (1..7).toSet() -> "Todos los días"
            (1..5).toSet() -> "De lunes a viernes"
            setOf(6, 7) -> "Fines de semana"
            else -> dias.sorted().joinToString(", ") {
                DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es"))
            }
        }

    val textoHoras: String
        get() = if (desde == hasta) "todo el día" else "de ${textoDeHora(desde)} a ${textoDeHora(hasta)}"

    fun aJson(): JSONObject = JSONObject()
        .put("id", id).put("nombre", nombre).put("dias", JSONArray(dias.sorted()))
        .put("desde", desde).put("hasta", hasta)
        .put("modo", modo?.name).put("volumenLlamada", volumenLlamada).put("volumenNotificaciones", volumenNotificaciones)
        .put("tonoLlamada", tonoLlamada).put("tonoNotificacion", tonoNotificacion).put("vibracionLlamada", vibracionLlamada)
        .put("silencio", silencio).put("excepciones", JSONArray(excepciones))
        .put("respuesta", respuesta).put("responderA", JSONArray(responderA))

    companion object {
        fun deJson(json: JSONObject): Perfil {
            val dias = json.getJSONArray("dias")
            val excepciones = json.optJSONArray("excepciones") ?: JSONArray()
            val responderA = json.optJSONArray("responderA") ?: JSONArray()
            return Perfil(
                id = json.getString("id"),
                nombre = json.getString("nombre"),
                dias = (0 until dias.length()).map { dias.getInt(it) }.toSet(),
                desde = json.getInt("desde"),
                hasta = json.getInt("hasta"),
                modo = json.textoONulo("modo")?.let { nombre -> Modo.entries.firstOrNull { it.name == nombre } },
                volumenLlamada = json.enteroONulo("volumenLlamada"),
                volumenNotificaciones = json.enteroONulo("volumenNotificaciones"),
                tonoLlamada = json.textoONulo("tonoLlamada"),
                tonoNotificacion = json.textoONulo("tonoNotificacion"),
                vibracionLlamada = json.textoONulo("vibracionLlamada"),
                silencio = json.optBoolean("silencio", false),
                excepciones = (0 until excepciones.length()).map { excepciones.getString(it) },
                respuesta = json.textoONulo("respuesta"),
                responderA = (0 until responderA.length()).map { responderA.getString(it) },
            )
        }

        fun nuevo() = Perfil(
            id = "", nombre = "Noche", dias = (1..7).toSet(), desde = 22 * 60, hasta = 7 * 60,
            modo = null, volumenLlamada = null, volumenNotificaciones = null,
            tonoLlamada = null, tonoNotificacion = null, vibracionLlamada = null,
            silencio = true, excepciones = emptyList(),
        )
    }
}

fun textoDeHora(minutos: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutos / 60, minutos % 60)

// JSONObject.optString devuelve "null" (texto) para un null guardado: estos lo devuelven como null de verdad.
internal fun JSONObject.textoONulo(clave: String): String? = if (isNull(clave)) null else optString(clave)

internal fun JSONObject.enteroONulo(clave: String): Int? = if (isNull(clave)) null else optInt(clave)
