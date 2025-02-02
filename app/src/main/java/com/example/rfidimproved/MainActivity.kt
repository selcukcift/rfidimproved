package com.example.rfidimproved

import android.annotation.SuppressLint
import android.widget.Toast
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.rfidimproved.ui.theme.RfidImprovedTheme
import java.util.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var webView: WebView
    private val validRfidTagId = "04A2B76A5A5480" // Replace with your actual RFID tag ID
    private lateinit var prefs: SharedPreferences

    private var isOpenFormSubmitted: Boolean
        get() = prefs.getBoolean("isOpenFormSubmitted", false)
        set(value) = prefs.edit().putBoolean("isOpenFormSubmitted", value).apply()

    private var isCloseFormSubmitted: Boolean
        get() = prefs.getBoolean("isCloseFormSubmitted", false)
        set(value) = prefs.edit().putBoolean("isCloseFormSubmitted", value).apply()

    private val submitFormReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isWeekday()) {
                val isOpenForm = intent?.getBooleanExtra("isOpenForm", true) ?: true
                if (isOpenForm) {
                    submitOpenForm(webView)
                } else {
                    submitCloseForm(webView)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    
        prefs = getSharedPreferences("RFIDAppPrefs", Context.MODE_PRIVATE)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    
        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            // Remove or review the usage of javaScriptEnabled
            loadUrl("https://forms.torvanmed.com/torvanmedical/form/OpenCloseLogFP030100/formperma/PiafoxlCxKW3i_pF0aISuWKzGLH0oIvnzD3SkmdLuD4")
        }
    
        val submitFormFilter = IntentFilter("com.example.rfidImproved.SUBMIT_FORM")
        registerReceiver(submitFormReceiver, submitFormFilter, RECEIVER_NOT_EXPORTED)
    
        setContent {
            RfidImprovedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RfidScannerApp(nfcAdapter)
                }
            }
        }
    
        scheduleAutomaticSubmissions()
        scheduleDailyReset()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(submitFormReceiver)
    }

    private fun scheduleAutomaticSubmissions() {
        scheduleSubmission(true, 8, 1, 8, 29)
        scheduleSubmission(false, 17, 0, 18, 0)
    }

    private fun scheduleSubmission(isOpenForm: Boolean, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent("com.example.rfidImproved.SUBMIT_FORM").apply {
            putExtra("isOpenForm", isOpenForm)
        }
        val pendingIntent = PendingIntent.getBroadcast(this, if (isOpenForm) 0 else 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Random.nextInt(startHour, endHour + 1))
            set(Calendar.MINUTE, Random.nextInt(startMinute, endMinute + 1))
            set(Calendar.SECOND, 0)
            
            // Ensure it's scheduled for a weekday
            while (!isWeekday(this)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
    
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
                while (!isWeekday(this)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
    
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            // Fall back to inexact alarm or request permission
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            // Optionally, you can prompt the user to grant the permission:
            // startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }

    private fun scheduleDailyReset() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DailyResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action && isWeekday()) {
            val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            handleScannedTag(tag)
        }
    }

    private fun handleScannedTag(tag: Tag?) {
        val tagId = tag?.id?.joinToString("") { "%02X".format(it) } ?: "Unknown"
        if (tagId == validRfidTagId) {
            val calendar = Calendar.getInstance()
            val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
    
            when {
                hourOfDay in 5..8 && !isOpenFormSubmitted -> {
                    if (hourOfDay == 8 && minute > 30) return
                    submitOpenForm(webView)
                    isOpenFormSubmitted = true
                }
                hourOfDay in 12..17 && !isCloseFormSubmitted -> {
                    submitCloseForm(webView)
                    isCloseFormSubmitted = true
                }
                else -> {
                    showConfirmationMessage("Outside of valid submission windows or already submitted")
                }
            }
        }
    }

    private fun isWeekday(calendar: Calendar = Calendar.getInstance()): Boolean {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
    }

    private fun submitOpenForm(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                // Click the "Open" radio button
                document.querySelector('#open-radio-button-id').click();
                
                // Fill out the text box
                document.querySelector('#comment-text-box-id').value = 'Opened by RFID Scanner';
                
                // Click the submit button
                document.querySelector('#submit-button-id').click();
                
                return true; // Indicate successful submission
            })()
        """.trimIndent()) { result ->
            if (result == "true") {
                showConfirmationMessage("Open form submitted successfully")
            }
        }
    }
    
    private fun submitCloseForm(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                // Click the "Close" radio button
                document.querySelector('#close-radio-button-id').click();
                
                // Fill out the text box
                document.querySelector('#comment-text-box-id').value = 'Closed by RFID Scanner';
                
                // Click the submit button
                document.querySelector('#submit-button-id').click();
                
                return true; // Indicate successful submission
            })()
        """.trimIndent()) { result ->
            if (result == "true") {
                showConfirmationMessage("Close form submitted successfully")
            }
        }
    }
    
    private fun showConfirmationMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}

class DailyResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("RFIDAppPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("isOpenFormSubmitted", false)
            putBoolean("isCloseFormSubmitted", false)
            apply()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RfidScannerApp(nfcAdapter: NfcAdapter?) {
    var scanResult by remember { mutableStateOf("Waiting for RFID scan...") }
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl("https://forms.torvanmed.com/torvanmedical/form/OpenCloseLogFP030100/formperma/PiafoxlCxKW3i_pF0aISuWKzGLH0oIvnzD3SkmdLuD4") // Replace with your actual form URL
        }
    }

    // Use DisposableEffect to manage the WebView's lifecycle
    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    // Check if NFC is available
    LaunchedEffect(nfcAdapter) {
        if (nfcAdapter == null) {
            scanResult = "NFC is not available on this device"
        } else if (!nfcAdapter.isEnabled) {
            scanResult = "NFC is disabled. Please enable NFC in your device settings."
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
        // Static logo
        StaticLogo()
    }
}

@Composable
fun StaticLogo() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.torvan_medical_logo_db7ea4da),
            contentDescription = "torvanMedicalLogo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

