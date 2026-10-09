package com.anksoft.myapplication.core.presentation

import com.anksoft.myapplication.core.domain.DataError
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.error_no_internet
import myapplication.shared.generated.resources.error_request_timeout
import myapplication.shared.generated.resources.error_server
import myapplication.shared.generated.resources.error_too_many_requests
import myapplication.shared.generated.resources.error_unknown

/**
 * Single place where a transport-level failure becomes user-facing copy.
 * AC-1.6: a raw exception message must never reach the UI.
 *
 * UNAUTHORIZED, CONFLICT and UNPROCESSABLE are intentionally absent: their meaning is
 * context-dependent (bad credentials on login vs. duplicate email on signup, an outdated
 * consent text), so callers map those themselves and this falls back to the generic text.
 */
fun DataError.toUiText(): UiText = when (this) {
    DataError.Remote.NO_INTERNET -> UiText.Resource(Res.string.error_no_internet)
    DataError.Remote.REQUEST_TIMEOUT -> UiText.Resource(Res.string.error_request_timeout)
    DataError.Remote.TOO_MANY_REQUESTS -> UiText.Resource(Res.string.error_too_many_requests)
    DataError.Remote.SERVER_ERROR -> UiText.Resource(Res.string.error_server)
    DataError.Remote.UNPROCESSABLE -> UiText.Resource(Res.string.error_unknown)
    else -> UiText.Resource(Res.string.error_unknown)
}
