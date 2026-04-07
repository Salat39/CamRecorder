package com.salat.camrec

import android.app.Application
import com.salat.camrec.presentation.logs.ExecTraceTree
import com.salat.recorder.domain.repository.RecorderRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var recorder: RecorderRepository

    override fun onCreate() {
        super.onCreate()
        timberInit()
        recorder.watchdog()
    }

    private fun timberInit() {
        if (BuildConfig.DEBUG) {
            Timber.plant(ExecTraceTree())
        } else {
            // For release builds, consider using a different tree, like Crashlytics
            // Timber.plant(new CrashlyticsTree());
        }
    }
}
