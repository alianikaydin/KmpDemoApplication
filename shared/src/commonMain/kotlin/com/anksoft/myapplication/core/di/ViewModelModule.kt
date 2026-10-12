package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.auth.presentation.login.LoginScreenModel
import com.anksoft.myapplication.features.auth.presentation.signup.SignUpScreenModel
import com.anksoft.myapplication.features.consent.presentation.prompt.ConsentPromptScreenModel
import com.anksoft.myapplication.features.home.presentation.HomeScreenModel
import com.anksoft.myapplication.features.settings.presentation.SettingsScreenModel
import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.presentation.AppUpdateController
import org.koin.dsl.module

val viewModelModule = module {
    factory { LoginScreenModel(get(), get(), get(), get()) }
    factory { SignUpScreenModel(get(), get(), get()) }
    factory { HomeScreenModel(get(), get()) }
    factory { ConsentPromptScreenModel(get(), get()) }
    factory { SettingsScreenModel(get(), get(), get(), get(), get(), get(), get()) }

    // App-scoped on purpose (not a factory): the state must outlive every screen and Activity (AC-2, AC-13).
    // The web binds no AppStore, so there the controller never checks (AC-17).
    single { AppUpdateController(get(), get(), getOrNull<AppStore>(), get(ApplicationScope), get()) }
}
