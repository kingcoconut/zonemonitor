package com.example.zonemonitor.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.data.*
import androidx.health.services.client.MeasureCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text

class MainActivity : ComponentActivity() {

    private lateinit var healthServicesClient: HealthServicesClient
    private var currentHeartRate by mutableStateOf(0.0)
    private lateinit var vibrator: Vibrator
    private var isVibrating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthServicesClient = HealthServices.getClient(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        setContent {
            MainScreen()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                PERMISSION_REQUEST_CODE
            )
        } else {
            startHeartRateMonitoring()
        }
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Current HR: ${currentHeartRate.toInt()} bpm")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { startHeartRateMonitoring() }) {
                Text("Refresh Heart Rate")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Vibration: ${if (isVibrating) "ON" else "OFF"}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startHeartRateMonitoring()
            } else {
                Log.d(TAG, "Body sensors permission not granted")
            }
        }
    }

    private fun startHeartRateMonitoring() {
        lifecycleScope.launch {
            try {
                val measureClient = healthServicesClient.measureClient
                measureClient.registerMeasureCallback(
                    DataType.HEART_RATE_BPM,
                    object : MeasureCallback {
                        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                            Log.d(TAG, "Availability changed for $dataType: $availability")
                        }

                        override fun onDataReceived(data: DataPointContainer) {
                            val heartRateSample = data.getData(DataType.HEART_RATE_BPM)
                            heartRateSample?.let {
                                if (it is SampleDataPoint<*>) {
                                    val heartRate = (it.value as Double)
                                    currentHeartRate = heartRate
                                    Log.d(TAG, "Heart Rate: $heartRate bpm")
                                    checkHeartRateAndVibrate(heartRate)
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error registering for heart rate data: ${e.message}")
            }
        }
    }

    private fun checkHeartRateAndVibrate(heartRate: Double) {
        if (heartRate < 80 && !isVibrating) {
            startVibration()
        } else if (heartRate >= 80 && isVibrating) {
            stopVibration()
        }
    }

    private fun startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 1000), 0)
        }
        isVibrating = true
    }

    private fun stopVibration() {
        vibrator.cancel()
        isVibrating = false
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1
    }
}
