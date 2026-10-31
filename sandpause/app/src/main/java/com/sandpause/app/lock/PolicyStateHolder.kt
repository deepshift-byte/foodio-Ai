package com.sandpause.app.lock

import android.content.Context
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A synchronous snapshot of everything the foreground guard needs. The
 * accessibility callback reads it on the main thread, so it must not suspend.
 */
data class PolicyState(
    val lockActive: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val dataHeavyPackages: Set<String> = emptySet(),
    val dataBudgetExceeded: Boolean = false,
    val playStorePin: Boolean = false,
    val ownPackage: String = "",
)

@Singleton
class PolicyStateHolder @Inject constructor(
    store: SettingsStore,
    engine: LockEngine,
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val ownPackage = context.packageName

    val state: StateFlow<PolicyState> = combine(
        store.settings,
        engine.session,
        store.dataState,
    ) { settings, session, data ->
        val today = LocalDate.now().toString()
        PolicyState(
            lockActive = session != null,
            allowedPackages = settings.allowedPackages.toSet(),
            dataHeavyPackages = settings.dataHeavyPackages.toSet(),
            dataBudgetExceeded = data.blocked && data.day == today,
            playStorePin = settings.playStorePin,
            ownPackage = ownPackage,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PolicyState(ownPackage = ownPackage))
}
