package com.jesper3d.tablet

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.jesper3d.protocol.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class Confirmation(val title: String, val text: String, val action: String, val run: () -> Unit)
internal typealias ExportDocument = (String, String, String) -> Unit
internal fun value(n: Double?, decimals: Int = 2) = n?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—"
internal fun timeLabel(time: Long?) = time?.let { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Sin lectura"

@Composable
internal fun JesperTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val activity = LocalContext.current as? android.app.Activity
    val view = LocalView.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF97D5C4), onPrimary = Color(0xFF073B30),
        primaryContainer = Color(0xFF203E35), onPrimaryContainer = Color(0xFFD8F5EA),
        secondaryContainer = Color(0xFF2A302D), onSecondaryContainer = Color(0xFFE0E6E1),
        background = Color(0xFF141715), surface = Color(0xFF1B1F1C),
        surfaceVariant = Color(0xFF242A26), onSurface = Color(0xFFE8EDE8),
        onSurfaceVariant = Color(0xFFAAB5AC), outline = Color(0xFF566258), outlineVariant = Color(0xFF343E36))
    else lightColorScheme(
        primary = Color(0xFF236A56), onPrimary = Color.White,
        primaryContainer = Color(0xFFE9F2EC), onPrimaryContainer = Color(0xFF174634),
        secondary = Color(0xFF4D5B52), secondaryContainer = Color(0xFFEDF0EA), onSecondaryContainer = Color(0xFF2A382E),
        background = Color(0xFFF8F9F5), surface = Color.White,
        surfaceVariant = Color(0xFFF0F2EC), onSurface = Color(0xFF202820), onSurfaceVariant = Color(0xFF687368),
        outline = Color(0xFFB3BEB1), outlineVariant = Color(0xFFE0E5DC))
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp)), content = content)
}

