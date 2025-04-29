package com.example.utasble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat

@Composable
fun StartingScreen(
    scannedDevices: List<ScanResult>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    isScanning: Boolean,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current

    // Filter the scanned devices to ensure only unique MAC addresses are shown
    val uniqueDevices = scannedDevices.distinctBy { it.device.address }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Button(
            onClick = {
                // Check Bluetooth permission before starting scan
                if (ActivityCompat.checkSelfPermission(
                        context as ComponentActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        context,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        MainActivity.REQUEST_BLUETOOTH_CONNECT
                    )
                } else {
                    // Start or stop scanning if permission is already granted
                    if (isScanning) {
                        onStopScan()
                    } else {
                        onStartScan()
                    }
                }
            }
        ) {
            Text(if (isScanning) "Stop Scan" else "Start Scan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display the list of unique scanned devices
        LazyColumn {
            items(uniqueDevices) { scanResult ->
                BLEDeviceRow(
                    device = scanResult,
                    onDeviceSelected = { selectedDevice ->
                        onDeviceSelected(selectedDevice)
                    }
                )
            }
        }
    }
}
