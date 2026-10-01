package com.anksoft.myapplication.core.network

import kotlinx.serialization.json.Json

/** Shared JSON settings so the real client and the mock server agree on the wire format. */
val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
