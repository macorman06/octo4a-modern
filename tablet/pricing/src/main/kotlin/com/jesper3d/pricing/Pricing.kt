package com.jesper3d.pricing

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val precision = MathContext.DECIMAL128
private val zero = BigDecimal.ZERO
private val sixty = BigDecimal("60")
fun BigDecimal.cents(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
fun decimal(text: String): BigDecimal? = text.trim().replace(',', '.').takeIf {
    it.length <= 30 && it.matches(Regex("\\d+(?:\\.\\d{1,12})?"))
}?.toBigDecimalOrNull()

data class DesignRate(val id: String, val name: String, val minutes: BigDecimal, val hourly: BigDecimal)
data class MaterialRate(val id: String, val name: String, val unit: String,
    val purchaseQuantity: BigDecimal, val prices: List<BigDecimal>) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && name.isNotBlank() && name.length <= 160 && unit.isNotBlank() && unit.length <= 20)
        require(purchaseQuantity > zero && prices.all { it >= zero })
        require(unit != "ud" || purchaseQuantity.stripTrailingZeros().scale() <= 0) { "Las unidades del pack deben ser enteras." }
    }
    // Repeated prices are independent observations. Dates and stock cannot be inferred.
    val unitPrice: BigDecimal? get() = if (prices.isEmpty()) null else
        prices.fold(zero, BigDecimal::add).divide(BigDecimal(prices.size), precision).divide(purchaseQuantity, precision)
}
data class PricingModel(val version: String, val design: List<DesignRate>,
    val printingHourly: BigDecimal, val assemblyHourly: BigDecimal,
    val additionalMargin: BigDecimal, val tax: BigDecimal,
    val watts: BigDecimal, val electricityPerKwh: BigDecimal, val electricityDefault: Boolean,
    val materials: List<MaterialRate>) {
    init {
        require(version == "1.0") { "Versión de modelo no compatible." }
        require(design.isNotEmpty() && design.map { it.id }.distinct().size == design.size)
        require(design.all { it.id.isNotBlank() && it.minutes >= zero && it.hourly >= zero })
        require(listOf(printingHourly, assemblyHourly, tax, watts, electricityPerKwh).all { it >= zero })
        require(additionalMargin >= zero && additionalMargin < BigDecimal.ONE)
        require(materials.size <= 200 && materials.map { it.id }.distinct().size == materials.size)
    }
}
data class MaterialUse(val material: MaterialRate, val quantity: BigDecimal?, val unitPriceOverride: BigDecimal? = null)
data class ExtraExpense(val name: String, val amount: BigDecimal?, val chargeToCustomer: Boolean)
data class QuoteInput(
    val materials: List<MaterialUse>, val materialsReviewed: Boolean,
    val designMinutes: BigDecimal?, val designHourly: BigDecimal?,
    val printingMinutes: BigDecimal?, val assemblyMinutes: BigDecimal?,
    val electricityIncluded: Boolean = false, val electricityCharged: Boolean = false,
    val extras: List<ExtraExpense> = emptyList(),
    val agreedNet: BigDecimal? = null, val actualActiveMinutes: BigDecimal? = null,
)
data class QuoteTotals(
    val materials: BigDecimal, val design: BigDecimal, val printing: BigDecimal, val assembly: BigDecimal,
    val electricity: BigDecimal, val extraExpenses: BigDecimal, val chargedExpenses: BigDecimal,
    val base: BigDecimal, val calculatedNet: BigDecimal, val agreedNet: BigDecimal,
    val commercialAdjustment: BigDecimal, val tax: BigDecimal, val customerTotal: BigDecimal,
    val expenses: BigDecimal, val estimatedProfit: BigDecimal, val effectiveMargin: BigDecimal?,
    val activeHourly: BigDecimal?, val activeTimeActual: Boolean,
)
data class QuoteEvaluation(val missing: List<String>, val totals: QuoteTotals?)

