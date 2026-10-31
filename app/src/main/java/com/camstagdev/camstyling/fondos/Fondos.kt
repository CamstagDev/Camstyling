package com.camstagdev.camstyling.fondos

import android.app.WallpaperManager
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import kotlin.math.max
import kotlin.math.roundToInt

/** Una imagen de una carpeta elegida: su dirección para leerla y su nombre. */
data class Imagen(val uri: Uri, val nombre: String)

/** Leer las carpetas de imágenes y poner una como fondo. */
object Fondos {

    /** Las imágenes de la carpeta (sin entrar a subcarpetas), por nombre. Vacía si ya no se puede leer. */
    fun imagenes(contexto: Context, carpeta: Uri): List<Imagen> {
        val raiz = DocumentsContract.getTreeDocumentId(carpeta)
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(carpeta, raiz)
        val columnas = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val lista = mutableListOf<Imagen>()
        runCatching {
            contexto.contentResolver.query(hijos, columnas, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(2)?.startsWith("image/") != true) continue
                    val id = c.getString(0)
                    lista += Imagen(DocumentsContract.buildDocumentUriUsingTree(carpeta, id), c.getString(1) ?: id)
                }
            }
        }
        return lista.sortedBy { it.nombre.lowercase() }
    }

    /** El nombre de la carpeta, para mostrarlo. */
    fun nombreDeCarpeta(contexto: Context, carpeta: Uri): String? = runCatching {
        val documento = DocumentsContract.buildDocumentUriUsingTree(carpeta, DocumentsContract.getTreeDocumentId(carpeta))
        contexto.contentResolver.query(documento, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /**
     * Pone la imagen de fondo. La lee achicada a lo justo para cubrir la
     * pantalla: una foto de 50 MP entera no cabe en memoria y el sistema la
     * achicaría igual.
     */
    fun poner(contexto: Context, imagen: Uri, pantalla: Pantalla) {
        val gestor = WallpaperManager.getInstance(contexto)
        val medidas = contexto.resources.displayMetrics
        val ancho = max(max(medidas.widthPixels, gestor.desiredMinimumWidth), 1)
        val alto = max(max(medidas.heightPixels, gestor.desiredMinimumHeight), 1)

        val fuente = ImageDecoder.createSource(contexto.contentResolver, imagen)
        val mapa = ImageDecoder.decodeBitmap(fuente) { decoder, info, _ ->
            // El sistema guarda el fondo comprimiéndolo: tiene que estar en memoria normal.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val escala = max(ancho.toFloat() / w, alto.toFloat() / h)
            if (escala < 1f) {
                decoder.setTargetSize((w * escala).roundToInt().coerceAtLeast(1), (h * escala).roundToInt().coerceAtLeast(1))
            }
        }
        try {
            gestor.setBitmap(mapa, null, true, pantalla.banderas)
        } finally {
            mapa.recycle()
        }
    }
}
