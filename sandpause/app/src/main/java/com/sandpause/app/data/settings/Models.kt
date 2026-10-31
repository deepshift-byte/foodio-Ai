package com.sandpause.app.data.settings

import com.sandpause.core.schedule.LockSchedule
import com.sandpause.core.security.AttemptState
import com.sandpause.core.time.ElapsedDeadline
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/**
 * Persisted DTOs. They are stored as JSON in DataStore. The core module's
 * classes are kept free of serialization annotations, so mapping lives here.
 */

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    LUGANDA("lg");

    companion object {
        fun fromCode(code: String): AppLanguage = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

@Serializable
data class ContactDto(val name: String, val number: String)

@Serializable
data class ScheduleDto(
    val id: String,
    val name: String,
    /** ISO day numbers, 1 = Monday .. 7 = Sunday. */
    val days: List<Int>,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
)

@Serializable
data class AttemptDto(
    val failures: Int = 0,
    val lockouts: Int = 0,
    val cooldownRemainingMs: Long = 0,
    val cooldownAnchorMs: Long = 0,
    val cooldownBootId: String = "",
)

@Serializable
data class SettingsDto(
    val setupDone: Boolean = false,
    val childName: String = "",
    val languageCode: String = AppLanguage.ENGLISH.code,
    val pinHash: String = "",
    val recoveryQuestion: String = "",
    val recoveryAnswerHash: String = "",
    /** 0 = allowance off. */
    val dailyAllowanceMin: Int = 0,
    /** 0 = no data budget. */
    val dataBudgetMb: Int = 0,
    val playStorePin: Boolean = false,
    val allowedPackages: List<String> = emptyList(),
    val dataHeavyPackages: List<String> = emptyList(),
    val contacts: List<ContactDto> = emptyList(),
    /** Empty means "use the built-in suggestions". */
    val suggestions: List<String> = emptyList(),
    val schedules: List<ScheduleDto> = emptyList(),
    val attempts: AttemptDto = AttemptDto(),
) {
    val language: AppLanguage get() = AppLanguage.fromCode(languageCode)
}

/** The lock that is running, if any. Restored after a restart or reboot. */
@Serializable
data class LockSnapshotDto(
    val active: Boolean = false,
    val id: Long = 0,
    val reason: String = "MANUAL",
    val startedWallMs: Long = 0,
    val totalMs: Long = 0,
    val remainingMs: Long = 0,
    val anchorElapsedMs: Long = 0,
    val bootId: String = "",
)

/** Data-budget bookkeeping for the current day. */
@Serializable
data class DataStateDto(
    val day: String = "",
    val blocked: Boolean = false,
    /** TrafficStats fallback: device mobile bytes at the start of [day]; -1 = unknown. */
    val baselineBytes: Long = -1L,
)

// ---- mapping to the core module ----

fun ScheduleDto.toCore(): LockSchedule = LockSchedule(
    id = id,
    name = name,
    days = days.map { DayOfWeek.of(it) }.toSet(),
    startMinute = startMinute,
    endMinute = endMinute,
    enabled = enabled,
)

fun LockSchedule.toDto(): ScheduleDto = ScheduleDto(
    id = id,
    name = name,
    days = days.map { it.value }.sorted(),
    startMinute = startMinute,
    endMinute = endMinute,
    enabled = enabled,
)

fun AttemptDto.toCore(): AttemptState = AttemptState(
    failures = failures,
    lockouts = lockouts,
    cooldown = if (cooldownRemainingMs > 0) {
        ElapsedDeadline(cooldownRemainingMs, cooldownAnchorMs, cooldownBootId)
    } else null,
)

fun AttemptState.toDto(): AttemptDto = AttemptDto(
    failures = failures,
    lockouts = lockouts,
    cooldownRemainingMs = cooldown?.remainingMs ?: 0L,
    cooldownAnchorMs = cooldown?.anchorElapsedMs ?: 0L,
    cooldownBootId = cooldown?.bootId ?: "",
)
