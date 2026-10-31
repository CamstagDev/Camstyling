package com.camstagdev.camstyling.avisos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Una notificación guardada. */
data class Guardado(val paquete: String, val titulo: String, val texto: String, val cuando: Long)

/**
 * El historial de notificaciones: una base de datos chica dentro de la app.
 * Nunca sale del teléfono (la app no tiene internet). Guarda lo que llega,
 * así queda aunque la descartes o la app la cambie después (un mensaje
 * borrado de WhatsApp, por ejemplo).
 */
class Historial(contexto: Context) : SQLiteOpenHelper(contexto.applicationContext, "historial.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE avisos (id INTEGER PRIMARY KEY, paquete TEXT, titulo TEXT, texto TEXT, cuando INTEGER)")
        db.execSQL("CREATE INDEX avisos_cuando ON avisos (cuando)")
    }

    override fun onUpgrade(db: SQLiteDatabase, antes: Int, ahora: Int) {}

    /** Anota una notificación (si no es igual a la última de la misma app) y borra lo que ya pasó de los [dias]. */
    fun anotar(paquete: String, titulo: String, texto: String, dias: Int) {
        if (titulo.isBlank() && texto.isBlank()) return
        val db = writableDatabase
        val repetida = db.rawQuery(
            "SELECT 1 FROM avisos WHERE paquete = ? AND titulo = ? AND texto = ? AND cuando > ? LIMIT 1",
            arrayOf(paquete, titulo, texto, (System.currentTimeMillis() - MINUTO).toString()),
        ).use { it.moveToFirst() }
        if (repetida) return
        db.insert("avisos", null, ContentValues().apply {
            put("paquete", paquete)
            put("titulo", titulo)
            put("texto", texto)
            put("cuando", System.currentTimeMillis())
        })
        db.delete("avisos", "cuando < ?", arrayOf((System.currentTimeMillis() - dias * DIA).toString()))
    }

    /** Lo más nuevo primero; con [busqueda], solo lo que la contiene en el título o el texto. */
    fun recientes(busqueda: String, limite: Int = 300): List<Guardado> {
        val filtro = if (busqueda.isBlank()) null else "titulo LIKE ? OR texto LIKE ?"
        val argumentos = if (busqueda.isBlank()) null else arrayOf("%$busqueda%", "%$busqueda%")
        return readableDatabase.query("avisos", arrayOf("paquete", "titulo", "texto", "cuando"), filtro, argumentos, null, null, "cuando DESC", limite.toString())
            .use { c ->
                buildList { while (c.moveToNext()) add(Guardado(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3))) }
            }
    }

    fun borrarTodo() {
        writableDatabase.delete("avisos", null, null)
    }

    private companion object {
        const val MINUTO = 60_000L
        const val DIA = 24 * 60 * 60_000L
    }
}
