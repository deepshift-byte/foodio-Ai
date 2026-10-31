package com.sandpause.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.sandpause.app.data.db.DailyUsageDao
import com.sandpause.app.data.db.EventDao
import com.sandpause.app.data.db.LockSessionDao
import com.sandpause.app.data.db.SandPauseDatabase
import com.sandpause.app.lock.AppClock
import com.sandpause.app.lock.SystemAppClock
import com.sandpause.app.voice.VoiceAnnouncer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** Long-lived scope for work that must outlive any screen or service. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideClock(): AppClock = SystemAppClock()

    /**
     * Settings live in device-protected storage, so they are readable before the
     * first unlock. That lets the lock be restored at LOCKED_BOOT_COMPLETED.
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> {
        val deviceContext = context.createDeviceProtectedStorageContext()
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(deviceContext.filesDir, "datastore/sandpause_settings.preferences_pb") },
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SandPauseDatabase =
        Room.databaseBuilder(
            context.createDeviceProtectedStorageContext(),
            SandPauseDatabase::class.java,
            "sandpause.db",
        ).build()

    @Provides
    fun provideLockSessionDao(db: SandPauseDatabase): LockSessionDao = db.lockSessions()

    @Provides
    fun provideEventDao(db: SandPauseDatabase): EventDao = db.events()

    @Provides
    fun provideDailyUsageDao(db: SandPauseDatabase): DailyUsageDao = db.dailyUsage()
}
