package com.forevercs.arushiai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.forevercs.arushiai.ui.theme.MyApplicationTheme
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false
        val callGranted = permissions[Manifest.permission.CALL_PHONE] ?: false
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
        Log.d("MainActivity", "Permissions updated: audio=$audioGranted, contacts=$contactsGranted, call=$callGranted, notif=$notifGranted")
        
        webView?.post {
            webView?.evaluateJavascript(
                "if (window.onNativePermissionsUpdated) { window.onNativePermissionsUpdated($audioGranted, $contactsGranted, $callGranted); }",
                null
            )
            webView?.evaluateJavascript(
                "if (window.refreshSetupCenter) { window.refreshSetupCenter(); }",
                null
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Proactively request normal voice mic permissions on startup for smooth voice assistant flow
        requestRequiredPermissions()
        handleWakeIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                this@MainActivity.webView = this
                                setupWebView(this)
                                loadUrl("file:///android_asset/index.html")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent == null) return
        val isWake = intent.getBooleanExtra("WAKE_TRIGGERED", false)
        val phrase = intent.getStringExtra("WAKE_PHRASE") ?: ""
        if (isWake) {
            webView?.post {
                webView?.evaluateJavascript(
                    "if (window.onWakeWordFromService) { window.onWakeWordFromService('${phrase.replace("'", "\\'")}'); }",
                    null
                )
            }
        }
    }

    private fun setupWebView(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            setSupportMultipleWindows(false)
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val requestedResources = request.resources
                val granted = mutableListOf<String>()
                var needsAudioPermission = false

                for (resource in requestedResources) {
                    if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        val hasAudio = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudio) {
                            granted.add(resource)
                        } else {
                            needsAudioPermission = true
                        }
                    }
                }

                if (needsAudioPermission) {
                    runOnUiThread {
                        requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                }

                if (granted.isNotEmpty()) {
                    request.grant(granted.toTypedArray())
                } else {
                    request.deny()
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("ArushiConsole", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                return true
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val didCrash = detail?.didCrash() ?: false
                Log.e("MainActivity", "WebView render process gone (crashed=$didCrash). Recovering WebView...")
                view?.let { wvInstance ->
                    val parent = wvInstance.parent as? ViewGroup
                    parent?.removeView(wvInstance)
                    wvInstance.destroy()
                    val newWv = WebView(this@MainActivity)
                    setupWebView(newWv)
                    this@MainActivity.webView = newWv
                    parent?.addView(newWv)
                    newWv.loadUrl("file:///android_asset/index.html")
                }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (url.startsWith("file:///android_asset/")) {
                    return false
                }
                // Delegate external links to default browser intent
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error opening URL: $url", e)
                }
                return false
            }
        }

        val bridge = AndroidBridge()
        wv.addJavascriptInterface(bridge, "AndroidBridge")
        wv.addJavascriptInterface(bridge, "Android")
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun isNativeBridge(): Boolean = true

        @JavascriptInterface
        fun getPlatformInfo(): String {
            val json = JSONObject()
            json.put("platform", "android_webview")
            val audio = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val contacts = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val call = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            json.put("audio", audio)
            json.put("contacts", contacts)
            json.put("call", call)
            json.put("hasAudioPermission", audio)
            json.put("hasContactsPermission", contacts)
            json.put("hasCallPermission", call)
            return json.toString()
        }

        @JavascriptInterface
        fun getPermissionsStatus(): String = getPlatformInfo()

        @JavascriptInterface
        fun getApiKey(): String {
            return try {
                val key = BuildConfig.GEMINI_API_KEY
                if (key.isNullOrBlank() || key == "MY_GEMINI_API_KEY") "" else key
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun requestAllPermissions(): Boolean {
            runOnUiThread {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE
                    )
                )
            }
            return true
        }

        @JavascriptInterface
        fun requestContactsPermission(): Boolean {
            runOnUiThread {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            }
            return true
        }

        @JavascriptInterface
        fun requestAudioPermission(): Boolean {
            runOnUiThread {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
            return true
        }

        @JavascriptInterface
        fun requestCallPermission(): Boolean {
            runOnUiThread {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
            }
            return true
        }

        @JavascriptInterface
        fun openSettings(type: String?): String {
            val res = JSONObject()
            res.put("tool", "openSettings")
            return try {
                val intent = when (type?.trim()?.lowercase()) {
                    "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    "app", "permissions", "flexibility" -> Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                    "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                    "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    else -> Intent(Settings.ACTION_SETTINGS)
                }.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                res.put("success", true)
                res.put("message", "Opened $type settings")
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Could not open settings: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun executeFastCommand(commandText: String?): String {
            val res = JSONObject()
            if (commandText.isNullOrBlank()) {
                res.put("handled", false)
                res.put("error", "Empty command")
                return res.toString()
            }
            val result = ArushiCommandRouter.execute(this@MainActivity, commandText)
            res.put("handled", result.handled)
            res.put("action", result.action)
            res.put("state", result.state)
            res.put("success", result.success)
            res.put("spokenReply", result.spokenReply)
            res.put("details", result.details)
            return res.toString()
        }

        @JavascriptInterface
        fun searchYouTube(query: String?): String {
            val res = JSONObject()
            val result = ArushiPhoneController.searchYouTube(this@MainActivity, query ?: "")
            res.put("success", result.success)
            res.put("state", result.state)
            res.put("message", result.message)
            res.put("spokenReply", result.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun searchWhatsApp(query: String?): String {
            val res = JSONObject()
            val result = ArushiPhoneController.searchWhatsApp(this@MainActivity, query ?: "")
            res.put("success", result.success)
            res.put("state", result.state)
            res.put("message", result.message)
            res.put("spokenReply", result.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun flashlightOn(): String {
            val res = JSONObject()
            val result = ArushiPhoneController.setFlashlight(this@MainActivity, true)
            res.put("success", result.success)
            res.put("state", result.state)
            res.put("message", result.message)
            res.put("spokenReply", result.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun flashlightOff(): String {
            val res = JSONObject()
            val result = ArushiPhoneController.setFlashlight(this@MainActivity, false)
            res.put("success", result.success)
            res.put("state", result.state)
            res.put("message", result.message)
            res.put("spokenReply", result.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun toggleFlashlight(): String {
            val res = JSONObject()
            val result = ArushiPhoneController.toggleFlashlight(this@MainActivity)
            res.put("success", result.success)
            res.put("state", result.state)
            res.put("message", result.message)
            res.put("spokenReply", result.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun globalBack(): String {
            val res = JSONObject()
            res.put("tool", "globalBack")
            val ctrlResult = ArushiPhoneController.globalBack(this@MainActivity)
            if (ctrlResult.success) {
                res.put("success", true)
                res.put("message", ctrlResult.message)
                res.put("spokenReply", ctrlResult.spokenReply ?: "Ji Sir.")
            } else {
                // Fallback to local back press if in foreground
                runOnUiThread {
                    onBackPressedDispatcher.onBackPressed()
                }
                res.put("success", false)
                res.put("state", ctrlResult.state)
                res.put("message", ctrlResult.message)
                res.put("spokenReply", ctrlResult.spokenReply ?: "Sir, global phone control ke liye Accessibility Service enable karni hogi.")
            }
            return res.toString()
        }

        @JavascriptInterface
        fun globalHome(): String {
            val res = JSONObject()
            res.put("tool", "globalHome")
            val ctrlResult = ArushiPhoneController.globalHome(this@MainActivity)
            res.put("success", ctrlResult.success)
            res.put("state", ctrlResult.state)
            res.put("message", ctrlResult.message)
            res.put("spokenReply", ctrlResult.spokenReply ?: "Ji Sir.")
            return res.toString()
        }

        @JavascriptInterface
        fun globalRecents(): String {
            val res = JSONObject()
            res.put("tool", "globalRecents")
            val ctrlResult = ArushiPhoneController.globalRecents(this@MainActivity)
            res.put("success", ctrlResult.success)
            res.put("state", ctrlResult.state)
            res.put("message", ctrlResult.message)
            res.put("spokenReply", ctrlResult.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun volumeUp(): String {
            val res = JSONObject()
            val result = ArushiPhoneController.adjustVolume(this@MainActivity, true)
            res.put("success", result.success)
            res.put("state", result.state)
            res.put("message", result.message)
            res.put("spokenReply", result.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun volumeDown(): String {
            val res = JSONObject()
            val result = ArushiPhoneController.adjustVolume(this@MainActivity, false)
            res.put("success", result.success)
            res.put("state", result.state)
            res.put("message", result.message)
            res.put("spokenReply", result.spokenReply ?: "")
            return res.toString()
        }

        @JavascriptInterface
        fun startArushiService(): String {
            val res = JSONObject()
            return try {
                val hasAudio = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (!hasAudio) {
                    runOnUiThread {
                        requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                    res.put("success", false)
                    res.put("state", "PERMISSION_REQUIRED")
                    res.put("error", "Microphone permission is required before starting background service.")
                    return res.toString()
                }

                ArushiForegroundService.start(this@MainActivity)
                val prefs = getSharedPreferences("arushi_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("auto_start_service", true).apply()

                res.put("success", true)
                res.put("state", "RUNNING")
                res.put("message", "Arushi background voice service started.")
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("state", "FAILURE")
                res.put("error", "Failed to start service: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun stopArushiService(): String {
            val res = JSONObject()
            return try {
                ArushiForegroundService.stop(this@MainActivity)
                val prefs = getSharedPreferences("arushi_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("auto_start_service", false).apply()

                res.put("success", true)
                res.put("state", "STOPPED")
                res.put("message", "Arushi background voice service stopped.")
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("state", "FAILURE")
                res.put("error", "Failed to stop service: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun isArushiServiceRunning(): Boolean {
            return ArushiForegroundService.isRunning
        }

        @JavascriptInterface
        fun getAccessibilityStatus(): Boolean {
            return ArushiAccessibilityService.isAccessibilityServiceEnabled(this@MainActivity)
        }

        @JavascriptInterface
        fun getWakeWordStatus(): Boolean {
            return ArushiForegroundService.isRunning
        }

        @JavascriptInterface
        fun pauseWakeWord(): Boolean {
            ArushiForegroundService.pauseListening()
            return true
        }

        @JavascriptInterface
        fun resumeWakeWord(): Boolean {
            ArushiForegroundService.resumeListening()
            return true
        }

        @JavascriptInterface
        fun getSetupStatus(): String {
            return ArushiSetupManager.getLiveStatus(this@MainActivity).toString()
        }

        @JavascriptInterface
        fun openBatterySettings(): Boolean {
            ArushiSetupManager.openBatterySettings(this@MainActivity)
            return true
        }

        @JavascriptInterface
        fun openAccessibilitySettings(): Boolean {
            ArushiSetupManager.openAccessibilitySettings(this@MainActivity)
            return true
        }

        @JavascriptInterface
        fun openNotificationSettings(): Boolean {
            ArushiSetupManager.openNotificationSettings(this@MainActivity)
            return true
        }

        @JavascriptInterface
        fun requestNotificationPermission(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runOnUiThread {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            return true
        }

        @JavascriptInterface
        fun goBack(): String {
            return globalBack()
        }

        @JavascriptInterface
        fun goHome(): String {
            val res = JSONObject()
            res.put("tool", "goHome")
            return try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                res.put("success", true)
                res.put("message", "Navigated to home screen")
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Cannot navigate to home: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun sendWhatsAppMessage(recipient: String?, message: String?): String {
            val res = JSONObject()
            res.put("tool", "sendWhatsAppMessage")
            val msgText = message?.trim() ?: ""
            if (msgText.isBlank()) {
                res.put("success", false)
                res.put("error", "Message text is empty")
                return res.toString()
            }

            return try {
                val target = recipient?.trim() ?: ""
                var targetPhone: String? = null
                var contactDisplayName: String? = null

                if (target.isNotBlank()) {
                    val digitsOnly = target.replace(Regex("[^0-9+]"), "")
                    if (digitsOnly.length >= 7) {
                        targetPhone = digitsOnly
                    } else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        val contact = findContact(target)
                        if (contact != null) {
                            contactDisplayName = contact.first
                            targetPhone = contact.second.replace(Regex("[^0-9+]"), "")
                        }
                    }
                }

                if (!targetPhone.isNullOrBlank()) {
                    var formattedPhone = targetPhone
                    if (formattedPhone.startsWith("+")) {
                        formattedPhone = formattedPhone.substring(1)
                    } else if (formattedPhone.length == 10) {
                        formattedPhone = "91$formattedPhone"
                    }

                    val encodedMsg = Uri.encode(msgText)
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=$encodedMsg")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val pm = packageManager
                    if (intent.resolveActivity(pm) != null) {
                        startActivity(intent)
                    } else {
                        intent.setPackage("com.whatsapp.w4b")
                        if (intent.resolveActivity(pm) != null) {
                            startActivity(intent)
                        } else {
                            val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(webIntent)
                        }
                    }

                    res.put("success", true)
                    res.put("recipient", contactDisplayName ?: targetPhone)
                    res.put("message", "WhatsApp chat opened for ${contactDisplayName ?: targetPhone} with message: '$msgText'")
                } else {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, msgText)
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val pm = packageManager
                    if (sendIntent.resolveActivity(pm) != null) {
                        startActivity(sendIntent)
                        res.put("success", true)
                        res.put("message", "WhatsApp share opened with message: '$msgText'")
                    } else {
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(msgText)}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(fallback)
                        res.put("success", true)
                        res.put("message", "WhatsApp opened with message")
                    }
                }
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Failed to send WhatsApp message: ${e.message}")
                res.toString()
            }
        }

        private fun resolveContactSearchTerms(query: String): List<String> {
            val clean = query.trim()
                .replace(Regex("^(my|meri|mera|mari|mara|maro|mare|apni|apna|ko|ne|call to|call|dial|message to|msg to|ફોન કરો|કોલ કરો|મેસેજ કરો)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(ko|pe|par|se|ne|karo|lagavo|moklo|mokalo|par message|ne message|ko call|ko message|ne call)$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (clean.isBlank()) return emptyList()

            val terms = mutableListOf(clean)
            val lower = clean.lowercase()
            when {
                lower in listOf("wife", "biwi", "patni", "gharwali", "wifey", "begum", "mrs", "vahu", "વાઇફ", "પત્ની", "વહુ", "વાઈફ") -> {
                    terms.addAll(listOf("wife", "biwi", "patni", "gharwali", "wifey", "begum", "mrs", "vahu", "વાઇફ", "પત્ની", "વહુ", "વાઈફ"))
                }
                lower in listOf("mom", "mother", "mummy", "maa", "ammi", "mataji", "ba", "મમ્મી", "મા", "બા", "માતાજી") -> {
                    terms.addAll(listOf("mom", "mother", "mummy", "maa", "ammi", "mataji", "ba", "મમ્મી", "મા", "બા", "માતાજી"))
                }
                lower in listOf("dad", "father", "papa", "pitaji", "abbu", "bapuji", "daddy", "પપ્પા", "બાપુજી", "પિતાજી") -> {
                    terms.addAll(listOf("dad", "father", "papa", "pitaji", "abbu", "bapuji", "daddy", "પપ્પા", "બાપુજી", "પિતાજી"))
                }
                lower in listOf("brother", "bhai", "bhaiya", "bro", "mota bhai", "nano bhai", "ભાઈ", "મોટાભાઈ") -> {
                    terms.addAll(listOf("brother", "bhai", "bhaiya", "bro", "mota bhai", "nano bhai", "ભાઈ", "મોટાભાઈ"))
                }
                lower in listOf("sister", "behan", "didi", "sis", "ben", "બેન", "દીદી") -> {
                    terms.addAll(listOf("sister", "behan", "didi", "sis", "ben", "બેન", "દીદી"))
                }
            }
            return terms.distinct()
        }

        private fun findContact(query: String): Pair<String, String>? {
            val terms = resolveContactSearchTerms(query)
            if (terms.isEmpty()) return null

            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = terms.joinToString(" OR ") { "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?" }
            val selectionArgs = terms.map { "%$it%" }.toTypedArray()

            val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIndex != -1) it.getString(nameIndex) ?: "" else ""
                    val number = if (numberIndex != -1) it.getString(numberIndex) ?: "" else ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        return Pair(name, number)
                    }
                }
            }
            return null
        }

        @JavascriptInterface
        fun openWhatsApp(): String {
            val res = JSONObject()
            res.put("tool", "openWhatsApp")
            return try {
                val pm = packageManager
                var intent = pm.getLaunchIntentForPackage("com.whatsapp")
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage("com.whatsapp.w4b")
                }
                if (intent == null) {
                    intent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
                    if (intent.resolveActivity(pm) == null) {
                        intent = null
                    }
                }

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    res.put("success", true)
                    res.put("message", "WhatsApp launch requested successfully")
                } else {
                    res.put("success", false)
                    res.put("error", "WhatsApp application is not installed on this device")
                }
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Failed to launch WhatsApp: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun openApp(appName: String?): String {
            val res = JSONObject()
            res.put("tool", "openApp")
            val raw = appName?.trim()?.lowercase() ?: ""
            if (raw.isBlank()) {
                res.put("success", false)
                res.put("error", "Application name was not specified")
                return res.toString()
            }

            val target = raw
                .replace(Regex("(ખોલો|ચાલુ કરો|ખોલ|ઓપન કરો|ખુલ્લો કરો|kholo|open karo|chalu karo|start karo)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(open|launch|start|chalu|khol)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()

            return try {
                val pm = packageManager
                var intent: Intent? = null
                var resolvedName = target

                when {
                    target == "whatsapp" || target.contains("whatsapp") || target.contains("વોટ્સએપ") || target.contains("વોટ્સઅપ") -> {
                        return openWhatsApp()
                    }
                    target == "youtube" || target.contains("youtube") || target.contains("યૂટ્યુબ") || target.contains("યુટ્યુબ") -> {
                        intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                        resolvedName = "YouTube"
                    }
                    target == "instagram" || target.contains("instagram") || target.contains("ઇન્સ્ટાગ્રામ") || target.contains("ઇન્સ્ટા") -> {
                        intent = pm.getLaunchIntentForPackage("com.instagram.android")
                        resolvedName = "Instagram"
                    }
                    target.contains("chrome") || target.contains("ક્રોમ") -> {
                        intent = pm.getLaunchIntentForPackage("com.android.chrome")
                        resolvedName = "Chrome"
                    }
                    target.contains("map") || target.contains("મેપ્સ") || target.contains("નકશા") -> {
                        intent = pm.getLaunchIntentForPackage("com.google.android.apps.maps")
                            ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
                        resolvedName = "Maps"
                    }
                    target.contains("setting") || target.contains("સેટિંગ") -> {
                        intent = Intent(Settings.ACTION_SETTINGS)
                        resolvedName = "Settings"
                    }
                    target.contains("phone") || target.contains("dialer") || target.contains("ફોન") || target.contains("ડાયલર") -> {
                        intent = Intent(Intent.ACTION_DIAL)
                        resolvedName = "Phone"
                    }
                    target.contains("message") || target.contains("sms") || target.contains("મેસેજ") || target.contains("સંદેશા") -> {
                        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this@MainActivity)
                        if (defaultSms != null) {
                            intent = pm.getLaunchIntentForPackage(defaultSms)
                        }
                        if (intent == null) {
                            intent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_MESSAGING)
                            }
                        }
                        resolvedName = "Messages"
                    }
                    target.contains("camera") || target.contains("કેમેરા") || target.contains("કેમેરો") -> {
                        intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                        resolvedName = "Camera"
                    }
                }

                // If not matched directly, search all installed launcher apps on device
                if (intent == null) {
                    val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val appsList = pm.queryIntentActivities(launcherIntent, 0)
                    for (resolveInfo in appsList) {
                        val label = resolveInfo.loadLabel(pm).toString().lowercase()
                        val pkgName = resolveInfo.activityInfo.packageName.lowercase()
                        if (label == target || label.contains(target) || pkgName.contains(target)) {
                            intent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                            resolvedName = resolveInfo.loadLabel(pm).toString()
                            if (intent != null) break
                        }
                    }
                }

                // Fallbacks for generic requests
                if (intent == null) {
                    if (target.contains("setting")) {
                        intent = Intent(Settings.ACTION_SETTINGS)
                        resolvedName = "Settings"
                    } else if (target.contains("camera")) {
                        intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                        resolvedName = "Camera"
                    }
                }

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    res.put("success", true)
                    res.put("message", "$resolvedName launched successfully")
                } else {
                    res.put("success", false)
                    res.put("error", "App '$appName' is not installed or could not be found.")
                }
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Failed to open $appName: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun openUrl(url: String?): String {
            val res = JSONObject()
            res.put("tool", "openUrl")
            if (url.isNullOrBlank()) {
                res.put("success", false)
                res.put("error", "URL is missing or empty")
                return res.toString()
            }

            val sanitizedUrl = url.trim()
            val uri = try { Uri.parse(sanitizedUrl) } catch (e: Exception) { null }
            val scheme = uri?.scheme?.lowercase()

            if (scheme != "https" && scheme != "http" && scheme != "tel" && scheme != "whatsapp") {
                res.put("success", false)
                res.put("error", "Only safe web schemes (http, https, tel, whatsapp) are allowed")
                return res.toString()
            }

            return try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                res.put("success", true)
                res.put("message", "Opened URL: $sanitizedUrl")
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Cannot open URL: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun makeCall(phoneNumber: String?): String {
            val res = JSONObject()
            res.put("tool", "makeCall")
            if (phoneNumber.isNullOrBlank()) {
                res.put("success", false)
                res.put("error", "Phone number is empty")
                return res.toString()
            }

            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            if (cleanNumber.length < 3) {
                res.put("success", false)
                res.put("error", "Invalid phone number format: $phoneNumber")
                return res.toString()
            }

            return try {
                val hasCallPerm = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                if (hasCallPerm) {
                    // Directly place the phone call immediately
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    res.put("success", true)
                    res.put("directCall", true)
                    res.put("message", "Direct call placed to $cleanNumber")
                } else {
                    // Fallback to dial pad and request permission for next time
                    runOnUiThread {
                        requestPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                    }
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    res.put("success", true)
                    res.put("directCall", false)
                    res.put("message", "Dialer opened for $cleanNumber (please grant Call permission for direct calling)")
                }
                res.put("phoneNumber", cleanNumber)
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Failed to place call: ${e.message}")
                res.toString()
            }
        }

        @JavascriptInterface
        fun callContact(contactName: String?): String {
            val res = JSONObject()
            res.put("tool", "callContact")
            val targetName = contactName?.trim()
            if (targetName.isNullOrBlank()) {
                res.put("success", false)
                res.put("error", "Contact name was not specified")
                return res.toString()
            }

            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                }
                res.put("success", false)
                res.put("permissionRequired", true)
                res.put("error", "Contacts permission is required to search contacts. Requesting permission.")
                return res.toString()
            }

            return try {
                val terms = resolveContactSearchTerms(targetName)
                if (terms.isEmpty()) {
                    res.put("success", false)
                    res.put("error", "Contact name cannot be empty")
                    return res.toString()
                }

                val matches = mutableListOf<Pair<String, String>>()
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val selection = terms.joinToString(" OR ") { "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?" }
                val selectionArgs = terms.map { "%$it%" }.toTypedArray()

                val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = if (nameIndex != -1) it.getString(nameIndex) ?: "" else ""
                        val number = if (numberIndex != -1) it.getString(numberIndex) ?: "" else ""
                        if (name.isNotBlank() && number.isNotBlank()) {
                            if (matches.none { m -> m.first.equals(name, ignoreCase = true) && m.second == number }) {
                                matches.add(name to number)
                            }
                        }
                    }
                }

                when {
                    matches.isEmpty() -> {
                        res.put("success", false)
                        res.put("matchesCount", 0)
                        res.put("error", "No contact found matching '${terms.first()}'")
                    }
                    matches.size == 1 -> {
                        val single = matches.first()
                        val hasCallPerm = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                        val cleanNum = single.second.replace(Regex("[^0-9+]"), "")

                        if (hasCallPerm) {
                            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNum")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(callIntent)
                            res.put("success", true)
                            res.put("directCall", true)
                            res.put("message", "Direct call placed to ${single.first} ($cleanNum)")
                        } else {
                            runOnUiThread {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                            }
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNum")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(dialIntent)
                            res.put("success", true)
                            res.put("directCall", false)
                            res.put("message", "Dialer opened for ${single.first} (grant Call permission for direct calling)")
                        }
                        val contactObj = JSONObject()
                        contactObj.put("name", single.first)
                        contactObj.put("number", single.second)
                        res.put("contact", contactObj)
                    }
                    else -> {
                        res.put("success", false)
                        res.put("clarificationNeeded", true)
                        res.put("matchesCount", matches.size)
                        res.put("error", "Found ${matches.size} matching contacts for '${terms.first()}'. Clarification needed.")
                        val arr = JSONArray()
                        for (m in matches.take(5)) {
                            val obj = JSONObject()
                            obj.put("name", m.first)
                            obj.put("number", m.second)
                            arr.put(obj)
                        }
                        res.put("matches", arr)
                    }
                }
                res.toString()
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", "Error searching contacts: ${e.message}")
                res.toString()
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}

