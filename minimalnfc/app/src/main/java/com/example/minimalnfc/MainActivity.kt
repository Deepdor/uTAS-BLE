package com.example.minimalnfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalConfiguration
import com.example.minimalnfc.TagDataParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import java.io.IOException

class MainActivity : ComponentActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private var tagData = mutableStateOf("Scan an NFC tag")
    private var isTagConnected = mutableStateOf(false)
    private var detectedTag: Tag? = null // Store the detected tag
    private var uid = mutableStateOf("No NFC Tag detected")
    private var name = mutableStateOf("")
    private var surname = mutableStateOf("")
    private var tagType = mutableStateOf("")
    private var photo = mutableStateOf<Int?>(null)
    private val tagDataParser = TagDataParser() // Instance of the parser
    private var temperatureValue = mutableStateOf("")
    private var humidityValue = mutableStateOf("")
    private var tagNameData = mutableStateOf("")
    private var lastTag: Tag? = null

    private lateinit var knownTags: Map<String, TagInfo>

    /*
    // Mock tag database for demonstration
    private val knownTags = mapOf(
        "52 E0 E6 DD 03 25 02 E0" to TagInfo(name = "Adam", surname = "Smith", tagType = "Prototype", photo = R.drawable.adam),
        "31 75 2D 8D 68 25 02 E0" to TagInfo(name = "Alpha", surname = "AlphaSurname", tagType = "Prototype", photo = R.drawable.alpha),
        "12 34 56 78 90 AB CD EF" to TagInfo(name = "Beta", surname = "BetaSurname", tagType = "Development"),
        "A6 2D E9 DD 03 25 02 E0" to TagInfo(name ="Kenneth", surname = "Gentian", tagType="Prototype",photo = R.drawable.kenneth),
        "1D 2B E9 DD 03 25 02 E0" to TagInfo(name = "Barbara", surname = "Gentian", tagType = "Prototype", photo = R.drawable.barbara)
    ) */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the status bar color to Gentian blue
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.gentian_blue)
        }
        // Initialize the knownTags map here using loadTagData()
        knownTags = loadTagData()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            MainScreen(
                name = name.value,
                surname = surname.value,
                tagType = tagType.value,
                uid = uid.value,
                temperature = temperatureValue.value,
                humidity = humidityValue.value,
                blockData = tagData.value,
                photo = photo.value,
                isTagConnected = isTagConnected.value,
                onButtonClick = {
                    if (detectedTag != null) {
                        val nfcV = NfcV.get(detectedTag)
                        if (nfcV != null) {
                            try {
                                nfcV.connect() // Connect to the tag first
                                tagData.value = ""
                                readMultipleBlocks(nfcV, detectedTag!!, startingBlock = 0, numberOfBlocks = 2)
                                nfcV.close()
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error during tag communication: ${e.message}")
                                isTagConnected.value = false
                            }
                        } else {
                            Toast.makeText(this, "NfcV technology not supported", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "No tag connected", Toast.LENGTH_SHORT).show()
                        isTagConnected.value = false
                    }
                },
                onWriteButtonClick = {
                    lastTag?.let { tag ->
                        WriteTestDataToTag(tag)
                    } ?: Toast.makeText(this, "No tag available", Toast.LENGTH_SHORT).show()
                }

            )
        }
    }
    private fun loadTagData(): Map<String, TagInfo> {
        val inputStream = resources.openRawResource(R.raw.tags)
        val reader = InputStreamReader(inputStream)
        val tagListType = object : TypeToken<List<TagInfo>>() {}.type
        val tagList: List<TagInfo> = Gson().fromJson(reader, tagListType)

        // Convert the list to a map using the UID as the key
        return tagList.associateBy { it.uid }
    }
    override fun onResume() {
        super.onResume()
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE // Ensure it's MUTABLE in SDK 35
        )

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED -> {
                handleTagDiscovered(intent)
            }
            else -> {
                Log.d("MainActivity", "Unexpected intent action: ${intent.action}")
            }
        }
    }

    private fun handleTagDiscovered(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            isTagConnected.value = true
            val tagId = tag.id.joinToString(separator = " ") { byte -> "%02X".format(byte) }
            uid.value = tagId
            lastTag = tag // Store the latest tag
            val tagInfo = knownTags[uid.value]
            if (tagInfo != null) {
                // Populate the values from the TagInfo object
                name.value = tagInfo.name
                surname.value = tagInfo.surname
                tagType.value = tagInfo.tagType

                // Convert photo name to resource ID
                photo.value = if (tagInfo.photo != null) {
                    resources.getIdentifier(tagInfo.photo, "drawable", packageName).takeIf { it != 0 }
                } else {
                    null
                }

                // If the tag type is "Prototype", read the blocks and parse the data
                if (tagInfo.tagType == "Prototype") {
                    readMultipleBlocks(NfcV.get(tag), tag, startingBlock = 0, numberOfBlocks = 1)
                }
            } else {
                // If the tag is unknown, reset the values
                name.value = ""
                surname.value = ""
                tagType.value = ""
                photo.value = null // or use a default photo: resources.getIdentifier("default_photo", "drawable", packageName)
            }
            detectedTag = tag
        } else {
            isTagConnected.value = false
            uid.value = "No NFC Tag detected."
        }
    }



    private fun readMultipleBlocks(nfcV: NfcV, tag: Tag, startingBlock: Int, numberOfBlocks: Int) {
        try {
            val tagUid = tag.id
            val cmd = ByteArray(12) // Correct array size for addressed mode command
            cmd[0] = 0x20.toByte() // Addressed mode flag
            cmd[1] = 0x23.toByte() // READ_MULTIPLE_BLOCKS command
            System.arraycopy(tagUid, 0, cmd, 2, 8) // Copy the UID into the command
            cmd[10] = startingBlock.toByte() // Starting block (e.g., Block 0)
            cmd[11] = numberOfBlocks.toByte() // Number of blocks to read (e.g., 1 block for 2 total)

            // Send the command and get the response
            val response = nfcV.transceive(cmd)

            // Extract block data (each block is typically 4 bytes)
            val block0 = response.copyOfRange(1, 5) // Block 0 (4 bytes)

            // If the tag type is "Prototype", display parsed values (Temperature and Humidity)
            if (tagType.value == "Prototype") {
                val temperature = TagDataParser().parseTemperature(block0)
                val humidity = TagDataParser().parseHumidity(block0)

                // Update the state values for temperature and humidity
                temperatureValue.value = temperature.toString()
                humidityValue.value = humidity.toString()

            } else {
                // Otherwise, display the raw block data
                tagData.value += "${block0.joinToString { "%02X".format(it) }}\n"
            }

        } catch (e: Exception) {
            Log.d("MainActivity", "Error reading blocks: ${e.message}")
            tagData.value = "Error reading blocks: ${e.message}"
        }
    }

    fun WriteTestDataToTag(tag: Tag) {
        try {
            val nfcv = NfcV.get(tag) // Use NfcV since RF430FRL152H supports ISO15693
            nfcv?.use { // `use` ensures the connection is properly closed
                nfcv.connect()

                // Example write: DEAD BEEF 0000 0000
                val data = byteArrayOf(
                    0xDE.toByte(), 0xAD.toByte(),
                    0xBE.toByte(), 0xEF.toByte(),
                    0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte()
                )

                // Write to block 0 (RF430 writes in 8-byte chunks)
                val cmd = byteArrayOf(0x21, 0x00) + data
                nfcv.transceive(cmd)

                Toast.makeText(this, "Write successful", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("NFC_WRITE", "Error writing to tag: ${e.message}")
            Toast.makeText(this, "Write failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }




}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    name: String,
    surname: String,
    tagType: String,
    uid: String,
    temperature: String,
    humidity: String,
    photo: Int?,
    isTagConnected: Boolean,
    blockData: String,
    onButtonClick: () -> Unit,
    onWriteButtonClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Scan a Sensor Tag!") },

                )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (photo != null) {
                    Image(painter = painterResource(id = photo), contentDescription = null)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (name.isNotEmpty()) {
                    Row {
                        Text(text = "Name: ", fontWeight = FontWeight.Bold)
                        Text(text = name, fontWeight = FontWeight.Normal)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (surname.isNotEmpty()) {
                    Row {
                        Text(text = "Surname: ", fontWeight = FontWeight.Bold)
                        Text(text = surname, fontWeight = FontWeight.Normal)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (tagType.isNotEmpty()) {
                    Row {
                        Text(text = "Tag Type: ", fontWeight = FontWeight.Bold)
                        Text(text = tagType, fontWeight = FontWeight.Normal)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(text = "UID: $uid")
                Spacer(modifier = Modifier.height(16.dp))

                // Display temperature and humidity in a single row for Prototype tag
                if (tagType == "Prototype") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        // Temperature Icon and Value
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_thermostat),
                                contentDescription = "Temperature Icon",
                                modifier = Modifier.size(24.dp)
                            )
                            Text(text = temperature)
                        }

                        // Humidity Icon and Value
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_water_drop),
                                contentDescription = "Humidity Icon",
                                modifier = Modifier.size(24.dp)
                            )
                            Text(text = humidity)
                        }
                    }
                }

                // Show raw block data if it's not a "Prototype"
                if (blockData.isNotEmpty() && tagType != "Prototype") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Raw Block Data: \n$blockData")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { onButtonClick() }) {
                    Text(text = if (isTagConnected) "Read Tag Data" else "Tag Disconnected")
                }
                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { onWriteButtonClick() }) {
                    Text(text = "Write Test Data")
                }
            }
        }
    )
}




@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen(
        name = "Adam",
        surname = "Smith",
        tagType = "Prototype",
        uid = "52 E0 E6 DD 03 25 02 E0",
        blockData = "Sample Block Data",
        temperature = "25.3", // Parsed temperature
        humidity = "45",      // Parsed humidity
        photo = R.drawable.adam,
        isTagConnected = true,
        onButtonClick = { /* Handle button click */ },
        onWriteButtonClick = {}
    )

}
