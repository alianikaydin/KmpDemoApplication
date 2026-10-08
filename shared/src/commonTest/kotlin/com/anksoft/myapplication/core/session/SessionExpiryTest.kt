package com.anksoft.myapplication.core.session

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SessionExpiryTest {

    private val session = SessionManager(MapSettings()).apply {
        saveToken("access")
        saveRefreshToken("refresh")
        saveUserId("u1")
    }
    private val observer = FakeSessionObserver()
    private val expiry = SessionExpiry(session, lazy { listOf(observer) })

    // AC-28
    @Test
    fun expiringClearsTheSession() {
        expiry.expire()

        assertThat(session.getToken()).isNull()
        assertThat(session.getRefreshToken()).isNull()
        assertThat(session.getUserId()).isNull()
    }

    // AC-28
    @Test
    fun observersAreToldTheSessionExpiredAfterItWasCleared() {
        var tokenSeenByObserver: String? = "unset"
        observer.onEvent = { tokenSeenByObserver = session.getToken() }

        expiry.expire()

        assertThat(observer.events).isEqualTo(listOf(FakeSessionObserver.Event.SignedOut(SignOutReason.EXPIRED)))
        assertThat(tokenSeenByObserver).isNull()
    }

    // AC-28
    @Test
    fun eventIsEmittedOnceAndAnotherExpiryOfTheSameSessionDoesNothing() = runTest {
        expiry.events.test {
            expiry.expire()
            expiry.expire()

            awaitItem()
            expectNoEvents()
        }
        assertThat(observer.events.size).isEqualTo(1)
    }

    // AC-28
    @Test
    fun expiryNoticeIsConsumedOnlyOnce() {
        assertThat(expiry.consumeExpiredNotice()).isFalse()

        expiry.expire()

        assertThat(expiry.consumeExpiredNotice()).isTrue()
        assertThat(expiry.consumeExpiredNotice()).isFalse()
    }

    // AC-28
    @Test
    fun expiringWithoutASessionNotifiesNobody() = runTest {
        session.clear()

        expiry.events.test {
            expiry.expire()

            expectNoEvents()
        }
        assertThat(observer.events).isEmpty()
        assertThat(expiry.consumeExpiredNotice()).isFalse()
    }

    // AC-28
    @Test
    fun aNewSessionCanExpireAgainAfterTheFirstOne() {
        expiry.expire()
        session.saveToken("access-2")
        session.saveRefreshToken("refresh-2")

        expiry.expire()

        assertThat(observer.events.size).isEqualTo(2)
    }
}
