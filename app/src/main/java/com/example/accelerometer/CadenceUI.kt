package com.example.accelerometer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Surface
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
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon

@Composable
fun CadenceUI(
    modifier: Modifier = Modifier
) {
    val uiState by CadenceState.state.collectAsState()

    val cadenceHz = uiState.cadenceHz
    val isWalking = uiState.isWalking
    val spm = (cadenceHz * 60.0).roundToInt()


    val maxHzForRing = 3.0
    val targetProgress = (cadenceHz / maxHzForRing).coerceIn(0.0, 1.0).toFloat()
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 350),
        label = "ringProgress"
    )

    val mint = Color(0xFF34C759)
    val idleRing = Color(0xFFB9BEC6)
    val baseRing = Color(0xFFE9EBEF)

    val ringColor by animateColorAsState(
        targetValue = if (isWalking) mint else idleRing,
        animationSpec = tween(durationMillis = 250),
        label = "ringColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // base ring
                drawArc(
                    color = baseRing,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )

                // glow behind progress
                if (isWalking && progress > 0f) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.22f),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // progress ring
                drawArc(
                    color = ringColor,
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
                    text = "${spm} steps/minute",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )

                Spacer(Modifier.height(16.dp))

                Icon(
                    imageVector = Icons.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = if (isWalking) Color(0xFF34C759) else Color(0xFFB0B0B0),
                    modifier = Modifier.size(50.dp)
                )
            }

        }

        Spacer(Modifier.height(20.dp))

        WalkingBar(isWalking = isWalking)

        Spacer(Modifier.height(14.dp))


        StatusPill(isWalking = isWalking)

        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun WalkingBar(isWalking: Boolean) {
    val fillTarget = if (isWalking) 1f else 0.25f
    val fill by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 300),
        label = "barFill"
    )

    val mint = Color(0xFF34C759)
    val bg = Color(0xFFE9EBEF)
    val fg = if (isWalking) mint else Color(0xFF7A808A)

    Surface(
        modifier = Modifier
            .width(240.dp)
            .height(12.dp), // λίγο πιο λεπτή μπάρα
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .background(fg, RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun StatusPill(isWalking: Boolean) {
    val mint = Color(0xFF34C759)

    val bg by animateColorAsState(
        targetValue = if (isWalking) mint.copy(alpha = 0.14f) else Color(0x0F111827),
        animationSpec = tween(200),
        label = "statusBg"
    )
    val border by animateColorAsState(
        targetValue = if (isWalking) mint.copy(alpha = 0.35f) else Color(0x25111827),
        animationSpec = tween(200),
        label = "statusBorder"
    )
    val text by animateColorAsState(
        targetValue = if (isWalking) mint else Color(0xFF6B7280),
        animationSpec = tween(200),
        label = "statusText"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Text(
            text = if (isWalking) "WALKING" else "NOT WALKING",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = text
        )
    }
}
