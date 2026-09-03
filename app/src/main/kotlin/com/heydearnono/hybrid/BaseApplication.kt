package com.heydearnono.hybrid

import android.app.Application
import com.heydearnono.hybrid.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class BaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@BaseApplication)
            modules(appModules(loggingEnabled = BuildConfig.DEBUG))
        }
    }
}
