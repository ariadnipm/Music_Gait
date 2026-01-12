package com.example.accelerometer

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accelerometer.ui.theme.AccelerometerTheme


class MainActivity : ComponentActivity() {


    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            AccelerometerTheme  {
                Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.White) { inner ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner)
                    ) {
                        val halfHeight = maxHeight * 0.5f
                        val blue = Color(0xFFE3F2FD)

                        // Πάνω μισό
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(halfHeight)
                                .background(blue)
                                .align(Alignment.TopStart)
                        )

                        // Wave (κάθεται πάνω στο boundary)
                        Canvas (
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .align(Alignment.TopStart)
                                .offset(y = halfHeight - 60.dp)
                        ) {
                            val w = size.width
                            val h = size.height

                            val path = Path().apply {
                                // ξεκινάμε από πάνω-αριστερά
                                moveTo(0f, 0f)

                                // κατεβαίνουμε κοντά στο κύμα
                                lineTo(0f, h * 0.55f)

                                // κύμα (2 “λοφάκια”)
                                cubicTo(
                                    w * 0.2f, h * 0.95f,
                                    w * 0.25f, h * 0.1f,
                                    w * 0.50f, h * 0.55f
                                )
                                cubicTo(
                                    w * 0.75f, h * 0.95f,
                                    w * 0.75f, h * 0.15f,
                                    w,        h * 0.55f
                                )

                                // κλείσιμο προς πάνω-δεξιά
                                lineTo(w, 0f)
                                close()
                            }

                            drawPath(path = path, color = blue)
                        }


                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal =  24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {

                            Button(
                                onClick = { startServiceSafe() },
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(50.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = blue )

                            ) {
                                Text("Start", fontSize = 22.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { stopServiceSafe() },
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(50.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = blue
                                    )
                            ) {
                                Text("Stop", fontSize = 22.sp)
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                        }
                    }






                }
            }
        }
    }

    private fun startServiceSafe() {
        val intent = Intent(applicationContext, RunningService::class.java)
            .apply { action = RunningService.Actions.START.toString() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopServiceSafe() {
        val intent = Intent(applicationContext, RunningService::class.java)
            .apply { action = RunningService.Actions.STOP.toString() }
        startService(intent)
    }
}
