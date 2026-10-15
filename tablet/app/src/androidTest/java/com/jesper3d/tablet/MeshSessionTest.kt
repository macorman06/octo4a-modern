package com.jesper3d.tablet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jesper3d.protocol.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Drives the production controller with serial transcripts, without opening USB. */
@RunWith(AndroidJUnit4::class)
class MeshSessionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val grid = listOf("Bilinear Leveling Grid:", "0 1", "0 -0.100 +0.000", "1 +0.100 +0.200")

    @Test fun meshIsCommittedBeforeFollowUpTimeoutAndCanBeReloadedFromTabletStorage() = runBlocking {
        val input = Channel<String>(64)
        val sent = mutableListOf<String>()
        val name = "test-${UUID.randomUUID()}"
        val controller = PrinterController(context, sendOverride = { command ->
            sent += command
            if (command == "G29") grid.forEach { input.send(it) }
            if (command != "M420 V") input.send("ok")
        }, preferencesName = name, queryIdleTimeoutMs = 50)
        val error = runCatching { controller.execute(Commands.probe, input) }.exceptionOrNull()
        assertTrue(error is CommandTimeoutException)
        assertTrue(controller.state.value.measurementCompleted)
        assertEquals(MeshSaveState.UNSAVED, controller.state.value.meshSaveState)
        assertEquals(0.3, controller.state.value.liveRecord!!.mesh.range!!, 0.00001)
        assertFalse(sent.contains("M500"))
        val reopened = PrinterController(context, preferencesName = name)
        assertEquals(controller.state.value.liveRecord, reopened.state.value.history.single())
        assertTrue(controller.diagnostics().contains("> G29"))
    }
    @Test fun completedMeasurementAndNextStatusQueryShareTheSameSession() = runBlocking {
        val input = Channel<String>(64)
        val sent = mutableListOf<String>()
        val controller = PrinterController(context, sendOverride = { command ->
            sent += command
            when (command) {
                "G29", "M420 V" -> grid.forEach { input.send(it) }
                "M114" -> input.send("X:20 Y:30 Z:5 E:0")
                "M105" -> input.send("T:22 /0 B:23 /0")
            }
            input.send("ok")
        }, preferencesName = "test-${UUID.randomUUID()}")
        controller.execute(Commands.probe, input)
        assertTrue(controller.state.value.homed)
        assertEquals(1, controller.state.value.history.size)
        controller.execute(Commands.status, input)
        assertEquals(22.0, controller.state.value.snapshot.nozzle!!.current, 0.00001)
        assertNotNull(controller.state.value.liveRecord)
        assertFalse(sent.contains("M500"))
    }
    @Test fun storageIsConfirmedOnlyWhenMarlinExplicitlyReportsIt() = runBlocking {
        val input = Channel<String>(64)
        var confirmStorage = false
        val controller = PrinterController(context, sendOverride = { command ->
            if (command == "M420 V") grid.forEach { input.send(it) }
            if (command == "M500" && confirmStorage) input.send("echo:Settings Stored (742 bytes)")
            input.send("ok")
        }, preferencesName = "test-${UUID.randomUUID()}")
        controller.execute(Commands.mesh, input)
        controller.execute(Commands.save, input)
        assertEquals(MeshSaveState.ACKNOWLEDGED_ONLY, controller.state.value.meshSaveState)
        assertNull(controller.state.value.liveRecord!!.savedAt)
        confirmStorage = true
        controller.execute(Commands.save, input)
        assertEquals(MeshSaveState.CONFIRMED, controller.state.value.meshSaveState)
        assertNotNull(controller.state.value.history.single().savedAt)
    }
}
