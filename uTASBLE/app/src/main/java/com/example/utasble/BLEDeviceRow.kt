package com.example.utasble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat

@Composable
fun BLEDeviceRow(device: ScanResult, onDeviceSelected: (BluetoothDevice) -> Unit) {
    val context = LocalContext.current
    val deviceName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        "Permission Needed"
    } else {
        device.device.name ?: "Unknown"
    }

    if (deviceName != "Unknown") { // Filter out devices with the name "Unknown"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onDeviceSelected(device.device) },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Name: $deviceName", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "MAC: ${device.device.address}")
            }
        }
    }
}
