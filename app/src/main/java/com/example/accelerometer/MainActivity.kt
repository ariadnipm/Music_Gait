package com.example.accelerometer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accelerometer.ui.theme.AccelerometerTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            if (!granted) notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AccelerometerTheme {
                val uiState by CadenceState.state.collectAsState()
                val uiRunning = uiState.isRunning

                var lastClickMs by remember { mutableLongStateOf(0L) }

                val blue = Color(0xCC7D88F3)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.White
                ) { inner ->

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F7FB)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 26.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CadenceUI()
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            enabled = !uiRunning,
                            onClick = {
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastClickMs < 600L) return@Button
                                lastClickMs = now

                                CadenceState.setRunning(true)
                                startServiceSafe()
                              /*  val recIntent = Intent(applicationContext, RunningService::class.java).apply {
                                    action = RunningService.Actions.RECORD_START.toString()
                                    putExtra("label", "test")
                                }
                                startService(recIntent)
                                */
                            },
                            modifier = Modifier
                                .width(220.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = blue)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start", fontSize = 20.sp)
                        }

                        Spacer(Modifier.height(12.dp))

                        if (uiRunning) {
                            FilledTonalButton(
                                onClick = {
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastClickMs < 600L) return@FilledTonalButton
                                    lastClickMs = now

                                    CadenceState.setRunning(false)
                                    stopServiceSafe()
                                   /* val recStop = Intent(applicationContext, RunningService::class.java).apply {
                                        action = RunningService.Actions.RECORD_STOP.toString()
                                    }
                                    startService(recStop) */
                                },
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Stop", fontSize = 20.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    disabledContainerColor = Color.Transparent,
                                    disabledContentColor = Color(0xFFB0B6BF)
                                )
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Stop", fontSize = 20.sp)
                            }
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
        lifecycleScope.launch {
            delay(100L) // mini delay
            val intent = Intent(applicationContext, RunningService::class.java)
                .apply { action = RunningService.Actions.STOP.toString() }
            startService(intent)
        }
    }
}

