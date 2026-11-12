package cz.misa.quakedeck.data

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class OAuthSessionStateTest {
    @Test fun lateSuccessAndFailureCannotResurrectDisconnectedOrDeleteReplacementSession() {
        var stored: String? = "old"
        val session = OAuthSessionState({ stored }, { stored = it }, { stored = null })
        val old = session.snapshot().first
        session.invalidate()
        assertFalse(session.save(old, "late"))
        assertNull(stored)
        val replacement = session.advance()
        assertTrue(session.save(replacement, "new"))
        assertFalse(session.invalidate(old))
        assertEquals("new", stored)
    }

    @Test fun statusReadsUseTheLoadedSessionAndTrackAllMutations() {
        var reads = 0
        val session = OAuthSessionState({ reads++; "saved" }, {}, {})
        repeat(100) { assertEquals("saved", session.snapshot().second) }
        assertEquals(1, reads)
        session.invalidate()
        repeat(100) { assertNull(session.snapshot().second) }
        assertEquals(1, reads)
    }

    @Test fun persistenceAndDisconnectAreOrderedByTheSameOwner() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var stored: String? = "old"
        val session = OAuthSessionState({ stored }, {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            stored = it
        }, { stored = null })
        val generation = session.snapshot().first
        val workers = Executors.newFixedThreadPool(2)
        try {
            val save = workers.submit<Boolean> { session.save(generation, "refreshed") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val disconnect = workers.submit<Boolean> { session.invalidate() }
            release.countDown()
            assertTrue(save.get(5, TimeUnit.SECONDS))
            assertTrue(disconnect.get(5, TimeUnit.SECONDS))
            assertNull(stored)
            assertFalse(session.save(generation, "late"))
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

    @Test fun onlyDefinitiveInvalidGrantDeletesAuthorization() {
        for (status in listOf(200, 429, 500, 503)) {
            assertFalse(isDefinitiveOAuthGrantFailure(status, "invalid_grant"))
        }
        for (error in listOf("", "temporarily_unavailable", "invalid_client", "invalid_scope")) {
            assertFalse(isDefinitiveOAuthGrantFailure(400, error))
        }
        assertTrue(isDefinitiveOAuthGrantFailure(400, "invalid_grant"))
        assertTrue(isDefinitiveOAuthGrantFailure(401, "invalid_grant"))
    }

    @Test fun forgedAndExpiredCallbacksDoNotQualifyForTransactionConsumption() {
        fun valid(redirect: String = "expected", state: String? = "right", age: Long = 10L) =
            validOAuthCallback(redirect, "expected", state, "right", age, 600_000L)
        assertTrue(valid())
        assertFalse(valid(redirect = "foreign"))
        assertFalse(valid(state = "wrong"))
        assertFalse(valid(state = null))
        assertFalse(valid(age = -1))
        assertFalse(valid(age = 600_001))
    }
}
