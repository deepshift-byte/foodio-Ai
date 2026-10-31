package com.sandpause.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sandpause.app.admin.DeviceOwnerController
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.lock.LockService
import com.sandpause.app.scheduling.WatchdogWorker
import com.sandpause.app.voice.VoiceAnnouncer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SandPauseApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var voice: VoiceAnnouncer
    @Inject lateinit var store: SettingsStore
    @Inject lateinit var deviceOwner: DeviceOwnerController
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        LockService.ensureChannel(this)
        voice.start()
        scope.launch {
            if (store.currentSettings().setupDone) {
                deviceOwner.applyHardening()
                WatchdogWorker.schedule(this@SandPauseApp)
                LockService.start(this@SandPauseApp)
            }
        }
    }
}
