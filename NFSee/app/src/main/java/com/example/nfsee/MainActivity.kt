package com.example.nfsee
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.nfsee.ui.theme.NFSeeTheme

class MainActivity : ComponentActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private var tagData by mutableStateOf("No NFC Tag detected.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            tagData = "NFC is not available on this device."
        }

        setContent {
            NFSeeTheme {
                MainScreen(tagData)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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
        Log.d("MainActivity", "onNewIntent triggered with action: ${intent.action}")
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            handleNfcIntent(intent)
        } else {
            Log.d("MainActivity", "Unexpected intent action: ${intent.action}")
        }
    }


    private fun handleNfcIntent(intent: Intent) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            val tagId = tag.id.joinToString(separator = " ") { byte -> "%02X".format(byte) }
            tagData = "NFC Tag detected with ID: $tagId"
            Log.d("MainActivity", tagData)
        } else {
            tagData = "No NFC Tag detected."
            Log.d("MainActivity", tagData)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(tagData: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFSee") }
            )
        },
        content = { paddingValues ->
            Text(
                text = tagData,
                modifier = Modifier.padding(paddingValues).padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NFSeeTheme {
        MainScreen("NFC Tag data will appear here")
    }
}