/** Own work is revenue. It must never be deducted again as an operating expense. */
object Pricing {
    fun evaluate(model: PricingModel, input: QuoteInput): QuoteEvaluation {
        val problems = mutableListOf<String>()
        if (!input.materialsReviewed) problems += "Revisa los materiales, incluidos soportes y residuos."
        fun valid(value: BigDecimal?, label: String): Boolean {
            if (value == null || value < zero) { problems += label; return false }
            return true
        }
        input.materials.forEach {
            valid(it.quantity, "Indica la cantidad de ${it.material.name}.")
            if (it.material.unit == "ud" && (it.quantity?.stripTrailingZeros()?.scale() ?: 0) > 0)
                problems += "${it.material.name}: introduce unidades enteras."
            valid(it.unitPriceOverride ?: it.material.unitPrice, "Falta precio de ${it.material.name}.")
        }
        valid(input.designMinutes, "Indica minutos de diseño; introduce 0 si no hay trabajo nuevo.")
        if (input.designMinutes?.signum() != 0)
            valid(input.designHourly, "Elige dificultad o una tarifa de diseño.")
        valid(input.printingMinutes, "Indica los minutos de impresión del lote, o 0.")
        valid(input.assemblyMinutes, "Indica los minutos de montaje del lote, o 0.")
        input.extras.forEach { valid(it.amount, "Indica el importe de ${it.name.ifBlank { "gasto adicional" }}.") }
        input.agreedNet?.let { valid(it, "El precio acordado no puede ser negativo.") }
        input.actualActiveMinutes?.let { valid(it, "El tiempo activo no puede ser negativo.") }
        if (problems.isNotEmpty()) return QuoteEvaluation(problems.distinct(), null)

        val materials = input.materials.fold(zero) { sum, use ->
            sum + use.quantity!! * (use.unitPriceOverride ?: use.material.unitPrice!!)
        }.cents()
        fun work(minutes: BigDecimal, rate: BigDecimal) = minutes.multiply(rate).divide(sixty, precision).cents()
        val designMinutes = input.designMinutes!!
        val printingMinutes = input.printingMinutes!!
        val assemblyMinutes = input.assemblyMinutes!!
        val design = work(designMinutes, input.designHourly ?: zero)
        val printing = work(printingMinutes, model.printingHourly)
        val assembly = work(assemblyMinutes, model.assemblyHourly)
        val electricity = if (input.electricityIncluded) model.watts.multiply(printingMinutes)
            .multiply(model.electricityPerKwh).divide(BigDecimal("60000"), precision).cents() else zero.cents()
        val extra = input.extras.fold(zero) { sum, e -> sum + e.amount!!.cents() }.cents()
        val charged = (input.extras.filter { it.chargeToCustomer }.fold(zero) { sum, e -> sum + e.amount!!.cents() }
            + if (input.electricityIncluded && input.electricityCharged) electricity else zero).cents()
        val base = (materials + design + printing + assembly + charged).cents()
        val calculated = base.divide(BigDecimal.ONE - model.additionalMargin, precision).cents()
        val net = input.agreedNet?.cents() ?: calculated
        val tax = net.multiply(model.tax).cents()
        val expenses = (materials + electricity + extra).cents()
        val profit = (net - expenses).cents()
        val activeMinutes = input.actualActiveMinutes ?: (designMinutes + assemblyMinutes)
        return QuoteEvaluation(emptyList(), QuoteTotals(materials, design, printing, assembly,
            electricity, extra, charged, base, calculated, net, (net - calculated).cents(), tax, (net + tax).cents(),
            expenses, profit, if (net.signum() == 0) null else profit.divide(net, precision),
            if (activeMinutes.signum() == 0) null else profit.multiply(sixty).divide(activeMinutes, precision).cents(),
            input.actualActiveMinutes != null))
    }
}

/** Allowlist-only projection: customer output never receives materials, rates or profit. */
data class CustomerQuote(val concept: String, val total: BigDecimal) {
    fun text(formatMoney: (BigDecimal) -> String): String =
        "Presupuesto" + concept.trim().takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty() +
            "\nPrecio final total: ${formatMoney(total)}"
}
