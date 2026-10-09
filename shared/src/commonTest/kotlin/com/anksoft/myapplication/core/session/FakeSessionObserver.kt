package com.anksoft.myapplication.core.session

/** Records session callbacks in order. [onEvent] runs right after each one is recorded. */
class FakeSessionObserver : SessionObserver {

    sealed interface Event {
        data object SignedIn : Event
        data class SignedOut(val reason: SignOutReason) : Event
    }

    val events = mutableListOf<Event>()
    var onEvent: (Event) -> Unit = {}

    override fun onSignedIn() = record(Event.SignedIn)

    override fun onSignedOut(reason: SignOutReason) = record(Event.SignedOut(reason))

    private fun record(event: Event) {
        events += event
        onEvent(event)
    }
}
