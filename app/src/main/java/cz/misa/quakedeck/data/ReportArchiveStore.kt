package cz.misa.quakedeck.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class ArchiveWriteResult(
    val added: Int,
    val duplicates: Int
)

internal data class ArchivedEarthquakeReport(
    val archiveKey: String,
    val eventKey: String,
    val sourceTime: String?,
    val receivedAt: Long,
    val rawJson: JSONObject
)

internal data class ArchivedReportRecord(
    val archiveKey: String,
    val code: Int,
    val sourceTime: String?,
    val receivedAt: Long,
    val issueType: String?,
    val eventKey: String?,
    val rawJson: JSONObject
)

/**
 * Persistent raw P2PQuake report archive.
 *
 * Complete JSON payloads are stored without collapsing them into summaries. The live incident model can
 * evolve independently, while a future replay can always rebuild exactly what
 * was known after each original report arrived.
 */
internal class ReportArchiveStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val databaseFile = context.applicationContext.getDatabasePath(DATABASE_NAME)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reports (
                archive_key TEXT PRIMARY KEY,
                semantic_key TEXT NOT NULL UNIQUE,
                upstream_id TEXT,
                code INTEGER NOT NULL,
                source_time TEXT,
                received_at INTEGER NOT NULL,
                issue_type TEXT,
                event_key TEXT,
                source TEXT NOT NULL,
                payload_bytes INTEGER NOT NULL,
                raw_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX reports_event_idx ON reports(event_key, source_time, received_at)")
        db.execSQL("CREATE INDEX reports_code_time_idx ON reports(code, source_time, received_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first archive schema. Keep migrations explicit so
        // replay history is repaired rather than silently discarded.
        if (oldVersion < 1) {
            onCreate(db)
            return
        }
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE reports ADD COLUMN semantic_key TEXT")

            // The live WebSocket and /history occasionally expose the same JMA
            // bulletin with different wrapper IDs or reception timestamps. Build
            // a stable key from the substantive payload, then collapse any pairs
            // already written by v0.9.41-v0.9.44 before enforcing uniqueness.
            db.query(
                "reports",
                arrayOf("archive_key", "code", "raw_json"),
                null,
                null,
                null,
                null,
                "received_at ASC, archive_key ASC"
            ).use { cursor ->
                val update = db.compileStatement(
                    "UPDATE reports SET semantic_key = ? WHERE archive_key = ?"
                )
                while (cursor.moveToNext()) {
                    val archiveKey = cursor.getString(0)
                    val code = cursor.getInt(1)
                    val raw = cursor.getString(2)
                    val semanticKey = runCatching {
                        buildSemanticKey(code, JSONObject(raw))
                    }.getOrElse {
                        "$code:legacy:$archiveKey"
                    }
                    update.clearBindings()
                    update.bindString(1, semanticKey)
                    update.bindString(2, archiveKey)
                    update.executeUpdateDelete()
                }
            }
            db.execSQL(
                """
                DELETE FROM reports
                WHERE rowid NOT IN (
                    SELECT MIN(rowid)
                    FROM reports
                    GROUP BY semantic_key
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX reports_semantic_unique_idx ON reports(semantic_key)"
            )
        }
    }

    fun storeReports(reports: List<JSONObject>, source: String): ArchiveWriteResult {
        if (reports.isEmpty()) return ArchiveWriteResult(0, 0)
        val db = writableDatabase
        var added = 0
        var duplicates = 0
        db.transaction {
            reports.forEach { json ->
                val raw = json.toString()
                val upstreamId = json.optString("id").trim().takeIf { it.isNotEmpty() }
                val code = json.optInt("code", -1)
                val archiveKey = buildArchiveKey(code, upstreamId, raw)
                val semanticKey = buildSemanticKey(code, json)
                val earthquake = json.optJSONObject("earthquake")
                val issue = json.optJSONObject("issue")
                val sourceTime = json.optString("time").ifBlank {
                    issue?.optString("time").orEmpty()
                }
                val eventKey = when {
                    earthquake?.optString("time").orEmpty().isNotBlank() ->
                        "quake:${earthquake?.optString("time")}"
                    issue?.optString("eventId").orEmpty().isNotBlank() ->
                        "event:${issue?.optString("eventId")}"
                    else -> null
                }
                val values = ContentValues().apply {
                    put("archive_key", archiveKey)
                    put("semantic_key", semanticKey)
                    put("upstream_id", upstreamId)
                    put("code", code)
                    put("source_time", sourceTime)
                    put("received_at", System.currentTimeMillis())
                    put("issue_type", issue?.optString("type"))
                    put("event_key", eventKey)
                    put("source", source)
                    put("payload_bytes", raw.toByteArray(StandardCharsets.UTF_8).size)
                    put("raw_json", raw)
                }
                val row = db.insertWithOnConflict(
                    "reports",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (row == -1L) duplicates++ else added++
            }
        }
        return ArchiveWriteResult(added, duplicates)
    }

    fun stats(
        enabled: Boolean,
        automaticHistoricalDownload: Boolean
    ): ReportArchiveStatus {
        val db = readableDatabase
        var reportCount = 0L
        var incidentCount = 0L
        var payloadBytes = 0L
        db.rawQuery(
            """
            SELECT COUNT(*),
                   COUNT(DISTINCT CASE
                       WHEN code = 551 AND event_key IS NOT NULL THEN event_key
                   END),
                   COALESCE(SUM(payload_bytes), 0)
            FROM reports
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                reportCount = cursor.getLong(0)
                incidentCount = cursor.getLong(1)
                payloadBytes = cursor.getLong(2)
            }
        }
        return ReportArchiveStatus(
            enabled = enabled,
            automaticHistoricalDownload = automaticHistoricalDownload,
            reportCount = reportCount,
            incidentCount = incidentCount,
            payloadBytes = payloadBytes,
            databaseBytes = archiveDiskBytes()
        )
    }

    /** SQLite may keep recent pages in sidecar files; report their real footprint too. */
    private fun archiveDiskBytes(): Long {
        val parent = databaseFile.parentFile ?: return databaseFile.takeIf { it.exists() }?.length() ?: 0L
        val baseName = databaseFile.name
        return listOf(baseName, "$baseName-wal", "$baseName-shm", "$baseName-journal")
            .sumOf { name -> parent.resolve(name).takeIf { it.exists() }?.length() ?: 0L }
    }

    /** Returns recent raw earthquake reports; the provider restores original source-time order. */
    fun loadRecentEarthquakeReports(limit: Int = 2_000): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        readableDatabase.rawQuery(
            """
            SELECT raw_json FROM (
                SELECT raw_json, source_time, received_at
                FROM reports
                WHERE code = 551
                ORDER BY source_time DESC, received_at DESC
                LIMIT ?
            )
            ORDER BY source_time ASC, received_at ASC
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { JSONObject(cursor.getString(0)) }.getOrNull()?.let(result::add)
            }
        }
        return result
    }

    fun loadEarthquakeReports(): List<ArchivedEarthquakeReport> =
        loadEarthquakeReportsForEvent(null)

    fun loadEarthquakeReportsForEvent(eventKey: String?): List<ArchivedEarthquakeReport> {
        val result = mutableListOf<ArchivedEarthquakeReport>()
        val selection = if (eventKey == null) {
            "code = 551 AND event_key IS NOT NULL"
        } else {
            "code = 551 AND event_key = ?"
        }
        val args = eventKey?.let { arrayOf(it) }
        readableDatabase.query(
            "reports",
            arrayOf(
                "archive_key",
                "event_key",
                "source_time",
                "received_at",
                "raw_json"
            ),
            selection,
            args,
            null,
            null,
            "source_time ASC, received_at ASC, archive_key ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val raw = runCatching { JSONObject(cursor.getString(4)) }.getOrNull() ?: continue
                result += ArchivedEarthquakeReport(
                    archiveKey = cursor.getString(0),
                    eventKey = cursor.getString(1),
                    sourceTime = cursor.getString(2),
                    receivedAt = cursor.getLong(3),
                    rawJson = raw
                )
            }
        }
        return result
    }


    fun loadAssociatedReportCandidates(): List<ArchivedReportRecord> {
        val result = mutableListOf<ArchivedReportRecord>()
        readableDatabase.query(
            "reports",
            arrayOf(
                "archive_key",
                "code",
                "source_time",
                "received_at",
                "issue_type",
                "event_key",
                "raw_json"
            ),
            "code IN (552, 554, 556)",
            null,
            null,
            null,
            "source_time ASC, received_at ASC, archive_key ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val raw = runCatching { JSONObject(cursor.getString(6)) }.getOrNull() ?: continue
                result += ArchivedReportRecord(
                    archiveKey = cursor.getString(0),
                    code = cursor.getInt(1),
                    sourceTime = cursor.getString(2),
                    receivedAt = cursor.getLong(3),
                    issueType = cursor.getString(4),
                    eventKey = cursor.getString(5),
                    rawJson = raw
                )
            }
        }
        return result
    }

    fun clear() {
        writableDatabase.delete("reports", null, null)
        writableDatabase.execSQL("VACUUM")
    }

    private fun buildArchiveKey(code: Int, upstreamId: String?, raw: String): String {
        if (!upstreamId.isNullOrBlank()) return "$code:$upstreamId"
        return "$code:sha256:${sha256(raw)}"
    }

    /**
     * Identity of the actual bulletin, independent of P2PQuake's transport
     * wrapper. The live WebSocket and /history can expose the same JMA report
     * with different top-level IDs, reception times, or omitted null fields.
     *
     * For earthquake reports, JMA issue time + report type + correction scope +
     * earthquake origin time form the stable bulletin identity. Real follow-up
     * and correction reports receive a different issue time/type/correction key,
     * while the live/backfill copies of one bulletin collide atomically.
     */
    private fun buildSemanticKey(code: Int, json: JSONObject): String {
        if (code == 551) {
            val issue = json.optJSONObject("issue")
            val earthquake = json.optJSONObject("earthquake")
            val issueTime = issue?.optString("time").orEmpty().trim()
            val issueType = issue?.optString("type").orEmpty().trim()
            val correction = issue?.optString("correct").orEmpty().trim()
            val eventId = issue?.optString("eventId").orEmpty().trim()
            val originTime = earthquake?.optString("time").orEmpty().trim()

            if (issueTime.isNotEmpty() && issueType.isNotEmpty()) {
                val identity = listOf(
                    issueTime,
                    issueType,
                    correction.ifEmpty { "None" },
                    eventId,
                    originTime
                ).joinToString("|")
                return "$code:jma:${sha256(identity)}"
            }
        }

        val canonical = canonicalJson(json, topLevel = true)
        return "$code:semantic:${sha256(canonical)}"
    }

    private fun canonicalJson(value: Any?, topLevel: Boolean = false): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence()
            .filterNot { topLevel && it in TRANSPORT_ONLY_FIELDS }
            .sorted()
            .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                JSONObject.quote(key) + ":" + canonicalJson(value.opt(key))
            }
        is JSONArray -> (0 until value.length())
            .joinToString(prefix = "[", postfix = "]", separator = ",") { index ->
                canonicalJson(value.opt(index))
            }
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DATABASE_NAME = "quakedeck_report_archive.db"
        const val DATABASE_VERSION = 2
        val TRANSPORT_ONLY_FIELDS = setOf("id", "time")
    }
}
