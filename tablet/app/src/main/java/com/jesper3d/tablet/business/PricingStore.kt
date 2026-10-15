package com.jesper3d.tablet.business

import android.content.Context
import com.jesper3d.pricing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

object PricingJson {
    const val MAX_BYTES = 256 * 1024
    private fun JSONObject.number(key: String): BigDecimal =
        decimal(get(key).toString()) ?: error("Valor no válido: $key")
    fun parse(text: String): PricingModel {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "El perfil supera 256 KB." }
        val root = JSONObject(text)
        require(root.getString("version_modelo") == "1.0") { "Versión de modelo no compatible." }
        require(root.getString("moneda") == "EUR") { "El modelo debe usar EUR." }
        require(!root.getBoolean("trabajo_propio_es_gasto")) { "El trabajo propio debe ser ingreso, no gasto." }
        require(root.getString("criterio_precio_material") == "media_simple") { "Criterio de material no compatible." }
        val design = root.getJSONObject("diseno").getJSONArray("perfiles")
        require(design.length() in 1..20) { "Número de perfiles de diseño no válido." }
        val rates = (0 until design.length()).map { i -> design.getJSONObject(i).let {
            val id = it.getString("id")
            DesignRate(id, it.optString("nombre", id.replaceFirstChar { c -> c.uppercase() }),
                it.number("minutos_estimados"), it.number("tarifa_eur_h"))
        } }
        val materials = root.getJSONArray("materiales")
        require(materials.length() <= 200)
        val entries = (0 until materials.length()).map { i -> materials.getJSONObject(i).let { item ->
            val prices = item.getJSONArray("precios_compra_eur")
            require(prices.length() <= 500) { "Demasiadas observaciones de precio." }
            MaterialRate(item.getString("id"), item.getString("nombre"), item.getString("unidad_base"),
                item.number("cantidad_base_por_compra"), (0 until prices.length()).map { p ->
                    decimal(prices.get(p).toString()) ?: error("Precio no válido en ${item.getString("nombre")}")
                })
        } }
        val printing = root.getJSONObject("impresion")
        val assembly = root.getJSONObject("montaje")
        require(!printing.getBoolean("es_gasto_a_descontar") && !assembly.getBoolean("es_gasto_a_descontar"))
        require(!printing.getBoolean("incluye_materiales") && !printing.getBoolean("incluye_electricidad"))
        val power = root.getJSONObject("electricidad")
        return PricingModel("1.0", rates, printing.number("tarifa_eur_h"), assembly.number("tarifa_eur_h"),
            root.number("margen_comercial_adicional"), root.number("tipo_impuesto"),
            power.number("potencia_media_estimada_w"), power.number("precio_eur_kwh"),
            power.getBoolean("incluir_por_defecto_en_modelo_basico"), entries)
    }
    fun encode(model: PricingModel): String = JSONObject().apply {
        put("version_modelo", model.version); put("moneda", "EUR")
        put("gastos_obligatorios", JSONArray().put("materiales")); put("trabajo_propio_es_gasto", false)
        put("criterio_precio_material", "media_simple")
        put("diseno", JSONObject().put("permitir_ajuste_de_horas_por_presupuesto", true).put("perfiles", JSONArray().apply {
            model.design.forEach { put(JSONObject().put("id", it.id).put("nombre", it.name)
                .put("minutos_estimados", it.minutes.toPlainString()).put("tarifa_eur_h", it.hourly.toPlainString())) }
        }))
        put("impresion", JSONObject().put("tarifa_eur_h", model.printingHourly.toPlainString())
            .put("incluye_materiales", false).put("incluye_electricidad", false).put("es_gasto_a_descontar", false))
        put("montaje", JSONObject().put("tarifa_eur_h", model.assemblyHourly.toPlainString())
            .put("minutos_estimados_por_defecto", JSONObject.NULL).put("es_gasto_a_descontar", false))
        put("electricidad", JSONObject().put("opcional", true).put("incluir_por_defecto_en_modelo_basico", model.electricityDefault)
            .put("potencia_media_estimada_w", model.watts.toPlainString()).put("precio_eur_kwh", model.electricityPerKwh.toPlainString())
            .put("origen", "estimacion_configurable"))
        put("margen_comercial_adicional", model.additionalMargin.toPlainString()); put("tipo_impuesto", model.tax.toPlainString())
        put("materiales", JSONArray().apply { model.materials.forEach { material ->
            put(JSONObject().put("id", material.id).put("nombre", material.name).put("unidad_base", material.unit)
                .put("cantidad_base_por_compra", material.purchaseQuantity.toPlainString())
                .put("precios_compra_eur", JSONArray().apply { material.prices.forEach { put(it.toPlainString()) } }))
        } })
    }.toString(2)
}

class PricingStore(context: Context, name: String = "business-pricing") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow<PricingModel?>(null)
    val model = mutable.asStateFlow()
    private val notices = MutableStateFlow("")
    val message = notices.asStateFlow()
    init {
        prefs.getString("model", null)?.let { encoded ->
            runCatching { PricingJson.parse(encoded) }.onSuccess { mutable.value = it }
                .onFailure { notices.value = "No se pudo leer el modelo guardado. Importa una copia de tu perfil." }
        }
    }
    fun save(model: PricingModel) {
        val encoded = PricingJson.encode(model)
        PricingJson.parse(encoded)
        check(prefs.edit().putString("model", encoded).commit()) { "No se pudo guardar el modelo. Vuelve a intentarlo." }
        mutable.value = model
        notices.value = "Modelo guardado en la tablet."
    }
    fun notice(message: String) { notices.value = message }
}
