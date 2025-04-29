package com.example.utasble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateListOf
import android.util.Log

object BluetoothUtils {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val scannedDevices = mutableStateListOf<ScanResult>()

    // Make ScanCallback a strong reference to avoid getting lost
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Prevent adding duplicate devices
            if (scannedDevices.none { it.device.address == result.device.address }) {
                scannedDevices.add(result)
                onDeviceFound?.invoke(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BluetoothUtils", "Bluetooth scan failed with error code: $errorCode")
        }
    }

    // Store the callback function passed in startScan
    private var onDeviceFound: ((ScanResult) -> Unit)? = null

    fun initializeBluetooth(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Activity, onDeviceFound: (ScanResult) -> Unit) {
        Log.d("BluetoothUtils", "Starting Bluetooth LE scan...")

        // Save the callback function
        this.onDeviceFound = onDeviceFound

        // Check if permissions are granted, if not request them
        if (!checkAndRequestPermissions(context)) {
            Log.e("BluetoothUtils", "Bluetooth scan permissions are not granted.")
            return
        }

        bluetoothLeScanner?.startScan(scanCallback)
    }

    fun stopScan(context: Context) {
        Log.d("BluetoothUtils", "Stopping Bluetooth LE scan...")

        // Check if the required permissions are granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BluetoothUtils", "Bluetooth scan permissions are not granted.")
            return
        }

        if (bluetoothLeScanner != null) {
            bluetoothLeScanner?.stopScan(scanCallback)
            onDeviceFound = null // Clear the callback to avoid any further invocations
        } else {
            Log.e("BluetoothUtils", "BluetoothLeScanner is null, unable to stop scan.")
        }
    }

    // Function to clear scanned devices
    fun clearScannedDevices() {
        scannedDevices.clear()
    }

    fun getScannedDevices() = scannedDevices

    // New function to centralize permission checking and request if necessary
    private fun checkAndRequestPermissions(activity: Activity): Boolean {
        val requiredPermissions = mutableListOf<String>()

        // Bluetooth permissions based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Location permission might still be required depending on device and use case
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (permissionsToRequest.isEmpty()) {
            // All permissions are granted
            true
        } else {
            // Request missing permissions
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toTypedArray(), MainActivity.REQUEST_BLUETOOTH_CONNECT)
            false
        }
    }
}
