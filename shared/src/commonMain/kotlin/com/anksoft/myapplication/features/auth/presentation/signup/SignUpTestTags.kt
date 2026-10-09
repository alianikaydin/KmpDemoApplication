package com.anksoft.myapplication.features.auth.presentation.signup

/** Stable semantics tags used by UI automation (Maestro flows in `.maestro-e2e/`). */
internal object SignUpTestTags {
    const val EMAIL_INPUT = "signup_email_input"
    const val PASSWORD_INPUT = "signup_password_input"
    const val CONFIRM_PASSWORD_INPUT = "signup_confirm_password_input"
    const val SUBMIT_BUTTON = "signup_submit_button"
    const val CONSENT_CHECKBOX = "signup_consent_checkbox"
    const val PRIVACY_POLICY = "signup_privacy_policy"
    const val CONSENT_RETRY = "signup_consent_retry"
}
