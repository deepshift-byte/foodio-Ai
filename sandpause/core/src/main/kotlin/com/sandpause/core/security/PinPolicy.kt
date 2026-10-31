package com.sandpause.core.security

/** Rules for PINs and recovery answers. */
object PinPolicy {
    private val PIN_REGEX = Regex("^\\d{4,6}$")

    /** A PIN is 4 to 6 digits. */
    fun isValidPin(pin: String): Boolean = PIN_REGEX.matches(pin)

    /** Recovery answers are compared case-insensitively, ignoring extra spaces. */
    fun normalizeAnswer(answer: String): String =
        answer.trim().lowercase().replace(Regex("\\s+"), " ")
}
