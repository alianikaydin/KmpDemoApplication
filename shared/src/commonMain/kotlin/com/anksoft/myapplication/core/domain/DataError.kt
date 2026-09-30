package com.anksoft.myapplication.core.domain

sealed interface DataError : Error {

    enum class Remote : DataError {
        NO_INTERNET,
        REQUEST_TIMEOUT,
        TOO_MANY_REQUESTS,
        UNAUTHORIZED,
        CONFLICT,
        SERVER_ERROR,
        SERIALIZATION,
        UNKNOWN
    }

    enum class Local : DataError {
        DISK_FULL,
        UNKNOWN
    }
}
