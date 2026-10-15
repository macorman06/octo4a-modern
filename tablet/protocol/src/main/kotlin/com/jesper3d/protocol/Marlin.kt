package com.jesper3d.protocol

import java.util.Locale

data class Temperature(val current: Double, val target: Double?)
data class Position(val x: Double, val y: Double, val z: Double)
data class Setting(val command: String, val parameters: String, val label: String)
data class ProbePoint(val x: Double, val y: Double, val z: Double)
data class Mesh(val rows: Map<Int, List<Double?>>, val columns: Int) {
    val values get() = rows.values.flatten().filterNotNull()
    val range get() = values.takeIf { it.isNotEmpty() }?.let { it.max() - it.min() }
}
data class PrinterSnapshot(
    val firmware: String? = null,
    val capabilities: Map<String, Boolean> = emptyMap(),
    val nozzle: Temperature? = null,
    val bed: Temperature? = null,
    val position: Position? = null,
    val settings: List<Setting> = emptyList(),
    val mesh: Mesh? = null,
    val leveling: Boolean? = null,
    val bilinear: Boolean = false,
    val probePoints: List<ProbePoint> = emptyList(),
) {
    val isMarlin get() = firmware?.contains("Marlin", ignoreCase = true) == true
}

/** Serial packets do not coincide with lines. Keep partial lines across reads. */
class LineFramer {
    private val pending = StringBuilder()
    fun accept(bytes: ByteArray): List<String> {
        val lines = mutableListOf<String>()
        bytes.forEach { byte ->
            when (val c = (byte.toInt() and 0xff).toChar()) {
                '\n' -> { lines += pending.toString().trimEnd('\r'); pending.clear() }
                else -> {
                    check(pending.length < 16384) { "Respuesta USB demasiado larga; reconecta." }
                    pending.append(c)
                }
            }
        }
        return lines
    }
}

object Marlin {
    private const val NUMBER = "[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)"
    private val positionPattern = Regex("(?:^|\\s)X:($NUMBER)\\s+Y:($NUMBER)\\s+Z:($NUMBER)")
    private val settingPattern = Regex("^(M\\d+)\\s+(.+)$")
    private val probePattern = Regex("Bed X:\\s*($NUMBER)\\s+Y:\\s*($NUMBER)\\s+Z:\\s*($NUMBER)")
    private val labels = mapOf(
        "M92" to "Pasos por milímetro", "M201" to "Aceleración máxima",
        "M203" to "Velocidad máxima", "M204" to "Aceleraciones",
        "M205" to "Movimiento avanzado", "M206" to "Desplazamiento de origen",
        "M301" to "PID de la boquilla", "M304" to "PID de la cama",
        "M420" to "Compensación de cama", "M851" to "Desplazamiento del sensor",
        "M906" to "Corriente de motores", "M900" to "Avance lineal",
    )
    fun isOk(line: String) = Regex("^ok(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(line.trim())
    fun isRestart(line: String) = line.trim().equals("start", ignoreCase = true)
    fun isFatal(line: String) = line.startsWith("!!") || line.contains("Printer halted", true) ||
        line.contains("kill()", true) || line.contains("THERMAL RUNAWAY", true)
    fun isError(line: String) = line.startsWith("Error:", true) ||
        line.contains("Unknown command", true) || line.startsWith("Resend:", true) || line.startsWith("rs ", true)

    fun parse(state: PrinterSnapshot, raw: String, settingsReport: Boolean = false): PrinterSnapshot {
        val line = raw.trim().removePrefix("echo:").trim()
        var next = state
        if (line.startsWith("FIRMWARE_NAME:")) next = next.copy(firmware = line.removePrefix("FIRMWARE_NAME:"))
        Regex("^Cap:([A-Z_]+):([01])$").matchEntire(line)?.let {
            next = next.copy(capabilities = next.capabilities + (it.groupValues[1] to (it.groupValues[2] == "1")))
        }
        fun temp(prefix: String): Temperature? =
            Regex("(?:^|\\s)$prefix:($NUMBER)(?:\\s*/\\s*($NUMBER))?").find(line)?.let {
                Temperature(it.groupValues[1].toDouble(), it.groupValues[2].toDoubleOrNull())
            }
        temp("T(?:0)?")?.let { next = next.copy(nozzle = it) }
        temp("B")?.let { next = next.copy(bed = it) }
        positionPattern.find(line)?.let {
            next = next.copy(position = Position(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), it.groupValues[3].toDouble()))
        }
        if (line.contains("Bilinear Leveling Grid", true)) next = next.copy(bilinear = true)
        Regex("Bed Leveling (ON|OFF)", RegexOption.IGNORE_CASE).find(line)?.let {
            next = next.copy(leveling = it.groupValues[1].equals("ON", true))
        }
        if (settingsReport) settingPattern.matchEntire(line)?.let {
            val command = it.groupValues[1]
            val setting = Setting(command, it.groupValues[2], labels[command] ?: command)
            next = next.copy(settings = next.settings.filterNot { s -> s.command == command && s.parameters == setting.parameters } + setting)
            if (command == "M420") Regex("(?:^|\\s)S([01])(?:\\s|$)").find(setting.parameters)?.let { match ->
                next = next.copy(leveling = match.groupValues[1] == "1")
            }
        }
        probePattern.find(line)?.let {
            val point = ProbePoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), it.groupValues[3].toDouble())
            next = next.copy(probePoints = (next.probePoints.filterNot { old -> old.x == point.x && old.y == point.y } + point).takeLast(4096))
        }
        return next
    }
}

