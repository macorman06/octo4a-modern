package com.jesper3d.tablet

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jesper3d.pricing.*
import com.jesper3d.tablet.business.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.util.UUID

class BusinessTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun d(s: String) = BigDecimal(s)
    private fun fixture() = PricingModel("1.0", listOf(DesignRate("test", "DATOS DE PRUEBA", d("60"), d("10"))),
        d("2"), d("6"), d("0"), d("0"), d("100"), d("0.20"), false,
        listOf(MaterialRate("test-material", "Material de prueba", "g", d("1000"), listOf(d("20"), d("20"), d("23")))))

    @Test fun profileRoundTripsPersistsAndRejectsIncompatibleImportWithoutOverwriting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "pricing-test-${UUID.randomUUID()}"
        val model = fixture()
        val encoded = PricingJson.encode(model)
        assertEquals(model, PricingJson.parse(encoded))
        PricingStore(context, name).save(model)
        assertEquals(model, PricingStore(context, name).model.value)
        assertEquals(3, PricingStore(context, name).model.value!!.materials.single().prices.size)
        assertThrows(IllegalArgumentException::class.java) { PricingJson.parse(encoded.replace("\"version_modelo\": \"1.0\"", "\"version_modelo\": \"9.0\"")) }
        assertThrows(IllegalArgumentException::class.java) { PricingJson.parse(encoded.replace("\"trabajo_propio_es_gasto\": false", "\"trabajo_propio_es_gasto\": true")) }
        assertEquals(model, PricingStore(context, name).model.value)
    }
    @Test fun offlineCalculationExportsOnlyTheCustomerProjection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PricingStore(context, "pricing-ui-test-${UUID.randomUUID()}").apply { save(fixture()) }
        var exported: String? = null
        compose.runOnUiThread { compose.activity.setContent { JesperTheme {
            BusinessScreen(store, {}, {}, { _, _, text -> exported = text })
        } } }
        compose.onNodeWithTag("quote-incomplete").performScrollTo().assertExists()
        compose.onNodeWithTag("quote-concept").performScrollTo().performTextInput("PRUEBA · Pieza")
        compose.onNodeWithTag("materials-reviewed").performScrollTo().performClick()
        listOf("design-minutes" to "60", "design-rate" to "10", "printing-minutes" to "120", "assembly-minutes" to "30").forEach { (tag, value) ->
            compose.onNodeWithTag(tag).performScrollTo().performTextInput(value)
        }
        shell("input keyevent 4") // Close the IME before capturing the complete result.
        compose.onNodeWithTag("estimated-profit").performScrollTo().assertExists()
        screenshot("pricing-calculation-tablet.png")
        compose.onNodeWithTag("preview-customer").performScrollTo().performClick()
        compose.onNodeWithTag("customer-preview").assertTextContains("17,00", substring = true)
        screenshot("pricing-customer-tablet.png")
        compose.onNodeWithText("Exportar texto para cliente").performClick()
        compose.runOnIdle {
            assertTrue(exported!!.contains("PRUEBA · Pieza")); assertTrue(exported!!.contains("17,00"))
            assertFalse(exported!!.contains("beneficio", true)); assertFalse(exported!!.contains("tarifa", true))
            assertFalse(exported!!.contains("material", true)); assertFalse(exported!!.contains("10,00"))
        }
    }
    @Test fun modelScreensAdaptToTabletAndCompactDarkLargeText() {
        val app = compose.activity.application as PrinterApplication
        compose.runOnIdle { app.pricing.save(fixture()) }
        compose.onNodeWithTag("open-pricing").performClick()
        compose.onNodeWithTag("pricing-tab-2").performClick()
        compose.onNodeWithText("Editar Material de prueba").performScrollTo().performClick()
        screenshot("pricing-material-tablet.png")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("pricing-tab-1").performClick()
        screenshot("pricing-tablet.png")
        try {
            shell("settings put system font_scale 1.3")
            shell("cmd uimode night yes")
            shell("wm size 432x960")
            compose.waitUntil(15000) { compose.activity.resources.configuration.screenWidthDp < 600 }
            compose.onNodeWithText("Presupuestos").assertIsDisplayed()
            compose.onNodeWithTag("pricing-tab-1").performClick()
            compose.onNodeWithTag("model-printing").performScrollTo().assertIsDisplayed()
            compose.waitUntil(10000) {
                !androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                    .isAppearanceLightStatusBars
            }
            screenshot("pricing-compact-dark.png")
            compose.onNodeWithTag("pricing-tab-2").performClick()
            compose.onNodeWithText("Editar Material de prueba").performScrollTo().performClick()
            screenshot("pricing-material-compact-dark.png")
            compose.onNodeWithText("Cancelar").performClick()
        } finally {
            shell("settings put system font_scale 1.0")
            shell("cmd uimode night no")
            shell("wm size 1600x1000")
        }
    }
    @Test fun replacingModelKeepsMissingMaterialsVisibleAndUpdatesSelectedDesignTariff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PricingStore(context, "pricing-replace-${UUID.randomUUID()}").apply { save(fixture()) }
        compose.runOnUiThread { compose.activity.setContent { JesperTheme { BusinessScreen(store, {}, {}, { _, _, _ -> }) } } }
        compose.onNodeWithText("Añadir material").performScrollTo().performClick()
        compose.onNodeWithText("Material de prueba").performClick()
        compose.onNodeWithTag("material-test-material").performScrollTo().performTextInput("100")
        compose.onNodeWithTag("materials-reviewed").performScrollTo().performClick()
        compose.onNodeWithText("DATOS DE PRUEBA").performScrollTo().performClick()
        compose.onNodeWithTag("printing-minutes").performScrollTo().performTextInput("0")
        compose.onNodeWithTag("assembly-minutes").performScrollTo().performTextInput("0")
        compose.onNodeWithTag("preview-customer").performScrollTo().assertExists()
        compose.onNodeWithTag("material-test-material").performScrollTo().performTextReplacement("100\n999")
        compose.onNodeWithTag("quote-incomplete").performScrollTo().assertExists()
        compose.onNodeWithTag("material-test-material").performScrollTo().performTextReplacement("100")
        compose.onNodeWithTag("preview-customer").performScrollTo().assertExists()
        compose.runOnIdle { store.save(fixture().copy(materials = emptyList(),
            design = listOf(fixture().design.single().copy(hourly = d("12"))))) }
        compose.onNodeWithTag("quote-incomplete").performScrollTo().assertExists()
        compose.onNodeWithTag("preview-customer").assertDoesNotExist()
        compose.onNodeWithTag("missing-material").performScrollTo().assertExists()
        compose.onNodeWithTag("design-rate").performScrollTo().assertTextContains("12", substring = true)
    }
    @Test fun editingPercentagesKeepsFractionsAndDoesNotRoundToZero() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PricingStore(context, "pricing-percent-${UUID.randomUUID()}").apply { save(fixture()) }
        compose.runOnUiThread { compose.activity.setContent { JesperTheme { BusinessScreen(store, {}, {}, { _, _, _ -> }) } } }
        compose.onNodeWithTag("pricing-tab-1").performClick()
        compose.onNodeWithTag("model-margin").performScrollTo().performTextReplacement("1")
        compose.onNodeWithTag("model-tax").performScrollTo().performTextReplacement("7,5")
        compose.onNodeWithText("Guardar tarifas").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(0, d("0.01").compareTo(store.model.value!!.additionalMargin))
            assertEquals(0, d("0.075").compareTo(store.model.value!!.tax))
        }
    }
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
