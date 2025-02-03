package com.example.rfidimproved

import android.annotation.SuppressLint
import android.widget.Toast
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.rfidimproved.ui.theme.RfidImprovedTheme
import java.util.*
import java.text.SimpleDateFormat

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var webView: WebView
    private val validRfidTagId = "E8E648A5500104E0"
    private val scanResultState = mutableStateOf("Waiting for RFID scan...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl("https://forms.torvanmed.com/torvanmedical/form/OpenCloseLogFP030100/formperma/PiafoxlCxKW3i_pF0aISuWKzGLH0oIvnzD3SkmdLuD4")
        }

        setContent {
            RfidImprovedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RfidScannerApp(
                        nfcAdapter = nfcAdapter,
                        scanResult = scanResultState.value,
                        onUpdateScanResult = { message ->
                            scanResultState.value = message
                            showConfirmationMessage(message)
                        }
                    )
                }
            }
        }

        // Set up NFC
        if (nfcAdapter == null) {
            showConfirmationMessage("This device doesn't support NFC")
        } else if (!nfcAdapter!!.isEnabled) {
            showConfirmationMessage("NFC is disabled. Please enable NFC in your device settings.")
        }
    }

    override fun onResume() {
        super.onResume()
        setupForegroundDispatch()
    }

    override fun onPause() {
        nfcAdapter?.disableForegroundDispatch(this)
        super.onPause()
    }

    private fun setupForegroundDispatch() {
        if (nfcAdapter != null) {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            val filters = arrayOf<IntentFilter>()
            val techLists = arrayOf<Array<String>>()
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techLists)
        }
    }

    private fun showConfirmationMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            handleScannedTag(tag)
        }
    }

    private fun handleScannedTag(tag: Tag?) {
        val tagId = tag?.id?.joinToString("") { "%02X".format(it) } ?: "Unknown"
        if (tagId == validRfidTagId) {
            runOnUiThread {
                val currentTime = Calendar.getInstance()
                val formType = when {
                    isWithinTimeRange(currentTime, 6, 30, 12, 0) -> "Open"
                    isWithinTimeRange(currentTime, 13, 0, 16, 30) -> "Close"
                    else -> "Outside"
                }
    
                when (formType) {
                    "Open" -> {
                        scanResultState.value = "Valid RFID tag scanned. Submitting Open form."
                        showConfirmationMessage(scanResultState.value)
                        submitOpenForm(webView)
                    }
                    "Close" -> {
                        scanResultState.value = "Valid RFID tag scanned. Submitting Close form."
                        showConfirmationMessage(scanResultState.value)
                        submitCloseForm(webView)
                    }
                    else -> {
                        scanResultState.value = "Valid RFID tag scanned, but outside of form submission hours."
                        showConfirmationMessage(scanResultState.value)
                    }
                }
            }
        } else {
            runOnUiThread {
                scanResultState.value = "Invalid RFID tag: $tagId"
                showConfirmationMessage(scanResultState.value)
            }
        }
    }
}

