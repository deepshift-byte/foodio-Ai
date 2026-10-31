package com.sandpause.core.policy

/**
 * Well-known package names that are never usable during a lock.
 *
 * The lists cover common Android builds and are not exhaustive. Device-owner
 * mode enforces the allowlist itself, so an unlisted app is blocked there too.
 */
object KnownPackages {
    /** Google Play Store. */
    const val PLAY_STORE = "com.android.vending"

    /** Pass-through for the lock screen's emergency and parent-contact calls. */
    val TELECOM_IN_CALL_UI: Set<String> = setOf(
        "com.android.server.telecom",
        "com.android.phone",
    )

    val BROWSERS: Set<String> = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.touch",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.UCMobile.intl",
        "com.uc.browser.en",
        "com.google.android.apps.searchlite",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
    )

    /** Dialer apps. Blocked so the child cannot dial USSD or mobile-money codes. */
    val DIALERS: Set<String> = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.miui.phone",
        "com.transsion.phonemaster",
        "com.android.contacts",
    )

    /** Always-blocked during a lock. */
    val ALWAYS_BLOCKED_DURING_LOCK: Set<String> =
        setOf(PLAY_STORE) + BROWSERS + DIALERS

    /** Android system UI. Never blocked, otherwise the lock would be unrecoverable. */
    val SYSTEM_UI: Set<String> = setOf("com.android.systemui", "com.android.settings")
}
