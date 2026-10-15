package com.jesper3d.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SessionRegressionTest {
    @Test fun `a G29 longer than ten minutes remains alive while Marlin reports busy`() = runTest {
        val input = Channel<String>(64)
        val sender = launch {
            repeat(11) { delay(60000); input.send("echo:busy: processing") }
            input.send("Bilinear Leveling Grid:")
            input.send("0 1")
            input.send("0 -0.100 0.000")
            input.send("1 0.100 0.200")
            input.send("ok")
        }
        val mesh = BilinearMeshReader()
        val result = awaitCommandReply("G29", input, 180000, 1200000, mesh::accept)
        assertNull(result.rejection)
        assertEquals(0.3, mesh.finish()!!.range!!, 0.000001)
        sender.join()
        // A subsequent query uses the same channel/session, with no reconnect.
        input.send("ok T:20 /0 B:21 /0")
        assertNull(awaitCommandReply("M105", input, 30000, 60000) {}.rejection)
    }
    @Test fun `an inactivity timeout is a visible protocol error rather than coroutine cancellation`() = runTest {
        val input = Channel<String>()
        val error = runCatching { awaitCommandReply("M420 V", input, 1000, 10000) {} }.exceptionOrNull()
        assertTrue(error is CommandTimeoutException)
        assertFalse(error is CancellationException)
        assertTrue(currentCoroutineContext().isActive)
        assertTrue(error!!.message!!.contains("M420 V"))
    }
    @Test fun `hard deadline still bounds endless busy responses`() = runTest {
        val input = Channel<String>(32)
        val sender = launch { while (isActive) { input.send("busy: processing"); delay(100) } }
        val error = runCatching { awaitCommandReply("G29", input, 500, 1000) {} }.exceptionOrNull()
        sender.cancelAndJoin()
        assertTrue(error is CommandTimeoutException)
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun `external cancellation is not hidden as a command timeout`() = runTest {
        val task = launch { awaitCommandReply("G29", Channel(), 180000, 1200000) {} }
        task.cancelAndJoin()
        assertTrue(task.isCancelled)
    }
    @Test fun `probe start messages do not look like a controller reboot`() = runTest {
        val input = Channel<String>(4)
        input.send("Starting probing")
        input.send("ok")
        assertNull(awaitCommandReply("G29", input, 1000, 10000) {}.rejection)
        assertFalse(Marlin.isRestart("Starting probing"))
        assertTrue(Marlin.isRestart(" start "))
    }
    @Test fun `a bare ok never proves EEPROM storage`() = runTest {
        val input = Channel<String>(4)
        input.send("ok")
        assertFalse(awaitCommandReply("M500", input, 1000, 10000) {}.stored)
        input.send("echo:Settings Stored (742 bytes; crc 1234)")
        input.send("ok")
        assertTrue(awaitCommandReply("M500", input, 1000, 10000) {}.stored)
    }
    @Test fun `EEPROM error cannot be mistaken for a successful save`() = runTest {
        val input = Channel<String>(4)
        input.send("echo:Settings Stored")
        input.send("Error:EEPROM write failed")
        input.send("ok")
        val reply = awaitCommandReply("M500", input, 1000, 10000) {}
        assertFalse(reply.stored)
        assertNotNull(reply.rejection)
    }
    @Test fun `a confirmed mesh survives failure of a follow up query`() = runTest {
        val input = Channel<String>(16)
        listOf("Bilinear Leveling Grid:", "0 1", "0 -0.1 0", "1 0.1 0.2", "ok").forEach { input.send(it) }
        val reader = BilinearMeshReader()
        awaitCommandReply("G29", input, 1000, 10000, reader::accept)
        val record = MeshRecord(capturedAt = 10, origin = MeshOrigin.MEASURED, mesh = reader.finish()!!)
        val archive = MeshHistoryCodec.encode(listOf(record))
        assertTrue(runCatching { awaitCommandReply("M114", input, 1000, 10000) {} }.isFailure)
        assertEquals(record, MeshHistoryCodec.decode(archive).single())
    }
    @Test fun `subdivided report does not corrupt the original probe grid`() {
        val reader = BilinearMeshReader()
        listOf("Bilinear Leveling Grid:", "0 1", "0 -0.1 0", "1 0.1 0.2",
            "Subdivided with CATMULL ROM Leveling Grid:", "0 1 2", "0 -0.1 -0.05 0").forEach(reader::accept)
        assertEquals(2, reader.finish()!!.columns)
    }
    @Test fun `repeated coordinates do not inflate the probe point count`() {
        var state = Marlin.parse(PrinterSnapshot(), "Bed X: 30 Y: 30 Z: 0.1")
        state = Marlin.parse(state, "Bed X: 30 Y: 30 Z: 0.12")
        assertEquals(1, state.probePoints.size)
        assertEquals(0.12, state.probePoints.single().z, 0.00001)
    }
    @Test fun `local history round trip retains unknown points and timestamps`() {
        val record = MeshRecord("test", 1234, MeshOrigin.READ, Mesh(mapOf(0 to listOf(0.1, null), 1 to listOf(-0.2, 0.0)), 2), 2345)
        assertEquals(listOf(record), MeshHistoryCodec.decode(MeshHistoryCodec.encode(listOf(record))))
        assertTrue(MeshHistoryCodec.decode("broken archive").isEmpty())
    }
    @Test fun `mesh comparison preserves signs and never fills missing points`() {
        val current = Mesh(mapOf(0 to listOf(0.1, null), 1 to listOf(-0.2, 0.0)), 2)
        val previous = Mesh(mapOf(0 to listOf(0.0, 0.5), 1 to listOf(-0.1, 0.0)), 2)
        val delta = current.difference(previous)!!
        assertEquals(0.1, delta.maxAbsolute, 0.00001)
        assertNull(delta.mesh.rows[0]!![1])
        assertEquals(-0.1, delta.mesh.rows[1]!![0]!!, 0.00001)
        assertNull(current.difference(Mesh(mapOf(0 to listOf(0.0, 0.0, 0.0), 1 to listOf(0.0, 0.0, 0.0)), 3)))
    }
    @Test fun `CSV uses decimal dots and explicit mesh indices in a Spanish locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            val record = MeshRecord(capturedAt = 0, origin = MeshOrigin.READ, mesh = Mesh(mapOf(0 to listOf(0.1, null), 1 to listOf(0.0, -0.2)), 2))
            val csv = MeshHistoryCodec.csv(record)
            assertTrue(csv.contains("x_index,y_index,z_mm\n0,0,0.1\n1,0,\n"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun `saving after probing requires explicit opt in and is ordered after measurement and readback`() {
        assertFalse(Commands.probe(false).commands.contains("M500"))
        val commands = Commands.probe(true).commands
        assertEquals("M500", commands.last())
        assertTrue(commands.indexOf("M420 V") > commands.indexOf("G29"))
    }
}
