package com.example.accelerometer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.accelerometer.ui.theme.AccelerometerTheme // <- βάλε το δικό σου theme (ή άλλαξέ το)

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
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Column(
                        modifier = Modifier
                            .padding(inner)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Button(onClick = { startServiceSafe() }) {
                            Text("Start")
                        }

                        Button(onClick = { stopServiceSafe() }) {
                            Text("Stop")
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
