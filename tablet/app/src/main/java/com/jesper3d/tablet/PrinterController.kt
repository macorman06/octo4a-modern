package com.jesper3d.tablet

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.jesper3d.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class PrinterApplication : Application() {
    val printer by lazy { PrinterController(this) }
}

data class UiState(
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String = "Conecta la impresora por USB para empezar.",
    val snapshot: PrinterSnapshot = PrinterSnapshot(),
    val homed: Boolean = false,
    val log: List<String> = emptyList(),
    val error: String? = null,
)

class PrinterController(private val context: Context) {
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val occupied = AtomicBoolean(false)
    private var session: Job? = null
    private var port: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var manager: SerialInputOutputManager? = null
    private var requests = Channel<CommandBatch>(1)
    private var replies = Channel<String>(256)
    @Volatile private var generation = 0
    var onClosed: (() -> Unit)? = null
    private val prefs = context.getSharedPreferences("machine", Context.MODE_PRIVATE)
    var profile: MachineProfile = MachineProfile(
        prefs.getFloat("maxX", 400f).toDouble(), prefs.getFloat("maxY", 400f).toDouble(), prefs.getFloat("maxZ", 400f).toDouble())
        private set
    var profileConfirmed = prefs.getBoolean("confirmed", false)
        private set
    var bilinearConfirmed = prefs.getBoolean("bilinear", false)
        private set

    fun configure(profile: MachineProfile, bilinear: Boolean) {
        check(!state.value.busy) { "Espera a que termine la operación." }
        this.profile = profile; profileConfirmed = true; bilinearConfirmed = bilinear
        prefs.edit().putFloat("maxX", profile.maxX.toFloat()).putFloat("maxY", profile.maxY.toFloat())
            .putFloat("maxZ", profile.maxZ.toFloat()).putBoolean("confirmed", true).putBoolean("bilinear", bilinear).apply()
        notice("Perfil de máquina confirmado. Estos límites pertenecen a la app.")
    }
    fun notice(text: String) { mutable.update { it.copy(message = text) } }

    fun connect(deviceName: String, baud: Int) {
        if (session?.isActive == true) return
        val id = ++generation
        requests = Channel(1); replies = Channel(256)
        val sessionRequests = requests
        val sessionReplies = replies
        occupied.set(true)
        mutable.value = UiState(busy = true, message = "Abriendo USB y esperando a Marlin…")
        session = scope.launch {
            try {
                val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
                    .firstOrNull { it.device.deviceName == deviceName } ?: error("Dispositivo USB no disponible.")
                check(usb.hasPermission(driver.device)) { "Falta el permiso USB." }
                check(baud in listOf(115200, 250000, 57600))
                connection = usb.openDevice(driver.device) ?: error("No se puede abrir el USB. Cierra otras apps de impresión.")
                val serial = driver.ports.first()
                port = serial
                serial.open(connection)
                serial.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                // Some CDC boards need DTR; opening may reset the controller. Only connect while idle.
                runCatching { serial.dtr = true }
                val framer = LineFramer()
                manager = SerialInputOutputManager(serial, object : SerialInputOutputManager.Listener {
                    override fun onNewData(data: ByteArray) {
                        if (generation != id) return
                        try {
                            framer.accept(data).forEach { line ->
                                check(sessionReplies.trySend(line).isSuccess) { "Demasiadas respuestas USB; reconecta." }
                            }
                        } catch (e: Exception) { fail(e.message ?: "Error al leer USB.", id) }
                    }
                    override fun onRunError(e: Exception) { fail("Se perdió el USB: ${e.message}", id) }
                }).also { it.start() }
                delay(2500)
                while (sessionReplies.tryReceive().isSuccess) { /* discard boot chatter before handshake */ }
                mutable.update { it.copy(connected = true) }
                execute(Commands.inspect, sessionReplies)
                mutable.update { it.copy(busy = false, message = if (it.snapshot.isMarlin) "Impresora conectada por USB." else "Firmware sin identificar: controles bloqueados.") }
                occupied.set(false)
                val polling = launch {
                    while (isActive) {
                        delay(4000)
                        if (state.value.snapshot.isMarlin) submit(Commands.status)
                    }
                }
                try {
                    for (batch in sessionRequests) {
                        try {
                            execute(batch, sessionReplies)
                            mutable.update { it.copy(message = "${batch.title}: completado.", error = null) }
                        } catch (e: RejectedCommand) {
                            mutable.update { it.copy(error = e.message, message = "Marlin ha rechazado la operación.", homed = false) }
                        } finally {
                            mutable.update { it.copy(busy = false) }
                            occupied.set(false)
                        }
                    }
                } finally { polling.cancel() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(error = e.message ?: "No se pudo completar la conexión.", message = "Conexión detenida. Revisa la impresora y reconecta.") }
            } finally {
                if (generation == id) generation++
                manager?.stop(); manager = null
                runCatching { port?.close() }; port = null
                runCatching { connection?.close() }; connection = null
                occupied.set(false)
                mutable.update { it.copy(connected = false, busy = false, homed = false) }
                withContext(NonCancellable + Dispatchers.Main) { onClosed?.invoke() }
            }
        }
    }

