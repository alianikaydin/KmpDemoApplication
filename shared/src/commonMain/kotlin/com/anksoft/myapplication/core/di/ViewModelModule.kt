package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.auth.presentation.login.LoginScreenModel
import com.anksoft.myapplication.features.auth.presentation.signup.SignUpScreenModel
import com.anksoft.myapplication.features.consent.presentation.prompt.ConsentPromptScreenModel
import com.anksoft.myapplication.features.home.presentation.HomeScreenModel
import com.anksoft.myapplication.features.settings.presentation.SettingsScreenModel
import org.koin.dsl.module

val viewModelModule = module {
    factory { LoginScreenModel(get(), get(), get(), get()) }
    factory { SignUpScreenModel(get(), get(), get()) }
    factory { HomeScreenModel(get()) }
    factory { ConsentPromptScreenModel(get(), get()) }
    factory { SettingsScreenModel(get(), get(), get(), get(), get(), getOrNull()) }
}
