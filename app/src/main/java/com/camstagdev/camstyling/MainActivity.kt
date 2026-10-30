package com.camstagdev.camstyling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.camstagdev.camstyling.tonos.PantallaTonos
import com.camstagdev.camstyling.ui.Modulo
import com.camstagdev.camstyling.ui.PantallaInicio
import com.camstagdev.camstyling.ui.TemaCamstyling
import com.camstagdev.camstyling.vibracion.PantallaVibracion

class MainActivity : ComponentActivity() {

    // Sube cada vez que la app vuelve al frente, para releer los permisos
    // (el usuario los da en Ajustes, fuera de la app).
    private var vecesAlFrente by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaCamstyling {
                var abierto by rememberSaveable { mutableStateOf<Modulo?>(null) }
                when (abierto) {
                    null -> PantallaInicio(alAbrir = { abierto = it })
                    Modulo.TONOS -> {
                        BackHandler { abierto = null }
                        PantallaTonos(vecesAlFrente = vecesAlFrente, alVolver = { abierto = null })
                    }
                    Modulo.VIBRACION -> {
                        BackHandler { abierto = null }
                        PantallaVibracion(vecesAlFrente = vecesAlFrente, alVolver = { abierto = null })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vecesAlFrente++
    }
}
