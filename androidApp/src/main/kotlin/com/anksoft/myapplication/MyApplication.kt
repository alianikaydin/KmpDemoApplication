package com.anksoft.myapplication

import android.app.Application
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.di.initKoin
import org.koin.android.ext.koin.androidContext

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The product flavor fixes the environment. Only the dev flavor honours BACKEND_URL
        // (-PkmpBackendUrl) and may fall back to the in-app demo backend; AppConfig enforces
        // that stage and prod ignore both.
        val config = AppConfig.create(
            environment = AppEnvironment.parse(BuildConfig.ENVIRONMENT),
            backendUrl = BuildConfig.BACKEND_URL,
            demoAllowed = BuildConfig.DEMO_ALLOWED,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE
        )
        initKoin(config = config) {
            androidContext(this@MyApplication)
        }
    }
}
