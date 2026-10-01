package com.anksoft.myapplication.core.network.mock

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine

/** Routes every request to [server] instead of the network. */
fun createMockEngine(server: MockAuthServer): HttpClientEngine =
    MockEngine { request -> server.handle(this, request) }
