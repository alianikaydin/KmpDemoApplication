package com.anksoft.myapplication.core.network

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
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
 */
suspend inline fun <reified T> safeCall(execute: () -> HttpResponse): Result<T, DataError.Remote> {
    val response = try {
        execute()
    } catch (_: UnresolvedAddressException) {
        return Result.Failure(DataError.Remote.NO_INTERNET)
    } catch (_: HttpRequestTimeoutException) {
        return Result.Failure(DataError.Remote.REQUEST_TIMEOUT)
    } catch (e: Exception) {
        coroutineContext.ensureActive()
        return Result.Failure(DataError.Remote.UNKNOWN)
    }
    return response.toResult()
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
