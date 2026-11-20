package com.camstagdev.camstyling.perfiles

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit
import com.camstagdev.camstyling.vibracion.Reglas
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Los perfiles de sonido y cuál manda ahora. Cuando empieza el primero se
 * guarda cómo estaba el teléfono ("normal") y, cuando ya no manda ninguno,
 * se vuelve a eso. El cambio lo hace una alarma inexacta por cada borde de
 * horario (no hay nada corriendo en medio).
 */
class Perfiles(contexto: Context) {

    private val app = contexto.applicationContext
    private val prefs = app.getSharedPreferences("perfiles", Context.MODE_PRIVATE)

    /** En orden: si dos mandan a la vez, gana el primero. */
    fun todos(): List<Perfil> {
        val json = JSONArray(prefs.getString(PERFILES, "[]"))
        return (0 until json.length()).mapNotNull { runCatching { Perfil.deJson(json.getJSONObject(it)) }.getOrNull() }
    }

    fun guardar(perfil: Perfil): Perfil {
        val conId = if (perfil.id.isNotEmpty()) perfil else perfil.copy(id = UUID.randomUUID().toString())
        val lista = todos().toMutableList()
        val i = lista.indexOfFirst { it.id == conId.id }
        if (i >= 0) lista[i] = conId else lista += conId
        escribir(lista)
        return conId
    }

    fun borrar(id: String) = escribir(todos().filter { it.id != id })

    private fun escribir(lista: List<Perfil>) = prefs.edit { putString(PERFILES, JSONArray(lista.map { it.aJson() }).toString()) }

    /**
     * El perfil que manda: el elegido a mano (mosaico, widget o un aparato
     * Bluetooth) si hay uno; si no, el del horario.
     */
    fun queManda(momento: LocalDateTime = LocalDateTime.now()): Perfil? {
        val todos = todos()
        return forzado?.let { id -> todos.firstOrNull { it.id == id } } ?: todos.firstOrNull { it.manda(momento) }
    }

    /** Un perfil elegido a mano, que manda hasta que se quite (null = según el horario). */
    var forzado: String?
        get() = prefs.getString(FORZADO, null)
        set(valor) = prefs.edit { if (valor == null) remove(FORZADO) else putString(FORZADO, valor) }

    /** Elige a mano un perfil (o vuelve al horario con null) y lo pone ya. */
    fun forzar(id: String?) {
        forzado = id
        aplicar()
    }

    /** El siguiente del ciclo "según el horario" -> cada perfil -> "según el horario" (mosaico y widget). */
    fun siguiente(): String? {
        val ids = listOf<String?>(null) + todos().map { it.id }
        return ids[(ids.indexOf(forzado).coerceAtLeast(0) + 1) % ids.size]
    }

    /**
     * Anota que ya se respondió a ese chat durante este perfil. Devuelve
     * false si ya estaba (así cada chat recibe una sola respuesta).
     */
    fun marcarRespondido(chat: String): Boolean {
        val ya = prefs.getStringSet(RESPONDIDOS, emptySet()).orEmpty()
        if (chat in ya) return false
        prefs.edit { putStringSet(RESPONDIDOS, ya + chat) }
        return true
    }

    /** El perfil que se aplicó por última vez (puede ir unos minutos atrasado respecto al reloj). */
    val aplicado: Perfil? get() = prefs.getString(APLICADO, null)?.let { id -> todos().firstOrNull { it.id == id } }

    /** Si hay horas de silencio en curso, lo que sí puede sonar; si no, null. Lo usa el servicio de notificaciones. */
    fun excepcionesEnSilencio(): List<String>? = aplicado?.takeIf { it.silencio }?.excepciones

    /**
     * Pone el perfil que manda ahora (o vuelve a lo normal) y programa el
     * próximo cambio. Con [forzar] lo vuelve a poner aunque sea el mismo
     * (después de editarlo).
     */
    fun aplicar(forzar: Boolean = false) {
        val ahora = queManda()
        val antes = prefs.getString(APLICADO, null)
        if (forzar || ahora?.id != antes) {
            if (antes == null && ahora != null) guardarNormal()
            runCatching { if (ahora != null) poner(ahora) else volverANormal() }
            prefs.edit {
                if (ahora == null) remove(APLICADO) else putString(APLICADO, ahora.id)
                // Cada perfil nuevo empieza sin chats respondidos.
                if (ahora?.id != antes) remove(RESPONDIDOS)
            }
        }
        programar()
        AtajosDePerfil.actualizar(app)
    }

    private val audio get() = app.getSystemService(AudioManager::class.java)
    private val avisos get() = app.getSystemService(NotificationManager::class.java)

    /** Sin "Acceso a No molestar" Android no deja cambiar el modo del timbre a silencio ni tocar "No molestar". */
    private val puedeNoMolestar get() = avisos.isNotificationPolicyAccessGranted