/** Only indexed rectangular bilinear reports are accepted; UBL has different ordering. */
class BilinearMeshReader {
    private var active = false
    private var invalid = false
    private var columns = 0
    private val rows = linkedMapOf<Int, List<Double?>>()
    fun accept(raw: String) {
        val line = raw.trim().removePrefix("echo:").trim()
        if (line.contains("Bilinear Leveling Grid", true)) {
            active = true; invalid = false; columns = 0; rows.clear(); return
        }
        if (line.contains("Subdivided", true)) { active = false; return }
        if (!active || line.isBlank()) return
        val parts = line.split(Regex("\\s+"))
        if (columns == 0) {
            val indices = parts.map { it.toIntOrNull() }
            if (indices.size >= 2 && indices == indices.indices.toList()) columns = indices.size
            return
        }
        val index = parts.firstOrNull()?.toIntOrNull() ?: return
        if (parts.size != columns + 1 || index !in 0..63 || rows.containsKey(index)) {
            invalid = true; return
        }
        val values = parts.drop(1).map { value ->
            when {
                value == "nan" || value == "NaN" || value.all { it == '*' } -> null
                else -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: run { invalid = true; null }
            }
        }
        rows[index] = values
    }
    fun finish(): Mesh? = if (!invalid && columns >= 2 && rows.size >= 2 && rows.keys.sorted() == rows.indices.toList())
        Mesh(rows.toSortedMap(), columns) else null
    private val <K, V> Map<K, V>.indices get() = 0 until size
}

data class MachineProfile(val maxX: Double = 400.0, val maxY: Double = 400.0, val maxZ: Double = 400.0) {
    init { require(listOf(maxX, maxY, maxZ).all { it.isFinite() && it in 1.0..2000.0 }) }
}
enum class Operation { INSPECT, STATUS, READ_MESH, SETTINGS, HOME, PROBE, SAVE, CONTROL }
data class CommandBatch(val title: String, val commands: List<String>, val timeoutMs: Long = 30000,
    val establishesHome: Boolean = false, val invalidatesHome: Boolean = false,
    val operation: Operation = Operation.CONTROL)

object Commands {
    val inspect = CommandBatch("Leyendo impresora", listOf("M115", "M105", "M114", "M503", "M420 V"), operation = Operation.INSPECT)
    val settings = CommandBatch("Leyendo ajustes activos", listOf("M503"), operation = Operation.SETTINGS)
    val mesh = CommandBatch("Leyendo malla", listOf("M420 V"), operation = Operation.READ_MESH)
    val status = CommandBatch("Actualizando estado", listOf("M105", "M114"), operation = Operation.STATUS)
    val home = CommandBatch("Buscando origen", listOf("G21", "G28", "M400", "M114", "M420"), 300000, establishesHome = true, operation = Operation.HOME)
    val probe = CommandBatch("Midiendo cama", listOf("G21", "G28", "G29", "M420 V", "M114"), 1200000, establishesHome = true, operation = Operation.PROBE)
    val save = CommandBatch("Guardando en la impresora", listOf("M500"), operation = Operation.SAVE)
    fun probe(saveAfter: Boolean) = if (saveAfter) probe.copy(commands = probe.commands + "M500") else probe
    fun fan(percent: Int): CommandBatch {
        require(percent in 0..100)
        return CommandBatch("Ventilador: $percent % solicitado", listOf(if (percent == 0) "M107" else "M106 S${percent * 255 / 100}"))
    }
    fun leveling(enabled: Boolean) = CommandBatch("Compensación de cama", listOf("M420 S${if (enabled) 1 else 0}", "M420"))
    fun heat(nozzle: Int, bed: Int): CommandBatch {
        require(nozzle in 0..240 && bed in 0..80) { "Límites de esta versión: boquilla 0–240 °C y cama 0–80 °C." }
        return CommandBatch("Ajustando temperaturas", listOf("M104 S$nozzle", "M140 S$bed", "M105"))
    }
    fun jog(axis: String, delta: Double, position: Position?, homed: Boolean, profile: MachineProfile): CommandBatch {
        require(homed && position != null) { "Busca el origen desde esta app antes de mover." }
        require(axis in listOf("X", "Y", "Z") && delta.isFinite() && delta != 0.0 && kotlin.math.abs(delta) <= 10.0)
        val current = when (axis) { "X" -> position.x; "Y" -> position.y; else -> position.z }
        val limit = when (axis) { "X" -> profile.maxX; "Y" -> profile.maxY; else -> profile.maxZ }
        val target = current + delta
        require(target.isFinite() && target in 0.0..limit) { "Movimiento fuera de los límites confirmados." }
        val value = String.format(Locale.US, "%.3f", target)
        return CommandBatch("Moviendo $axis", listOf("G21", "G90", "G1 $axis$value F${if (axis == "Z") 120 else 1200}", "M400", "M114"))
    }
}
