package com.jesper3d.tablet

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.jesper3d.protocol.*
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val printer get() = (application as PrinterApplication).printer
    private val usb get() = getSystemService(USB_SERVICE) as UsbManager
    private var devices by mutableStateOf<List<UsbSerialDriver>>(emptyList())
    private val permissionAction get() = "$packageName.USB_PERMISSION"
    private var requestedDevice: String? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            if (device != null && device.deviceName == requestedDevice && usb.hasPermission(device)) {
                startConnection(device.deviceName, intent.getIntExtra("baud", 115200))
            } else printer.notice("Permiso USB no concedido. Puedes volver a conectar.")
            requestedDevice = null
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ContextCompat.registerReceiver(this, receiver, IntentFilter(permissionAction), ContextCompat.RECEIVER_NOT_EXPORTED)
        scan()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF006B60), onPrimary = Color.White,
                primaryContainer = Color(0xFFD8EEE7), background = Color(0xFFF5F7F4),
                surface = Color(0xFFFCFDFB), surfaceVariant = Color(0xFFE4EAE4),
                onSurface = Color(0xFF17221E), onSurfaceVariant = Color(0xFF53635B))) {
                val state by printer.state.collectAsStateWithLifecycle()
                PrinterScreen(state, printer, devices, ::scan, ::requestConnection)
            }
        }
    }
    private fun scan() { devices = UsbSerialProber.getDefaultProber().findAllDrivers(usb) }
    private fun requestConnection(name: String, baud: Int) {
        val device = usb.deviceList.values.firstOrNull { it.deviceName == name } ?: run { scan(); return }
        if (usb.hasPermission(device)) startConnection(name, baud)
        else {
            requestedDevice = name
            val intent = Intent(permissionAction).setPackage(packageName).putExtra("baud", baud)
            val pending = PendingIntent.getBroadcast(this, baud, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            usb.requestPermission(device, pending)
        }
    }
    private fun startConnection(name: String, baud: Int) {
        ContextCompat.startForegroundService(this, Intent(this, PrinterService::class.java).putExtra("device", name).putExtra("baud", baud))
    }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}

private data class Confirmation(val title: String, val detail: String, val confirm: String = "Continuar", val run: () -> Unit)
private val sections = listOf("Resumen", "Cama", "Ajustes", "Consola")
private fun number(value: Double?, decimals: Int = 2) = value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—"

@Composable
private fun PrinterScreen(state: UiState, printer: PrinterController, devices: List<UsbSerialDriver>, scan: () -> Unit,
    connect: (String, Int) -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var showConnection by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    val ready = state.connected && !state.busy && state.snapshot.isMarlin
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Jesper 3D", style = MaterialTheme.typography.headlineSmall)
                    Text("CR-10 S4 · control desde tu tablet", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { showProfile = true }, enabled = !state.busy) { Text("Perfil") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (state.connected) confirmation = Confirmation("Desconectar USB", "Los calentadores pueden seguir encendidos al desconectar la app.", "Desconectar") { printer.disconnect() }
                    else { scan(); showConnection = true }
                }, enabled = !state.busy) { Text(if (state.connected) "USB conectado" else "Conectar USB") }
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (state.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                Text(state.message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (state.connected) TextButton(onClick = { printer.emergency() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Parada firmware") }
            }
            state.error?.let {
                Text(it, Modifier.fillMaxWidth().padding(20.dp, 10.dp), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth >= 840.dp
                Row {
                    if (wide) {
                        Column(Modifier.width(168.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sections.forEachIndexed { i, label ->
                                FilledTonalButton(onClick = { section = i }, modifier = Modifier.fillMaxWidth(),
                                    colors = if (section == i) ButtonDefaults.filledTonalButtonColors() else
                                        ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent)) { Text(label) }
                            }
                            Spacer(Modifier.weight(1f))
                            Text("USB directo\nMarlin · sin servidor", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        when (section) {
                            0 -> Overview(state, printer, ready, wide) { confirmation = it }
                            1 -> BedScreen(state, printer, ready) { confirmation = it }
                            2 -> SettingsScreen(state, printer, ready) { confirmation = it }
                            3 -> ConsoleScreen(state)
                        }
                    }
                }
                if (!wide) { /* Navigation is below the content on smaller screens. */ }
            }
            if (sections.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                sections.forEachIndexed { i, text -> TextButton(onClick = { section = i }) { Text(if (section == i) "• $text" else text) } }
            }
        }
    }
    confirmation?.let { confirm ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(confirm.title) }, text = { Text(confirm.detail) },
            confirmButton = { TextButton(onClick = { confirmation = null; confirm.run() }, enabled = !state.busy) { Text(confirm.confirm) } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } })
    }
    if (showConnection) ConnectionDialog(devices, scan, { showConnection = false }) { name, baud -> showConnection = false; connect(name, baud) }
    if (showProfile) ProfileDialog(printer) { showProfile = false }
}

