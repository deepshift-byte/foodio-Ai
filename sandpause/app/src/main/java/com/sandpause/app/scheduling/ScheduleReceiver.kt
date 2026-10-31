package com.sandpause.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.lock.DataBudgetEnforcer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fired by the exact alarm at a schedule start or just after midnight. */
@AndroidEntryPoint
class ScheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: ScheduleCoordinator
    @Inject lateinit var dataBudget: DataBudgetEnforcer
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                runCatching { dataBudget.refresh() }
                coordinator.evaluate()
            } finally {
                pending.finish()
            }
        }
    }
}
