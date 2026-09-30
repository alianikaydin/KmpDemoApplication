package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.auth.domain.UserDataValidator
import com.anksoft.myapplication.features.auth.domain.usecase.LoginUseCase
import org.koin.dsl.module

val useCaseModule = module {
    single { UserDataValidator() }
    factory { LoginUseCase(get()) }
}
