package com.anksoft.myapplication.core.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.anksoft.myapplication.core.domain.DataError
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.error_no_internet
import myapplication.shared.generated.resources.error_request_timeout
import myapplication.shared.generated.resources.error_server
import myapplication.shared.generated.resources.error_too_many_requests
import myapplication.shared.generated.resources.error_unknown
import kotlin.test.Test

class DataErrorToUiTextTest {

    // AC-3
    @Test
    fun noInternetMapsToItsResource() {
        assertThat(DataError.Remote.NO_INTERNET.toUiText())
            .isEqualTo(UiText.Resource(Res.string.error_no_internet))
    }

    // AC-3
    @Test
    fun timeoutMapsToItsResource() {
        assertThat(DataError.Remote.REQUEST_TIMEOUT.toUiText())
            .isEqualTo(UiText.Resource(Res.string.error_request_timeout))
    }

    // AC-3
    @Test
    fun tooManyRequestsMapsToItsResource() {
        assertThat(DataError.Remote.TOO_MANY_REQUESTS.toUiText())
            .isEqualTo(UiText.Resource(Res.string.error_too_many_requests))
    }

    // AC-3
    @Test
    fun serverErrorMapsToItsResource() {
        assertThat(DataError.Remote.SERVER_ERROR.toUiText())
            .isEqualTo(UiText.Resource(Res.string.error_server))
    }

    // AC-3
    @Test
    fun contextDependentAndOtherErrorsFallBackToTheUnknownResource() {
        val others = listOf(
            DataError.Remote.UNAUTHORIZED,
            DataError.Remote.CONFLICT,
            DataError.Remote.UNPROCESSABLE,
            DataError.Remote.SERIALIZATION,
            DataError.Remote.UNKNOWN,
            DataError.Local.DISK_FULL,
            DataError.Local.UNKNOWN
        )

        others.forEach { error ->
            assertThat(error.toUiText()).isEqualTo(UiText.Resource(Res.string.error_unknown))
        }
    }

    // AC-3: raw English text must never leak, so no error may become UiText.Dynamic.
    @Test
    fun everyErrorBecomesAResourceNeverDynamicText() {
        val all: List<DataError> = DataError.Remote.entries + DataError.Local.entries

        all.forEach { error ->
            assertThat(error.toUiText()).isInstanceOf(UiText.Resource::class)
        }
    }
}
