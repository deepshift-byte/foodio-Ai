package com.sandpause.app.lock

import android.content.Context
import android.os.SystemClock
import com.sandpause.app.R
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.security.ParentSession
import com.sandpause.core.policy.AccessPolicy
import com.sandpause.core.policy.PolicyInput
import com.sandpause.core.policy.Verdict
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides what happens when an app comes to the foreground. It is called by the
 * accessibility service (primary fallback detector) and by the usage-stats poller
 * (backup detector). Both use the same rules, so the result is the same either way.
 */
@Singleton
class ForegroundGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policy: PolicyStateHolder,
    private val parentSession: ParentSession,
    private val events: EventLogger,
    private val overlay: LockOverlay,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var lastBlockedPkg: String? = null
    private var lastBlockedAt = 0L

    /**
     * [goHome] sends the child to the launcher when a blocked app opens outside a lock
     * (for example a data-heavy app once the data budget is spent).
     */
    fun onForeground(packageName: String, goHome: () -> Unit) {
        val p = policy.state.value
        val verdict = AccessPolicy.decide(
            PolicyInput(
                packageName = packageName,
                lockActive = p.lockActive,
                allowedPackages = p.allowedPackages,
                dataHeavyPackages = p.dataHeavyPackages,
                dataBudgetExceeded = p.dataBudgetExceeded,
                playStoreNeedsPin = p.playStorePin,
                parentVerified = parentSession.isVerified(),
                ownPackage = p.ownPackage,
            ),
        )
        when (verdict) {
            Verdict.ALLOW -> {
                if (p.lockActive && packageName == p.ownPackage) overlay.hide()
            }
            Verdict.BLOCK -> {
                logBlocked(packageName)
                if (p.lockActive) {
                    LockLauncher.showLock(context)
                    overlay.show()
                } else {
                    goHome()
                    LockLauncher.toast(context, R.string.data_budget_notice)
                }
            }
            Verdict.REQUIRE_PARENT_PIN -> {
                scope.launch { events.log(EventType.PLAY_STORE_PIN_PROMPT) }
                ParentGateActivity.launch(context)
            }
        }
    }

    /** Logs at most once per app every five seconds, so window churn does not flood the log. */
    private fun logBlocked(packageName: String) {
        val now = SystemClock.elapsedRealtime()
        if (packageName == lastBlockedPkg && now - lastBlockedAt < 5_000L) return
        lastBlockedPkg = packageName
        lastBlockedAt = now
        scope.launch { events.log(EventType.BLOCKED_APP, packageName) }
    }
}
