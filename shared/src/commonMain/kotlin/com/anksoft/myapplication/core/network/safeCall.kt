package com.anksoft.myapplication.core.network

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.ContentConvertException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlin.coroutines.coroutineContext

/**
 * Wraps a Ktor call so transport failures become typed [DataError.Remote] values.
 * Rethrows CancellationException via ensureActive so coroutine cancellation is
 * never swallowed and misreported as a network error.
 *
 * Every failure is logged once, here, through [logRemoteFailure]. [path] is the endpoint
 * constant (never a URL with a query); bodies and exception messages are never logged.
 */
suspend inline fun <reified T> safeCall(
    logger: AppLogger,
    path: String,
    execute: () -> HttpResponse
): Result<T, DataError.Remote> {
    val response = try {
        execute()
    } catch (e: UnresolvedAddressException) {
        logRemoteFailure(logger, path, DataError.Remote.NO_INTERNET, status = null, cause = e)
        return Result.Failure(DataError.Remote.NO_INTERNET)
    } catch (e: HttpRequestTimeoutException) {
        logRemoteFailure(logger, path, DataError.Remote.REQUEST_TIMEOUT, status = null, cause = e)
        return Result.Failure(DataError.Remote.REQUEST_TIMEOUT)
    } catch (e: Exception) {
        coroutineContext.ensureActive()
        logRemoteFailure(logger, path, DataError.Remote.UNKNOWN, status = null, cause = e)
        return Result.Failure(DataError.Remote.UNKNOWN)
    }
    val result = response.toResult<T>()
    if (result is Result.Failure) {
        logRemoteFailure(logger, path, result.error, status = response.status.value, cause = null)
    }
    return result
}

/**
 * The single place where remote failures are logged. Only the error type, path, status code
 * and the throwable's class name are recorded.
 */
@PublishedApi
internal fun logRemoteFailure(
    logger: AppLogger,
    path: String,
    error: DataError.Remote,
    status: Int?,
    cause: Throwable?
) {
    logger.log(error.logSeverity(), LogTags.NETWORK, cause) {
        "Remote failure error=$error path=$path status=${status ?: "-"}"
    }
}

/** Expected, user-recoverable failures are warnings; everything else is an error. */
internal fun DataError.Remote.logSeverity(): LogSeverity = when (this) {
    DataError.Remote.NO_INTERNET,
    DataError.Remote.REQUEST_TIMEOUT,
    DataError.Remote.UNAUTHORIZED,
    DataError.Remote.CONFLICT,
    DataError.Remote.TOO_MANY_REQUESTS -> LogSeverity.WARN
    DataError.Remote.SERVER_ERROR,
    DataError.Remote.SERIALIZATION,
    DataError.Remote.UNKNOWN -> LogSeverity.ERROR
}

suspend inline fun <reified T> HttpResponse.toResult(): Result<T, DataError.Remote> =
    when (status.value) {
        in 200..299 -> try {
            Result.Success(body<T>())
        } catch (_: SerializationException) {
            Result.Failure(DataError.Remote.SERIALIZATION)
        } catch (_: ContentConvertException) {
            // Ktor 3 wraps kotlinx.serialization failures in JsonConvertException.
            Result.Failure(DataError.Remote.SERIALIZATION)
        }
        401 -> Result.Failure(DataError.Remote.UNAUTHORIZED)
        408 -> Result.Failure(DataError.Remote.REQUEST_TIMEOUT)
        409 -> Result.Failure(DataError.Remote.CONFLICT)
        429 -> Result.Failure(DataError.Remote.TOO_MANY_REQUESTS)
        in 500..599 -> Result.Failure(DataError.Remote.SERVER_ERROR)
        else -> Result.Failure(DataError.Remote.UNKNOWN)
    }
