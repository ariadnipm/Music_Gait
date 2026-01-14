package com.example.accelerometer

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
                        val blue = Color(0xCC7D88F3)
                        var uiRunning by remember { mutableStateOf(false) }
                        var lastClickMs by remember { mutableLongStateOf(0L) }




                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal =  24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CadenceUI(
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            Button(
                                enabled = !uiRunning,
                                onClick = { val now = SystemClock.elapsedRealtime()
                                    if (now - lastClickMs < 600L) return@Button
                                    lastClickMs = now

                                    uiRunning = true
                                    CadenceState.setRunning(true)
                                    startServiceSafe() },
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(50.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = blue )

                            ) {
                                Text("Start", fontSize = 24.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                enabled = uiRunning,
                                onClick = {
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastClickMs < 600L) return@Button
                                    lastClickMs = now

                                    uiRunning = false
                                    CadenceState.setRunning(false)
                                    stopServiceSafe() },
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(50.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = blue
                                    )
                            ) {
                                Text("Stop", fontSize = 24.sp)
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
