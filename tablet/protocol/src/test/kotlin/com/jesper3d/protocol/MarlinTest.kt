package com.jesper3d.protocol

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MarlinTest {
    @Test fun `USB packets can split lines and contain several replies`() {
        val framer = LineFramer()
        assertEquals(emptyList<String>(), framer.accept("ok T:19".toByteArray()))
        assertEquals(listOf("ok T:195.2 /200 B:48 /50", "busy: processing"),
            framer.accept("5.2 /200 B:48 /50\r\nbusy: processing\npar".toByteArray()))
        assertEquals(listOf("partial"), framer.accept("tial\n".toByteArray()))
    }
    @Test(expected = IllegalStateException::class) fun `corrupt unbounded serial response is rejected`() {
        LineFramer().accept("a".repeat(16385).toByteArray())
    }
    @Test fun `temperature on an ok line updates both sensors`() {
        val state = Marlin.parse(PrinterSnapshot(), "ok T:199.6 /200.0 B:49.8 /50.0 @:10 B@:0")
        assertEquals(Temperature(199.6, 200.0), state.nozzle)
        assertEquals(Temperature(49.8, 50.0), state.bed)
        assertEquals(Temperature(20.0, null), Marlin.parse(state, "T0:20").nozzle)
    }
    @Test fun `motor counts in M114 do not overwrite millimeter positions`() {
        val parsed = Marlin.parse(PrinterSnapshot(), "X:10.00 Y:20.00 Z:3.50 E:0.00 Count X:800 Y:1600 Z:1400")
        assertEquals(Position(10.0, 20.0, 3.5), parsed.position)
    }
    @Test fun `capabilities remain unknown until explicitly reported`() {
        var state = Marlin.parse(PrinterSnapshot(), "FIRMWARE_NAME:Marlin TH3D UFW 2.85")
        assertTrue(state.isMarlin)
        assertNull(state.capabilities["Z_PROBE"])
        state = Marlin.parse(state, "Cap:Z_PROBE:1")
        state = Marlin.parse(state, "Cap:EEPROM:0")
        assertEquals(true, state.capabilities["Z_PROBE"])
        assertEquals(false, state.capabilities["EEPROM"])
    }
    @Test fun `settings are collected only during an explicit settings report`() {
        assertTrue(Marlin.parse(PrinterSnapshot(), "echo: M92 X80 Y80 Z400 E93").settings.isEmpty())
        var state = Marlin.parse(PrinterSnapshot(), "echo: M92 X80 Y80 Z400 E93", true)
        state = Marlin.parse(state, "echo: M851 X-40 Y-5 Z-2.15", true)
        assertEquals(listOf("M92", "M851"), state.settings.map { it.command })
        assertEquals("Desplazamiento del sensor", state.settings.last().label)
    }
    @Test fun `bed leveling reports and M503 state are both understood`() {
        val state = Marlin.parse(PrinterSnapshot(), "echo:Bed Leveling OFF")
        assertEquals(false, state.leveling)
        assertEquals(true, Marlin.parse(state, "echo: M420 S1 Z10.00", true).leveling)
    }
    @Test fun `probe samples retain reported physical coordinates`() {
        val state = Marlin.parse(PrinterSnapshot(), "Bed X: 30.000 Y: 35.000 Z: -0.125")
        assertEquals(listOf(ProbePoint(30.0, 35.0, -0.125)), state.probePoints)
    }
    @Test fun `busy and echoed ok never acknowledge a command`() {
        assertTrue(Marlin.isOk("ok"))
        assertTrue(Marlin.isOk("ok T:20 /0"))
        assertFalse(Marlin.isOk("echo:ok"))
        assertFalse(Marlin.isOk("busy: processing"))
        assertFalse(Marlin.isOk("okay"))
    }
    @Test fun `firmware failures and resends are distinguishable from acknowledgments`() {
        assertTrue(Marlin.isFatal("!! Printer halted. kill() called!"))
        assertTrue(Marlin.isError("Resend: 14"))
        assertTrue(Marlin.isError("echo:Unknown command: M420"))
        assertFalse(Marlin.isOk("Error:Heating failed"))
    }
    private fun reader(vararg rows: String) = BilinearMeshReader().apply {
        accept("Bilinear Leveling Grid:"); accept("0 1 2"); rows.forEach(::accept)
    }
    @Test fun `bilinear mesh preserves indices and signed heights`() {
        val mesh = reader("0 -0.100 +0.000 +0.100", "1 -0.050 +0.025 +0.125", "2 +0.000 +0.050 +0.150").finish()!!
        assertEquals(3, mesh.columns)
        assertEquals(-0.1, mesh.rows[0]!![0]!!, 1e-9)
        assertEquals(0.25, mesh.range!!, 1e-9)
    }
    @Test fun `unprobed points are never filled with fabricated zeros`() {
        val mesh = reader("0 -0.100 ***** +0.100", "1 NaN +0.025 +0.125").finish()!!
        assertNull(mesh.rows[0]!![1])
        assertNull(mesh.rows[1]!![0])
        assertEquals(4, mesh.values.size)
    }
    @Test fun `partial corrupt duplicate and UBL mesh reports are rejected`() {
        assertNull(reader("0 -0.100 0.000 0.100").finish())
        assertNull(reader("0 -0.100 BAD 0.100", "1 -0.1 0 0.1").finish())
        assertNull(reader("0 -0.100 0.000 0.100", "0 -0.1 0 0.1").finish())
        assertNull(reader("0 -0.100 0.000 0.100", "2 -0.1 0 0.1").finish())
        val ubl = BilinearMeshReader()
        listOf("Bed Topography Report:", "0 1 2", "0 -0.1 0 0.1", "1 -0.1 0 0.1").forEach(ubl::accept)
        assertNull(ubl.finish())
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown homing state prevents movement`() {
        Commands.jog("Z", 1.0, Position(0.0, 0.0, 1.0), false, MachineProfile())
    }
    @Test(expected = IllegalArgumentException::class) fun `jog cannot cross a confirmed axis limit`() {
        Commands.jog("X", 1.0, Position(400.0, 100.0, 10.0), true, MachineProfile())
    }
    @Test(expected = IllegalArgumentException::class) fun `jog cannot go below the bed`() {
        Commands.jog("Z", -1.0, Position(10.0, 10.0, 0.0), true, MachineProfile())
    }
    @Test(expected = IllegalArgumentException::class) fun `non finite dimensions are rejected`() { MachineProfile(Double.NaN) }
    @Test(expected = IllegalArgumentException::class) fun `non finite jog is rejected`() {
        Commands.jog("X", Double.NaN, Position(10.0, 10.0, 10.0), true, MachineProfile())
    }
    @Test fun `Spanish locale cannot introduce a decimal comma into Gcode`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            val commands = Commands.jog("Z", 0.1, Position(10.0, 10.0, 1.0), true, MachineProfile()).commands
            assertTrue(commands.contains("G1 Z1.100 F120"))
            assertEquals("M400", commands[commands.lastIndex - 1])
            assertEquals("M114", commands.last())
        } finally { Locale.setDefault(previous) }
    }
    @Test(expected = IllegalArgumentException::class) fun `unbounded heater targets are rejected`() { Commands.heat(999, 50) }
    @Test fun `reading a mesh never probes or writes EEPROM`() {
        assertEquals(listOf("M420 V"), Commands.mesh.commands)
        assertFalse(Commands.inspect.commands.contains("G29"))
        assertFalse(Commands.inspect.commands.contains("M500"))
        assertFalse(Commands.probe.commands.contains("M500"))
    }
}
