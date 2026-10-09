package com.anksoft.myapplication.core.domain

sealed interface DataError : Error {

    enum class Remote : DataError {
        NO_INTERNET,
        REQUEST_TIMEOUT,
        TOO_MANY_REQUESTS,
        UNAUTHORIZED,
        CONFLICT,

        /** HTTP 422: the request was understood but refused, for example a consent text version that is gone. */
        UNPROCESSABLE,
        SERVER_ERROR,
        SERIALIZATION,
        UNKNOWN
    }

    enum class Local : DataError {
        DISK_FULL,
        UNKNOWN
    }
}
