package com.example.nfcsenser

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcV
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.nfcsenser.ui.theme.NFCSenserTheme

class MainActivity : ComponentActivity() {
    private lateinit var nfcAdapter: NfcAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Log.e("MainActivity", "NFC is not available on this device.")
            return
        }
        setContent {
            NFCSenserTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding), nfcAdapter)
                }
            }
        }
        resolveIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIntent(intent)
    }

    private fun resolveIntent(intent: Intent) {
        val validActions = listOf(
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED
        )

        if (intent.action in validActions) {
            Log.d("MainActivity", "NFC intent resolved with action: ${intent.action}")

            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (rawMsgs != null) {
                val messages = rawMsgs.map { it as NdefMessage }
                processNdefMessages(messages)
            } else {
                // Handle non-NDEF tags or unknown tag types
                handleUnknownTag(intent)
            }
        } else {
            Log.d("MainActivity", "Unexpected intent action: ${intent.action}")
        }
    }

    private fun processNdefMessages(messages: List<NdefMessage>) {
        // Handle the NDEF messages (e.g., display them in the UI)
        for (message in messages) {
            val records = message.records
            for (record in records) {
                val payload = String(record.payload)
                Log.d("MainActivity", "NDEF Record: $payload")
                updateTagData(payload)
            }
        }
    }

    private fun handleUnknownTag(intent: Intent) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            val id = tag.id.joinToString { "%02X".format(it) }
            Log.d("MainActivity", "Unknown NFC tag detected with ID: $id")

            // Handle the unknown tag or raw data from the tag
            val techList = tag.techList.joinToString(", ")
            Log.d("MainActivity", "Tag Tech List: $techList")

            // Handle the specific technologies (like NfcV or Ndef) here if needed
        }
    }

    private fun updateTagData(data: String) {
        Log.d("MainActivity", "Tag Data: $data")
        // Update the UI with the tag data
    }
}
@Composable
fun MainScreen(modifier: Modifier = Modifier, nfcAdapter: NfcAdapter?, context: Context? = null) {
    var tagData by remember { mutableStateOf("Tag data will appear here") }

    fun updateTagData(data: String) {
        tagData = data
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            context?.let { ctx ->
                //val intent = Intent(ctx, ctx::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                //val pendingIntent = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                //val intentFilters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
                //nfcAdapter?.enableForegroundDispatch(ctx as ComponentActivity, pendingIntent, intentFilters, null)
                tagData = "Ready to scan, bring NFC tag close to the phone."
            }
        }) {
            Text("Scan Tag")
        }


        Spacer(modifier = Modifier.height(20.dp))

        Text(text = tagData, style = MaterialTheme.typography.bodyLarge)
    }

}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NFCSenserTheme {
        MainScreen(nfcAdapter = null, context = null)
    }
}