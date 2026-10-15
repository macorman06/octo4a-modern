package com.jesper3d.tablet

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesper3d.protocol.*
import kotlin.math.abs
import kotlin.math.min

@Composable
internal fun BedPage(state: UiState, printer: PrinterController, ready: Boolean, export: ExportDocument,
    confirm: (Confirmation) -> Unit) {
    var historyId by rememberSaveable { mutableStateOf<String?>(null) }
    var referenceId by rememberSaveable { mutableStateOf<String?>(null) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var measureDialog by rememberSaveable { mutableStateOf(false) }
    val record = state.history.firstOrNull { it.id == historyId } ?: state.liveRecord ?: state.history.firstOrNull()
    val archived = historyId != null || state.liveRecord == null
    val reference = state.history.firstOrNull { it.id == referenceId && it.id != record?.id }
        ?: state.history.firstOrNull { it.id != record?.id }
    val delta = if (record != null && reference != null) record.mesh.difference(reference.mesh) else null
    val displayMesh = if (compare && delta != null) delta.mesh else record?.mesh

    PageTitle("Tu cama, punto a punto", "Mide, revisa y conserva cada calibración.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button({ measureDialog = true }, enabled = ready && printer.profileConfirmed && printer.bilinearConfirmed &&
            state.snapshot.capabilities["Z_PROBE"] == true && state.snapshot.capabilities["AUTOLEVEL"] == true) { Text("Medir cama") }
        OutlinedButton({ historyId = null; compare = false; printer.action(Commands.mesh) }, enabled = ready) { Text("Leer malla") }
    }
    MeshStorageStatus(state, record, archived)
    if (displayMesh != null && record != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Metric(if (compare && delta != null) "Cambio máximo" else "Desnivel total",
                "${value(if (compare && delta != null) delta.maxAbsolute else record.mesh.range, 3)} mm", Modifier.weight(1f))
            Metric("Puntos", "${record.mesh.columns} × ${record.mesh.rows.size}", Modifier.weight(1f),
                "${record.mesh.values.size} con lectura")
            Metric(if (compare && delta != null) "Cambio medio" else "Compensación",
                if (compare && delta != null) "${value(delta.mean, 3)} mm" else when (state.snapshot.leveling) {
                    true -> if (archived || !state.connected) "Sin verificar" else "Activa"
                    false -> if (archived || !state.connected) "Sin verificar" else "Inactiva"
                    null -> "Sin verificar"
                }, Modifier.weight(1f))
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (compare && delta != null) "Diferencia respecto a ${timeLabel(reference?.capturedAt)}" else "Altura de cada punto",
                        Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("mm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MeshMap(displayMesh, difference = compare && delta != null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ confirm(Confirmation("Guardar en la impresora", "Se enviará M500 para guardar los ajustes activos admitidos por Marlin. La copia de la tablet es independiente. Verifica la persistencia después de reiniciar.", "Guardar") { printer.save() }) },
                enabled = ready && !archived && state.snapshot.capabilities["EEPROM"] == true) { Text("Guardar en impresora") }
            TextButton({ export("malla-${record.capturedAt}.csv", "text/csv", MeshHistoryCodec.csv(record)) }) { Text("Exportar CSV") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Compensación de la impresora", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = state.snapshot.leveling == true,
                onCheckedChange = { enable -> confirm(Confirmation(if (enable) "Activar compensación" else "Desactivar compensación",
                    "Se cambiará la compensación activa de la impresora. El guardado se realiza por separado.", "Aplicar") { printer.action(Commands.leveling(enable)) }) },
                enabled = ready && !archived && state.liveRecord?.mesh?.values?.isNotEmpty() == true)
        }
    } else {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Empieza con una lectura", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Conecta tu impresora y pulsa Leer malla. Consultar los puntos existentes no mueve la máquina.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Al medir una cama nueva, la app actualiza el mapa y conserva una copia local. Guardarla en la impresora es un paso distinto.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (state.measurementCompleted) Text("Última medición finalizada ${timeLabel(state.meshMeasuredAt)}" +
        (state.measurementElapsedMs?.let { " · ${it / 1000} s" } ?: ""), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Historial de la tablet", style = MaterialTheme.typography.titleMedium)
    Text("Hasta 12 mallas conservadas. Ver una copia no la envía a la impresora.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.history.isEmpty()) Text("Las mallas aparecerán aquí después de una lectura válida.", style = MaterialTheme.typography.bodySmall)
    else {
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton({ menu = true }) { Text(if (record == null) "Elegir una malla" else timeLabel(record.capturedAt)) }
            DropdownMenu(menu, { menu = false }) {
                if (state.liveRecord != null) DropdownMenuItem(text = { Text("Última lectura de esta sesión") }, onClick = { historyId = null; compare = false; menu = false })
                state.history.forEach { old -> DropdownMenuItem(text = { Text("${timeLabel(old.capturedAt)} · ${if (old.origin == MeshOrigin.MEASURED) "medida" else "leída"}") },
                    onClick = { historyId = old.id; compare = false; menu = false }) }
            }
        }
        if (reference != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(compare, { compare = it }, enabled = delta != null, modifier = Modifier.testTag("compare-mesh"))
                Spacer(Modifier.width(12.dp)); Text("Comparar con otra malla")
            }
            if (delta == null) Text("Estas cuadrículas tienen dimensiones diferentes; no se comparan punto a punto.", style = MaterialTheme.typography.bodySmall)
            if (compare) {
                var referenceMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton({ referenceMenu = true }) { Text("Referencia: ${timeLabel(reference.capturedAt)}") }
                    DropdownMenu(referenceMenu, { referenceMenu = false }) {
                        state.history.filter { it.id != record?.id }.forEach { old -> DropdownMenuItem(text = { Text(timeLabel(old.capturedAt)) },
                            onClick = { referenceId = old.id; referenceMenu = false }) }
                    }
                }
            }
        }
    }
    if (measureDialog) ProbeDialog(state, { measureDialog = false }) { save ->
        historyId = null; compare = false; measureDialog = false; printer.probe(save)
    }
}

