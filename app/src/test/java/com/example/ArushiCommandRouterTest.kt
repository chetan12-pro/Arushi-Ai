package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArushiCommandRouterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testWakeGreetingDirect() {
        val res = ArushiCommandRouter.execute(context, "Hello Arushi")
        assertTrue(res.handled)
        assertEquals("WAKE_GREETING", res.action)
        assertEquals("Ji Sir, boliye. Main sun rahi hoon.", res.spokenReply)
    }

    @Test
    fun testPoliteGreeting() {
        val res = ArushiCommandRouter.execute(context, "Arushi tum kaise ho")
        assertTrue(res.handled)
        assertEquals("GREETING", res.action)
        assertEquals("Sir, main bilkul theek hoon. Aap sunaiye.", res.spokenReply)
    }

    @Test
    fun testFlashlightCommands() {
        val onRes = ArushiCommandRouter.execute(context, "Hello Arushi, flashlight on karo")
        assertTrue(onRes.handled)
        assertEquals("FLASHLIGHT_ON", onRes.action)

        val offRes = ArushiCommandRouter.execute(context, "torch off karo")
        assertTrue(offRes.handled)
        assertEquals("FLASHLIGHT_OFF", offRes.action)
    }

    @Test
    fun testNavigationCommands() {
        val backRes = ArushiCommandRouter.execute(context, "back")
        assertTrue(backRes.handled)
        assertEquals("GLOBAL_BACK", backRes.action)

        val homeRes = ArushiCommandRouter.execute(context, "home")
        assertTrue(homeRes.handled)
        assertEquals("GLOBAL_HOME", homeRes.action)

        val recentsRes = ArushiCommandRouter.execute(context, "recent apps")
        assertTrue(recentsRes.handled)
        assertEquals("GLOBAL_RECENTS", recentsRes.action)
    }

    @Test
    fun testVolumeCommands() {
        val upRes = ArushiCommandRouter.execute(context, "volume up")
        assertTrue(upRes.handled)
        assertEquals("VOLUME_UP", upRes.action)

        val downRes = ArushiCommandRouter.execute(context, "volume kam karo")
        assertTrue(downRes.handled)
        assertEquals("VOLUME_DOWN", downRes.action)
    }

    @Test
    fun testYouTubeSearchCommand() {
        val res = ArushiCommandRouter.execute(context, "YouTube par Taarak Mehta Ka Ooltah Chashmah search karo")
        assertTrue(res.handled)
        assertEquals("YOUTUBE_SEARCH", res.action)
    }

    @Test
    fun testComplexQueryRequiresGemini() {
        val res = ArushiCommandRouter.execute(context, "Tell me a story about quantum physics and AI")
        assertFalse(res.handled)
        assertEquals("GEMINI_REQUIRED", res.action)
    }
}
