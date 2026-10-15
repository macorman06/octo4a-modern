package com.jesper3d.protocol

import java.util.UUID
import kotlin.math.abs

enum class MeshOrigin { MEASURED, READ }
enum class MeshSaveState { UNKNOWN, UNSAVED, CONFIRMED, ACKNOWLEDGED_ONLY }
data class MeshRecord(
    val id: String = UUID.randomUUID().toString(),
    val capturedAt: Long,
    val origin: MeshOrigin,
    val mesh: Mesh,
    val savedAt: Long? = null,
)
data class MeshDelta(val mesh: Mesh, val maxAbsolute: Double, val mean: Double)

fun Mesh.difference(reference: Mesh): MeshDelta? {
    if (columns != reference.columns || rows.keys != reference.rows.keys) return null
    val delta = Mesh(rows.mapValues { (y, row) -> row.mapIndexed { x, z ->
        val previous = reference.rows.getValue(y)[x]
        if (z == null || previous == null) null else z - previous
    } }, columns)
    if (delta.values.isEmpty()) return null
    return MeshDelta(delta, delta.values.maxOf { abs(it) }, delta.values.average())
}

/** Local archive, not printer storage. No physical XY coordinates are inferred. */
object MeshHistoryCodec {
    fun encode(records: List<MeshRecord>): String = records.take(12).joinToString("\n") { record ->
        listOf(record.id, record.capturedAt.toString(), record.origin.name, record.savedAt?.toString() ?: "-",
            record.mesh.columns.toString(), record.mesh.rows.size.toString(),
            record.mesh.rows.toSortedMap().values.flatten().joinToString(",") { it?.toString() ?: "-" }).joinToString(";")
    }
    fun decode(text: String): List<MeshRecord> = text.lineSequence().take(12).mapNotNull { line ->
        runCatching {
            val fields = line.split(';')
            require(fields.size == 7)
            val columns = fields[4].toInt(); val rowCount = fields[5].toInt()
            require(columns in 2..64 && rowCount in 2..64)
            val values = fields[6].split(',').map { if (it == "-") null else it.toDouble().also { n -> require(n.isFinite()) } }
            require(values.size == columns * rowCount)
            MeshRecord(fields[0], fields[1].toLong(), MeshOrigin.valueOf(fields[2]),
                Mesh(values.chunked(columns).mapIndexed { i, row -> i to row }.toMap(), columns), fields[3].toLongOrNull())
        }.getOrNull()
    }.toList()
    fun csv(record: MeshRecord): String = buildString {
        append("# Jesper 3D; captured_at_ms=${record.capturedAt}; local_copy=true\n")
        append("x_index,y_index,z_mm\n")
        record.mesh.rows.toSortedMap().forEach { (y, row) -> row.forEachIndexed { x, z ->
            append("$x,$y,${z?.toString() ?: ""}\n")
        } }
    }
}
