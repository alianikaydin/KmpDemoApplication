package com.anksoft.myapplication.features.settings.presentation

internal object SettingsTestTags {
    const val ENVIRONMENT = "settings_environment"
    const val VERSION = "settings_version"
    const val BACK_BUTTON = "settings_back_button"
    const val LOGOUT_BUTTON = "settings_logout_button"
    const val TEST_CRASH = "settings_test_crash"
    const val LANGUAGE_SYSTEM = "settings_language_system"
    const val LANGUAGE_TURKISH = "settings_language_tr"
    const val LANGUAGE_ENGLISH = "settings_language_en"
    const val CONSENT_TOGGLE = "settings_consent_toggle"
    const val PRIVACY_POLICY = "settings_privacy_policy"
    const val CONSENT_RETRY = "settings_consent_retry"
    /** Exactly one of these is on screen: it tells UI automation whether the switch is on. */
    const val CONSENT_STATE_ON = "settings_consent_state_on"
    const val CONSENT_STATE_OFF = "settings_consent_state_off"
}