@Composable
internal fun PrinterScreen(state: UiState, printer: PrinterController, devices: List<UsbSerialDriver>, scan: () -> Unit,
    connect: (String, Int) -> Unit, export: ExportDocument, importPricing: () -> Unit = {}) {
    var page by rememberSaveable { mutableIntStateOf(1) }
    var pricing by rememberSaveable { mutableStateOf(false) }
    if (pricing) {
        com.jesper3d.tablet.business.BusinessScreen(
            (LocalContext.current.applicationContext as PrinterApplication).pricing,
            onBack = { pricing = false }, importProfile = importPricing, export = export,
            connected = state.connected, onEmergency = printer::emergency)
        return
    }
    var confirm by remember { mutableStateOf<Confirmation?>(null) }
    var connectionDialog by rememberSaveable { mutableStateOf(false) }
    var profileDialog by rememberSaveable { mutableStateOf(false) }
    val ready = state.connected && !state.busy && state.snapshot.isMarlin
    val pages = listOf("Control", "Cama", "Ajustes", "Registro")
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= 800.dp
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Jesper 3D", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (state.connected) "CR-10 S4 · USB conectado" else "Tu espacio de calibración", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ profileDialog = true }, enabled = !state.busy) { Text("Perfil") }
                    if (state.connected) TextButton({ printer.emergency() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Parada")
                    }
                    OutlinedButton(onClick = {
                        if (state.connected) confirm = Confirmation("Cerrar conexión", "Desconectar la tablet no apaga los calentadores ni detiene una operación física.", "Desconectar") { printer.disconnect() }
                        else { scan(); connectionDialog = true }
                    }, enabled = !state.busy) { Text(if (state.connected) "Desconectar" else "Conectar USB") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Impresora", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    TextButton({ pricing = true }, Modifier.testTag("open-pricing")) { Text("Presupuestos") }
                }
                Row(Modifier.weight(1f)) {
                    if (wide) Column(Modifier.width(148.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pages.forEachIndexed { index, title ->
                            Surface(Modifier.fillMaxWidth().testTag("nav-$index").clickable { page = index },
                                color = if (page == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)) {
                                Text(title, Modifier.padding(16.dp, 13.dp), fontWeight = if (page == index) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (page == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Jesper 3D\n0.3 · Android", Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(if (wide) 32.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        OperationStatus(state)
                        when (page) {
                            0 -> ControlPage(state, printer, ready) { confirm = it }
                            1 -> BedPage(state, printer, ready, export) { confirm = it }
                            2 -> SettingsPage(state, printer, ready) { confirm = it }
                            3 -> LogPage(state, printer, export)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (!wide) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        pages.forEachIndexed { index, title -> TextButton({ page = index }, Modifier.testTag("nav-$index")) {
                            Text(title, fontWeight = if (page == index) FontWeight.Bold else FontWeight.Normal)
                        } }
                    }
                }
            }
        }
    }
    confirm?.let { item -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(item.title) }, text = { Text(item.text) },
        confirmButton = { TextButton({ confirm = null; item.run() }, enabled = !state.busy) { Text(item.action) } },
        dismissButton = { TextButton({ confirm = null }) { Text("Cancelar") } }) }
    if (connectionDialog) ConnectionDialog(devices, scan, { connectionDialog = false }) { name, rate -> connectionDialog = false; connect(name, rate) }
    if (profileDialog) ProfileDialog(printer) { profileDialog = false }
}

@Composable
private fun OperationStatus(state: UiState) {
    val working = state.busy && state.operation != Operation.STATUS
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(working, state.startedAt) { while (working) { now = System.currentTimeMillis(); delay(1000) } }
    if (working) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.step.ifEmpty { state.message }, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            state.startedAt?.let { Text("${((now - it).coerceAtLeast(0) / 1000)} s", style = MaterialTheme.typography.labelMedium) }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.operation == Operation.PROBE) {
            val points = maxOf(state.snapshot.probePoints.size, state.liveRecord?.mesh?.values?.size ?: 0)
            Text((if (points > 0) "$points puntos recibidos" else "Esperando datos de sondeo") + " · paso ${state.stepIndex}/${state.stepCount}",
                style = MaterialTheme.typography.bodySmall)
        }
    } else Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    state.error?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable
internal fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Metric(label: String, content: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(content, fontSize = 25.sp, fontWeight = FontWeight.Medium)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ControlPage(state: UiState, printer: PrinterController, ready: Boolean, confirm: (Confirmation) -> Unit) {
    PageTitle("Control", "Temperaturas, movimientos y preparación de la impresora.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Metric("Boquilla", "${value(state.snapshot.nozzle?.current, 1)} °C", Modifier.weight(1f), "Objetivo ${value(state.snapshot.nozzle?.target, 0)} °C")
        Metric("Cama", "${value(state.snapshot.bed?.current, 1)} °C", Modifier.weight(1f), "Objetivo ${value(state.snapshot.bed?.target, 0)} °C")
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Calentamiento", style = MaterialTheme.typography.titleMedium)
    var nozzle by rememberSaveable { mutableStateOf("200") }
    var bed by rememberSaveable { mutableStateOf("50") }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(nozzle, { nozzle = it }, Modifier.weight(1f), label = { Text("Boquilla · °C") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(bed, { bed = it }, Modifier.weight(1f), label = { Text("Cama · °C") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {
            runCatching { Commands.heat(nozzle.toInt(), bed.toInt()) }.onSuccess { batch ->
                confirm(Confirmation("Aplicar temperaturas", "Boquilla $nozzle °C · cama $bed °C. Confirma que la impresora está en reposo y supervisada.", "Calentar") { printer.action(batch) })
            }.onFailure { printer.notice(it.message ?: "Valores no válidos.") }
        }, enabled = ready) { Text("Aplicar") }
        OutlinedButton({ printer.action(Commands.heat(0, 0)) }, enabled = ready) { Text("Apagar calentadores") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Mover ejes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        OutlinedButton({ confirm(Confirmation("Buscar origen", "Se moverán X, Y y Z. Confirma que la cama está libre y el sensor funciona.", "Buscar origen") {
            printer.action(Commands.home)
        }) }, enabled = ready && printer.profileConfirmed) { Text("Buscar origen") }
    }
    val position = state.snapshot.position
    Text("X ${value(position?.x)}   Y ${value(position?.y)}   Z ${value(position?.z)} mm", fontFamily = FontFamily.Monospace)
    var step by rememberSaveable { mutableDoubleStateOf(1.0) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.1, 1.0, 10.0).forEach { size -> FilterChip(step == size, { step = size }, label = { Text("${value(size, 1)} mm") }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("X", "Y", "Z").forEach { axis ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton({ printer.jog(axis, -step) }, enabled = ready && state.homed) { Text("−") }
                Text(axis)
                OutlinedButton({ printer.jog(axis, step) }, enabled = ready && state.homed) { Text("+") }
            }
        }
    }
    Text(if (state.homed) "Origen confirmado en esta sesión." else "Confirma el perfil y busca el origen antes de mover.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Ventilador de capa", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0, 50, 100).forEach { percent -> OutlinedButton({ printer.action(Commands.fan(percent)) }, enabled = ready) { Text("$percent %") } }
    }
    Text("Envía una orden al ventilador de capa; la impresora no comunica su velocidad real.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsPage(state: UiState, printer: PrinterController, ready: Boolean, confirm: (Confirmation) -> Unit) {
    PageTitle("Ajustes", "Valores activos de Marlin. Pueden diferir de los guardados en la impresora.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton({ printer.action(Commands.settings) }, enabled = ready) { Text("Leer ajustes") }
        OutlinedButton({ confirm(Confirmation("Guardar ajustes activos", "Se guardarán todos los ajustes activos que admita tu firmware, incluidos cambios hechos en su pantalla. La persistencia debe comprobarse tras reiniciar.", "Guardar") { printer.save() }) },
            enabled = ready && state.snapshot.capabilities["EEPROM"] == true) { Text("Guardar en EEPROM") }
    }
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar un ajuste") }, placeholder = { Text("Pasos, velocidad, sensor, PID…") })
    val filtered = state.snapshot.settings.filter { "${it.label} ${it.command} ${it.parameters}".contains(query, true) }
    if (filtered.isEmpty()) Text(if (state.snapshot.settings.isEmpty()) "Todavía no hay una lectura de ajustes." else "No hay coincidencias.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    filtered.forEach { setting ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(setting.label, fontWeight = FontWeight.Medium)
            SelectionContainer { Text("${setting.command} ${setting.parameters}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton({ expanded = !expanded }) { Text(if (expanded) "Ocultar firmware y capacidades" else "Firmware y capacidades") }
    if (expanded) {
        Text(state.snapshot.firmware ?: "Sin identificar", style = MaterialTheme.typography.bodySmall)
        state.snapshot.capabilities.forEach { (key, supported) -> Text("$key · ${if (supported) "sí" else "no"}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun LogPage(state: UiState, printer: PrinterController, export: ExportDocument) {
    PageTitle("Registro", "Las respuestas se conservan aunque falle la conexión o cierres la app.")
    OutlinedButton({ export("jesper3d-diagnostico.txt", "text/plain", printer.diagnostics()) }) { Text("Exportar diagnóstico") }
    var issuesOnly by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(issuesOnly, { issuesOnly = it }); Spacer(Modifier.width(12.dp)); Text("Solo incidencias") }
    val lines = if (issuesOnly) state.log.filter { it.contains(" ! ") || it.contains("Error:", true) } else state.log
    SelectionContainer { Text(lines.joinToString("\n").ifEmpty { "Todavía no hay respuestas USB en esta sesión. El diagnóstico incluye el registro anterior." },
        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp) }
}

@Composable
private fun ConnectionDialog(devices: List<UsbSerialDriver>, scan: () -> Unit, dismiss: () -> Unit, connect: (String, Int) -> Unit) {
    var selected by remember(devices) { mutableStateOf(devices.singleOrNull()?.device?.deviceName) }
    var baud by rememberSaveable { mutableIntStateOf(115200) }
    var idle by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Conexión USB") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Conecta la impresora con un cable de datos OTG. Cierra otras apps que estén usando el puerto.")
            if (devices.isEmpty()) Text("No se ha detectado un puerto serie compatible.")
            devices.forEach { driver -> Row(Modifier.fillMaxWidth().clickable { selected = driver.device.deviceName }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == driver.device.deviceName, { selected = driver.device.deviceName })
                Text("${driver.device.productName ?: driver.javaClass.simpleName}\nVID ${driver.device.vendorId} · PID ${driver.device.productId}", style = MaterialTheme.typography.bodySmall)
            } }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(115200, 250000, 57600).forEach { rate ->
                FilterChip(baud == rate, { baud = rate }, label = { Text("$rate", fontSize = 12.sp) })
            } }
            Text("Abrir el puerto puede reiniciar algunas placas.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(idle, { idle = it }); Text("La impresora está en reposo y no imprime desde SD.") }
            TextButton(scan) { Text("Buscar dispositivos") }
        }
    }, confirmButton = { TextButton({ selected?.let { connect(it, baud) } }, enabled = idle && devices.any { it.device.deviceName == selected }) { Text("Conectar") } },
        dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}

@Composable
private fun ProfileDialog(printer: PrinterController, dismiss: () -> Unit) {
    var x by remember { mutableStateOf(value(printer.profile.maxX, 0)) }
    var y by remember { mutableStateOf(value(printer.profile.maxY, 0)) }
    var z by remember { mutableStateOf(value(printer.profile.maxZ, 0)) }
    var bilinear by remember { mutableStateOf(printer.bilinearConfirmed) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Perfil de la impresora") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CR-10 S4 · confirma los recorridos máximos desde el origen para tu máquina.")
            OutlinedTextField(x, { x = it }, label = { Text("X máximo · mm") }, singleLine = true)
            OutlinedTextField(y, { y = it }, label = { Text("Y máximo · mm") }, singleLine = true)
            OutlinedTextField(z, { z = it }, label = { Text("Z máximo · mm") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(bilinear, { bilinear = it }); Text("He verificado el sensor y la nivelación bilineal.") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Configura la tablet; no cambia el firmware ni su número de puntos.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({
        runCatching { printer.configure(MachineProfile(x.replace(',', '.').toDouble(), y.replace(',', '.').toDouble(), z.replace(',', '.').toDouble()), bilinear) }
            .onSuccess { dismiss() }.onFailure { error = it.message ?: "Dimensiones no válidas." }
    }) { Text("Confirmar perfil") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}
