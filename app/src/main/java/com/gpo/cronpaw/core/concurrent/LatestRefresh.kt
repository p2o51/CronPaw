package com.gpo.cronpaw.core.concurrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Main-thread confined. Share an in-flight read; replace it when its inputs change. */
internal class LatestRefresh<K, T>(private val scope: CoroutineScope) {
    private var running: Pair<K, Deferred<T>>? = null

    /** A superseded request is not cancellation of the observer/worker waiting for it. */
    suspend fun awaitLatest(initial: Deferred<T>): T {
        var task = initial
        while (true) {
            try {
                return task.await()
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                val replacement = running?.second
                if (replacement == null || replacement === task) throw cancelled
                task = replacement
            }
        }
    }

    fun request(key: K, force: Boolean = false, read: suspend () -> T): Deferred<T> {
        running?.let { (previous, task) ->
            if (!force && previous == key && task.isActive) return task
            task.cancel()
        }
        return scope.async { read() }.also { running = key to it }
    }
}