    private fun poner(perfil: Perfil) {
        // Primero los volúmenes: subir el de llamada saca al teléfono del modo vibrar.
        perfil.volumenLlamada?.let { volumen(AudioManager.STREAM_RING, it) }
        perfil.volumenNotificaciones?.let { volumen(AudioManager.STREAM_NOTIFICATION, it) }
        perfil.modo?.let { if (puedeNoMolestar || it != Modo.SILENCIO) runCatching { audio.ringerMode = it.timbre } }
        if (Settings.System.canWrite(app)) {
            perfil.tonoLlamada?.let { RingtoneManager.setActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE, Uri.parse(it)) }
            perfil.tonoNotificacion?.let { RingtoneManager.setActualDefaultRingtoneUri(app, RingtoneManager.TYPE_NOTIFICATION, Uri.parse(it)) }
        }
        perfil.vibracionLlamada?.let { Reglas(app).idDeLlamada = it }
        if (puedeNoMolestar) {
            if (perfil.silencio) {
                // "No molestar" con prioridad: alarmas y llamadas de favoritos. Las excepciones las toca
                // Camstyling como alarma (ver EscuchaNotificaciones).
                avisos.notificationPolicy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                        NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                        NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                )
                avisos.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                normal()?.let { restaurarNoMolestar(it) }
            }
        }
    }

    private fun volumen(canal: Int, porcentaje: Int) = runCatching {
        audio.setStreamVolume(canal, audio.getStreamMaxVolume(canal) * porcentaje.coerceIn(0, 100) / 100, 0)
    }

    private fun normal(): JSONObject? = prefs.getString(NORMAL, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** Anota cómo está el teléfono antes del primer perfil. */
    private fun guardarNormal() {
        val politica = runCatching { avisos.notificationPolicy }.getOrNull()
        val json = JSONObject()
            .put("timbre", audio.ringerMode)
            .put("llamada", audio.getStreamVolume(AudioManager.STREAM_RING))
            .put("notificaciones", audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
            .put("tonoLlamada", RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE)?.toString())
            .put("tonoNotificacion", RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_NOTIFICATION)?.toString())
            .put("vibracionLlamada", Reglas(app).idDeLlamada)
            .put("filtro", avisos.currentInterruptionFilter)
        if (politica != null) {
            json.put("categorias", politica.priorityCategories)
                .put("llamadas", politica.priorityCallSenders)
                .put("mensajes", politica.priorityMessageSenders)
                .put("efectos", politica.suppressedVisualEffects)
        }
        prefs.edit { putString(NORMAL, json.toString()) }
    }

    private fun volverANormal() {
        val json = normal() ?: return
        runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, json.getInt("llamada"), 0) }
        runCatching { audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, json.getInt("notificaciones"), 0) }
        val timbre = json.getInt("timbre")
        if (puedeNoMolestar || timbre != AudioManager.RINGER_MODE_SILENT) runCatching { audio.ringerMode = timbre }
        if (Settings.System.canWrite(app)) {
            RingtoneManager.setActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE, json.textoONulo("tonoLlamada")?.let(Uri::parse))
            RingtoneManager.setActualDefaultRingtoneUri(app, RingtoneManager.TYPE_NOTIFICATION, json.textoONulo("tonoNotificacion")?.let(Uri::parse))
        }
        Reglas(app).idDeLlamada = json.textoONulo("vibracionLlamada")
        if (puedeNoMolestar) restaurarNoMolestar(json)
        prefs.edit { remove(NORMAL) }
    }

    private fun restaurarNoMolestar(json: JSONObject) = runCatching {
        if (json.has("categorias")) {
            avisos.notificationPolicy = NotificationManager.Policy(
                json.getInt("categorias"), json.getInt("llamadas"), json.getInt("mensajes"), json.getInt("efectos"),
            )
        }
        avisos.setInterruptionFilter(json.optInt("filtro", NotificationManager.INTERRUPTION_FILTER_ALL))
    }

    /**
     * Arma una alarma para el próximo borde de horario de algún perfil (o la
     * quita si no hay perfiles). Es inexacta y despierta una sola vez: puede
     * llegar unos minutos tarde.
     */
    private fun programar() {
        val alarmas = app.getSystemService(AlarmManager::class.java)
        val aviso = PendingIntent.getBroadcast(
            app, 0, Intent(app, CambioDePerfil::class.java).setAction(CambioDePerfil.ACCION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val perfiles = todos()
        if (perfiles.isEmpty()) {
            alarmas.cancel(aviso)
            return
        }
        val ahora = LocalDateTime.now()
        val hoy = LocalDate.now()
        val proximo = (0..8L).flatMap { d ->
            val dia = hoy.plusDays(d)
            perfiles.flatMap { listOf(it.desde, it.hasta) }.map { dia.atStartOfDay().plusMinutes(it.toLong()) }
        }.filter { it.isAfter(ahora) }.minOrNull() ?: return
        val cuando = proximo.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, aviso)
    }

    private companion object {
        const val PERFILES = "perfiles"
        const val APLICADO = "aplicado"
        const val NORMAL = "normal"
        const val FORZADO = "forzado"
        const val RESPONDIDOS = "respondidos"
    }
}

/**
 * Despierta en cada borde de horario, al prender el teléfono y al cambiar la
 * hora: pone el perfil que manda y arma la próxima alarma.
 */
class CambioDePerfil : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intento: Intent) {
        if (intento.action !in ACCIONES) return
        Perfiles(contexto).aplicar()
    }

    companion object {
        const val ACCION = "com.camstagdev.camstyling.CAMBIO_DE_PERFIL"
        private val ACCIONES = setOf(
            ACCION,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
