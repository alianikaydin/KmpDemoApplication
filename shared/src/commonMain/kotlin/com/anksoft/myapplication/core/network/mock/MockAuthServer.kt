package com.anksoft.myapplication.core.network.mock

import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RegisterRequestDto
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.myapplication.core.network.appJson
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/** A user the mock backend knows about before anyone registers. */
data class SeedUser(
    val email: String,
    val password: String,
    val name: String? = null
)

/**
 * In-memory stand-in for the auth backend, used in demo mode. Speaks the same
 * contract as AuthDtos.kt so the real client stack (safeCall, serialization,
 * error mapping) is exercised unchanged.
 *
 * Users live only in memory and are lost when the app process ends.
 * Routing is by path, so new endpoints (e.g. auth/refresh) are one more branch.
 */
class MockAuthServer(seedUsers: List<SeedUser> = listOf(DEMO_USER)) {

    private class Account(val password: String, val user: UserDto)

    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, Account>()
    private var nextId = 1
    private var nextTokenSerial = 1

    init {
        seedUsers.forEach { seed ->
            val email = seed.email.normalized()
            accounts[email] = Account(seed.password, UserDto(newUserId(), email, seed.name))
        }
    }

    suspend fun handle(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.trimStart('/')
        if (request.method != HttpMethod.Post) return scope.respondStatus(HttpStatusCode.NotFound)
        return when (path) {
            AuthRemoteDataSource.PATH_LOGIN -> login(scope, request)
            AuthRemoteDataSource.PATH_REGISTER -> register(scope, request)
            else -> scope.respondStatus(HttpStatusCode.NotFound)
        }
    }

    private suspend fun login(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val body = request.decodeBody<LoginRequestDto>()
            ?: return scope.respondStatus(HttpStatusCode.BadRequest)
        val response = mutex.withLock {
            val account = accounts[body.email.normalized()]
            if (account == null || account.password != body.password) null else issueTokens(account.user)
        } ?: return scope.respondStatus(HttpStatusCode.Unauthorized)
        return scope.respondJson(response)
    }

    private suspend fun register(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val body = request.decodeBody<RegisterRequestDto>()
            ?: return scope.respondStatus(HttpStatusCode.BadRequest)
        val email = body.email.normalized()
        val response = mutex.withLock {
            if (email in accounts) {
                null
            } else {
                val user = UserDto(id = newUserId(), email = email)
                accounts[email] = Account(body.password, user)
                issueTokens(user)
            }
        } ?: return scope.respondStatus(HttpStatusCode.Conflict)
        return scope.respondJson(response)
    }

    private fun issueTokens(user: UserDto): AuthResponseDto {
        val serial = nextTokenSerial++
        return AuthResponseDto(
            accessToken = "mock-access-${user.id}-$serial",
            refreshToken = "mock-refresh-${user.id}-$serial",
            user = user
        )
    }

    private fun newUserId(): String = "demo-${nextId++}"

    private fun String.normalized(): String = trim().lowercase()

    private suspend inline fun <reified T> HttpRequestData.decodeBody(): T? = try {
        appJson.decodeFromString<T>(body.toByteArray().decodeToString())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun MockRequestHandleScope.respondJson(response: AuthResponseDto): HttpResponseData =
        respond(
            content = appJson.encodeToString(AuthResponseDto.serializer(), response),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )

    private fun MockRequestHandleScope.respondStatus(status: HttpStatusCode): HttpResponseData =
        respond(content = "", status = status)

    companion object {
        val DEMO_USER = SeedUser(email = "demo@example.com", password = "Demo1234", name = "Demo User")
    }
}
