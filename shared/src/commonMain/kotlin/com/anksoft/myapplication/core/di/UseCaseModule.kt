package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.features.auth.domain.UserDataValidator
import com.anksoft.myapplication.features.auth.domain.usecase.LoginUseCase
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import com.anksoft.myapplication.features.update.domain.usecase.CheckForUpdateUseCase
import org.koin.dsl.module

val useCaseModule = module {
    single { UserDataValidator() }
    factory { LoginUseCase(get()) }
    factory { ObserveConsentTextsUseCase(get(), get()) }
    factory { CheckForUpdateUseCase(get(), get(), get<AppConfig>().versionName, get()) }
}
