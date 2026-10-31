package com.camstagdev.camstyling.avisos

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.camstagdev.camstyling.adicionales.Carga
import com.camstagdev.camstyling.perfiles.Perfiles
import com.camstagdev.camstyling.tonos.DatosDeAviso
import com.camstagdev.camstyling.tonos.ReglasDeSonido
import com.camstagdev.camstyling.tonos.Reproductor
import com.camstagdev.camstyling.tonos.Vistos
import com.camstagdev.camstyling.vibracion.Reglas
import com.camstagdev.camstyling.vibracion.Vibrador
import java.util.concurrent.Executors

/**
 * El sistema la despierta solo cuando llega una notificación; no corre en
 * segundo plano el resto del tiempo, así que no gasta batería.
 *
 * Toca el sonido de la regla que coincida (módulo Tonos: por app, categoría,
 * persona o grupo, o palabra) y vibra con el patrón de la app (módulo
 * Vibración). También anota las categorías y los chats que ve, para
 * ofrecerlos al crear una regla.
 */
class EscuchaNotificaciones : NotificationListenerService() {

    /** Última vez que avisó cada app: un mensaje suele llegar como 2 o 3 notificaciones juntas. */
    private val ultimaVez = mutableMapOf<String, Long>()

    /** Notificaciones que ya avisaron y piden avisar una sola vez (FLAG_ONLY_ALERT_ONCE). */
    private val yaAvisadas = mutableSetOf<String>()

    private val historial by lazy { Historial(this) }
    private val trabajo = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(aviso: StatusBarNotification, clasificacion: RankingMap?) {
        if (aviso.packageName == packageName) return
        val notificacion = aviso.notification

        // Lo que no avisa: lo fijo (música, descargas) y los resúmenes de grupo.
        if (aviso.isOngoing) return
        if (notificacion.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val ranking = Ranking().takeIf { clasificacion?.getRanking(aviso.key, it) == true }
        val datos = DatosDeAviso.de(aviso)
        Vistos(this).anotar(aviso.packageName, ranking?.channel, datos.chats)
        val ajustes = AjustesAvisos(this)
        if (ajustes.historial) guardarEnHistorial(aviso, ajustes.diasDeHistorial)

        val sonido = ReglasDeSonido(this).buscar(datos)
        val patron = Reglas(this).patronDeApp(aviso.packageName)

        // Horas de silencio (módulo Perfiles): "No molestar" calla todo y aquí solo pasan las excepciones.
        val excepciones = Perfiles(this).excepcionesEnSilencio()
        val esExcepcion = excepciones != null && excepciones.any { datos.coincideCon(it) }
        if (excepciones != null && !esExcepcion) return

        // Módulo Avisos: recordar si sigue sin abrir y parpadear la linterna.
        val recordar = ajustes.recordatorio && ajustes.elegidosRecordatorio.any { datos.coincideCon(it) }
        val linterna = ajustes.linterna && ajustes.elegidosLinterna.any { datos.coincideCon(it) } &&
            (!ajustes.linternaSoloApagada || !getSystemService(PowerManager::class.java).isInteractive)
        if (sonido == null && patron == null && !esExcepcion && !recordar && !linterna) return

        // Las actualizaciones silenciosas (el mismo aviso que cambia) avisan una sola vez.
        if (notificacion.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && !yaAvisadas.add(aviso.key)) return

        // Lo que el sistema tampoco dejaría sonar: canal silencioso, "No molestar", modo silencio.
        if (ranking != null) {
            if (!esExcepcion && !ranking.matchesInterruptionFilter()) return
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        }
        val modo = getSystemService(AudioManager::class.java).ringerMode
        if (modo == AudioManager.RINGER_MODE_SILENT && !esExcepcion) return

        val ahora = System.currentTimeMillis()
        if (ahora - (ultimaVez[aviso.packageName] ?: 0L) < SEPARACION_MS) return
        ultimaVez[aviso.packageName] = ahora

        if (recordar) Recordatorio.programar(this, aviso.key)
        if (linterna) Linterna.parpadear(this)

        if (esExcepcion) {
            // Suena como alarma para pasar "No molestar": con su regla o con el sonido de notificación del teléfono.
            val tono = sonido?.let { Uri.parse(it.tono) }
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
            if (tono != null) runCatching { Reproductor.tocar(this, tono, comoAlarma = true) }
            if (patron != null) Vibrador.vibrar(this, patron, Vibrador.Uso.NOTIFICACION)
            return
        }

        // En modo vibración no suena, pero sí vibra.
        if (sonido != null && modo == AudioManager.RINGER_MODE_NORMAL) {
            runCatching { Reproductor.tocar(this, Uri.parse(sonido.tono)) }
        }
        if (patron != null) Vibrador.vibrar(this, patron, Vibrador.Uso.NOTIFICACION)
    }

    /** Fuera del hilo principal: escribir en la base de datos puede tardar unos milisegundos. */
    private fun guardarEnHistorial(aviso: StatusBarNotification, dias: Int) {
        val extras = aviso.notification.extras
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val texto = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val paquete = aviso.packageName
        trabajo.execute { runCatching { historial.anotar(paquete, titulo, texto, dias) } }
    }

    override fun onNotificationRemoved(aviso: StatusBarNotification) {
        yaAvisadas.remove(aviso.key)
        Recordatorio.cancelar(this, aviso.key)
    }

    override fun onListenerConnected() {
        activa = this
        Carga.registrar(this)
    }

    override fun onDestroy() {
        activa = null
        Carga.desregistrar(this)
        historial.close()
        trabajo.shutdown()
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        activa = null
        Carga.desregistrar(this)
        // Si el sistema la desconecta (p. ej. al actualizar la app), pedir que la vuelva a conectar.
        requestRebind(ComponentName(this, EscuchaNotificaciones::class.java))
    }

    companion object {
        private const val SEPARACION_MS = 1500L

        /** El servicio conectado, para que el recordatorio vea si la notificación sigue en la barra. */
        @Volatile
        var activa: EscuchaNotificaciones? = null
            private set
    }
}
