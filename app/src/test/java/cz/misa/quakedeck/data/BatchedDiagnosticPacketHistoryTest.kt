package cz.misa.quakedeck.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BatchedDiagnosticPacketHistoryTest {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    @After
    fun stopWriter() {
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun burstStaysInMemoryAndDebouncesToOneWrite() {
        val reads = AtomicInteger()
        val writes = AtomicInteger()
        val persisted = CountDownLatch(1)
        var disk = ""
        val history = history(read = { reads.incrementAndGet(); emptyList() }, write = {
            disk = it
            writes.incrementAndGet()
            persisted.countDown()
        }, debounceMillis = 500)
        repeat(500) { history.append(packet(it)) }
        assertEquals(500, history.snapshot().size)
        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        history.flushAsync().get(5, TimeUnit.SECONDS)
        assertEquals(1, reads.get())
        assertEquals(1, writes.get())
        assertEquals(500, JSONArray(disk).length())
        assertEquals(history.snapshot(), decode(disk))
    }

    @Test
    fun officialPacketPassesFeltFloodWhileDiskWriteIsBlocked() {
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = mutableListOf<String>()
        val history = history(write = {
            writing.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            writes.add(it)
        })
        history.append(packet(-1))
        val firstFlush = history.flushAsync()
        assertTrue(writing.await(5, TimeUnit.SECONDS))
        val dispatched = mutableListOf<Int>()
        val receipt = Executors.newSingleThreadExecutor()
        try {
            // This is the same ingress function invoked by WebSocket.onMessage.
            receipt.submit {
                val record: (String) -> Unit = { raw ->
                    history.append(packet(JSONObject(raw).optInt("seq"), raw))
                }
                repeat(600) { index ->
                    receiveP2pLivePacket("""{"code":561,"seq":$index}""", record) {
                        dispatched.add(it.getInt("code"))
                    }
                }
                receiveP2pLivePacket("""{"code":551,"seq":600}""", record) {
                    dispatched.add(it.getInt("code"))
                }
            }.get(3, TimeUnit.SECONDS)
            assertEquals(listOf(551), dispatched)
            assertEquals(602, history.snapshot().size)
        } finally {
            release.countDown()
            receipt.shutdownNow()
        }
        firstFlush.get(5, TimeUnit.SECONDS)
        history.flushAsync().get(5, TimeUnit.SECONDS)
        val exported = JSONObject(DmDssDiagnosticsSnapshot(packetHistory = decode(writes.last()))
            .toMachineReadableJson()).getJSONArray("packets")
        assertEquals(602, exported.length())
        assertEquals(600, (0 until exported.length()).count {
            JSONObject(exported.getJSONObject(it).getString("payload")).optInt("code") == 561
        })
    }

    @Test
    fun appendDuringWriteIsPersistedByFollowingScheduledWrite() {
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val savedNewer = CountDownLatch(1)
        val writes = mutableListOf<String>()
        val history = history(write = {
            if (writes.isEmpty()) {
                writing.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            writes.add(it)
            if (writes.size == 2) savedNewer.countDown()
        }, debounceMillis = 50)
        history.append(packet(1))
        assertTrue(writing.await(5, TimeUnit.SECONDS))
        try {
            history.append(packet(2))
            assertEquals(listOf(packet(1), packet(2)), history.snapshot())
        } finally {
            release.countDown()
        }
        assertTrue(savedNewer.await(5, TimeUnit.SECONDS))
        history.flushAsync().get(5, TimeUnit.SECONDS)
        assertEquals(listOf(packet(1), packet(2)), decode(writes.last()))
    }

    @Test
    fun sharedRegistryLoadsOnceAndMergesPacketsReceivedDuringInitialRead() {
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val registry = DiagnosticPacketHistoryRegistry()
        var disk = ""
        val first = registry.getOrCreate("shared-file") {
            history(read = {
                reading.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                listOf(packet(0))
            }, write = { disk = it })
        }
        assertTrue(reading.await(5, TimeUnit.SECONDS))
        val second = registry.getOrCreate("shared-file") { error("Must reuse the shared coordinator") }
        assertSame(first, second)
        try {
            first.append(packet(1))
            second.append(packet(2).copy(source = DIAGNOSTIC_SOURCE_DMDSS))
        } finally {
            release.countDown()
        }
        second.flushAsync().get(5, TimeUnit.SECONDS)
        assertEquals(listOf(0L, 1L, 2L), decode(disk).map { it.recordedAtMillis })
        assertEquals(listOf(DIAGNOSTIC_SOURCE_P2PQUAKE, DIAGNOSTIC_SOURCE_P2PQUAKE,
            DIAGNOSTIC_SOURCE_DMDSS), decode(disk).map { it.source })
        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test
    fun failedWriteRemainsDirtyAndCanBeFlushedAgain() {
        val failures = AtomicInteger()
        var attempts = 0
        var disk = ""
        val history = history(write = {
            if (attempts++ == 0) throw IOException("Disk unavailable")
            disk = it
        }, onFailure = { failures.incrementAndGet() })
        history.append(packet(1))
        history.flushAsync().get(5, TimeUnit.SECONDS)
        history.append(packet(2))
        history.flushAsync().get(5, TimeUnit.SECONDS)
        assertEquals(1, failures.get())
        assertEquals(listOf(packet(1), packet(2)), decode(disk))
    }

    @Test
    fun exactUtf8AndEscapedJsonBoundaryRequiresBothLimitsToBeExceeded() {
        val entries = (1..3).map { packet(it, "熊本\n\"\\".repeat(100)) }
        val serialized = entries.joinToString(",", "[", "]") { it.encodeDiagnosticPacket().json }
        val exactBytes = serialized.toByteArray(Charsets.UTF_8).size
        assertEquals(entries, trimDmDssPacketHistory(entries, 2, exactBytes))
        assertEquals(entries, trimDmDssPacketHistory(entries, 3, exactBytes - 1))
        assertEquals(entries.drop(1), trimDmDssPacketHistory(entries, 2, exactBytes - 1))
        assertEquals(emptyList<DmDssPacketDiagnostic>(), trimDmDssPacketHistory(entries, 0, 0))
    }

    @Test
    fun productionRetentionKeepsEitherSingleLimitAndTrimsWhenBothAreExceeded() {
        val small = (1..300).map { packet(it) }
        assertEquals(small, trimDmDssPacketHistory(small, 200, 2 * 1024 * 1024))
        val large = (1..200).map { packet(it, "x".repeat(12_000)) }
        assertEquals(large, trimDmDssPacketHistory(large, 200, 2 * 1024 * 1024))
        val extra = large + packet(201, "x".repeat(12_000))
        assertEquals(extra.drop(1), trimDmDssPacketHistory(extra, 200, 2 * 1024 * 1024))
    }

    @Test
    fun operationalAndCumulativePacketsKeepTheirOriginalDispatchOrder() {
        val recorded = mutableListOf<String>()
        val dispatched = mutableListOf<Int>()
        val codes = listOf(551, 552, 554, 556, 9611, 555)
        (listOf(561) + codes).forEach { code ->
            receiveP2pLivePacket("""{"code":$code}""", { recorded.add(it) }) {
                dispatched.add(it.getInt("code"))
            }
        }
        receiveP2pLivePacket("invalid", { recorded.add(it) }) { fail("Invalid JSON dispatched") }
        assertEquals(codes, dispatched)
        assertEquals(7, recorded.size) // Includes 561 and malformed evidence; excludes 555.
    }

    private fun history(
        read: () -> List<DmDssPacketDiagnostic> = { emptyList() },
        write: (String) -> Unit = {},
        onFailure: (Throwable) -> Unit = { throw AssertionError(it) },
        debounceMillis: Long = 60_000
    ) = BatchedDiagnosticPacketHistory(executor, read, write, onFailure, debounceMillis = debounceMillis)

    private fun packet(index: Int, payload: String = """{"seq":$index}""") =
        DmDssPacketDiagnostic(index.toLong(), "IN", "text", "json", payload, DIAGNOSTIC_SOURCE_P2PQUAKE)

    private fun decode(text: String): List<DmDssPacketDiagnostic> {
        val array = JSONArray(text)
        return (0 until array.length()).map {
            val entry = array.getJSONObject(it)
            DmDssPacketDiagnostic(entry.getLong("recordedAtMillis"), entry.getString("direction"),
                entry.getString("transport"), entry.getString("type"), entry.getString("payload"),
                entry.getString("source"))
        }
    }
}