    private class RejectedCommand(message: String) : Exception(message)
    private suspend fun execute(batch: CommandBatch, input: Channel<String>) {
        mutable.update { it.copy(busy = true, message = batch.title, error = null,
            homed = if (batch.establishesHome || batch.invalidatesHome) false else it.homed) }
        for (command in batch.commands) {
            // Replies received outside a request are unsolicited. Never use an old 'ok'.
            while (true) {
                val unsolicited = input.tryReceive().getOrNull() ?: break
                check(!Marlin.isFatal(unsolicited) && !unsolicited.startsWith("start", true)) { "Marlin se reinició o se detuvo. Reconecta." }
                mutable.update { it.copy(snapshot = Marlin.parse(it.snapshot, unsolicited)) }
                log("< $unsolicited")
            }
            if (command == "M503") mutable.update { it.copy(snapshot = it.snapshot.copy(settings = emptyList())) }
            if (command == "G29") mutable.update { it.copy(snapshot = it.snapshot.copy(probePoints = emptyList())) }
            val meshReader = if (command == "M420 V") BilinearMeshReader() else null
            if (meshReader != null) mutable.update { it.copy(snapshot = it.snapshot.copy(mesh = null)) }
            write(command)
            var rejected: String? = null
            withTimeout(batch.timeoutMs) {
                while (true) {
                    val line = input.receive()
                    log("< $line")
                    check(!Marlin.isFatal(line)) { "Marlin ha detenido la impresora: $line" }
                    check(!line.startsWith("start", true)) { "La impresora se ha reiniciado. Reconecta." }
                    check(!line.startsWith("Resend:", true) && !line.startsWith("rs ", true)) { "Marlin pide resincronizar. Reconecta antes de continuar." }
                    mutable.update { it.copy(snapshot = Marlin.parse(it.snapshot, line, command == "M503")) }
                    meshReader?.accept(line)
                    if (Marlin.isError(line)) rejected = line
                    if (Marlin.isOk(line)) break
                }
            }
            if (rejected != null) {
                if (batch === Commands.inspect) {
                    log("! Consulta no disponible: $command")
                } else throw RejectedCommand(rejected!!)
            }
            meshReader?.finish()?.let { mesh -> mutable.update { it.copy(snapshot = it.snapshot.copy(mesh = mesh)) } }
        }
        if (batch.establishesHome) mutable.update { it.copy(homed = it.snapshot.position != null) }
    }

    fun submit(batch: CommandBatch): Boolean {
        if (!state.value.connected || !occupied.compareAndSet(false, true)) return false
        mutable.update { it.copy(busy = true) }
        if (!requests.trySend(batch).isSuccess) {
            occupied.set(false); mutable.update { it.copy(busy = false) }; return false
        }
        return true
    }
    fun action(batch: CommandBatch) {
        if (!state.value.snapshot.isMarlin) { notice("Identifica Marlin antes de usar los controles."); return }
        submit(batch)
    }
    fun jog(axis: String, delta: Double) {
        runCatching {
            check(profileConfirmed) { "Confirma las dimensiones en Perfil antes de mover." }
            Commands.jog(axis, delta, state.value.snapshot.position, state.value.homed, profile)
        }.onSuccess { action(it) }.onFailure { notice(it.message ?: "Movimiento no permitido.") }
    }
    fun probe() {
        if (!profileConfirmed || !bilinearConfirmed || state.value.snapshot.capabilities["Z_PROBE"] != true ||
            state.value.snapshot.capabilities["AUTOLEVEL"] != true) {
            notice("Se requiere un perfil bilineal confirmado y que Marlin anuncie sensor y nivelación automática."); return
        }
        action(Commands.probe)
    }
    fun emergency() {
        scope.launch {
            try {
                write("M112")
                mutable.update { it.copy(error = "M112 enviado por USB. Comprueba la parada en la impresora; usa su interruptor si no responde.") }
            } catch (e: Exception) {
                mutable.update { it.copy(error = "No se ha confirmado el envío de M112. Detén la impresora con su interruptor.") }
            }
            finally {
                disconnect()
            }
        }
    }
    private suspend fun write(command: String) = writeLock.withLock {
        val serial = port ?: error("USB desconectado.")
        serial.write((command + "\n").toByteArray(Charsets.US_ASCII), 1500)
        log("> $command")
    }
    private fun log(line: String) { mutable.update { it.copy(log = (it.log + line).takeLast(180)) } }
    private fun fail(message: String, id: Int) {
        if (generation != id) return
        mutable.update { it.copy(error = message, message = "Conexión USB interrumpida.") }
        session?.cancel()
    }
    fun disconnect() { session?.cancel() }
}
