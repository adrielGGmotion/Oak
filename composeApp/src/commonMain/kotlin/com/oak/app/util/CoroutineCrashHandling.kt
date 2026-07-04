package com.oak.app.util

import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Checks whether the throwable chain originates from Android OkHttp's `AsyncTimeout`
 * throwing `IllegalStateException("Unbalanced enter/exit")`. This is a known Android
 * framework bug triggered when Ktor's `attachToUserJob` cleanup handler closes the
 * response stream during coroutine cancellation.
 */
internal fun Throwable.isAndroidOkHttpCrash(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is IllegalStateException && cause.message == "Unbalanced enter/exit") {
            return true
        }
        cause = cause.cause
    }
    return false
}

/**
 * Creates a [CoroutineExceptionHandler] that suppresses the known Android
 * OkHttp `AsyncTimeout` crash and rethrows all other exceptions directly
 * (without wrapping) to preserve crash-reporting fidelity.
 */
internal fun androidOkHttpCrashHandler(tag: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        if (throwable.isAndroidOkHttpCrash()) {
            println("$tag: suppressed Android OkHttp AsyncTimeout crash: $throwable")
        } else {
            throw throwable
        }
    }
