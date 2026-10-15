package com.jesper3d.protocol

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class CommandTimeoutException(val command: String) : IOException(
    "Marlin no ha terminado de responder a $command. Se conserva la última malla leída. La operación no se repite automáticamente.")
class FirmwareStoppedException(message: String) : IOException(message)
class CommandRejectedException(message: String) : IOException(message)

data class CommandReply(val stored: Boolean, val rejection: String?)

/** A command deadline is a protocol failure, never a cancellation of its parent session. */
suspend fun awaitCommandReply(
    command: String,
    input: ReceiveChannel<String>,
    idleTimeoutMs: Long,
    totalTimeoutMs: Long,
    onLine: (String) -> Unit,
): CommandReply {
    val result = withTimeoutOrNull(totalTimeoutMs) {
        var rejection: String? = null
        var stored = false
        while (true) {
            // Busy/temperature/probe reports refresh the inactivity deadline, not the hard limit.
            val line = withTimeoutOrNull(idleTimeoutMs) { input.receive() }
                ?: throw CommandTimeoutException(command)
            onLine(line)
            if (Marlin.isFatal(line)) throw FirmwareStoppedException("Marlin ha detenido la impresora: $line")
            if (Marlin.isRestart(line)) throw FirmwareStoppedException("Marlin se ha reiniciado. Comprueba la impresora antes de reconectar.")
            if (line.trim().startsWith("Resend:", true) || line.trim().startsWith("rs ", true))
                throw FirmwareStoppedException("Marlin pide resincronizar. No se repite el movimiento; reconecta cuando termine la impresora.")
            if (Marlin.isError(line.trim())) rejection = line
            if (line.contains("Settings Stored", true)) stored = true
            if (Marlin.isOk(line)) return@withTimeoutOrNull CommandReply(stored && rejection == null, rejection)
        }
        @Suppress("UNREACHABLE_CODE") CommandReply(false, rejection)
    }
    return result ?: throw CommandTimeoutException(command)
}
