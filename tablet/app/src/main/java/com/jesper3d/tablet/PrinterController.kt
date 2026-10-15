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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class PrinterApplication : Application() {
    val printer by lazy { PrinterController(this) }
}

data class UiState(
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String = "Conecta tu impresora para consultar su estado.",
    val snapshot: PrinterSnapshot = PrinterSnapshot(),
    val homed: Boolean = false,
    val log: List<String> = emptyList(),
    val error: String? = null,
    val operation: Operation? = null,
    val step: String = "",
    val stepIndex: Int = 0,
    val stepCount: Int = 0,
    val startedAt: Long? = null,
    val lastReplyAt: Long? = null,
    val liveRecord: MeshRecord? = null,
    val history: List<MeshRecord> = emptyList(),
    val meshReadAt: Long? = null,
    val meshMeasuredAt: Long? = null,
    val meshSaveState: MeshSaveState = MeshSaveState.UNKNOWN,
    val meshSavedAt: Long? = null,
    val measurementCompleted: Boolean = false,
    val measurementElapsedMs: Long? = null,
)

class PrinterController internal constructor(private val context: Context,
    private val sendOverride: (suspend (String) -> Unit)? = null,
    preferencesName: String = "machine",
    private val queryIdleTimeoutMs: Long = 30000,
) {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(UiState(history = MeshHistoryCodec.decode(prefs.getString("mesh-history", "") ?: "")))
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val occupied = AtomicBoolean(false)
    private var session: Job? = null
    private var port: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var manager: SerialInputOutputManager? = null
    private var requests = Channel<CommandBatch>(1)
    @Volatile private var generation = 0
    var onClosed: (() -> Unit)? = null
    private val diagnosticLock = Any()
    private val diagnosticFile = File(context.filesDir, "printer-diagnostic.log")
    private val logTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    var profile: MachineProfile = MachineProfile(prefs.getFloat("maxX", 400f).toDouble(),
        prefs.getFloat("maxY", 400f).toDouble(), prefs.getFloat("maxZ", 400f).toDouble())
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
        notice("Perfil actualizado en la tablet.")
    }
    fun notice(text: String) { mutable.update { it.copy(message = text) } }

    fun connect(deviceName: String, baud: Int) {
        if (session?.isActive == true) return
        val id = ++generation
        requests = Channel(1)
        val sessionRequests = requests
        // A complete mesh can arrive in one USB burst after G29.
        val replies = Channel<String>(4096)
        occupied.set(true)
        val previous = state.value
        mutable.value = UiState(busy = true, message = "Abriendo USB…", history = previous.history, log = previous.log)
        session = scope.launch {
            try {
                val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
                    .firstOrNull { it.device.deviceName == deviceName } ?: error("Dispositivo USB no disponible.")
                check(usb.hasPermission(driver.device)) { "Falta el permiso USB." }
                check(baud in listOf(115200, 250000, 57600))
                log("! Conexión: VID=${driver.device.vendorId} PID=${driver.device.productId}; $baud baudios")
                connection = usb.openDevice(driver.device) ?: error("No se puede abrir el USB. Cierra otras apps de impresión.")
                val serial = driver.ports.first()
                port = serial
                serial.open(connection)
                serial.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                runCatching { serial.dtr = true }
                val framer = LineFramer()
                manager = SerialInputOutputManager(serial, object : SerialInputOutputManager.Listener {
                    override fun onNewData(data: ByteArray) {
                        if (generation != id) return
                        try {
                            framer.accept(data).forEach { line ->
                                check(replies.trySend(line).isSuccess) { "Se desbordó la recepción USB; resultado conservado en la tablet." }
                            }
                        } catch (e: Exception) { fail(e.message ?: "Error de lectura USB.", id) }
                    }
                    override fun onRunError(e: Exception) { fail("Se perdió el USB: ${e.message}", id) }
                }).also { it.start() }
                delay(2500)
                while (replies.tryReceive().isSuccess) { /* discard boot chatter before handshake */ }
                mutable.update { it.copy(connected = true) }
                execute(Commands.inspect, replies)
                mutable.update { it.copy(busy = false, operation = null, message =
                    if (it.snapshot.isMarlin) "Conectada por USB. Datos actualizados." else "Firmware sin identificar. Controles bloqueados.") }
                occupied.set(false)
                val polling = launch {
                    while (isActive) {
                        delay(6000)
                        if (state.value.snapshot.isMarlin) submit(Commands.status, silent = true)
                    }
                }
                try {
                    for (batch in sessionRequests) {
                        try {
                            execute(batch, replies)
                            if (batch.operation != Operation.STATUS) mutable.update { it.copy(message = completionMessage(batch, it)) }
                        } catch (e: CommandRejectedException) {
                            log("! ${e.message}")
                            mutable.update { it.copy(error = e.message, message = if (batch.operation == Operation.PROBE && it.measurementCompleted)
                                "Medición terminada. Revisa el aviso de la consulta posterior." else "Marlin ha rechazado la operación.", homed = false) }
                        } finally {
                            mutable.update { it.copy(busy = false, operation = null, step = "") }
                            occupied.set(false)
                        }
                    }
                } finally { polling.cancel() }
            } catch (e: CommandTimeoutException) {
                // A protocol deadline must not be mistaken for parent coroutine cancellation.
                log("! TIEMPO DE ESPERA: ${e.command}")
                mutable.update { it.copy(error = e.message, message = if (it.measurementCompleted)
                    "La medición terminó; falló una respuesta posterior. Consulta la copia de la tablet." else
                    "Sin respuesta de Marlin. Consulta el registro antes de reconectar.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                log("! FALLO: ${e.message}")
                mutable.update { it.copy(error = e.message ?: "Error de conexión.", message = "Conexión interrumpida. Se conserva la última lectura.") }
            } finally {
                if (generation == id) generation++
                manager?.stop(); manager = null
                runCatching { port?.close() }; port = null
                runCatching { connection?.close() }; connection = null
                occupied.set(false)
                mutable.update { it.copy(connected = false, busy = false, homed = false, operation = null, step = "") }
                log("! USB cerrado; nunca se repiten automáticamente movimientos ni mediciones.")
                withContext(NonCancellable + Dispatchers.Main) { onClosed?.invoke() }
            }
        }
    }

    private fun completionMessage(batch: CommandBatch, state: UiState): String = when (batch.operation) {
        Operation.PROBE -> if (state.liveRecord != null) "Malla actualizada y copiada en la tablet. ${saveLabel(state.meshSaveState)}"
            else "Medición terminada; Marlin no ha comunicado una malla bilineal legible. Revisa el registro."
        Operation.SAVE -> saveLabel(state.meshSaveState)
        Operation.READ_MESH -> if (state.liveRecord != null) "Malla de la impresora actualizada en pantalla." else "Marlin no ha comunicado una malla bilineal legible."
        else -> "${batch.title}: completado."
    }
    private fun saveLabel(state: MeshSaveState) = when (state) {
        MeshSaveState.CONFIRMED -> "Marlin ha confirmado el guardado."
        MeshSaveState.ACKNOWLEDGED_ONLY -> "Orden de guardado aceptada; almacenamiento sin confirmar."
        MeshSaveState.UNSAVED -> "Pendiente de guardar en la impresora."
        MeshSaveState.UNKNOWN -> "Guardado en la impresora sin verificar."
    }

    internal suspend fun execute(batch: CommandBatch, input: Channel<String>) {
        val silent = batch.operation == Operation.STATUS
        val started = System.currentTimeMillis()
        mutable.update { it.copy(busy = true, operation = batch.operation,
            message = if (silent) it.message else batch.title, error = if (silent) it.error else null,
            startedAt = started, stepCount = batch.commands.size,
            homed = if (batch.establishesHome || batch.invalidatesHome) false else it.homed) }
        for ((index, command) in batch.commands.withIndex()) {
            while (true) {
                val unsolicited = input.tryReceive().getOrNull() ?: break
                if (Marlin.isFatal(unsolicited) || Marlin.isRestart(unsolicited))
                    throw FirmwareStoppedException("Marlin se reinició o se detuvo: $unsolicited")
                consume(unsolicited, false)
            }
            mutable.update { it.copy(stepIndex = index + 1, step = stepLabel(command)) }
            if (command == "M503") mutable.update { it.copy(snapshot = it.snapshot.copy(settings = emptyList())) }
            if (command == "M114") mutable.update { it.copy(snapshot = it.snapshot.copy(position = null)) }
            if (command == "G28") mutable.update { it.copy(snapshot = it.snapshot.copy(leveling = null, position = null)) }
            if (command == "G29") mutable.update { it.copy(
                snapshot = it.snapshot.copy(probePoints = emptyList(), mesh = null, leveling = null),
                liveRecord = null, meshSaveState = MeshSaveState.UNSAVED, meshSavedAt = null,
                measurementCompleted = false, measurementElapsedMs = null, meshMeasuredAt = null, meshReadAt = null) }
            val reader = if (command == "M420 V" || command == "G29") BilinearMeshReader() else null
            write(command)
            val longRunning = command == "G29" || command == "G28" || command == "M400"
            val reply = awaitCommandReply(command, input,
                idleTimeoutMs = if (longRunning) 180000 else queryIdleTimeoutMs,
                totalTimeoutMs = if (longRunning) batch.timeoutMs else 60000,
            ) { line -> consume(line, command == "M503"); reader?.accept(line) }
            val rejection = reply.rejection
            if (rejection != null) {
                if (batch.operation == Operation.INSPECT ||
                    (batch.operation == Operation.PROBE && command in listOf("M420 V", "M114"))) {
                    log("! Consulta no disponible: $command · ${reply.rejection}")
                    mutable.update { it.copy(error = "${stepLabel(command)}: ${reply.rejection}") }
                    continue
                }
                throw CommandRejectedException(rejection)
            }
            if (command == "G29") mutable.update { it.copy(measurementCompleted = true,
                meshMeasuredAt = System.currentTimeMillis(), measurementElapsedMs = System.currentTimeMillis() - started) }
            reader?.finish()?.let { mesh ->
                recordMesh(mesh, if (command == "G29" || batch.operation == Operation.PROBE) MeshOrigin.MEASURED else MeshOrigin.READ)
            }
            if (command == "M500") {
                val now = System.currentTimeMillis()
                mutable.update { state ->
                    val record = state.liveRecord?.let { if (reply.stored) it.copy(savedAt = now) else it }
                    state.copy(meshSaveState = if (reply.stored) MeshSaveState.CONFIRMED else MeshSaveState.ACKNOWLEDGED_ONLY,
                        meshSavedAt = if (reply.stored) now else null, liveRecord = record,
                        history = state.history.map { old -> if (record != null && record.id == old.id) record else old })
                }
                persistHistory()
            }
        }
        if (batch.establishesHome) mutable.update { it.copy(homed = it.snapshot.position != null) }
    }
    private fun stepLabel(command: String) = when (command) {
        "G21" -> "Preparando unidades"; "G28" -> "Buscando el origen"; "G29" -> "Midiendo los puntos de la cama"
        "M420 V" -> "Recibiendo la malla"; "M114" -> "Consultando posición"; "M400" -> "Esperando al movimiento"
        "M500" -> "Guardando en la impresora"; "M503" -> "Leyendo ajustes"; "M105" -> "Consultando temperaturas"
        "M115" -> "Identificando firmware"; else -> "Aplicando cambio"
    }
    private fun consume(line: String, settings: Boolean) {
        log("< $line")
        mutable.update { it.copy(snapshot = Marlin.parse(it.snapshot, line, settings), lastReplyAt = System.currentTimeMillis()) }
    }
    private fun recordMesh(mesh: Mesh, origin: MeshOrigin) {
        val now = System.currentTimeMillis()
        mutable.update { state ->
            val previous = state.liveRecord
            val record = if (previous != null && previous.mesh == mesh) previous else
                MeshRecord(capturedAt = now, origin = origin, mesh = mesh)
            state.copy(snapshot = state.snapshot.copy(mesh = mesh), liveRecord = record, meshReadAt = now,
                meshSaveState = if (previous?.mesh == mesh) state.meshSaveState else
                    if (origin == MeshOrigin.MEASURED) MeshSaveState.UNSAVED else MeshSaveState.UNKNOWN,
                meshSavedAt = if (previous?.mesh == mesh) state.meshSavedAt else null,
                history = (listOf(record) + state.history.filterNot { it.id == record.id }).take(12))
        }
        persistHistory()
    }
    private fun persistHistory() { prefs.edit().putString("mesh-history", MeshHistoryCodec.encode(state.value.history)).apply() }

    private fun submit(batch: CommandBatch, silent: Boolean = false): Boolean {
        if (!state.value.connected || !occupied.compareAndSet(false, true)) {
            if (!silent) notice("Espera a que termine la operación actual.")
            return false
        }
        mutable.update { it.copy(busy = true) }
        if (!requests.trySend(batch).isSuccess) { occupied.set(false); mutable.update { it.copy(busy = false) }; return false }
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
    fun probe(saveAfter: Boolean = false) {
        if (!profileConfirmed || !bilinearConfirmed || state.value.snapshot.capabilities["Z_PROBE"] != true ||
            state.value.snapshot.capabilities["AUTOLEVEL"] != true) {
            notice("Confirma el perfil bilineal. Marlin debe anunciar sensor y nivelación automática."); return
        }
        if (saveAfter && state.value.snapshot.capabilities["EEPROM"] != true) { notice("Marlin no anuncia almacenamiento EEPROM."); return }
        action(Commands.probe(saveAfter))
    }
    fun save() {
        if (state.value.snapshot.capabilities["EEPROM"] != true) { notice("Marlin no anuncia almacenamiento EEPROM."); return }
        action(Commands.save)
    }
    fun emergency() {
        scope.launch {
            try { write("M112"); mutable.update { it.copy(error = "M112 solicitado. Comprueba la parada; usa el interruptor si no responde.") } }
            catch (e: Exception) { mutable.update { it.copy(error = "No se pudo enviar M112. Usa el interruptor de la impresora.") } }
            finally { disconnect("Parada solicitada. Revisa la impresora antes de reconectar.") }
        }
    }
    private suspend fun write(command: String) = writeLock.withLock {
        if (sendOverride != null) sendOverride.invoke(command)
        else (port ?: error("USB desconectado.")).write((command + "\n").toByteArray(Charsets.US_ASCII), 1500)
        log("> $command")
    }
    private fun log(line: String) {
        val entry = synchronized(diagnosticLock) {
            val entry = "${logTime.format(Date())} $line"
            runCatching {
                if (diagnosticFile.length() > 512000) diagnosticFile.writeText(diagnosticFile.readText().takeLast(256000))
                diagnosticFile.appendText(entry + "\n")
            }
            entry
        }
        mutable.update { it.copy(log = (it.log + entry).takeLast(800)) }
    }
    fun diagnostics(): String = synchronized(diagnosticLock) {
        val current = state.value
        "Jesper 3D 0.2.0-alpha\nConectada: ${current.connected}\nMedición terminada: ${current.measurementCompleted}\n" +
            "Guardado: ${current.meshSaveState}\nEstado: ${current.message}\nError: ${current.error ?: "ninguno"}\n\n" +
            runCatching { diagnosticFile.readText() }.getOrDefault(current.log.joinToString("\n"))
    }
    private fun fail(message: String, id: Int) {
        if (generation != id) return
        log("! $message")
        mutable.update { it.copy(error = message, message = "USB interrumpido. Resultado y registro conservados.") }
        session?.cancel()
    }
    fun disconnect(message: String? = "USB desconectado. Se conserva la última lectura en la tablet.") {
        if (message != null) notice(message)
        session?.cancel()
    }
}
