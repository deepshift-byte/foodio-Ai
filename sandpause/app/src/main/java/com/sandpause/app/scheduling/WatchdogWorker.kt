package com.sandpause.app.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.lock.LockService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Runs about every 15 minutes (the WorkManager minimum). It restarts the lock
 * service if it was killed and re-checks schedules, as a backup to the exact alarms.
 */
@HiltWorker
class WatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val store: SettingsStore,
    private val coordinator: ScheduleCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!store.currentSettings().setupDone) return Result.success()
        LockService.start(applicationContext)
        coordinator.evaluate()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "sandpause-watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
