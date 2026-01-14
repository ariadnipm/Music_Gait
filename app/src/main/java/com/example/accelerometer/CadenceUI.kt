package com.example.accelerometer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CadenceUI(
    modifier: Modifier = Modifier
) {
    val uiState by CadenceState.state.collectAsState()

    val cadenceHz = uiState.cadenceHz
    val isWalking = uiState.isWalking
    val spm = (cadenceHz * 60.0).roundToInt()

    // ring gauge scaling
    val maxHzForRing = 3.0
    val progress = (cadenceHz / maxHzForRing).coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xFFE0E0E0),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )

                drawArc(
                    color = if (isWalking) Color(0xFF2E7D32) else Color(0xFF757575),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.2f Hz", cadenceHz),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${spm} spm",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        WalkingBar(isWalking = isWalking)

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isWalking) "WALKING" else "NOT WALKING",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isWalking) Color(0xFF2E7D32) else Color(0xFF616161)
        )
    }
}

@Composable
private fun WalkingBar(isWalking: Boolean) {
    val bg = Color(0xFFE0E0E0)
    val fg = if (isWalking) Color(0xFF2E7D32) else Color(0xFF616161)

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(14.dp)
            .background(bg, RoundedCornerShape(999.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (isWalking) 1f else 0.25f)
                .background(fg, RoundedCornerShape(999.dp))
        )
    }
}
