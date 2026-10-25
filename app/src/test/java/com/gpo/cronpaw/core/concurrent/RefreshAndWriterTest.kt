package com.gpo.cronpaw.core.concurrent

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RefreshAndWriterTest {
    @Test fun identicalInFlightReadsAreShared() = runBlocking {
        val reader = LatestRefresh<String, Int>(this)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val first = reader.request("today") { calls++; gate.await(); 7 }
        val second = reader.request("today") { calls++; 9 }
        assertSame(first, second)
        gate.complete(Unit)
        assertEquals(7, first.await())
        assertEquals(1, calls)
        assertEquals(9, reader.request("today") { calls++; 9 }.await())
        assertEquals(2, calls)
    }

    @Test fun replacedReadsCancelTheirWorkButKeepTheObserverAlive() = runBlocking {
        val reader = LatestRefresh<String, Int>(this)
        val started = CompletableDeferred<Unit>()
        var stopped = false
        val old = reader.request("old") {
            try { started.complete(Unit); awaitCancellation() } finally { stopped = true }
        }
        val waiter = async { reader.awaitLatest(old) }
        started.await()
        reader.request("new") { 42 }
        assertEquals(42, withTimeout(5_000) { waiter.await() })
        assertTrue(stopped)
        assertTrue(isActive)
    }

    @Test fun aForcedRefreshDoesNotReuseAReadStartedBeforeAWrite() = runBlocking {
        val reader = LatestRefresh<String, Int>(this)
        val old = reader.request("today") { awaitCancellation() }
        val fresh = reader.request("today", force = true) { 2 }
        assertNotSame(old, fresh)
        assertTrue(old.isCancelled)
        assertEquals(2, fresh.await())
    }

    @Test fun callerCancellationIsNotSwallowed() = runBlocking {
        val reader = LatestRefresh<String, Int>(this)
        val read = reader.request("today") { awaitCancellation() }
        val waiter = launch { reader.awaitLatest(read); fail("cancelled waiter returned") }
        yield()
        waiter.cancelAndJoin()
        assertTrue(waiter.isCancelled)
        read.cancelAndJoin()
    }

    @Test fun writesAreSerialAndOnlyTheNewestWaitingSnapshotSurvives() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + owner)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val lastWritten = CompletableDeferred<Unit>()
        val writes = mutableListOf<Int>()
        val writer = LatestWriter<Int>(scope) {
            if (it == 1) { firstStarted.complete(Unit); releaseFirst.await() }
            writes += it
            if (it == 100) lastWritten.complete(Unit)
        }
        try {
            writer.submit(1)
            firstStarted.await()
            for (i in 2..100) writer.submit(i)
            releaseFirst.complete(Unit)
            withTimeout(5_000) { lastWritten.await() }
            assertEquals(listOf(1, 100), writes)
        } finally { owner.cancelAndJoin() }
    }

    @Test fun oneFailedWriteDoesNotKillPersistence() = runBlocking {
        val owner = SupervisorJob()
        val failed = CompletableDeferred<Unit>()
        val recovered = CompletableDeferred<Int>()
        val writer = LatestWriter<Int>(CoroutineScope(coroutineContext + owner)) {
            if (it == 1) { failed.complete(Unit); error("disk full") }
            recovered.complete(it)
        }
        try {
            writer.submit(1)
            failed.await()
            writer.submit(2)
            assertEquals(2, withTimeout(5_000) { recovered.await() })
        } finally { owner.cancelAndJoin() }
    }
}
