package com.jesper3d.tablet

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Checks the real Activity without connecting hardware or sending commands. */
class TabletSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun disconnectedTabletLaunchesAndKeepsMachineControlsDisabled() {
        compose.onNodeWithText("Jesper 3D").assertIsDisplayed()
        compose.onNodeWithText("Conectar USB").assertIsEnabled()
        compose.onNodeWithText("Aplicar").assertIsNotEnabled()
        screenshot("tablet-overview.png")
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("La forma de tu cama").assertIsDisplayed()
        compose.onNodeWithText("Leer malla").assertIsNotEnabled()
        screenshot("tablet-bed.png")
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Leer ajustes").assertIsNotEnabled()
        compose.onNodeWithText("Guardar en EEPROM").assertIsNotEnabled()
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("Todavía no hay una conexión USB.").assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
