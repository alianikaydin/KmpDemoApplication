package com.anksoft.myapplication.core.analytics

import kotlin.reflect.KClass

/**
 * Records every call to the port in order. A call whose type is in [throwOn] is recorded and then
 * throws, like a vendor SDK that fails.
 */
class FakeAnalytics(private val throwOn: Set<KClass<out Call>> = emptySet()) : Analytics {

    sealed interface Call {
        data class Event(val name: String, val params: Map<String, String>) : Call
        data class SetCollection(val enabled: Boolean) : Call
        data object Reset : Call
    }

    private val _calls = mutableListOf<Call>()
    val calls: List<Call> get() = _calls

    val events: List<Call.Event> get() = _calls.filterIsInstance<Call.Event>()

    override fun logEvent(name: String, params: Map<String, String>) = record(Call.Event(name, params))

    override fun setCollectionEnabled(enabled: Boolean) = record(Call.SetCollection(enabled))

    override fun resetAnalyticsData() = record(Call.Reset)

    private fun record(call: Call) {
        _calls += call
        if (call::class in throwOn) error("vendor failure on $call")
    }
}
