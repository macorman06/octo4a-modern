package com.jesper3d.pricing

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class PricingTest {
    private fun d(s: String) = BigDecimal(s)
    // Synthetic fixtures. User-specific rates and purchase history are kept outside this public repository.
    private val material = MaterialRate("sample", "Material de prueba", "g", d("1000"), listOf(d("20")))
    private val model = PricingModel("1.0", listOf(DesignRate("sample", "Prueba", d("60"), d("10"))),
        d("2"), d("6"), d("0"), d("0"), d("100"), d("0.20"), false, listOf(material))
    private fun input() = QuoteInput(listOf(MaterialUse(material, d("100"))), true, d("60"), d("10"), d("120"), d("30"))
    private fun eq(expected: String, actual: BigDecimal?) { assertNotNull(actual); assertEquals(0, d(expected).compareTo(actual!!)) }
    @Test fun ownWorkIsNotDeductedTwice() {
        val r = Pricing.evaluate(model, input()).totals!!
        eq("2", r.expenses); eq("19", r.customerTotal); eq("17", r.estimatedProfit)
        eq("11.33", r.activeHourly) // Machine occupation is excluded from 90 active minutes.
    }
    @Test fun extraMarginUsesResultingPriceRatherThanMarkup() {
        val r = Pricing.evaluate(model.copy(additionalMargin = d("0.20")), input()).totals!!
        eq("23.75", r.calculatedNet); eq("21.75", r.estimatedProfit)
    }
    @Test fun taxIsExcludedFromIncomeAndProfit() {
        val r = Pricing.evaluate(model.copy(tax = d("0.10")), input()).totals!!
        eq("1.90", r.tax); eq("20.90", r.customerTotal); eq("17", r.estimatedProfit)
    }
    @Test fun expenseCanBeRecordedWithoutBeingCharged() {
        val r = Pricing.evaluate(model, input().copy(extras = listOf(ExtraExpense("Envío", d("5"), false)))).totals!!
        eq("19", r.customerTotal); eq("7", r.expenses); eq("12", r.estimatedProfit)
    }
    @Test fun recordedAndChargedExpenseIsDeductedOnlyOnce() {
        val r = Pricing.evaluate(model, input().copy(extras = listOf(ExtraExpense("Envío", d("5"), true)))).totals!!
        eq("24", r.customerTotal); eq("17", r.estimatedProfit)
    }
    @Test fun electricityIsOptionalAndSeparatelyChargeable() {
        eq("0", Pricing.evaluate(model, input()).totals!!.electricity)
        val r = Pricing.evaluate(model, input().copy(electricityIncluded = true)).totals!!
        eq("0.04", r.electricity); eq("16.96", r.estimatedProfit)
        eq("17", Pricing.evaluate(model, input().copy(electricityIncluded = true, electricityCharged = true)).totals!!.estimatedProfit)
    }
    @Test fun materialSubtotalIsRoundedAfterSummingUnroundedLines() {
        val tiny = material.copy(purchaseQuantity = d("1"), prices = listOf(d("0.004")))
        val r = Pricing.evaluate(model, input().copy(materials = listOf(MaterialUse(tiny, d("1")), MaterialUse(tiny, d("1"))))).totals!!
        eq("0.01", r.materials)
    }
    @Test fun electricityRoundsOnceForWholeDuration() {
        val r = Pricing.evaluate(model, input().copy(printingMinutes = d("1"), electricityIncluded = true)).totals!!
        eq("0.00", r.electricity)
    }
    @Test fun exactHalfCentRoundsUpAndUnitCountsMustBeWhole() {
        eq("0.01", d("0.005").cents())
        val component = material.copy(unit = "ud", purchaseQuantity = d("1"))
        assertNull(Pricing.evaluate(model, input().copy(materials = listOf(MaterialUse(component, d("0.5"))))).totals)
    }
    @Test fun repeatedPriceObservationsAreNotDeduplicated() {
        eq("4", material.copy(purchaseQuantity = d("1"), prices = listOf(d("3"), d("3"), d("6"))).unitPrice)
    }
    @Test fun rollAndSpoolUnitsConvertBeforeRounding() {
        eq("0.02", material.unitPrice)
        eq("1.25", material.copy(purchaseQuantity = d("4"), prices = listOf(d("5"))).unitPrice)
    }
    @Test fun actualLotOverrideIsExplicit() {
        val r = Pricing.evaluate(model, input().copy(materials = listOf(MaterialUse(material, d("100"), d("0.03"))))).totals!!
        eq("3", r.materials)
    }
    @Test fun unknownInputsNeverProduceDefinitiveTotals() {
        val r = Pricing.evaluate(model, input().copy(printingMinutes = null))
        assertNull(r.totals); assertTrue(r.missing.any { it.contains("impresión") })
    }
    @Test fun zeroDesignRequiresNoNewWorkTariff() {
        eq("0", Pricing.evaluate(model, input().copy(designMinutes = d("0.0"), designHourly = null)).totals!!.design)
    }
    @Test fun noMaterialsRequiresExplicitReview() {
        assertNull(Pricing.evaluate(model, input().copy(materials = emptyList(), materialsReviewed = false)).totals)
        eq("0", Pricing.evaluate(model, input().copy(materials = emptyList())).totals!!.materials)
    }
    @Test fun zeroRevenueAndActiveTimeDoNotDivideByZero() {
        val r = Pricing.evaluate(model, input().copy(agreedNet = d("0"), actualActiveMinutes = d("0"))).totals!!
        assertNull(r.effectiveMargin); assertNull(r.activeHourly); eq("-2", r.estimatedProfit)
    }
    @Test fun manualAgreementChangesProfitWithoutChangingOriginalRates() {
        val r = Pricing.evaluate(model, input().copy(agreedNet = d("15"))).totals!!
        eq("19", r.calculatedNet); eq("-4", r.commercialAdjustment); eq("13", r.estimatedProfit)
    }
    @Test fun actualActiveTimeIsKeptSeparateFromQuotedMachineTime() {
        val r = Pricing.evaluate(model, input().copy(actualActiveMinutes = d("60"))).totals!!
        eq("17", r.activeHourly); assertTrue(r.activeTimeActual)
    }
    @Test fun negativeConsumptionAndMissingExpenseAreRejected() {
        assertNull(Pricing.evaluate(model, input().copy(materials = listOf(MaterialUse(material, d("-1"))))).totals)
        assertNull(Pricing.evaluate(model, input().copy(extras = listOf(ExtraExpense("Pendiente", null, true)))).totals)
    }
    @Test fun customerOutputContainsOnlyPublicFields() {
        val text = CustomerQuote("Pieza", d("19.00")).text { "$it EUR" }
        assertEquals("Presupuesto · Pieza\nPrecio final total: 19.00 EUR", text)
        assertFalse(text.contains("beneficio")); assertFalse(text.contains("Material"))
    }
    @Test fun invalidMarginIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { model.copy(additionalMargin = d("1")) }
    }
    @Test fun decimalInputSupportsSpanishWithoutExponentOrNegativeValues() {
        eq("1.5", decimal("1,5")); assertNull(decimal("1e4")); assertNull(decimal("-1")); assertNull(decimal(""))
    }
}
