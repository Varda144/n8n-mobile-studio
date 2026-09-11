package com.n8n.mobile.studio.core

import kotlinx.coroutines.CancellationException

/**
 * Lightweight result type without external dependencies.
 *
 * Use [Ok] for success and [Err] for failure. Call [run] or [runSuspend] to
 * invoke a block and capture any thrown exception as an [Err].
 */
sealed interface AppResult<out T> {

    data class Ok<out T>(val value: T) : AppResult<T>

    data class Err(val error: Throwable) : AppResult<Nothing> {
        val message: String get() = error.message ?: error.javaClass.simpleName
    }

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Ok -> value
        is Err -> default
    }

    fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Ok) block(value)
        return this
    }

    fun onFailure(block: (Throwable) -> Unit): AppResult<T> {
        if (this is Err) block(error)
        return this
    }

    fun toResult(): kotlin.Result<T> = when (this) {
        is Ok -> kotlin.Result.success(value)
        is Err -> kotlin.Result.failure(error)
    }

    companion object {
        fun <T> ok(value: T): AppResult<T> = Ok(value)

        fun <T> err(error: Throwable): AppResult<T> = Err(error)

        fun <T> err(message: String): AppResult<T> = Err(IllegalStateException(message))

        inline fun <T> run(block: () -> T): AppResult<T> = try {
            Ok(block())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Err(t)
        }

        suspend inline fun <T> runSuspend(crossinline block: suspend () -> T): AppResult<T> = try {
            Ok(block())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Err(t)
        }
    }
}