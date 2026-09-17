package cz.misa.quakedeck.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NtpRequestLimiterTest {
    @Test
    fun repeatedFailedSyncsStayWithinTwentyRequestsInEveryRollingHour() {
        var now = 0L
        val limiter = NtpRequestLimiter { now }
        val sent = mutableListOf<Long>()

        // The failure path retries three samples every five minutes, without any success.
        repeat(48) { cycle ->
            now = cycle * 5 * 60_000L
            repeat(3) { sample ->
                now = cycle * 5 * 60_000L + sample * 120L
                if (limiter.tryAcquire()) sent += now
                assertTrue(sent.count { now - it < 3_600_000L } <= 20)
            }
        }

        assertEquals(80, sent.size)
    }

    @Test
    fun allowanceReturnsOnlyAsIndividualRequestsLeaveTheWindow() {
        var now = 0L
        val limiter = NtpRequestLimiter { now }
        repeat(10) { assertTrue(limiter.tryAcquire()) }
        now = 30 * 60_000L
        repeat(10) { assertTrue(limiter.tryAcquire()) }

        now = 3_599_999L
        assertFalse(limiter.tryAcquire())
        now = 3_600_000L
        repeat(10) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())

        now = 90 * 60_000L
        repeat(10) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun rejectedAttemptsDoNotExtendTheCooldown() {
        var now = 0L
        val limiter = NtpRequestLimiter { now }
        repeat(20) { assertTrue(limiter.tryAcquire()) }
        now = 3_599_999L
        repeat(100) { assertFalse(limiter.tryAcquire()) }
        now = 3_600_000L
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun concurrentCallersShareOneAllowance() {
        val limiter = NtpRequestLimiter { 0L }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = executor.invokeAll(
                List(100) { Callable { limiter.tryAcquire() } },
                5,
                TimeUnit.SECONDS
            )
            assertEquals(20, results.count { it.get() })
        } finally {
            executor.shutdownNow()
        }
    }
}
