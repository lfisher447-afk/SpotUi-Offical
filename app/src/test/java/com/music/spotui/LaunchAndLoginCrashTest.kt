package com.music.spotui

import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.ui.notification.PlaybackService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
class LaunchAndLoginCrashTest {

    private var serviceController: ServiceController<PlaybackService>? = null

    private val playbackServiceRule = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<MyApplication>()
            val component = ComponentName(app, PlaybackService::class.java)
            val controller = Robolectric.buildService(PlaybackService::class.java).create()
            serviceController = controller
            val service = controller.get()
            val libIntent = Intent(MediaLibraryService.SERVICE_INTERFACE).setComponent(component)
            val sessionIntent = Intent(MediaSessionService.SERVICE_INTERFACE).setComponent(component)
            val plainIntent = Intent().setComponent(component)
            val binder = service.onBind(libIntent)
            val shadowApp = Shadows.shadowOf(app)
            shadowApp.setComponentNameAndServiceForBindService(component, binder)
            shadowApp.setComponentNameAndServiceForBindServiceForIntent(libIntent, component, binder)
            shadowApp.setComponentNameAndServiceForBindServiceForIntent(sessionIntent, component, binder)
            shadowApp.setComponentNameAndServiceForBindServiceForIntent(plainIntent, component, binder)
        }

        override fun after() {
            serviceController?.destroy()
            serviceController = null
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(playbackServiceRule).around(composeRule)

    @Test
    fun coldLaunch_rendersLoginScreen_andCookieDialog_withoutCrashing() {
        val uncaught = AtomicReference<Throwable?>(null)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            uncaught.compareAndSet(null, e)
        }

        try {
            val app = ApplicationProvider.getApplicationContext<MyApplication>()
            assertNotNull(app)
            assertTrue(SpotifySession.spDc(app).isBlank())

            composeRule.mainClock.autoAdvance = false
            composeRule.mainClock.advanceTimeBy(500)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // Verify Login screen rendered
            composeRule.onNodeWithText("Log in to Spotify").assertExists()

            // Open Manual Cookie Login dialog
            composeRule.onNodeWithContentDescription("Manual cookie login").performClick()
            composeRule.mainClock.advanceTimeBy(300)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onNodeWithText("Manual Spotify Cookie Login").assertExists()
            composeRule.onNodeWithText("Open Spotify in Browser").assertExists()

            // Dismiss Manual Cookie Login dialog
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.mainClock.advanceTimeBy(300)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // Trigger WebView reload button safely
            composeRule.onNodeWithContentDescription("Reload page").performClick()
            composeRule.mainClock.advanceTimeBy(300)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertNull("Uncaught exception during launch/login flow", uncaught.get())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}
