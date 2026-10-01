package com.anksoft.myapplication

import android.app.Application
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.di.initKoin
import org.koin.android.ext.koin.androidContext

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds run against the in-app mock backend; release uses the real API.
        initKoin(config = if (BuildConfig.DEBUG) AppConfig.Demo else AppConfig()) {
            androidContext(this@MyApplication)
        }
    }
}
