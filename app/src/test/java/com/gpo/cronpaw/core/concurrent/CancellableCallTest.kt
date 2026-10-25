package com.gpo.cronpaw.core.concurrent

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CancellableCallTest {
    @Test fun cancellationClosesTheSocketWhileTheBodyIsStillArriving() = runBlocking {
        ServerSocket(0).use { server ->
            val headersSent = CompletableDeferred<Unit>()
            val executor = Executors.newSingleThreadExecutor()
            val disconnected = executor.submit<Boolean> {
                server.accept().use { socket ->
                    socket.soTimeout = 3_000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* request headers */ }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nhello".toByteArray())
                        flush()
                    }
                    headersSent.complete(Unit)
                    input.read() == -1
                }
            }
            val client = OkHttpClient()
            try {
                val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build())
                val task = async { call.awaitBody() }
                withTimeout(5_000) { headersSent.await() }
                task.cancelAndJoin()
                assertTrue(call.isCanceled())
                assertTrue(disconnected.get(5, TimeUnit.SECONDS))
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
                executor.shutdownNow()
            }
        }
    }

    @Test fun repositoryFallbacksDoNotConvertCancellationToEmptyData() = runBlocking {
        try {
            suspendResult<Unit> { throw CancellationException("superseded") }
            fail("cancellation was swallowed")
        } catch (_: CancellationException) { /* expected */ }
        assertTrue(suspendResult<Unit> { error("offline") }.isFailure)
    }
}
