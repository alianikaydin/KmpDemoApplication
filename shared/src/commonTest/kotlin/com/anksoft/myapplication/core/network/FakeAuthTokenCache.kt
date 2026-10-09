package com.anksoft.myapplication.core.network

/** Counts [clear] calls so tests can assert when the bearer cache is dropped. */
class FakeAuthTokenCache : AuthTokenCache {
    var clearCount = 0
        private set

    override fun clear() {
        clearCount++
    }
}
