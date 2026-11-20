package com.camstagdev.camstyling.sistema

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.camstagdev.camstyling.fondos.Rotacion
import com.camstagdev.camstyling.perfiles.Perfiles
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda todos los ajustes de Camstyling (reglas, patrones, perfiles,
 * fondos, avisos y adicionales) en un archivo, y los vuelve a poner desde
 * él. El archivo lo elige el usuario: no sale del teléfono salvo que él lo
 * mueva.
 */
object Respaldo {

    /** Los archivos de ajustes que van al respaldo. */
    private val ARCHIVOS = listOf("vibracion", "sonidos", "fondos", "perfiles", "avisos", "adicionales", "llamadas")

    fun guardar(contexto: Context, destino: Uri) {
        val archivos = JSONObject()
        ARCHIVOS.forEach { nombre ->
            val valores = JSONObject()
            contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE).all.forEach { (clave, valor) ->
                val tipo = when (valor) {
                    is String -> "texto"
                    is Boolean -> "si_no"
                    is Int -> "entero"
                    is Long -> "largo"
                    is Float -> "decimal"
                    is Set<*> -> "conjunto"
                    else -> return@forEach
                }
                val guardado = if (valor is Set<*>) JSONArray(valor.toList()) else valor
                valores.put(clave, JSONObject().put("tipo", tipo).put("valor", guardado))
            }
            archivos.put(nombre, valores)
        }
        val json = JSONObject().put("app", "Camstyling").put("version", 1).put("ajustes", archivos)
        contexto.contentResolver.openOutputStream(destino, "wt").use { salida ->
            requireNotNull(salida) { "No se pudo escribir el archivo" }.write(json.toString(2).toByteArray())
        }
    }

    /** Reemplaza los ajustes por los del archivo y vuelve a armar lo programado. */
    fun restaurar(contexto: Context, origen: Uri) {
        val texto = contexto.contentResolver.openInputStream(origen).use { entrada ->
            requireNotNull(entrada) { "No se pudo leer el archivo" }.readBytes().toString(Charsets.UTF_8)
        }
        val json = JSONObject(texto)
        require(json.optString("app") == "Camstyling") { "No es un respaldo de Camstyling" }
        val archivos = json.getJSONObject("ajustes")
        ARCHIVOS.filter { archivos.has(it) }.forEach { nombre ->
            val valores = archivos.getJSONObject(nombre)
            contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE).edit {
                clear()
                valores.keys().forEach { clave ->
                    val dato = valores.getJSONObject(clave)
                    when (dato.getString("tipo")) {
                        "texto" -> putString(clave, dato.getString("valor"))
                        "si_no" -> putBoolean(clave, dato.getBoolean("valor"))
                        "entero" -> putInt(clave, dato.getInt("valor"))
                        "largo" -> putLong(clave, dato.getLong("valor"))
                        "decimal" -> putFloat(clave, dato.getDouble("valor").toFloat())
                        "conjunto" -> {
                            val lista = dato.getJSONArray("valor")
                            putStringSet(clave, (0 until lista.length()).map { lista.getString(it) }.toSet())
                        }
                    }
                }
            }
        }
        Perfiles(contexto).aplicar(forzar = true)
        Rotacion.programar(contexto)
    }
}
