package com.anksoft.myapplication.core.session

import com.anksoft.myapplication.core.storage.SessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Ends the session when the backend no longer accepts it (the refresh token was rejected).
 *
 * [observers] is lazy because the network layer calls [expire] and observers may themselves
 * depend on the network layer; resolving them on first use avoids a construction cycle.
 */
class SessionExpiry(
    private val sessionManager: SessionManager,
    private val observers: Lazy<List<SessionObserver>>
) {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once per expired session; the app navigates to Login on it. */
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    private val noticePending = MutableStateFlow(false)

    /**
     * Clears the session, tells the observers and emits on [events]. A second call for the same
     * session is a no-op because there is nothing left to clear.
     *
     * The check, the clearing and the notifications run under the session lock, so a sign-in on
     * another thread cannot slip in between and then be told it was signed out.
     */
    fun expire() {
        // Resolved before the lock is taken: building the observers may take locks of its own.
        val sessionObservers = observers.value
        sessionManager.withSessionLock {
            if (sessionManager.getToken() != null || sessionManager.getRefreshToken() != null) {
                sessionManager.clear()
                noticePending.value = true
                sessionObservers.forEach { it.onSignedOut(SignOutReason.EXPIRED) }
                _events.tryEmit(Unit)
            }
        }
    }

    /** True exactly once after an expiry, so Login shows its "session expired" notice a single time. */
    fun consumeExpiredNotice(): Boolean = noticePending.compareAndSet(expect = true, update = false)
}
