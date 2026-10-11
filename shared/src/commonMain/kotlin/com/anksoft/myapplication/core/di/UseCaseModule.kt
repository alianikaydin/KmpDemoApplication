package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.auth.domain.UserDataValidator
import com.anksoft.myapplication.features.auth.domain.usecase.LoginUseCase
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import org.koin.dsl.module

val useCaseModule = module {
    single { UserDataValidator() }
    factory { LoginUseCase(get()) }
    factory { ObserveConsentTextsUseCase(get(), get()) }
}
