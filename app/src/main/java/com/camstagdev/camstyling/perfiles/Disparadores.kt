package com.camstagdev.camstyling.perfiles

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Qué pasa al conectar un aparato: unos audífonos o el auto por Bluetooth, o
 * unos audífonos con cable ([CABLE]). Puede poner un perfil (que manda hasta
 * desconectarlo) y el volumen de la música.
 */
data class Disparador(val aparato: String, val nombre: String, val perfil: String?, val volumenMusica: Int?) {

    fun aJson(): JSONObject = JSONObject()
        .put("aparato", aparato).put("nombre", nombre).put("perfil", perfil).put("volumenMusica", volumenMusica)

    companion object {
        const val CABLE = "cable"

        fun deJson(j: JSONObject) = Disparador(
            aparato = j.getString("aparato"),
            nombre = j.optString("nombre"),
            perfil = j.optString("perfil").takeIf { it.isNotEmpty() && !j.isNull("perfil") },
            volumenMusica = if (j.isNull("volumenMusica") || !j.has("volumenMusica")) null else j.getInt("volumenMusica"),
        )
    }
}

class Disparadores(contexto: Context) {

    private val app = contexto.applicationContext
    private val prefs = app.getSharedPreferences("disparadores", Context.MODE_PRIVATE)

    fun todos(): List<Disparador> {
        val json = JSONArray(prefs.getString(LISTA, "[]"))
        return (0 until json.length()).mapNotNull { runCatching { Disparador.deJson(json.getJSONObject(it)) }.getOrNull() }
    }

    fun guardar(d: Disparador) = escribir(todos().filter { it.aparato != d.aparato } + d)

    fun borrar(aparato: String) = escribir(todos().filter { it.aparato != aparato })

    private fun escribir(lista: List<Disparador>) = prefs.edit { putString(LISTA, JSONArray(lista.map { it.aJson() }).toString()) }

    /** El aparato que puso el perfil elegido a mano, para quitarlo al desconectarlo. */
    var puestoPor: String?
        get() = prefs.getString(PUESTO_POR, null)
        set(valor) = prefs.edit { if (valor == null) remove(PUESTO_POR) else putString(PUESTO_POR, valor) }

    fun alConectar(aparato: String) {
        val d = todos().firstOrNull { it.aparato == aparato } ?: return
        d.volumenMusica?.let { porcentaje ->
            val audio = app.getSystemService(AudioManager::class.java)
            val maximo = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, maximo * porcentaje.coerceIn(0, 100) / 100, 0) }
        }
        d.perfil?.let { id ->
            val perfiles = Perfiles(app)
            if (perfiles.todos().any { it.id == id }) {
                puestoPor = aparato
                perfiles.forzar(id)
            }
        }
    }

    fun alDesconectar(aparato: String) {
        if (puestoPor != aparato) return
        puestoPor = null
        val perfiles = Perfiles(app)
        // Solo si nadie lo cambió a mano mientras tanto.
        if (perfiles.forzado == todos().firstOrNull { it.aparato == aparato }?.perfil) perfiles.forzar(null)
    }

    private companion object {
        const val LISTA = "lista"
        const val PUESTO_POR = "puesto_por"
    }
}

/**
 * Escucha las conexiones. Se registra mientras corre el servicio de
 * notificaciones; Android solo la despierta al conectar o desconectar algo.
 */
class Conexiones : BroadcastReceiver() {

    // Sin el permiso, Android no manda los avisos de Bluetooth: si llegan, lo tiene.
    @SuppressLint("MissingPermission")
    override fun onReceive(contexto: Context, intento: Intent) {
        if (isInitialStickyBroadcast) return
        val disparadores = Disparadores(contexto)
        when (intento.action) {
            AudioManager.ACTION_HEADSET_PLUG -> {
                if (intento.getIntExtra("state", 0) == 1) disparadores.alConectar(Disparador.CABLE)
                else disparadores.alDesconectar(Disparador.CABLE)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val aparato = aparatoDe(intento)?.address ?: return
                if (intento.action == BluetoothDevice.ACTION_ACL_CONNECTED) disparadores.alConectar(aparato)
                else disparadores.alDesconectar(aparato)
            }
        }
    }

    private fun aparatoDe(intento: Intent): BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
        intento.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        intento.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    companion object {
        private var registrado: Conexiones? = null

        fun registrar(contexto: Context) {
            if (registrado != null) return
            val filtro = IntentFilter().apply {
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            val receptor = Conexiones()
            // Son avisos del sistema: no hace falta exportarlo.
            runCatching { ContextCompat.registerReceiver(contexto, receptor, filtro, ContextCompat.RECEIVER_NOT_EXPORTED) }
                .onSuccess { registrado = receptor }
        }

        fun desregistrar(contexto: Context) {
            registrado?.let { runCatching { contexto.unregisterReceiver(it) } }
            registrado = null
        }

        fun puedeVerBluetooth(contexto: Context) =
            contexto.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        /** Los aparatos Bluetooth vinculados: dirección -> nombre. */
        @SuppressLint("MissingPermission")
        fun vinculados(contexto: Context): List<Pair<String, String>> {
            if (!puedeVerBluetooth(contexto)) return emptyList()
            val adaptador = contexto.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
            return runCatching { adaptador.bondedDevices.map { it.address to (it.name ?: it.address) } }
                .getOrDefault(emptyList()).sortedBy { it.second.lowercase() }
        }
    }
}
