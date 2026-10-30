package com.camstagdev.camstyling.vibracion

import org.json.JSONObject
import kotlin.math.roundToInt

/** Un pedazo de vibración: cuánto dura y con qué fuerza (0 = quieto, 255 = la máxima del motor). */
data class Tramo(val ms: Long, val fuerza: Int)

/**
 * Una vibración con nombre. Las predeterminadas vienen en la app (id "p:..."); las
 * propias las crea el usuario (id "c:...") con una [Receta] o a partir de un sonido.
 */
data class Patron(
    val id: String,
    val nombre: String,
    val tramos: List<Tramo>,
    /** Los valores del editor, para poder volver a abrirlo. Null si salió de un sonido o es predeterminado. */
    val receta: Receta? = null,
    /** Salió del ritmo de un sonido: en llamadas se repite sin pausa, al compás del tono. */
    val delSonido: Boolean = false,
) {
    val esPropio: Boolean get() = id.startsWith("c:")
    val duracionMs: Long get() = tramos.sumOf { it.ms }
    val vibra: Boolean get() = tramos.any { it.fuerza > 0 && it.ms > 0 }

    fun aJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("nombre", nombre)
        .put("tramos", tramos.joinToString(",") { "${it.ms}:${it.fuerza}" })
        .put("delSonido", delSonido)
        .apply { receta?.let { put("receta", it.aJson()) } }

    companion object {
        fun deJson(json: JSONObject): Patron = Patron(
            id = json.getString("id"),
            nombre = json.getString("nombre"),
            tramos = json.getString("tramos").split(",").filter { it.isNotBlank() }.map {
                val (ms, fuerza) = it.split(":")
                Tramo(ms.toLong(), fuerza.toInt())
            },
            receta = json.optJSONObject("receta")?.let(Receta::deJson),
            delSonido = json.optBoolean("delSonido"),
        )
    }
}

/** Cómo cambia la fuerza de un pulso al siguiente. */
enum class Forma(val titulo: String) { PAREJA("Pareja"), SUBE("Sube"), BAJA("Baja") }

/** Los valores del editor de patrones: con ellos se arman los tramos. */
data class Receta(
    val pulsos: Int = 2,
    val duracionMs: Int = 120,
    val pausaMs: Int = 120,
    /** De 1 a 100 (%). */
    val fuerza: Int = 100,
    val forma: Forma = Forma.PAREJA,
    val repeticiones: Int = 1,
    val pausaEntreMs: Int = 500,
) {
    fun aTramos(): List<Tramo> = buildList {
        repeat(repeticiones) { r ->
            for (i in 0 until pulsos) {
                val porcentaje = when (forma) {
                    Forma.PAREJA -> fuerza
                    Forma.SUBE -> fuerza * (i + 1) / pulsos
                    Forma.BAJA -> fuerza * (pulsos - i) / pulsos
                }
                add(Tramo(duracionMs.toLong(), (porcentaje * 255 / 100.0).roundToInt().coerceIn(1, 255)))
                if (i < pulsos - 1) add(Tramo(pausaMs.toLong(), 0))
            }
            if (r < repeticiones - 1) add(Tramo(pausaEntreMs.toLong(), 0))
        }
    }

    fun aJson(): JSONObject = JSONObject()
        .put("pulsos", pulsos)
        .put("duracionMs", duracionMs)
        .put("pausaMs", pausaMs)
        .put("fuerza", fuerza)
        .put("forma", forma.name)
        .put("repeticiones", repeticiones)
        .put("pausaEntreMs", pausaEntreMs)

    companion object {
        fun deJson(json: JSONObject): Receta = Receta(
            pulsos = json.getInt("pulsos"),
            duracionMs = json.getInt("duracionMs"),
            pausaMs = json.getInt("pausaMs"),
            fuerza = json.getInt("fuerza"),
            forma = runCatching { Forma.valueOf(json.getString("forma")) }.getOrDefault(Forma.PAREJA),
            repeticiones = json.getInt("repeticiones"),
            pausaEntreMs = json.getInt("pausaEntreMs"),
        )
    }
}

private const val F = 255

/** Atajo: tramos(80 to F, 100 to 0) = vibra 80 ms fuerte y se queda quieto 100 ms. */
private fun tramos(vararg pares: Pair<Int, Int>) = pares.map { (ms, fuerza) -> Tramo(ms.toLong(), fuerza) }

/** Las que trae la app. Como los teléfonos de antes: cortas, dobles, el timbre clásico, SOS... */
val PREDETERMINADOS: List<Patron> = listOf(
    Patron("p:sin", "Sin vibración", emptyList()),
    Patron("p:corto", "Toque corto", tramos(80 to F)),
    Patron("p:doble", "Doble toque", tramos(70 to F, 110 to 0, 70 to F)),
    Patron("p:triple", "Triple toque", tramos(60 to F, 90 to 0, 60 to F, 90 to 0, 60 to F)),
    Patron("p:latido", "Latido", tramos(100 to F, 90 to 0, 130 to 140)),
    Patron("p:largo", "Largo", tramos(700 to F)),
    Patron("p:timbre", "Timbre clásico", tramos(400 to F, 200 to 0, 400 to F)),
    Patron("p:rafaga", "Ráfaga", (1..8).flatMap { tramos(35 to F, 45 to 0) }.dropLast(1)),
    Patron(
        "p:ola", "Ola",
        listOf(40, 80, 120, 170, 220, 255, 220, 170, 120, 80, 40).map { Tramo(60, it) },
    ),
    Patron("p:tictac", "Tic-tac", tramos(40 to F, 300 to 0, 40 to 120)),
    Patron(
        "p:sos", "SOS",
        tramos(
            100 to F, 100 to 0, 100 to F, 100 to 0, 100 to F, 300 to 0,
            300 to F, 100 to 0, 300 to F, 100 to 0, 300 to F, 300 to 0,
            100 to F, 100 to 0, 100 to F, 100 to 0, 100 to F,
        ),
    ),
)
