package com.example.utasble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.UUID



class MainActivity : ComponentActivity() {
    private val scannedDevices = mutableStateListOf<ScanResult>()
    private val isScanning = mutableStateOf(false)
    private var bluetoothGatt: BluetoothGatt? = null // Store the BluetoothGatt instance here
    private val batteryLevel = mutableStateOf(100)
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private val targetCharacteristicData = mutableStateOf("No data")

    companion object {
        const val REQUEST_BLUETOOTH_CONNECT = 1001
        val TARGET_SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val TARGET_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a7")
        val CONTROL_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val BATTERY_CHARACTERISTIC_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BluetoothUtils.initializeBluetooth(this)

        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "starting_screen") {
                composable(route = "starting_screen") {
                    StartingScreen(
                        scannedDevices = scannedDevices.toList(),
                        onStartScan = { requestBluetoothPermissionOrStartScan() },
                        onStopScan = { stopScanAndClear() },
                        isScanning = isScanning.value,
                        onDeviceSelected = { device ->
                            stopScanAndClear()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                                ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                ActivityCompat.requestPermissions(
                                    this@MainActivity,
                                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                                    REQUEST_BLUETOOTH_CONNECT
                                )
                            } else {
                                connectToDevice(device, navController) // Pass navController here
                            }
                        }
                    )
                }

                composable(route = "device_connected_screen/{deviceName}") { backStackEntry ->
                    val deviceName = backStackEntry.arguments?.getString("deviceName") ?: "Unknown"
                    DeviceConnectedScreen(
                        deviceName = deviceName,
                        batteryLevel = batteryLevel.value.toString(),
                        targetCharacteristicValue = targetCharacteristicData.value,
                        bluetoothGatt = bluetoothGatt,
                    )
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, navController: NavController) {
        // Stop the scan to avoid multiple devices trying to connect at once
        stopScanAndClear()
        clearGatt() // Ensure previous GATT instance is cleaned up
        bluetoothGatt = device.connectGatt(this, false,
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                runOnUiThread {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            gatt?.discoverServices()
                            Toast.makeText(this@MainActivity, "Successfully connected to device", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothGatt.STATE_DISCONNECTED -> {
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                Toast.makeText(this@MainActivity, "Failed to connect to device", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Disconnected from device", Toast.LENGTH_SHORT).show()
                            }
                            gatt?.close() // Ensure GATT is properly closed when disconnected
                            bluetoothGatt = null // Clear the BluetoothGatt reference
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            private fun setCharacteristicNotificationSequentially(
                gatt: BluetoothGatt,
                characteristics: List<BluetoothGattCharacteristic>
            ) {
                characteristics.forEachIndexed { index, characteristic ->
                    val delayMillis = index * 200L // Delay to avoid overlapping descriptor writes
                    window.decorView.postDelayed({
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                            println("Attempting to write descriptor for ${characteristic.uuid}")
                        }
                    }, delayMillis)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                runOnUiThread {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        var targetServiceFound = false
                        val characteristicsToSubscribe = mutableListOf<BluetoothGattCharacteristic>()

                        gatt?.services?.forEach { service ->
                            handleService(service)
                            if (service.uuid == TARGET_SERVICE_UUID) {
                                targetServiceFound = true
                            }

                            service.characteristics.forEach { characteristic ->
                                if (characteristic.uuid == TARGET_CHARACTERISTIC_UUID) {
                                    // Add target characteristic to list for subscription
                                    characteristicsToSubscribe.add(characteristic)
                                    targetCharacteristic = characteristic
                                }

                                if (characteristic.uuid == BATTERY_CHARACTERISTIC_UUID) {
                                    // Add battery characteristic to list for subscription
                                    characteristicsToSubscribe.add(characteristic)
                                }
                            }
                        }

                        if (targetServiceFound) {
                            gatt?.let {
                                bluetoothGatt = it
                                setCharacteristicNotificationSequentially(it, characteristicsToSubscribe)
                                val deviceName = it.device.name ?: "Unknown"
                                navController.navigate(route = "device_connected_screen/$deviceName")
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Connected to an incorrect device, disconnecting...", Toast.LENGTH_SHORT).show()
                            gatt?.disconnect()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to discover services", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                super.onCharacteristicChanged(gatt, characteristic)

                if (characteristic?.uuid == BATTERY_CHARACTERISTIC_UUID) {
                    val newBatteryLevel = characteristic?.value?.get(0)?.toInt() ?: 0
                    batteryLevel.value = newBatteryLevel // Update the state
                }
                if (characteristic?.uuid == TARGET_CHARACTERISTIC_UUID) {
                    val receivedData = characteristic?.value
                    val parsedData = receivedData?.let { parseTargetCharacteristicData(it) } ?: "No data"

                    // Update the UI with the new data
                    runOnUiThread {
                        targetCharacteristicData.value = parsedData
                    }
                }
            }
        })
    }


    private fun handleService(service: BluetoothGattService) {
        println("Discovered service: ${service.uuid}")
    }

    private fun requestBluetoothPermissionOrStartScan() {
        val requiredPermissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_BLUETOOTH_CONNECT)
        } else {
            startScanWithTimeout()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanWithTimeout()
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth permission is required to scan devices",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startScanWithTimeout() {
        isScanning.value = true
        scannedDevices.clear()

        BluetoothUtils.startScan(this@MainActivity) { result ->
            scannedDevices.add(result)
        }

        val timeoutMillis = 10000L
        window.decorView.postDelayed({
            stopScanAndClear()
        }, timeoutMillis)
    }

    private fun stopScanAndClear() {
        isScanning.value = false
        BluetoothUtils.stopScan(this)
        BluetoothUtils.clearScannedDevices()
    }

    private fun parseTargetCharacteristicData(data: ByteArray): String {
        if (data.size < 8) return "Invalid data"

        val pressure = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)

        val accelX = decodeAcceleration(data[3].toInt())
        val accelY = decodeAcceleration(data[4].toInt())
        val accelZ = decodeAcceleration(data[5].toInt())
        val wasShaken = data[6].toInt()
        val isTouching = data[7].toInt()
        return "Pressure: ${pressure}mBar, Accel X: %.2fg, Accel Y: %.2fg, Accel Z: %.2fg, Shaken: $wasShaken, Touching: $isTouching".format(accelX, accelY, accelZ)
    }

    private fun decodeAcceleration(rawValue: Int): Float {
        // Treat rawValue as unsigned byte (0 to 255)
        val unsignedValue = rawValue and 0xFF

        // Normalize the unsigned byte value to a range of -2g to +2g
        val normalizedValue = (unsignedValue - 127) / 127.0
        val gValue = normalizedValue * 2.0

        return gValue.toFloat()
    }

    private fun clearGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

}
