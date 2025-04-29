package com.example.utasble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.data.Entry


@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectedScreen(
    deviceName: String,
    batteryLevel: String,
    targetCharacteristicValue: String,
    bluetoothGatt: BluetoothGatt?
) {
    var timeValue by remember { mutableStateOf(0.toUByte()) }
    var pressureValue by remember { mutableStateOf(0.toUByte()) }
    var isTimeSelected by remember { mutableStateOf(true) }
    var selectedProgram by remember { mutableStateOf("Timed") }
    val payload = remember { mutableStateOf(byteArrayOf(0x02, 0x00, 0x00)) }

    // State to keep track of whether verbose mode is enabled
    var isVerboseModeEnabled by remember { mutableStateOf(false) }

    // Buffer to hold pressure values for the chart
    val pressureDataBuffer = remember { mutableStateListOf<Entry>() }
    var nextXValue by remember { mutableStateOf(0f) }  // Track the next X value for the data points
    val bufferSize = 300 // Set the size of the buffer
    val batchSizeToRemove = 50

    // Parsing function to extract values from the targetCharacteristicValue string
    val parsedValues = remember(targetCharacteristicValue) {
        val parts = targetCharacteristicValue.split(", ")
        val pressure = parts.getOrNull(0)?.split(": ")?.getOrNull(1)?.removeSuffix("mBar")?.toIntOrNull() ?: 0
        val accelX = parts.getOrNull(1)?.split(": ")?.getOrNull(1)?.removeSuffix("g")?.toFloatOrNull() ?: 0f
        val accelY = parts.getOrNull(2)?.split(": ")?.getOrNull(1)?.removeSuffix("g")?.toFloatOrNull() ?: 0f
        val accelZ = parts.getOrNull(3)?.split(": ")?.getOrNull(1)?.removeSuffix("g")?.toFloatOrNull() ?: 0f
        val wasShaken = parts.getOrNull(4)?.split(": ")?.getOrNull(1)?.trim() == "1"
        val isTouching = parts.getOrNull(5)?.split(": ")?.getOrNull(1)?.trim() == "1"

        ParsedValues(pressure, accelX, accelY, accelZ, wasShaken, isTouching)
    }

    LaunchedEffect(parsedValues.pressure) {
        if (pressureDataBuffer.size >= bufferSize) {
            pressureDataBuffer.removeRange(0, batchSizeToRemove) // Remove the oldest batch of data
        }
        pressureDataBuffer.add(Entry(nextXValue, parsedValues.pressure.toFloat()))
        nextXValue += 1f // Increment nextXValue for each new data point
    }

    DisposableEffect(Unit) {
        onDispose {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            println("Bluetooth GATT connection disposed.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Connected to $deviceName") })
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(4.dp)
            ) {
                Text(text = "Battery Level: $batteryLevel%")
                Spacer(modifier = Modifier.height(8.dp))

                // Display Pressure, Shaken, and Touch status in one row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pressure: ${parsedValues.pressure} mBar",
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                // Toggle verbose mode
                                val verboseCommand = byteArrayOf(0x07)
                                bluetoothGatt?.let {
                                    val characteristic = it.getService(MainActivity.TARGET_SERVICE_UUID)
                                        ?.getCharacteristic(MainActivity.CONTROL_CHARACTERISTIC_UUID)
                                    characteristic?.value = verboseCommand
                                    it.writeCharacteristic(characteristic)

                                    // Toggle the local verbose mode state
                                    isVerboseModeEnabled = !isVerboseModeEnabled
                                    println("Verbose mode ${if (isVerboseModeEnabled) "enabled" else "disabled"}")
                                }
                            }
                    )
                    Text(text = "Shaken: ${parsedValues.wasShaken}", modifier = Modifier.weight(1f))
                    Text(text = "Touch: ${parsedValues.isTouching}", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Display Acceleration values in one row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "X: %.2f g".format(parsedValues.accelX), modifier = Modifier.weight(1f))
                    Text(text = "Y: %.2f g".format(parsedValues.accelY), modifier = Modifier.weight(1f))
                    Text(text = "Z: %.2f g".format(parsedValues.accelZ), modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                // Add your LineChartCompose right below the target characteristic values
                LineChartCompose(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp), // Adjust the height as per your preference
                    chartData = pressureDataBuffer.toList()
                )


                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                selectedProgram = "Timed"
                                payload.value[0] = 0x02
                                println("Program set to Timed")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedProgram == "Timed") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Timed")
                        }
                        Button(
                            onClick = {
                                selectedProgram = "Ramp to"
                                payload.value[0] = 0x03
                                println("Program set to Ramp to")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedProgram == "Ramp to") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ramp to")
                        }
                        Button(
                            onClick = {
                                selectedProgram = "Maintain"
                                payload.value[0] = 0x04
                                println("Program set to Maintain")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedProgram == "Maintain") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Maintain")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            bluetoothGatt?.let {
                                val characteristic = it.getService(MainActivity.TARGET_SERVICE_UUID)
                                    ?.getCharacteristic(MainActivity.CONTROL_CHARACTERISTIC_UUID)
                                characteristic?.value = payload.value
                                it.writeCharacteristic(characteristic)
                                println("Running program with payload: ${payload.value.joinToString()}")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run program")
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        var sliderPosition by remember { mutableStateOf(0f) }
                        Slider(
                            value = sliderPosition,
                            onValueChange = {
                                sliderPosition = it
                                val newValue = it.toInt().toUByte()
                                if (isTimeSelected) {
                                    timeValue = newValue
                                    println("Time value updated: $timeValue")
                                    payload.value[1] = timeValue.toByte()
                                } else {
                                    pressureValue = newValue
                                    println("Pressure value updated: $pressureValue")
                                    payload.value[2] = pressureValue.toByte()
                                }
                            },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(text = "Pressure: ${pressureValue * 100u} mBar", modifier = Modifier.weight(1f))

                        Switch(
                            checked = isTimeSelected,
                            onCheckedChange = {
                                isTimeSelected = it
                                println("Switch state changed: ${if (isTimeSelected) "Time" else "Pressure"}")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(text = "Time: + ${timeValue * 10u} sec", modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                bluetoothGatt?.let {
                                    val characteristic = it.getService(MainActivity.TARGET_SERVICE_UUID)
                                        ?.getCharacteristic(MainActivity.CONTROL_CHARACTERISTIC_UUID)
                                    characteristic?.value = byteArrayOf(0x01)
                                    it.writeCharacteristic(characteristic)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Run")
                        }
                        Button(
                            onClick = {
                                bluetoothGatt?.let {
                                    val characteristic = it.getService(MainActivity.TARGET_SERVICE_UUID)
                                        ?.getCharacteristic(MainActivity.CONTROL_CHARACTERISTIC_UUID)
                                    characteristic?.value = byteArrayOf(0x00)
                                    it.writeCharacteristic(characteristic)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop")
                        }
                        Button(
                            onClick = {
                                bluetoothGatt?.let {
                                    val characteristic = it.getService(MainActivity.TARGET_SERVICE_UUID)
                                        ?.getCharacteristic(MainActivity.CONTROL_CHARACTERISTIC_UUID)
                                    characteristic?.value = byteArrayOf(0x05)
                                    it.writeCharacteristic(characteristic)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sleep")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp)) // Add a bit of space below the buttons
                    Text(
                        text = "(c) MicroTIPs team",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

            }
        }
    )
}


data class ParsedValues(
    val pressure: Int,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val wasShaken: Boolean,
    val isTouching: Boolean
)
