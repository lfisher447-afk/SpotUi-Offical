// File: app/src/main/java/com/music/spotui/util/Result.kt
package com.music.spotui.util

import kotlinx.coroutines.CancellationException

/** A typed outcome for operations that can fail without crashing the UI. */
sealed interface Result<out T> {
    /** A completed operation containing [value]. */
    data class Success<T>(val value: T) : Result<T>

    /** An operation that is actively loading. */
    data object Loading : Result<Nothing>

    /** A recoverable failure with a safe message for presentation. */
    data class Failure(
        val throwable: Throwable,
        val userMessage: String,
    ) : Result<Nothing>

    companion object {
        /** Executes [block], logging and converting an unexpected failure into [Failure]. */
        inline fun <T> runCatching(
            message: String,
            block: () -> T,
        ): Result<T> = kotlin.runCatching(block)
            .fold(
                onSuccess = { Success(it) },
                onFailure = {
                    Logger.error("Result", message, it)
                    Failure(it, message)
                },
            )
    }
}

/** Maps a successful [Result] while retaining loading and error states. */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Failure -> this
    Result.Loading -> Result.Loading
    is Result.Success -> Result.runCatching("Unable to transform result") { transform(value) }
}

/** Extracts the success value, or returns [fallback] for loading and failure states. */
fun <T> Result<T>.getOrElse(fallback: () -> T): T = when (this) {
    is Result.Success -> value
    is Result.Failure, Result.Loading -> fallback()
}

/** Executes suspending [block], rethrowing cancellation and returning a logged typed failure otherwise. */
suspend fun <T> runSuspendCatching(message: String, block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Logger.error("Result", message, throwable)
    Result.Failure(throwable, message)
}
