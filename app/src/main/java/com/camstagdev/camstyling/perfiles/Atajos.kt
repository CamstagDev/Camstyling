package com.camstagdev.camstyling.perfiles

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.camstagdev.camstyling.R

/**
 * El mosaico de ajustes rápidos y el widget: cada toque pasa al siguiente
 * perfil ("Según horario" -> cada perfil -> "Según horario"). No corren
 * nada en segundo plano: se actualizan cuando cambia el perfil.
 */
object AtajosDePerfil {

    fun texto(contexto: Context): String {
        val perfiles = Perfiles(contexto)
        val forzado = perfiles.forzado?.let { id -> perfiles.todos().firstOrNull { it.id == id } }
        return forzado?.nombre ?: (perfiles.queManda()?.nombre?.let { "$it (horario)" } ?: "Según horario")
    }

    fun cambiar(contexto: Context) {
        val perfiles = Perfiles(contexto)
        perfiles.forzar(perfiles.siguiente())
    }

    /** Lo llama Perfiles cada vez que aplica un perfil. */
    fun actualizar(contexto: Context) {
        runCatching { TileService.requestListeningState(contexto, ComponentName(contexto, MosaicoPerfil::class.java)) }
        val gestor = AppWidgetManager.getInstance(contexto)
        val ids = gestor.getAppWidgetIds(ComponentName(contexto, WidgetPerfil::class.java))
        if (ids.isNotEmpty()) WidgetPerfil.dibujar(contexto, gestor, ids)
    }
}

class MosaicoPerfil : TileService() {

    override fun onStartListening() {
        val mosaico = qsTile ?: return
        mosaico.label = "Perfil"
        mosaico.subtitle = AtajosDePerfil.texto(this)
        mosaico.state = if (Perfiles(this).forzado != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        mosaico.icon = Icon.createWithResource(this, R.drawable.ic_perfil)
        mosaico.updateTile()
    }

    override fun onClick() {
        AtajosDePerfil.cambiar(this)
        onStartListening()
    }
}

class WidgetPerfil : AppWidgetProvider() {

    override fun onUpdate(contexto: Context, gestor: AppWidgetManager, ids: IntArray) = dibujar(contexto, gestor, ids)

    override fun onReceive(contexto: Context, intento: Intent) {
        if (intento.action == CAMBIAR) AtajosDePerfil.cambiar(contexto) else super.onReceive(contexto, intento)
    }

    companion object {
        private const val CAMBIAR = "com.camstagdev.camstyling.CAMBIAR_PERFIL"

        fun dibujar(contexto: Context, gestor: AppWidgetManager, ids: IntArray) {
            val toque = PendingIntent.getBroadcast(
                contexto, 0,
                Intent(contexto, WidgetPerfil::class.java).setAction(CAMBIAR),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val vista = RemoteViews(contexto.packageName, R.layout.widget_perfil).apply {
                setTextViewText(R.id.perfil, AtajosDePerfil.texto(contexto))
                setOnClickPendingIntent(R.id.raiz, toque)
            }
            gestor.updateAppWidget(ids, vista)
        }
    }
}
