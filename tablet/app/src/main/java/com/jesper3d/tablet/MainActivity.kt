package com.jesper3d.tablet

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
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
import java.io.File

class MainActivity : ComponentActivity() {
    private val printer get() = (application as PrinterApplication).printer
    private val usb get() = getSystemService(USB_SERVICE) as UsbManager
    private var devices by mutableStateOf<List<UsbSerialDriver>>(emptyList())
    private val permissionAction get() = "$packageName.USB_PERMISSION"
    private var requestedDevice: String? = null
    private val exportFile get() = File(cacheDir, "pending-export.txt")
    private val exportDocument = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { uri ->
            runCatching {
                requireNotNull(contentResolver.openOutputStream(uri)).use { output -> exportFile.inputStream().use { it.copyTo(output) } }
            }.onSuccess { printer.notice("Archivo exportado.") }.onFailure { printer.notice("No se pudo guardar el archivo: ${it.message}") }
        }
    }
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
        ContextCompat.registerReceiver(this, receiver, IntentFilter(permissionAction), ContextCompat.RECEIVER_NOT_EXPORTED)
        scan()
        setContent {
            JesperTheme {
                val state by printer.state.collectAsStateWithLifecycle()
                DisposableEffect(state.connected) {
                    if (state.connected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                PrinterScreen(state, printer, devices, ::scan, ::requestConnection) { name, type, text ->
                    runCatching {
                        exportFile.writeText(text)
                        exportDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(type).putExtra(Intent.EXTRA_TITLE, name))
                    }.onFailure { printer.notice("No se pudo preparar la exportación.") }
                }
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
