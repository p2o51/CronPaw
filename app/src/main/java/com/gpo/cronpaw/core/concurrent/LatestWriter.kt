package com.gpo.cronpaw.core.concurrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One writer, at most one waiting snapshot. Never cancel an in-progress disk write. */
internal class LatestWriter<T>(scope: CoroutineScope, write: suspend (T) -> Unit) {
    private val pending = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch {
            for (snapshot in pending) {
                try {
                    write(snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A cache failure must not prevent the next snapshot from being persisted.
                }
            }
        }
    }

    fun submit(snapshot: T) { pending.trySend(snapshot) }
}
