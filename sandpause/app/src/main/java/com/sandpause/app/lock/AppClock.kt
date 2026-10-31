package com.sandpause.app.lock

import android.os.SystemClock
import java.io.File

/**
 * Time sources. Lock timers use [elapsedMs] and [bootId]. The wall clock is used
 * only for display, reports and schedule windows.
 */
interface AppClock {
    /** Wall-clock time in ms since the epoch. Can be changed by the user. */
    fun wallMs(): Long

    /** Monotonic time since boot, in ms. Cannot be changed by the user. */
    fun elapsedMs(): Long

    /** A value that changes on every reboot. Empty if the kernel does not expose it. */
    fun bootId(): String
}

class SystemAppClock : AppClock {
    private val boot: String by lazy {
        runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("")
    }

    override fun wallMs(): Long = System.currentTimeMillis()

    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()

    override fun bootId(): String = boot
}
