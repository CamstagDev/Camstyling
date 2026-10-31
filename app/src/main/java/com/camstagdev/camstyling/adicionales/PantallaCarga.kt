package com.camstagdev.camstyling.adicionales

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La animación de carga: una batería que se llena hasta el nivel actual y
 * late mientras carga. Sale sobre la pantalla de bloqueo unos segundos y se
 * cierra sola (o al tocarla). Desbloquear sigue llevando a tu fondo normal.
 */
class PantallaCarga : ComponentActivity() {

    private val hilo = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        Carga.quitarAviso(this)
        val conectado = intent.getBooleanExtra(CONECTADO, true)
        val nivel = Carga.nivel(this)
        setContent { Animacion(conectado, nivel, alTocar = { finish() }) }
        hilo.postDelayed({ finish() }, DURACION_MS)
    }

    override fun onDestroy() {
        hilo.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val CONECTADO = "conectado"
        const val DURACION_MS = 5000L
    }
}

@Composable
private fun Animacion(conectado: Boolean, nivel: Int, alTocar: () -> Unit) {
    val llenado = remember { Animatable(0f) }
    LaunchedEffect(Unit) { llenado.animateTo(nivel / 100f, tween(1200)) }
    val latido by rememberInfiniteTransition(label = "latido").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "brillo",
    )
    val color = when {
        !conectado -> Color(0xFF9E9E9E)
        nivel <= 20 -> Color(0xFFFFB300)
        else -> Color(0xFF4CD964)
    }
    val brillo = if (conectado) latido else 1f

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = alTocar),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Canvas(Modifier.size(width = 120.dp, height = 220.dp)) {
                val grosor = 6.dp.toPx()
                val tapa = Size(size.width * 0.36f, 14.dp.toPx())
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset((size.width - tapa.width) / 2, 0f),
                    size = tapa,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                val cuerpo = Size(size.width, size.height - tapa.height - 4.dp.toPx())
                val arriba = size.height - cuerpo.height
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(0f, arriba),
                    size = cuerpo,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(grosor),
                )
                val margen = grosor * 2
                val alto = (cuerpo.height - margen * 2) * llenado.value
                drawRoundRect(
                    color = color.copy(alpha = brillo),
                    topLeft = Offset(margen, size.height - margen - alto),
                    size = Size(size.width - margen * 2, alto),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
            }
            Text("${(llenado.value * 100).toInt()} %", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
            Text(if (conectado) "Cargando" else "Desconectado", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
        }
    }
}
