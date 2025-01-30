package com.example.rfidimproved

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.rfidimproved.ui.theme.RfidImprovedTheme
import kotlinx.coroutines.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RfidImprovedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RfidScannerApp()
                }
            }
        }
    }
}



@Composable
fun RfidScannerApp() {
    var scanResult by remember { mutableStateOf("Waiting for RFID scan...") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Create WebView without displaying it
    DisposableEffect(Unit) {
        val newWebView = WebView(LocalContext.current).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl("https://example.com/form") // Replace with your actual form URL
        }
        webView = newWebView

        onDispose {
            newWebView.destroy()
        }
    }

    LaunchedEffect(key1 = Unit) {
        while (isActive) {
            val result = simulateRfidScan()
            scanResult = when (result) {
                "Open RFID" -> {
                    submitOpenForm(webView)
                    "Open log submitted successfully"
                }
                "Close RFID" -> {
                    submitCloseForm(webView)
                    "Close log submitted successfully"
                }
                else -> "Unknown RFID scanned"
            }
            delay(5000) // Wait for 5 seconds before next scan
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Open&Close Logs",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = scanResult,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Display company logo instead of WebView
        Image(
            painter = painterResource(id = R.drawable.company_logo), // Make sure to add your logo to the drawable resources
            contentDescription = "Company Logo",
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp)
        )
    }
}

suspend fun simulateRfidScan(): String {
    delay(2000) // Simulate 2-second scan
    return if (Math.random() < 0.5) "Open RFID" else "Close RFID"
}

fun submitOpenForm(webView: WebView?) {
    webView?.evaluateJavascript("""
        function waitForElement(selector, callback) {
            if (document.querySelector(selector)) {
                callback();
            } else {
                setTimeout(() => waitForElement(selector, callback), 100);
            }
        }

        waitForElement('#submitButton', () => {
            // Form is loaded, proceed with interaction
            const radioButtons = document.querySelectorAll('input[type="radio"]');
            const commentBox = document.getElementById('commentBox');
            const submitButton = document.getElementById('submitButton');

            if (radioButtons.length >= 3 && commentBox && submitButton) {
                // Click the first three radio buttons
                radioButtons[0].click();
                radioButtons[1].click();
                radioButtons[2].click();
                
                // Fill out the text box
                commentBox.value = 'Open RFID scanned';
                
                // Click the submit button
                submitButton.click();
            } else {
                console.error('Some form elements are missing');
            }
        });
    """.trimIndent(), null)
}

fun submitCloseForm(webView: WebView?) {
    webView?.evaluateJavascript("""
        function waitForElement(selector, callback) {
            if (document.querySelector(selector)) {
                callback();
            } else {
                setTimeout(() => waitForElement(selector, callback), 100);
            }
        }

        waitForElement('#submitButton', () => {
            // Form is loaded, proceed with interaction
            const radioButtons = document.querySelectorAll('input[type="radio"]');
            const commentBox = document.getElementById('commentBox');
            const submitButton = document.getElementById('submitButton');

            if (radioButtons.length >= 6 && commentBox && submitButton) {
                // Click different radio buttons for close action
                radioButtons[3].click();
                radioButtons[4].click();
                radioButtons[5].click();
                
                // Fill out the same text box with a different message
                commentBox.value = 'Close RFID scanned';
                
                // Click the submit button
                submitButton.click();
            } else {
                console.error('Some form elements are missing');
            }
        });
    """.trimIndent(), null)
}