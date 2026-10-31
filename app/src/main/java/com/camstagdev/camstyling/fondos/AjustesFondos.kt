package com.camstagdev.camstyling.fondos

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit

/** Dónde se pone el fondo. */
enum class Pantalla(val texto: String, val banderas: Int) {
    INICIO("Inicio", WallpaperManager.FLAG_SYSTEM),
    BLOQUEO("Bloqueo", WallpaperManager.FLAG_LOCK),
    LAS_DOS("Las dos", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

/** En qué orden se recorren las imágenes de una carpeta. */
enum class Orden(val texto: String) { AL_AZAR("Al azar"), EN_ORDEN("En orden") }

/** De día se usa la carpeta principal; de noche, la de noche (si está prendida). */
enum class Franja { DIA, NOCHE }

/** Cada cuánto se cambia, en horas. */
val INTERVALOS = listOf(1, 3, 6, 12, 24)

fun textoDeIntervalo(horas: Int): String = when (horas) {
    1 -> "Cada hora"
    24 -> "Cada día"
    else -> "Cada $horas horas"
}

/**
 * Lo que eligió el usuario para los fondos y por dónde va la rotación. Se
 * guarda en el teléfono (SharedPreferences); la pantalla y el trabajo de
 * fondo leen lo mismo.
 */
class AjustesFondos(contexto: Context) {

    private val app = contexto.applicationContext
    private val prefs = app.getSharedPreferences("fondos", Context.MODE_PRIVATE)

    var pantalla: Pantalla
        get() = Pantalla.entries.firstOrNull { it.name == prefs.getString(PANTALLA, null) } ?: Pantalla.LAS_DOS
        set(valor) = prefs.edit { putString(PANTALLA, valor.name) }

    /** Si el cambio automático está prendido. */
    var activo: Boolean
        get() = prefs.getBoolean(ACTIVO, false)
        set(valor) = prefs.edit { putBoolean(ACTIVO, valor) }

    var carpeta: Uri?
        get() = prefs.getString(CARPETA, null)?.let(Uri::parse)
        set(valor) = cambiarCarpeta(CARPETA, valor)

    var orden: Orden
        get() = Orden.entries.firstOrNull { it.name == prefs.getString(ORDEN, null) } ?: Orden.AL_AZAR
        set(valor) = prefs.edit { putString(ORDEN, valor.name) }

    var horas: Int
        get() = prefs.getInt(HORAS, 24).takeIf { it in INTERVALOS } ?: 24
        set(valor) = prefs.edit { putInt(HORAS, valor) }

    var noche: Boolean
        get() = prefs.getBoolean(NOCHE, false)
        set(valor) = prefs.edit { putBoolean(NOCHE, valor) }

    var carpetaNoche: Uri?
        get() = prefs.getString(CARPETA_NOCHE, null)?.let(Uri::parse)
        set(valor) = cambiarCarpeta(CARPETA_NOCHE, valor)

    /** Minutos desde la medianoche en que empieza y termina la noche. */
    var desde: Int
        get() = prefs.getInt(DESDE, 20 * 60)
        set(valor) = prefs.edit { putInt(DESDE, valor) }

    var hasta: Int
        get() = prefs.getInt(HASTA, 7 * 60)
        set(valor) = prefs.edit { putInt(HASTA, valor) }

    /** La noche cuenta solo si está prendida y tiene su carpeta. */
    val usaNoche: Boolean get() = noche && carpetaNoche != null

    fun carpetaDe(franja: Franja): Uri? = if (franja == Franja.NOCHE) carpetaNoche else carpeta

    /** Lo último que puso la rotación: a qué hora, en qué franja y qué imagen de cada carpeta. */
    var ultimoCambio: Long
        get() = prefs.getLong(ULTIMO_CAMBIO, 0)
        set(valor) = prefs.edit { putLong(ULTIMO_CAMBIO, valor) }

    var ultimaFranja: Franja?
        get() = Franja.entries.firstOrNull { it.name == prefs.getString(ULTIMA_FRANJA, null) }
        set(valor) = prefs.edit { if (valor == null) remove(ULTIMA_FRANJA) else putString(ULTIMA_FRANJA, valor.name) }

    fun ultimaImagen(franja: Franja): String? = prefs.getString(ULTIMA_IMAGEN + franja.name, null)

    fun ponerUltimaImagen(franja: Franja, nombre: String) = prefs.edit { putString(ULTIMA_IMAGEN + franja.name, nombre) }

    /**
     * Guarda la carpeta y se queda con el permiso para leerla después (también
     * tras reiniciar). Suelta el de la anterior, si ya nadie la usa.
     */
    private fun cambiarCarpeta(clave: String, nueva: Uri?) {
        val anterior = prefs.getString(clave, null)?.let(Uri::parse)
        val resolver = app.contentResolver
        if (nueva != null) {
            resolver.takePersistableUriPermission(nueva, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.edit {
            if (nueva == null) remove(clave) else putString(clave, nueva.toString())
            remove(ULTIMA_IMAGEN + (if (clave == CARPETA) Franja.DIA else Franja.NOCHE).name)
        }
        if (anterior != null && anterior != nueva && anterior != carpeta && anterior != carpetaNoche) {
            runCatching { resolver.releasePersistableUriPermission(anterior, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    private companion object {
        const val PANTALLA = "pantalla"
        const val ACTIVO = "activo"
        const val CARPETA = "carpeta"
        const val ORDEN = "orden"
        const val HORAS = "horas"
        const val NOCHE = "noche"
        const val CARPETA_NOCHE = "carpeta_noche"
        const val DESDE = "desde"
        const val HASTA = "hasta"
        const val ULTIMO_CAMBIO = "ultimo_cambio"
        const val ULTIMA_FRANJA = "ultima_franja"
        const val ULTIMA_IMAGEN = "ultima_imagen:"
    }
}
