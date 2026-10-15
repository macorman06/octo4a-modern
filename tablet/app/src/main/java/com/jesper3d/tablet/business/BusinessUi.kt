package com.jesper3d.tablet.business

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jesper3d.pricing.*
import com.jesper3d.tablet.ExportDocument
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.security.MessageDigest
import org.json.JSONObject

internal fun money(value: BigDecimal): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).format(value)
private fun short(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace('.', ',')
private fun precise(value: BigDecimal): String = short(value.setScale(10, RoundingMode.HALF_UP))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BusinessScreen(store: PricingStore, onBack: () -> Unit, importProfile: () -> Unit, export: ExportDocument,
    connected: Boolean = false, onEmergency: () -> Unit = {}) {
    BackHandler(onBack = onBack)
    val model by store.model.collectAsState()
    val message by store.message.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf("Calcular", "Modelo", "Materiales")
    val holder = rememberSaveableStateHolder()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Presupuestos", style = MaterialTheme.typography.titleLarge)
                TextButton(onBack) { Text("Impresora") }
            }
            if (connected) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sesión USB activa", Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onEmergency, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Parada") }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(importProfile, Modifier.testTag("import-pricing")) { Text("Importar modelo") }
                if (model != null) TextButton({ export("modelo-economico-privado.json", "application/json", PricingJson.encode(model!!)) }) {
                    Text("Exportar modelo privado")
                }
            }
            if (model != null) TabRow(page) { pages.forEachIndexed { i, title ->
                Tab(page == i, { page = i }, Modifier.testTag("pricing-tab-$i"), text = { Text(title) })
            } }
            if (message.isNotBlank()) Text(message, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodySmall)
            holder.SaveableStateProvider(page) {
                Column(Modifier.weight(1f).widthIn(max = 880.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    val current = model
                    if (current == null) {
                        Text("Tu modelo de trabajo", style = MaterialTheme.typography.headlineSmall)
                        Text("Importa el perfil JSON que acompaña a esta versión para cargar tus tarifas y materiales. Se guarda en la tablet y funciona sin USB ni internet.")
                        Text("Diseño, impresión y montaje son importes cobrados por tu trabajo. Los materiales se descuentan del ingreso; electricidad y otros gastos se incluyen solo si los registras.")
                        Text("El perfil contiene precios privados. Importarlo no crea compras ni existencias.", style = MaterialTheme.typography.bodySmall)
                    } else when (page) {
                        0 -> CalculatorPage(current, export)
                        1 -> ModelPage(current, store)
                        2 -> MaterialsPage(current, store)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, text: String, change: (String) -> Unit, tag: String,
    modifier: Modifier = Modifier, optional: Boolean = false) {
    OutlinedTextField(text, change, modifier.fillMaxWidth().testTag(tag), label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = text.isNotBlank() && decimal(text) == null,
        supportingText = if (optional) { { Text("Opcional") } } else null)
}
@Composable
private fun Toggle(text: String, checked: Boolean, change: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, change, Modifier.testTag(tag))
        Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
private fun AmountRow(label: String, amount: BigDecimal, tag: String? = null) {
    Row(Modifier.fillMaxWidth().then(if (tag == null) Modifier else Modifier.testTag(tag)), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f)); Text(money(amount), Modifier.padding(start = 16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalculatorPage(model: PricingModel, export: ExportDocument) {
    var concept by rememberSaveable { mutableStateOf("") }
    var designMinutes by rememberSaveable { mutableStateOf("") }
    var designRate by rememberSaveable { mutableStateOf("") }
    var printingMinutes by rememberSaveable { mutableStateOf("") }
    var assemblyMinutes by rememberSaveable { mutableStateOf("") }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var power by rememberSaveable { mutableStateOf(model.electricityDefault) }
    var chargePower by rememberSaveable { mutableStateOf(false) }
    var extra by rememberSaveable { mutableStateOf(false) }
    var extraAmount by rememberSaveable { mutableStateOf("") }
    var chargeExtra by rememberSaveable { mutableStateOf(false) }
    var agreed by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("") }
    // Encoded strings are saveable across rotation; no committed quote/history is claimed.
    var quantities by rememberSaveable { mutableStateOf("") }
    val amounts = if (quantities.isBlank()) emptyMap() else JSONObject(quantities).let { json ->
        json.keys().asSequence().associateWith { json.getString(it) }
    }
    fun setAmount(id: String, value: String) { quantities = JSONObject(amounts + (id to value)).toString() }
    fun remove(id: String) { quantities = JSONObject(amounts - id).toString() }
    val fingerprint = remember(model) { MessageDigest.getInstance("SHA-256")
        .digest(PricingJson.encode(model).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } }
    var draftFingerprint by rememberSaveable { mutableStateOf(fingerprint) }
    val modelChanged = draftFingerprint != fingerprint
    val missingMaterials = amounts.keys - model.materials.map { it.id }.toSet()
    // Gate complete totals during the change itself, before the effect can refresh saved state.
    LaunchedEffect(fingerprint) {
        if (draftFingerprint != fingerprint) {
            reviewed = false
            if (selected.isNotBlank()) {
                val rate = model.design.firstOrNull { it.id == selected }
                designRate = rate?.hourly?.let(::short).orEmpty()
                if (rate == null) selected = ""
            }
            draftFingerprint = fingerprint
        }
    }
    var publicText by remember { mutableStateOf<String?>(null) }
    Text("Calcula un trabajo", style = MaterialTheme.typography.headlineSmall)
    Text("Cálculo provisional del lote completo. No es un presupuesto enviado ni un pedido guardado.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(concept, { concept = it }, Modifier.fillMaxWidth().testTag("quote-concept"), label = { Text("Concepto para el cliente") }, singleLine = true)
    Text("Materiales", style = MaterialTheme.typography.titleMedium)
    Text("Añade los materiales que utilizarás. Cantidades en su unidad base; incluye soportes y residuos una sola vez.", style = MaterialTheme.typography.bodySmall)
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ menu = true }) { Text("Añadir material") }
        DropdownMenu(menu, { menu = false }) { model.materials.filter { it.id !in amounts }.forEach { material ->
            DropdownMenuItem(text = { Text(material.name) }, onClick = { setAmount(material.id, ""); menu = false; reviewed = false })
        } }
    }
    model.materials.filter { it.id in amounts }.forEach { material ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberField("${material.name} (${material.unit})", amounts[material.id].orEmpty(), { setAmount(material.id, it) }, "material-${material.id}")
            Text(material.unitPrice?.let { "Media histórica: ${precise(it)} €/${material.unit}" } ?: "Precio pendiente: completa su histórico en Materiales.", style = MaterialTheme.typography.bodySmall)
            TextButton({ remove(material.id); reviewed = false }) { Text("Quitar ${material.name}") }
        }
    }
    missingMaterials.forEach { id ->
        Text("Un material del borrador ya no está en este modelo. Importa el perfil anterior o quita la línea para continuar.",
            Modifier.testTag("missing-material"), color = MaterialTheme.colorScheme.error)
        TextButton({ remove(id); reviewed = false }) { Text("Quitar material no disponible") }
    }
    Toggle("Materiales revisados. Si la lista está vacía, confirmo que este trabajo no consume material.", reviewed, { reviewed = it }, "materials-reviewed")
    HorizontalDivider()
    Text("Importes por tu trabajo", style = MaterialTheme.typography.titleMedium)
    Text("Los tiempos son del lote completo. Introduce 0 cuando no exista esa tarea; dejarlo vacío significa pendiente.", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { model.design.forEach { rate ->
        FilterChip(selected == rate.id, { selected = rate.id; designMinutes = short(rate.minutes); designRate = short(rate.hourly) },
            label = { Text(rate.name) })
    } }
    NumberField("Minutos de diseño", designMinutes, { designMinutes = it }, "design-minutes")
    NumberField("Tarifa de diseño (€/h)", designRate, { designRate = it; selected = "" }, "design-rate")
    NumberField("Minutos de impresión", printingMinutes, { printingMinutes = it }, "printing-minutes")
    NumberField("Minutos de montaje", assemblyMinutes, { assemblyMinutes = it }, "assembly-minutes")
    Text("Impresión: ${money(model.printingHourly)}/h · Montaje: ${money(model.assemblyHourly)}/h. Son importes cobrados, no gastos.", style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Text("Gastos opcionales", style = MaterialTheme.typography.titleMedium)
    Toggle("Registrar electricidad estimada", power, { power = it }, "electricity-included")
    if (power) {
        Text("Estimación: ${short(model.watts)} W y ${short(model.electricityPerKwh)} €/kWh. No es una medición.", style = MaterialTheme.typography.bodySmall)
        Toggle("Repercutir también la electricidad al cliente", chargePower, { chargePower = it }, "electricity-charged")
    }
    Toggle("Registrar otro gasto adicional", extra, { extra = it }, "extra-included")
    if (extra) {
        NumberField("Gasto adicional (€)", extraAmount, { extraAmount = it }, "extra-amount")
        Toggle("Repercutir también este gasto al cliente", chargeExtra, { chargeExtra = it }, "extra-charged")
    }
    NumberField("Precio acordado sin impuestos (€)", agreed, { agreed = it }, "agreed-net", optional = true)
    Text("Sustituye el precio calculado y conserva la base. Margen adicional: ${short(model.additionalMargin * BigDecimal("100"))} % · Impuestos configurados: ${short(model.tax * BigDecimal("100"))} %.", style = MaterialTheme.typography.bodySmall)
    val uses = model.materials.filter { it.id in amounts }.map { MaterialUse(it, decimal(amounts[it.id].orEmpty())) }
    val invalidOptional = agreed.isNotBlank() && decimal(agreed) == null
    val evaluated = Pricing.evaluate(model, QuoteInput(uses, reviewed && !modelChanged && missingMaterials.isEmpty(), decimal(designMinutes), decimal(designRate),
        decimal(printingMinutes), decimal(assemblyMinutes), power, chargePower,
        if (extra) listOf(ExtraExpense("Gasto adicional", decimal(extraAmount), chargeExtra)) else emptyList(), decimal(agreed)))
    val totals = if (invalidOptional) null else evaluated.totals
    HorizontalDivider()
    Text("Detalle interno", style = MaterialTheme.typography.titleLarge)
    if (totals == null) {
        Text("Cálculo pendiente", Modifier.testTag("quote-incomplete"), style = MaterialTheme.typography.titleMedium)
        (evaluated.missing + if (invalidOptional) listOf("Revisa el precio acordado.") else emptyList()).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    } else {
        AmountRow("Materiales", totals.materials)
        AmountRow("Diseño", totals.design); AmountRow("Impresión", totals.printing); AmountRow("Montaje", totals.assembly)
        AmountRow("Gastos repercutidos", totals.chargedExpenses)
        AmountRow("Base calculada", totals.base); AmountRow("Venta sin impuestos", totals.agreedNet)
        if (totals.commercialAdjustment.signum() != 0) AmountRow("Ajuste comercial", totals.commercialAdjustment)
        AmountRow("Impuestos configurados", totals.tax)
        AmountRow("Precio final al cliente", totals.customerTotal, "customer-total")
        HorizontalDivider()
        AmountRow("Materiales y gastos a descontar", totals.expenses)
        AmountRow("Beneficio estimado del trabajo", totals.estimatedProfit, "estimated-profit")
        Text("Incluye la remuneración de tu trabajo propio. No es beneficio después de impuestos personales o gastos no registrados.", style = MaterialTheme.typography.bodySmall)
        Text("Margen efectivo estimado: " + (totals.effectiveMargin?.let { precise(it * BigDecimal("100")) + " %" } ?: "No disponible"))
        Text("Remuneración estimada por hora activa: " + (totals.activeHourly?.let(::money)?.plus("/h") ?: "No disponible"))
        Text("Hora activa = diseño y montaje; no incluye impresión automática.", style = MaterialTheme.typography.bodySmall)
        Button({ publicText = CustomerQuote(concept, totals.customerTotal).text(::money) }, Modifier.testTag("preview-customer")) { Text("Vista para cliente") }
    }
    publicText?.let { text -> AlertDialog(onDismissRequest = { publicText = null }, title = { Text("Vista para cliente") },
        text = { SelectionContainer { Text(text, Modifier.testTag("customer-preview")) } },
        confirmButton = { TextButton({ export("presupuesto-cliente.txt", "text/plain", text) }) { Text("Exportar texto para cliente") } },
        dismissButton = { TextButton({ publicText = null }) { Text("Cerrar") } }) }
}

@Composable
private fun ModelPage(model: PricingModel, store: PricingStore) {
    val values = remember(model) { mutableStateMapOf<String, String>().apply {
        model.design.forEach { put("minutes-${it.id}", short(it.minutes)); put("rate-${it.id}", short(it.hourly)) }
        put("printing", short(model.printingHourly)); put("assembly", short(model.assemblyHourly))
        put("margin", short(model.additionalMargin * BigDecimal("100"))); put("tax", short(model.tax * BigDecimal("100")))
        put("watts", short(model.watts)); put("energy", short(model.electricityPerKwh))
    } }
    var powerDefault by remember(model) { mutableStateOf(model.electricityDefault) }
    Text("Tu modelo económico", style = MaterialTheme.typography.headlineSmall)
    Text("Materiales como gasto obligatorio. Diseño, impresión y montaje remuneran tu trabajo; no se vuelven a descontar. Modelo ${model.version}.")
    Text("Guardar cambios recalcula el borrador abierto. Esta versión todavía no guarda presupuestos enviados.", style = MaterialTheme.typography.bodySmall)
    model.design.forEach { profile ->
        Text("Diseño · ${profile.name}", style = MaterialTheme.typography.titleMedium)
        NumberField("Minutos orientativos", values["minutes-${profile.id}"].orEmpty(), { values["minutes-${profile.id}"] = it }, "model-minutes-${profile.id}")
        NumberField("Tarifa (€/h)", values["rate-${profile.id}"].orEmpty(), { values["rate-${profile.id}"] = it }, "model-rate-${profile.id}")
    }
    listOf("printing" to "Impresión (€/h)", "assembly" to "Montaje (€/h)", "margin" to "Margen comercial adicional (%)",
        "tax" to "Impuestos configurados (%)", "watts" to "Potencia media estimada (W)", "energy" to "Electricidad (€/kWh)").forEach { (key, label) ->
        NumberField(label, values[key].orEmpty(), { values[key] = it }, "model-$key")
    }
    Toggle("Incluir electricidad por defecto en cálculos nuevos", powerDefault, { powerDefault = it }, "model-power-default")
    Text("Las tarifas y los precios son tu configuración interna. La electricidad es una estimación; el porcentaje de impuestos es editable y no determina el tratamiento fiscal aplicable.", style = MaterialTheme.typography.bodySmall)
    val valid = values.values.all { decimal(it) != null } && (decimal(values["margin"].orEmpty()) ?: BigDecimal("100")) < BigDecimal("100")
    Button({ runCatching {
        fun n(key: String) = decimal(values.getValue(key))!!
        store.save(model.copy(design = model.design.map { it.copy(minutes = n("minutes-${it.id}"), hourly = n("rate-${it.id}")) },
            printingHourly = n("printing"), assemblyHourly = n("assembly"), additionalMargin = n("margin").movePointLeft(2),
            tax = n("tax").movePointLeft(2), watts = n("watts"), electricityPerKwh = n("energy"), electricityDefault = powerDefault))
    }.onFailure { store.notice(it.message ?: "No se pudieron guardar las tarifas.") } }, enabled = valid) { Text("Guardar tarifas") }
    if (!valid) Text("Completa los valores. El margen debe ser menor de 100 %.", color = MaterialTheme.colorScheme.error)
}

@Composable
private fun MaterialsPage(model: PricingModel, store: PricingStore) {
    var edit by remember { mutableStateOf<MaterialRate?>(null) }
    Text("Catálogo de materiales", style = MaterialTheme.typography.headlineSmall)
    Text("Precios de referencia, sin cantidades de stock. La media conserva cada observación, incluidas las repetidas. No hay fechas de compra confirmadas ni consulta de tiendas.")
    OutlinedButton({ edit = MaterialRate(UUID.randomUUID().toString(), "Material nuevo", "ud", BigDecimal.ONE, emptyList()) }) { Text("Añadir material") }
    model.materials.forEach { material ->
        HorizontalDivider()
        Text(material.name, style = MaterialTheme.typography.titleMedium)
        Text(material.unitPrice?.let { "${precise(it)} €/${material.unit}" } ?: "Precio de referencia pendiente")
        Text("Formato de compra: ${short(material.purchaseQuantity)} ${material.unit} · ${material.prices.size} observaciones", style = MaterialTheme.typography.bodySmall)
        Text(material.prices.joinToString(" · ") { money(it) }, style = MaterialTheme.typography.bodySmall)
        TextButton({ edit = material }) { Text("Editar ${material.name}") }
    }
    edit?.let { material -> MaterialEditor(material, { edit = null }) { updated ->
        runCatching { store.save(model.copy(materials = if (model.materials.any { it.id == updated.id })
            model.materials.map { if (it.id == updated.id) updated else it } else model.materials + updated)) }
            .onSuccess { edit = null }.onFailure { store.notice(it.message ?: "No se pudo guardar el material.") }
    } }
}

@Composable
private fun MaterialEditor(material: MaterialRate, dismiss: () -> Unit, save: (MaterialRate) -> Unit) {
    var name by remember(material.id) { mutableStateOf(material.name) }
    var unit by remember(material.id) { mutableStateOf(material.unit) }
    var amount by remember(material.id) { mutableStateOf(short(material.purchaseQuantity)) }
    var prices by remember(material.id) { mutableStateOf(material.prices.joinToString("\n") { short(it) }) }
    val parts = prices.split(Regex("[;\n]+")).filter { it.isNotBlank() }
    val parsed = parts.map { decimal(it) }
    val valid = name.isNotBlank() && name.length <= 160 && unit.isNotBlank() && unit.length <= 20 &&
        (decimal(amount)?.signum() ?: 0) > 0 && (unit.trim() != "ud" || (decimal(amount)?.stripTrailingZeros()?.scale() ?: 1) <= 0) &&
        parsed.all { it != null } && parsed.size <= 500
    AlertDialog(onDismissRequest = dismiss, title = { Text("Editar material") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
            OutlinedTextField(unit, { unit = it }, label = { Text("Unidad base: g, m, ud…") }, singleLine = true)
            NumberField("Cantidad base por compra", amount, { amount = it }, "purchase-quantity")
            OutlinedTextField(prices, { prices = it }, label = { Text("Precios de compra (€), uno por línea") }, minLines = 3)
            Text("Para un pack, indica sus unidades; para una bobina, sus gramos; para un rollo, sus metros. Precios sin fecha ni existencias inferidas.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ save(material.copy(name = name.trim(), unit = unit.trim(), purchaseQuantity = decimal(amount)!!, prices = parsed.filterNotNull())) }, enabled = valid) { Text("Guardar material") } },
        dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}
