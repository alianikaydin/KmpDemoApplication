package com.anksoft.myapplication

import android.app.Application
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.di.initKoin
import org.koin.android.ext.koin.androidContext

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds use the in-app mock backend unless -PkmpBackendUrl is set;
        // release always uses the default config.
        initKoin(config = AppConfig.forBuild(BuildConfig.DEBUG, BuildConfig.BACKEND_URL)) {
            androidContext(this@MyApplication)
        }
    }
}