@Composable
fun RfidScannerApp(
    nfcAdapter: NfcAdapter?,
    scanResult: String,
    onUpdateScanResult: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = scanResult)
        if (nfcAdapter == null) {
            Text(text = "NFC is not available on this device")
        } else if (!nfcAdapter.isEnabled) {
            Text(text = "NFC is disabled. Please enable it in your device settings.")
        } else {
            Text(text = "Ready to scan RFID tags")
        }
    }
}
        
    

    private fun isWithinTimeRange(
        currentTime: Calendar,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): Boolean {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
        }
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
        }
        return currentTime.after(start) && currentTime.before(end)
    }

    private fun submitOpenForm(webView: WebView) {
        webView.evaluateJavascript("""
        (function() {
            return new Promise((resolve, reject) => {
                if (document.readyState === 'complete') {
                    submitForm();
                } else {
                    window.addEventListener('load', submitForm);
                }
    
                function submitForm() {
                    // Select the "Open" radio button
                    const openRadioLabel = document.querySelector('label[for="Radio2_1"]');
                    const openRadioInput = document.getElementById('Radio2_1');
                    
                    // Select the first "Yes" radio button
                    const yesRadioLabel1 = document.querySelector('label[for="Radio_1"]');
                    const yesRadioInput1 = document.getElementById('Radio_1');
                    
                    // Select the second "Yes" radio button
                    const yesRadioLabel2 = document.querySelector('label[for="Radio1_1"]');
                    const yesRadioInput2 = document.getElementById('Radio1_1');
                    
                    // Select the text input field
                    const textBox = document.getElementById('SingleLine-arialabel');
                    
                    // Select the submit button
                    const submitButton = document.querySelector('button.fmSmtButton[elname="submit"]');
    
                    if (!openRadioLabel || !openRadioInput || !yesRadioLabel1 || !yesRadioInput1 || 
                        !yesRadioLabel2 || !yesRadioInput2 || !textBox || !submitButton) {
                        reject('Required form elements not found');
                        return;
                    }
    
                    // Click the "Open" radio button
                    openRadioLabel.click();
                    
                    // Click the first "Yes" radio button
                    yesRadioLabel1.click();
                    
                    // Click the second "Yes" radio button
                    yesRadioLabel2.click();
                    
                    // Fill in the text box
                    textBox.value = 'Sc06';
                    textBox.dispatchEvent(new Event('input', { bubbles: true }));
                    textBox.dispatchEvent(new Event('change', { bubbles: true }));
                    
                    // Click the submit button
                    submitButton.click();
    
                    // Wait for form submission to complete
                    setTimeout(() => {
                        resolve(true);
                    }, 2000);
                }
            });
        })()
         """.trimIndent()) { result ->
            if (result == "true") {
                runOnUiThread {
                    showConfirmationMessage("Open form submitted successfully")
                }
            } else {
                runOnUiThread {
                    showConfirmationMessage("Error submitting open form")
                }
            }
        }
    }

private fun submitCloseForm(webView: WebView) {
    webView.evaluateJavascript(
        """
        (function() {
            return new Promise((resolve, reject) => {
                if (document.readyState === 'complete') {
                    submitForm();
                } else {
                    window.addEventListener('load', submitForm);
                }

                function submitForm() {
                    // Select the "Close" radio button
                    const closeRadioLabel = document.querySelector('label[for="Radio2_2"]');
                    const closeRadioInput = document.getElementById('Radio2_2');

                    // Select the first "Yes" radio button
                    const yesRadioLabel1 = document.querySelector('label[for="Radio_1"]');
                    const yesRadioInput1 = document.getElementById('Radio_1');

                    // Select the second "Yes" radio button
                    const yesRadioLabel2 = document.querySelector('label[for="Radio1_1"]');
                    const yesRadioInput2 = document.getElementById('Radio1_1');

                    // Select the text input field
                    const textBox = document.getElementById('SingleLine-arialabel');

                    // Select the submit button
                    const submitButton = document.querySelector('button.fmSmtButton[elname="submit"]');

                    if (!closeRadioLabel || !closeRadioInput || !yesRadioLabel1 || !yesRadioInput1 ||
                        !yesRadioLabel2 || !yesRadioInput2 || !textBox || !submitButton) {
                        reject('Required form elements not found');
                        return;
                    }

                    // Click the "Close" radio button
                    closeRadioLabel.click();

                    // Click the first "Yes" radio button
                    yesRadioLabel1.click();

                    // Click the second "Yes" radio button
                    yesRadioLabel2.click();

                    // Fill in the text box
                    textBox.value = 'Sc06';
                    textBox.dispatchEvent(new Event('input', { bubbles: true }));
                    textBox.dispatchEvent(new Event('change', { bubbles: true }));

                    // Click the submit button
                    submitButton.click();

                    // Wait for form submission to complete
                    setTimeout(() => {
                        resolve(true);
                    }, 2000);
                }
            });
        })()
        """.trimIndent()
    ) { result ->
        runOnUiThread {
            when {
                result == "true" -> showConfirmationMessage("Close form submitted successfully")
                else -> showConfirmationMessage("Error submitting close form")
            }
        }
    }
}

private fun runOnUiThread(runnable: () -> Unit) {
    this.runOnUiThread(runnable)
}

private fun showConfirmationMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}