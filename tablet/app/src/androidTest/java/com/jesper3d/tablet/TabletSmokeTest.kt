package com.jesper3d.tablet

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import com.jesper3d.protocol.*

/** Checks the real Activity without connecting hardware or sending commands. */
class TabletSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun disconnectedTabletLaunchesAndKeepsMachineControlsDisabled() {
        compose.onNodeWithText("Jesper 3D").assertIsDisplayed()
        compose.onNodeWithText("Conectar USB").assertIsEnabled()
        compose.onNodeWithText("Tu cama, punto a punto").assertIsDisplayed()
        compose.onNodeWithText("Leer malla").assertIsNotEnabled()
        compose.onNodeWithText("Medir cama").assertIsNotEnabled()
        compose.onNodeWithText("Guardar en impresora").assertIsDisplayed().assertIsNotEnabled()
        screenshot("tablet-bed.png")
        compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("Aplicar").assertIsNotEnabled()
        screenshot("tablet-overview.png")
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Leer ajustes").assertIsNotEnabled()
        compose.onNodeWithText("Guardar en EEPROM").assertIsNotEnabled()
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("Exportar diagnóstico").assertIsEnabled()
    }
    @Test fun archivedMeshCanBeInspectedAndComparedWithoutConnectingAPrinter() {
        val mesh = Mesh((0..4).associateWith { y -> (0..4).map { x -> (x - 2) * 0.04 + (y - 2) * 0.015 } }, 5)
        val old = Mesh(mesh.rows.mapValues { (_, row) -> row.map { it!! - 0.05 } }, 5)
        val record = MeshRecord("new", 1792080000000L, MeshOrigin.MEASURED, mesh)
        val reference = MeshRecord("old", 1792076400000L, MeshOrigin.READ, old)
        val application = compose.activity.application as PrinterApplication
        compose.runOnUiThread {
            compose.activity.setContent {
                JesperTheme {
                    PrinterScreen(UiState(message = "VISTA DE PRUEBA · DATOS SIMULADOS",
                        liveRecord = record, history = listOf(record, reference), meshSaveState = MeshSaveState.UNSAVED),
                        application.printer, emptyList(), {}, { _, _ -> }, { _, _, _ -> })
                }
            }
        }
        compose.onNodeWithTag("mesh-map").assertExists().performTouchInput { click(center) }
        compose.onNodeWithTag("selected-point").assertTextContains("Punto [2, 2]", substring = true)
        compose.onNodeWithText("Exportar CSV").assertIsEnabled()
        compose.onNodeWithText("Guardar en impresora").assertIsDisplayed().assertIsNotEnabled()
        screenshot("tablet-mesh-simulated.png")
        compose.onNodeWithTag("compare-mesh").performScrollTo().performClick()
        compose.onNodeWithText("Cambio máximo").assertExists()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        // UTP collects this directory before uninstalling the test app and its data.
        val directory = File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
