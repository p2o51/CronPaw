package com.gpo.cronpaw.core.concurrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

internal data class HttpBody(val code: Int, val text: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Read and close on OkHttp's thread; cancellation also interrupts an in-flight body read. */
internal suspend fun Call.awaitBody(): HttpBody = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resumeWith(runCatching {
                response.use { HttpBody(it.code, it.body.string()) }
            })
        }
    })
}

internal suspend inline fun <T> suspendResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
