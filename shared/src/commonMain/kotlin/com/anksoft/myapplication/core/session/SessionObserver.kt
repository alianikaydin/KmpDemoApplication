package com.anksoft.myapplication.core.session

/** Why a session ended. */
enum class SignOutReason {
    /** The user chose to log out. */
    USER_LOGOUT,

    /** The backend rejected the refresh token (revoked, expired or reused). */
    EXPIRED
}

/**
 * Listens to session changes. Callbacks run synchronously on the calling thread right after the
 * session was stored or cleared, so an observer already sees the new state when the triggering
 * call returns. Keep them short; start longer work in your own scope.
 */
interface SessionObserver {
    fun onSignedIn()
    fun onSignedOut(reason: SignOutReason)
}
