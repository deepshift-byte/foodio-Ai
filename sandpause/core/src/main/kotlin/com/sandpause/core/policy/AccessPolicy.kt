package com.sandpause.core.policy

/** Everything the policy needs to decide whether an app may be in the foreground. */
data class PolicyInput(
    val packageName: String,
    val lockActive: Boolean,
    /** Apps the parent has allowed during a lock. */
    val allowedPackages: Set<String>,
    /** Apps the parent tagged "data-heavy". */
    val dataHeavyPackages: Set<String>,
    /** True once today's mobile-data budget is spent. */
    val dataBudgetExceeded: Boolean,
    /** Play Store opens are blocked until a parent enters the PIN. */
    val playStoreNeedsPin: Boolean,
    /** Whether a parent has recently verified the PIN. */
    val parentVerified: Boolean,
    /** This app's own package. Always allowed. */
    val ownPackage: String,
)

enum class Verdict { ALLOW, BLOCK, REQUIRE_PARENT_PIN }

object AccessPolicy {

    fun decide(input: PolicyInput): Verdict {
        val pkg = input.packageName
        if (pkg == input.ownPackage) return Verdict.ALLOW
        if (pkg in KnownPackages.SYSTEM_UI) return Verdict.ALLOW

        if (pkg == KnownPackages.PLAY_STORE && input.playStoreNeedsPin && !input.parentVerified) {
            return Verdict.REQUIRE_PARENT_PIN
        }

        if (input.dataBudgetExceeded && pkg in input.dataHeavyPackages) return Verdict.BLOCK

        if (!input.lockActive) return Verdict.ALLOW

        // Store, browsers and dialers stay blocked during a lock, even if allowlisted.
        if (pkg in KnownPackages.ALWAYS_BLOCKED_DURING_LOCK) return Verdict.BLOCK
        if (pkg in KnownPackages.TELECOM_IN_CALL_UI) return Verdict.ALLOW

        if (pkg in input.allowedPackages) return Verdict.ALLOW
        return Verdict.BLOCK
    }
}