@Composable
private fun MeshStorageStatus(state: UiState, record: MeshRecord?, archived: Boolean) {
    val label = when {
        archived && record != null -> "Copia local · ${timeLabel(record.capturedAt)}"
        !state.connected && record != null -> "Última lectura conservada · USB desconectado"
        record != null -> "Mapa actualizado · ${timeLabel(state.meshReadAt)}"
        else -> "Sin malla recibida"
    }
    val storage = if (archived) "La copia de la tablet no demuestra qué hay guardado ahora en la impresora."
    else when (state.meshSaveState) {
        MeshSaveState.CONFIRMED -> "Marlin confirmó M500 ${timeLabel(state.meshSavedAt)}. Persistencia tras reinicio sin verificar."
        MeshSaveState.UNSAVED -> "Cambios en RAM · pendiente de guardar en la impresora."
        MeshSaveState.ACKNOWLEDGED_ONLY -> "M500 aceptado · Marlin no confirmó el almacenamiento."
        MeshSaveState.UNKNOWN -> "Guardado en la impresora sin verificar."
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(storage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (record != null) Text("Copia local disponible para exportar, incluso sin USB.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProbeDialog(state: UiState, dismiss: () -> Unit, start: (Boolean) -> Unit) {
    var saveAfter by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Medir la cama") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Se buscará el origen y se medirán los puntos definidos en Marlin. Confirma que la cama está libre, el sensor funciona y las temperaturas se han estabilizado.")
            Text("El mapa se actualizará al terminar y quedará una copia en la tablet.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(saveAfter, { saveAfter = it }, enabled = state.snapshot.capabilities["EEPROM"] == true)
                Text("Guardar también en la impresora al terminar")
            }
            Text(if (saveAfter) "M500 guardará todos los ajustes activos admitidos por el firmware, no solo los puntos de la cama."
                else "No se enviará una orden de guardado a la impresora. Podrás hacerlo después.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ start(saveAfter) }) { Text("Iniciar medición") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}

@Composable
internal fun MeshMap(mesh: Mesh, difference: Boolean = false) {
    var selected by remember(mesh) { mutableStateOf<Pair<Int, Int>?>(null) }
    val density = LocalDensity.current
    val margin = with(density) { 34.dp.toPx() }
    val textSize = with(density) { 12.sp.toPx() }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val negative = Color(0xFF4D9F9A)
    val positive = Color(0xFFC48C5C)
    val max = mesh.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001) ?: 1.0
    fun geometry(width: Float, height: Float): Triple<Float, Float, Float> {
        val cell = min((width - margin * 2) / mesh.columns, (height - margin * 2) / mesh.rows.size).coerceAtLeast(1f)
        return Triple(cell, (width - cell * mesh.columns) / 2, (height - cell * mesh.rows.size) / 2)
    }
    Canvas(Modifier.fillMaxWidth().height(360.dp).testTag("mesh-map")
        .semantics { contentDescription = "Malla de ${mesh.columns} columnas y ${mesh.rows.size} filas. Alturas en milímetros." }
        .pointerInput(mesh) { detectTapGestures { point ->
            val (cell, left, top) = geometry(size.width.toFloat(), size.height.toFloat())
            val x = ((point.x - left) / cell).toInt()
            val screenY = ((point.y - top) / cell).toInt()
            if (point.x >= left && point.y >= top && x in 0 until mesh.columns && screenY in 0 until mesh.rows.size)
                selected = x to (mesh.rows.size - 1 - screenY)
        } }) {
        val (cell, left, top) = geometry(size.width, size.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; this.textSize = textSize; textAlign = Paint.Align.CENTER }
        mesh.rows.toSortedMap(reverseOrder()).entries.forEachIndexed { screenY, (y, row) ->
            row.forEachIndexed { x, z ->
                val color = if (z == null) neutral else lerp(neutral, if (z < 0) negative else positive, (abs(z) / max * 0.85).toFloat())
                val offset = Offset(left + x * cell + 2, top + screenY * cell + 2)
                val dimensions = Size((cell - 4).coerceAtLeast(1f), (cell - 4).coerceAtLeast(1f))
                drawRect(color, offset, dimensions)
                if (selected == x to y) drawRect(accent, offset, dimensions, style = Stroke(3f))
                if (cell >= textSize * 4) drawContext.canvas.nativeCanvas.drawText(value(z, 3),
                    left + (x + 0.5f) * cell, top + (screenY + 0.5f) * cell + textSize / 3, paint)
            }
            if (cell > textSize * 1.6f) drawContext.canvas.nativeCanvas.drawText("$y", left - textSize * 1.4f,
                top + (screenY + 0.5f) * cell + textSize / 3, paint)
        }
        if (cell > textSize * 1.6f) repeat(mesh.columns) { x -> drawContext.canvas.nativeCanvas.drawText("$x",
            left + (x + 0.5f) * cell, top + mesh.rows.size * cell + textSize * 1.6f, paint) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(negative)); Text("−${value(max, 3)}", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text(if (difference) "Diferencia de altura · mm" else "Altura Z · mm", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text("+${value(max, 3)}", style = MaterialTheme.typography.labelSmall); Box(Modifier.size(10.dp).background(positive))
    }
    Text("X → · Y ↑ · escala automática · toca un punto para inspeccionarlo", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    selected?.let { (x, y) -> Text("Punto [$x, $y] · ${value(mesh.rows[y]?.get(x), 3)} mm",
        Modifier.testTag("selected-point"), fontWeight = FontWeight.Medium) }
    Text("Los índices no representan coordenadas XY físicas. — indica un punto sin lectura.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
