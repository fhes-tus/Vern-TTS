package com.veritas.reader

import com.veritas.reader.tts.LookAheadProducer
import com.veritas.reader.tts.SynthesisPriorityGate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SynthesisSchedulingTest {
    @Test fun reservedDemandRunsBeforeAheadWork() = runTest {
        val gate = SynthesisPriorityGate()
        val ticket = gate.reserveDemand()
        val calls = mutableListOf<String>()
        launch { gate.ahead({ true }) { calls.add("ahead") } }
        launch { gate.demand(ticket) { calls.add("demand") } }
        runCurrent()
        assertEquals(listOf("demand", "ahead"), calls)
        assertEquals(0, gate.pendingDemands)
    }

    @Test fun anAlreadyRunningCallFinishesBeforeDemandAndOnlyOneCallRuns() = runTest {
        val gate = SynthesisPriorityGate()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        var active = 0
        var maximum = 0
        suspend fun record(name: String, wait: Boolean = false) {
            active++; maximum = maxOf(maximum, active); calls.add(name)
            try { if (wait) release.await() } finally { active-- }
        }
        val first = launch { gate.ahead({ true }) { record("running", true) } }
        runCurrent()
        val ticket = gate.reserveDemand()
        launch { gate.demand(ticket) { record("demand") } }
        launch { gate.ahead({ true }) { record("ahead") } }
        runCurrent()
        assertEquals(listOf("running"), calls)
        assertTrue(first.isActive)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("running", "demand", "ahead"), calls)
        assertEquals(1, maximum)
    }

    @Test fun anAheadWaiterRechecksPriorityAfterAcquiringTheMutex() = runTest {
        val gate = SynthesisPriorityGate()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        launch { gate.ahead({ true }) { calls.add("running"); release.await() } }
        runCurrent()
        launch { gate.ahead({ true }) { calls.add("queued ahead") } }
        runCurrent() // Ahead passed its first check while no demand was reserved.
        val ticket = gate.reserveDemand()
        launch { gate.demand(ticket) { calls.add("demand") } }
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("running", "demand", "queued ahead"), calls)
    }

    @Test fun cancelledReservationsNeverEnterSynthesisAndReleaseExactlyOnce() = runTest {
        val gate = SynthesisPriorityGate()
        val ticket = gate.reserveDemand()
        ticket.cancel(); ticket.cancel()
        assertNull(gate.demand(ticket) { "must not run" })
        assertEquals(0, gate.pendingDemands)
        assertEquals("ahead", gate.ahead({ true }) { "ahead" })
    }

    @Test fun cancellingADemandWaitingForTheEngineDoesNotBlockAheadForever() = runTest {
        val gate = SynthesisPriorityGate()
        val release = CompletableDeferred<Unit>()
        launch { gate.ahead({ true }) { release.await() } }
        runCurrent()
        val ticket = gate.reserveDemand()
        val waiting = launch { gate.demand(ticket) { error("Cancelled demand ran") } }
        runCurrent()
        waiting.cancel()
        runCurrent()
        assertEquals(0, gate.pendingDemands)
        release.complete(Unit)
        runCurrent()
        assertEquals("resumed", gate.ahead({ true }) { "resumed" })
    }

    @Test fun synthesisFailureReleasesDemandPriority() = runTest {
        val gate = SynthesisPriorityGate()
        val ticket = gate.reserveDemand()
        assertTrue(runCatching { gate.demand(ticket) { error("fixture failure") } }.isFailure)
        assertEquals(0, gate.pendingDemands)
        assertEquals("resumed", gate.ahead({ true }) { "resumed" })
    }

    @Test fun aWithdrawnAheadCandidateIsNotSynthesizedAfterWaiting() = runTest {
        val gate = SynthesisPriorityGate()
        val ticket = gate.reserveDemand()
        var wanted = true
        val pending = async { gate.ahead({ wanted }) { "must not run" } }
        runCurrent()
        wanted = false
        ticket.cancel()
        runCurrent()
        assertNull(pending.await())
    }

    private fun window(start: Int, end: Int) = LookAheadProducer.Window(start, end, 0L, 1f, 1f) { "$it" }

    @Test fun onlyTheLatestWindowIsProcessedBeforeTheWorkerStarts() = runTest {
        val calls = mutableListOf<Int>()
        val producer = LookAheadProducer(backgroundScope, { _, index -> calls.add(index) })
        producer.update(window(1, 4))
        producer.update(window(7, 9))
        runCurrent()
        assertEquals(listOf(7, 8), calls)
        producer.close()
    }

    @Test fun windowReplacementFinishesInFlightWorkThenUsesTheNewWindow() = runTest {
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        var active = 0
        var maximum = 0
        val producer = LookAheadProducer(backgroundScope, { _, index ->
            active++; maximum = maxOf(maximum, active); calls.add(index)
            try { if (index == 1) release.await() } finally { active-- }
        })
        producer.update(window(1, 4))
        runCurrent()
        producer.update(window(7, 9))
        runCurrent()
        assertEquals(listOf(1), calls)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 7, 8), calls)
        assertEquals(1, maximum)
        producer.close()
    }

    @Test fun duplicateWakeupsDoNotRepeatACompletedWindow() = runTest {
        val calls = mutableListOf<Int>()
        val producer = LookAheadProducer(backgroundScope, { _, index -> calls.add(index) })
        val request = window(1, 3)
        producer.update(request); producer.update(request)
        runCurrent()
        producer.update(request)
        runCurrent()
        assertEquals(listOf(1, 2), calls)
        producer.close()
    }

    @Test fun oneFailedAheadItemDoesNotKillTheWorkerOrRetryForever() = runTest {
        val calls = mutableListOf<Int>()
        val failures = mutableListOf<Exception>()
        val producer = LookAheadProducer(backgroundScope, { _, index ->
            calls.add(index); if (index == 1) error("fixture failure")
        }, { failures.add(it) })
        producer.update(window(1, 3))
        runCurrent()
        producer.update(window(4, 5))
        runCurrent()
        assertEquals(listOf(1, 2, 4), calls)
        assertEquals(1, failures.size)
        producer.close()
    }

    @Test fun clearDropsRemainingWorkAndCloseRejectsLaterWindows() = runTest {
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val producer = LookAheadProducer(backgroundScope, { _, index -> calls.add(index); release.await() })
        producer.update(window(1, 4))
        runCurrent()
        producer.clear()
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(1), calls)
        producer.close()
        producer.update(window(8, 9))
        runCurrent()
        assertEquals(listOf(1), calls)
    }

    @Test fun rapidWindowUpdatesDoNotCreateMultipleProducers() = runTest {
        val calls = mutableListOf<Int>()
        val producer = LookAheadProducer(backgroundScope, { _, index -> calls.add(index) })
        repeat(100) { producer.update(window(it, it + 1)) }
        runCurrent()
        assertEquals(listOf(99), calls)
        producer.close()
    }

    @Test fun anOlderFailedRequestCannotClearANewerAheadWindow() = runTest {
        val calls = mutableListOf<Int>()
        val producer = LookAheadProducer(backgroundScope, { _, index -> calls.add(index) })
        val old = window(1, 2)
        producer.update(old)
        producer.update(window(8, 9))
        producer.clearIf(old)
        runCurrent()
        assertEquals(listOf(8), calls)
        producer.close()
    }

    @Test fun aBudgetStopEndsThisPassAndANewWindowCanResumeTheSameWorker() = runTest {
        val calls = mutableListOf<Int>()
        var room = 1
        val producer = LookAheadProducer(backgroundScope, { _, index -> calls.add(index); room-- },
            shouldContinue = { _, _ -> room > 0 })
        producer.update(window(1, 5))
        runCurrent()
        assertEquals(listOf(1), calls)
        room = 2
        producer.update(window(2, 5))
        runCurrent()
        assertEquals(listOf(1, 2, 3), calls)
        producer.close()
    }
}