@Composable
private fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun Overview(state: UiState, printer: PrinterController, ready: Boolean, wide: Boolean, confirm: (Confirmation) -> Unit) {
    Text("Tu impresora, a mano", style = MaterialTheme.typography.headlineMedium)
    Text("Consulta el estado y prepara la máquina antes de imprimir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TemperaturePanel("Boquilla", state.snapshot.nozzle, Modifier.weight(1f))
        TemperaturePanel("Cama", state.snapshot.bed, Modifier.weight(1f))
    }
    var nozzle by rememberSaveable { mutableStateOf("200") }
    var bed by rememberSaveable { mutableStateOf("50") }
    Panel("Temperaturas objetivo") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(nozzle, { nozzle = it }, Modifier.weight(1f), label = { Text("Boquilla · °C") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(bed, { bed = it }, Modifier.weight(1f), label = { Text("Cama · °C") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = ready, onClick = {
                runCatching { Commands.heat(nozzle.toInt(), bed.toInt()) }.onSuccess { batch ->
                    confirm(Confirmation("Calentar impresora", "Boquilla: $nozzle °C. Cama: $bed °C. Comprueba que la impresora está en reposo y supervisada.", "Aplicar") { printer.action(batch) })
                }.onFailure { printer.notice(it.message ?: "Temperaturas no válidas.") }
            }) { Text("Aplicar") }
            OutlinedButton(enabled = ready, onClick = { printer.action(Commands.heat(0, 0)) }) { Text("Apagar calentadores") }
        }
    }
    Panel("Movimiento de los ejes") {
        val p = state.snapshot.position
        Text("X ${number(p?.x)}    Y ${number(p?.y)}    Z ${number(p?.z)} mm", style = MaterialTheme.typography.titleMedium)
        Text(if (state.homed) "Origen establecido en esta sesión." else "Busca el origen para habilitar los movimientos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        var step by rememberSaveable { mutableDoubleStateOf(1.0) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.1, 1.0, 10.0).forEach { value -> FilterChip(step == value, { step = value }, label = { Text("${number(value, 1)} mm") }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("X", "Y", "Z").forEach { axis ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedButton(enabled = ready && state.homed, onClick = { printer.jog(axis, step) }) { Text("$axis +") }
                    OutlinedButton(enabled = ready && state.homed, onClick = { printer.jog(axis, -step) }) { Text("$axis −") }
                }
            }
        }
        Button(enabled = ready && printer.profileConfirmed, onClick = {
            confirm(Confirmation("Buscar el origen", "La impresora moverá los ejes X, Y y Z. Confirma que está en reposo, el sensor funciona y la cama está libre.", "Buscar origen") { printer.action(Commands.home) })
        }) { Text("Buscar origen de los ejes") }
        if (!printer.profileConfirmed) Text("Confirma las dimensiones en Perfil para habilitar el origen.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TemperaturePanel(title: String, temperature: Temperature?, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("${number(temperature?.current, 1)} °C", style = MaterialTheme.typography.headlineMedium)
            Text("Objetivo ${number(temperature?.target, 0)} °C", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BedScreen(state: UiState, printer: PrinterController, ready: Boolean, confirm: (Confirmation) -> Unit) {
    val snapshot = state.snapshot
    Text("La forma de tu cama", style = MaterialTheme.typography.headlineMedium)
    Text("Compensación: ${when (snapshot.leveling) { true -> "activada"; false -> "desactivada"; null -> "sin confirmar" }}")
    Panel("Malla bilineal") {
        val mesh = snapshot.mesh
        if (mesh == null) {
            Text("Lee la malla para mostrar los puntos que comunica Marlin. En esta versión se interpreta el formato bilineal indexado.")
        } else {
            Text("${mesh.columns} × ${mesh.rows.size} puntos · diferencia máxima ${number(mesh.range, 3)} mm")
            Text("Altura Z en mm · X aumenta hacia la derecha; Y aumenta hacia arriba.", style = MaterialTheme.typography.bodySmall)
            var selected by remember(mesh) { mutableStateOf<Pair<Int, Int>?>(null) }
            mesh.rows.toSortedMap(reverseOrder()).forEach { (y, row) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Y$y", Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall)
                    row.forEachIndexed { x, z ->
                        val peak = mesh.values.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(0.001) ?: 1.0
                        val color = if (z == null) MaterialTheme.colorScheme.surfaceVariant else
                            if (z >= 0) Color(0xFFD9A56C).copy(alpha = (0.2 + 0.65 * kotlin.math.abs(z) / peak).toFloat()) else
                                MaterialTheme.colorScheme.primary.copy(alpha = (0.12 + 0.65 * kotlin.math.abs(z) / peak).toFloat())
                        Box(Modifier.weight(1f).heightIn(min = 54.dp).background(color, RoundedCornerShape(8.dp))
                            .clickable { selected = x to y }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Text(number(z, 3), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 34.dp)) {
                repeat(mesh.columns) { x -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("X$x", style = MaterialTheme.typography.labelSmall) } }
            }
            selected?.let { (x, y) -> Text("Punto [$x, $y]: ${number(mesh.rows[y]?.get(x), 3)} mm. Índices de malla; la posición XY física no se deduce de estos índices.") }
            Text("Verde: valor negativo · ocre: positivo · —: punto sin medir. Los colores muestran valores Z, no el ajuste de cada tornillo.", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = ready, onClick = { printer.action(Commands.mesh) }) { Text("Leer malla") }
            OutlinedButton(enabled = ready && mesh != null && mesh.values.isNotEmpty(), onClick = {
                val enable = snapshot.leveling != true
                confirm(Confirmation(if (enable) "Activar compensación" else "Desactivar compensación",
                    "Cambia el estado activo de la compensación. No guarda los ajustes en EEPROM.", "Aplicar") { printer.action(Commands.leveling(enable)) })
            }) { Text(if (snapshot.leveling == true) "Desactivar" else "Activar") }
        }
    }
    Panel("Medir una nueva malla") {
        Text("El número y la zona de los puntos dependen del firmware Marlin. Esta app consulta la cuadrícula que devuelve la impresora.")
        val allowed = ready && printer.profileConfirmed && printer.bilinearConfirmed && snapshot.capabilities["Z_PROBE"] == true && snapshot.capabilities["AUTOLEVEL"] == true
        Button(enabled = allowed, onClick = {
            confirm(Confirmation("Medir la cama con el sensor", "Se buscará el origen y se ejecutará la nivelación bilineal. Confirma que el sensor funciona, la cama está libre y las temperaturas se han estabilizado. La malla se sustituye en RAM; no se guarda automáticamente.", "Iniciar medición") { printer.probe() })
        }) { Text("Iniciar medición") }
        if (!allowed) Text("Requiere perfil bilineal confirmado, sensor y nivelación automática anunciados por Marlin, y conexión en reposo.", style = MaterialTheme.typography.bodySmall)
        if (snapshot.probePoints.isNotEmpty()) {
            Text("Coordenadas comunicadas durante la medición", style = MaterialTheme.typography.titleSmall)
            snapshot.probePoints.forEach { Text("X ${number(it.x)} · Y ${number(it.y)} · Z ${number(it.z, 3)} mm", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, printer: PrinterController, ready: Boolean, confirm: (Confirmation) -> Unit) {
    Text("Ajustes de la impresora", style = MaterialTheme.typography.headlineMedium)
    Text("Lectura de los valores activos en RAM. Pueden diferir de los guardados en EEPROM.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(enabled = ready, onClick = { printer.action(Commands.settings) }) { Text("Leer ajustes") }
        OutlinedButton(enabled = ready && state.snapshot.capabilities["EEPROM"] == true, onClick = {
            confirm(Confirmation("Guardar en la impresora", "M500 guarda los ajustes activos admitidos por tu firmware. La persistencia de la malla y del desplazamiento Z debe verificarse tras reiniciar. También se guardarán los cambios activos hechos desde la pantalla de la impresora.", "Guardar en EEPROM") { printer.action(Commands.save) })
        }) { Text("Guardar en EEPROM") }
    }
    Panel("Firmware y capacidades") {
        Text(state.snapshot.firmware ?: "Sin identificar", style = MaterialTheme.typography.bodySmall)
        state.snapshot.capabilities.forEach { (name, supported) ->
            Text("$name · ${if (supported) "sí" else "no"}", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (state.snapshot.settings.isEmpty()) Text("Marlin aún no ha comunicado ajustes. Puedes consultar la respuesta en Consola.")
    state.snapshot.settings.forEach { setting ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(setting.label, style = MaterialTheme.typography.titleMedium)
            Text("${setting.command} ${setting.parameters}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ConsoleScreen(state: UiState) {
    Text("Conversación con Marlin", style = MaterialTheme.typography.headlineMedium)
    Text("Últimas 180 líneas. Las consultas se envían de una en una y esperan la respuesta de Marlin.")
    SelectionContainerCompat(state.log.joinToString("\n").ifEmpty { "Todavía no hay una conexión USB." })
}

@Composable
private fun SelectionContainerCompat(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ConnectionDialog(devices: List<UsbSerialDriver>, scan: () -> Unit, dismiss: () -> Unit, connect: (String, Int) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var baud by rememberSaveable { mutableIntStateOf(115200) }
    var idle by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Conectar por USB") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Conecta la tablet con un cable de datos USB OTG y selecciona la impresora.")
            if (devices.isEmpty()) Text("No se ha detectado un puerto serie compatible.")
            devices.forEach { driver ->
                Row(Modifier.fillMaxWidth().clickable { selected = driver.device.deviceName }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == driver.device.deviceName, onClick = { selected = driver.device.deviceName })
                    Text("${driver.device.productName ?: driver.javaClass.simpleName}\nVID ${driver.device.vendorId} · PID ${driver.device.productId}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row { listOf(115200, 250000, 57600).forEach { rate -> FilterChip(baud == rate, { baud = rate }, label = { Text("$rate") }) } }
            Text("Abrir el USB puede reiniciar algunas placas. Cierra Octo4a y otras apps que usen ese puerto.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(idle, { idle = it }); Text("La impresora está en reposo y no imprime desde SD.")
            }
            TextButton(onClick = scan) { Text("Buscar dispositivos") }
        }
    }, confirmButton = {
        TextButton(enabled = idle && devices.any { it.device.deviceName == selected }, onClick = { selected?.let { connect(it, baud) } }) { Text("Conectar") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable
private fun ProfileDialog(printer: PrinterController, dismiss: () -> Unit) {
    var x by remember { mutableStateOf(number(printer.profile.maxX, 0)) }
    var y by remember { mutableStateOf(number(printer.profile.maxY, 0)) }
    var z by remember { mutableStateOf(number(printer.profile.maxZ, 0)) }
    var bilinear by remember { mutableStateOf(printer.bilinearConfirmed) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Perfil CR-10 S4") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Confirma los recorridos máximos desde el origen. El perfil inicial propone 400 mm por eje; ajusta los valores a tu máquina modificada.")
            OutlinedTextField(x, { x = it }, label = { Text("Límite X · mm") }, singleLine = true)
            OutlinedTextField(y, { y = it }, label = { Text("Límite Y · mm") }, singleLine = true)
            OutlinedTextField(z, { z = it }, label = { Text("Límite Z · mm") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(bilinear, { bilinear = it }); Text("He verificado sensor y nivelación bilineal en mi firmware.")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Esto configura los límites de la app; no modifica el firmware ni el número de puntos de la malla.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton(onClick = {
            runCatching { printer.configure(MachineProfile(x.replace(',', '.').toDouble(), y.replace(',', '.').toDouble(), z.replace(',', '.').toDouble()), bilinear) }
                .onSuccess { dismiss() }.onFailure { error = it.message ?: "Dimensiones no válidas." }
        }) { Text("Confirmar perfil") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
