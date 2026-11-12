package cz.misa.quakedeck.data

import org.junit.Assert.*
import org.junit.Test

class BackfillPageDispatcherTest {
    @Test fun oneReportPerTurnLetsLiveWorkRunBetweenHistoricalReports() {
        val turns = ArrayDeque<() -> Unit>()
        val applied = mutableListOf<String>()
        val dispatcher = BackfillPageDispatcher<Int>({ turns.addLast(it) }, { true }) {
            applied += "history:$it"
        }
        val done = dispatcher.dispatch(listOf(1, 2, 3))
        turns.addLast { applied += "live" }
        assertEquals(2, turns.size)
        turns.removeFirst().invoke()
        assertEquals(listOf("history:1"), applied)
        assertFalse(done.isDone)
        while (turns.isNotEmpty()) turns.removeFirst().invoke()
        assertEquals(listOf("history:1", "live", "history:2", "history:3"), applied)
        assertTrue(done.isDone)
    }

    @Test fun cancellationStopsQueuedApplicationAndResolvesTheWaitingWorker() {
        val turns = ArrayDeque<() -> Unit>()
        val applied = mutableListOf<Int>()
        var current = true
        val dispatcher = BackfillPageDispatcher<Int>({ turns.addLast(it) }, { current }) { applied += it }
        val done = dispatcher.dispatch(listOf(1, 2, 3))
        turns.removeFirst().invoke()
        current = false
        turns.removeFirst().invoke()
        assertEquals(listOf(1), applied)
        assertTrue(done.isCompletedExceptionally)
        assertTrue(turns.isEmpty())
    }

    @Test fun applicationFailureDoesNotLeaveTheWorkerWaitingForever() {
        val turns = ArrayDeque<() -> Unit>()
        val dispatcher = BackfillPageDispatcher<Int>({ turns.addLast(it) }, { true }) { error("fixture") }
        val done = dispatcher.dispatch(listOf(1))
        turns.removeFirst().invoke()
        assertTrue(done.isCompletedExceptionally)
    }
}
