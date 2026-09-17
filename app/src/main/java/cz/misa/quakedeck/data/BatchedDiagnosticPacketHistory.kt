package cz.misa.quakedeck.data

import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One cache/coordinator per file, shared by every diagnostics-store instance. */
internal class DiagnosticPacketHistoryRegistry {
    private val histories = mutableMapOf<String, BatchedDiagnosticPacketHistory>()

    @Synchronized
    fun getOrCreate(key: String, create: () -> BatchedDiagnosticPacketHistory): BatchedDiagnosticPacketHistory =
        histories.getOrPut(key, create)
}

internal data class EncodedDiagnosticPacket(val packet: DmDssPacketDiagnostic, val json: String) {
    val bytes: Long = json.toByteArray(Charsets.UTF_8).size.toLong()
}

/** Exact JSON-array size, without serializing the whole history on each append. */
internal class DiagnosticPacketBuffer(private val maxEntries: Int, private val maxBytes: Int) {
    private val entries = ArrayDeque<EncodedDiagnosticPacket>()
    private var entryBytesWithCommas = 0L

    fun append(entry: EncodedDiagnosticPacket) {
        entries.addLast(entry)
        entryBytesWithCommas += entry.bytes + 1
        while (entries.size > maxEntries.coerceAtLeast(0) && serializedBytes() > maxBytes.coerceAtLeast(0)) {
            entryBytesWithCommas -= entries.removeFirst().bytes + 1
        }
    }

    fun snapshot(): List<EncodedDiagnosticPacket> = entries.toList()

    private fun serializedBytes(): Long = 2 + entryBytesWithCommas - if (entries.isEmpty()) 0 else 1
}

/**
 * Disk reads, serialization of snapshots and atomic writes run on one serial executor.
 * Receipt only encodes the new packet and briefly locks the bounded buffer. A write
 * acknowledges its own revision, so appends made during I/O cannot be marked clean.
 */
internal class BatchedDiagnosticPacketHistory(
    private val executor: ScheduledExecutorService,
    read: () -> List<DmDssPacketDiagnostic>,
    private val write: (String) -> Unit,
    private val onWriteFailure: (Throwable) -> Unit,
    private val maxEntries: Int = 200,
    private val maxBytes: Int = 2 * 1024 * 1024,
    private val debounceMillis: Long = 750L
) {
    private val lock = Any()
    private var buffer = DiagnosticPacketBuffer(maxEntries, maxBytes)
    private var revision = 0L
    private var persistedRevision = 0L
    private var scheduledWrite: ScheduledFuture<*>? = null

    // Queued before any writes. New receipts can append while the initial read is blocked.
    private val loaded = executor.submit {
        val restored = read().map { it.encodeDiagnosticPacket() }
        synchronized(lock) {
            val merged = DiagnosticPacketBuffer(maxEntries, maxBytes)
            restored.forEach(merged::append)
            buffer.snapshot().forEach(merged::append)
            buffer = merged
        }
    }

    fun append(packet: DmDssPacketDiagnostic) {
        val encoded = packet.encodeDiagnosticPacket()
        synchronized(lock) {
            buffer.append(encoded)
            revision++
            scheduleLocked(debounceMillis)
        }
    }

    fun snapshot(): List<DmDssPacketDiagnostic> {
        // Export/UI reads include restored history; receive callbacks never wait here.
        loaded.get()
        return synchronized(lock) { buffer.snapshot().map { it.packet } }
    }

    /** Expedite an orderly provider stop without blocking its caller on disk I/O. */
    fun flushAsync(): Future<*> = synchronized(lock) {
        scheduledWrite?.cancel(false)
        scheduledWrite = null
        executor.submit { persist() }
    }

    private fun scheduleLocked(delayMillis: Long) {
        if (scheduledWrite != null) return
        scheduledWrite = executor.schedule({
            synchronized(lock) { scheduledWrite = null }
            persist()
        }, delayMillis, TimeUnit.MILLISECONDS)
    }

    private fun persist() {
        val (writingRevision, entries) = synchronized(lock) {
            if (revision == persistedRevision) return
            revision to buffer.snapshot()
        }
        val result = runCatching { write(entries.joinToString(",", "[", "]") { it.json }) }
        synchronized(lock) {
            if (result.isSuccess) persistedRevision = writingRevision
            if (revision != persistedRevision) {
                scheduleLocked(if (result.isSuccess) debounceMillis else 5_000L)
            }
        }
        result.exceptionOrNull()?.let(onWriteFailure)
    }
}
