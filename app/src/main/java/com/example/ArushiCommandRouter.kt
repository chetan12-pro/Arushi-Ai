package com.example

import android.content.Context
import android.util.Log

data class FastCommandResult(
    val handled: Boolean,
    val action: String,
    val state: String,
    val success: Boolean,
    val spokenReply: String,
    val details: String
)

/**
 * ArushiCommandRouter parses and executes deterministic local voice commands
 * without sending them to Gemini Live WebSocket. This provides instant ~10ms execution.
 */
object ArushiCommandRouter {
    private const val TAG = "ArushiCommandRouter"

    fun execute(context: Context, rawText: String): FastCommandResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return FastCommandResult(false, "NONE", "IGNORED", false, "", "Empty input")
        }

        // Clean wake prefix: "hello arushi", "hey arushi", "arushi", "bhai arushi", "ok arushi"
        var text = trimmed.lowercase()
            .replace(Regex("^(hello\\s+arushi|hey\\s+arushi|ok\\s+arushi|bhai\\s+arushi|arushi|હેલો\\s+આરુષી|આરુષી)[,\\s]*", RegexOption.IGNORE_CASE), "")
            .trim()

        // Remove trailing polite words
        text = text.replace(Regex("[,\\s]*(please|plz|na|karo|bhai|ji)$", RegexOption.IGNORE_CASE), "").trim()

        Log.d(TAG, "Evaluating fast command: original='$rawText', cleaned='$text'")

        // 1. Check if user only said the wake word / greeting
        if (text.isBlank() || text in listOf("hello", "hey", "hi", "suno", "sun rahe ho")) {
            return FastCommandResult(
                handled = true,
                action = "WAKE_GREETING",
                state = "SUCCESS",
                success = true,
                spokenReply = "Ji Sir, boliye. Main sun rahi hoon.",
                details = "Wake word acknowledged"
            )
        }

        if (text in listOf("tum kaise ho", "kaise ho", "kya haal hai", "aap kaise ho")) {
            return FastCommandResult(
                handled = true,
                action = "GREETING",
                state = "SUCCESS",
                success = true,
                spokenReply = "Sir, main bilkul theek hoon. Aap sunaiye.",
                details = "Polite status acknowledged"
            )
        }

        // 2. Flashlight controls
        if (text.matches(Regex(".*(flashlight|torch|light)\\s+(on|chalu|start|jalao).*")) ||
            text in listOf("flashlight on", "torch on", "light on", "turn on flashlight", "turn on torch")
        ) {
            val res = ArushiPhoneController.setFlashlight(context, true)
            return FastCommandResult(
                handled = true,
                action = "FLASHLIGHT_ON",
                state = res.state,
                success = res.success,
                spokenReply = res.spokenReply ?: "Ji Sir, flashlight on kar di hai.",
                details = res.message
            )
        }

        if (text.matches(Regex(".*(flashlight|torch|light)\\s+(off|band|stop|bujhao).*")) ||
            text in listOf("flashlight off", "torch off", "light off", "turn off flashlight", "turn off torch")
        ) {
            val res = ArushiPhoneController.setFlashlight(context, false)
            return FastCommandResult(
                handled = true,
                action = "FLASHLIGHT_OFF",
                state = res.state,
                success = res.success,
                spokenReply = res.spokenReply ?: "Ji Sir, flashlight off kar di hai.",
                details = res.message
            )
        }

        // 3. System Navigation
        if (text in listOf("back", "go back", "piche", "peeche jao", "back jao", "back karo")) {
            val res = ArushiPhoneController.globalBack(context)
            return FastCommandResult(
                handled = true,
                action = "GLOBAL_BACK",
                state = res.state,
                success = res.success,
                spokenReply = res.spokenReply ?: if (res.success) "Ji Sir." else "Sir, global phone control ke liye Accessibility Service enable karni hogi.",
                details = res.message
            )
        }

        if (text in listOf("home", "go home", "home screen", "main screen", "home jao")) {
            val res = ArushiPhoneController.globalHome(context)
            return FastCommandResult(
                handled = true,
                action = "GLOBAL_HOME",
                state = res.state,
                success = res.success,
                spokenReply = res.spokenReply ?: "Ji Sir.",
                details = res.message
            )
        }

        if (text in listOf("recent apps", "recent", "recents", "recent applications", "recent app")) {
            val res = ArushiPhoneController.globalRecents(context)
            return FastCommandResult(
                handled = true,
                action = "GLOBAL_RECENTS",
                state = res.state,
                success = res.success,
                spokenReply = res.spokenReply ?: if (res.success) "Ji Sir." else "Sir, recent apps ke liye Accessibility Service enable karni hogi.",
                details = res.message
            )
        }

        // 4. Volume controls
        if (text in listOf("volume up", "volume badhao", "awaaz badhao", "sound badhao", "increase volume", "volume tez") ||
            (text.contains("volume") && (text.contains("up") || text.contains("badha") || text.contains("tez") || text.contains("high")))) {
            val res = ArushiPhoneController.adjustVolume(context, true)
            return FastCommandResult(
                handled = true,
                action = "VOLUME_UP",
                state = res.state,
                success = res.success,
                spokenReply = res.spokenReply ?: "Ji Sir, volume badha diya hai.",
                details = res.message
            )
        }

        if (text in listOf("volume down", "volume kam", "volume kam karo", "awaaz kam", "awaaz kam karo", "sound kam", "decrease volume", "volume slow", "volume low") ||
            (text.contains("volume") && (text.contains("down") || text.contains("kam") || text.contains("slow") || text.contains("low") || text.contains("ghata")))) {
            val res = ArushiPhoneController.adjustVolume(context, false)
            return FastCommandResult(
                handled = true,
                action = "VOLUME_DOWN",
                state = res.state,
                success = res.success,
                spokenReply = res.spokenReply ?: "Ji Sir, volume kam kar diya hai.",
                details = res.message
            )
        }

        // 5. YouTube Search (e.g., "YouTube par Taarak Mehta Ka Ooltah Chashmah search karo")
        val ytSearchMatch = Regex("^(youtube\\s+par|youtube\\s+pe|search\\s+youtube\\s+for|youtube\\s+mein|youtube\\s+search)\\s+(.+?)((\\s+(search|karo|dekho|chalao|play))?$)", RegexOption.IGNORE_CASE).find(text)
        if (ytSearchMatch != null) {
            var q = ytSearchMatch.groupValues[2].trim()
            q = q.replace(Regex("\\s+(search|karo|dekho|chalao|play)$", RegexOption.IGNORE_CASE), "").trim()
            if (q.isNotBlank()) {
                val res = ArushiPhoneController.searchYouTube(context, q)
                return FastCommandResult(
                    handled = true,
                    action = "YOUTUBE_SEARCH",
                    state = res.state,
                    success = res.success,
                    spokenReply = res.spokenReply ?: "Ji Sir, YouTube par $q search kar rahi hoon.",
                    details = res.message
                )
            }
        }

        // 6. WhatsApp Search (e.g., "WhatsApp mein Shilpa search karo")
        val waSearchMatch = Regex("^(whatsapp\\s+mein|whatsapp\\s+pe|search\\s+whatsapp\\s+for|whatsapp\\s+search)\\s+(.+?)((\\s+(search|karo|dhundo))?$)", RegexOption.IGNORE_CASE).find(text)
        if (waSearchMatch != null) {
            val q = waSearchMatch.groupValues[2].trim().replace(Regex("\\s+(search|karo|dhundo)$", RegexOption.IGNORE_CASE), "").trim()
            if (q.isNotBlank()) {
                val res = ArushiPhoneController.searchWhatsApp(context, q)
                return FastCommandResult(
                    handled = true,
                    action = "WHATSAPP_SEARCH",
                    state = res.state,
                    success = res.success,
                    spokenReply = res.spokenReply ?: "Ji Sir, WhatsApp open kar diya hai.",
                    details = res.message
                )
            }
        }

        // 7. App launches (deterministic common apps)
        val appKeywords = listOf(
            "youtube" to "youtube",
            "whatsapp" to "whatsapp",
            "instagram" to "instagram",
            "chrome" to "chrome",
            "google" to "google",
            "settings" to "settings",
            "camera" to "camera",
            "maps" to "maps",
            "contacts" to "contacts",
            "phone" to "phone",
            "messages" to "messages",
            "gallery" to "gallery",
            "clock" to "clock",
            "calculator" to "calculator"
        )

        for ((kw, appIdentifier) in appKeywords) {
            if (text == "$kw open karo" || text == "open $kw" || text == "$kw kholo" || text == "$kw chalu karo" || text == kw || text == "khol $kw") {
                val res = ArushiPhoneController.openApp(context, appIdentifier)
                return FastCommandResult(
                    handled = true,
                    action = "OPEN_APP",
                    state = res.state,
                    success = res.success,
                    spokenReply = res.spokenReply ?: "Ji Sir, $kw open kar rahi hoon.",
                    details = res.message
                )
            }
        }

        // Not a simple deterministic local command -> Route to Gemini Live intelligence!
        return FastCommandResult(
            handled = false,
            action = "GEMINI_REQUIRED",
            state = "PENDING_GEMINI",
            success = false,
            spokenReply = "",
            details = "Request requires natural language understanding from Gemini Live."
        )
    }
}
