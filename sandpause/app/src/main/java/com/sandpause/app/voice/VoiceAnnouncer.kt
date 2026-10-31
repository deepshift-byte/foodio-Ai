package com.sandpause.app.voice

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.sandpause.app.R
import com.sandpause.app.data.settings.AppLanguage
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.lock.LockEngine
import com.sandpause.core.hourglass.VoiceLine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks lock lines with Android TextToSpeech and always shows them as captions.
 *
 * - A 3-second cooldown applies between lines. Completion ("Well done") always plays.
 * - If the device has no Luganda voice, the line is spoken in English. The caption stays Luganda.
 * - Optional bundled audio clips are not used yet. TTS is the only voice source.
 */
@Singleton
class VoiceAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    private val engine: LockEngine,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _caption = MutableStateFlow<String?>(null)
    val caption: StateFlow<String?> = _caption.asStateFlow()

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private val queue = ArrayDeque<Pair<VoiceLine, AppLanguage>>()
    private var lastSpokenMs = Long.MIN_VALUE / 4
    private var captionJob: Job? = null
    private var started = false

    /** Speaks the milestone and finish announcements the lock engine emits. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            engine.lockEvents.collect { event ->
                when (event) {
                    is LockEngine.LockEvent.Milestone -> speak(VoiceLine.Milestone(event.minutes))
                    LockEngine.LockEvent.Finished -> speak(VoiceLine.Done, bypassCooldown = true)
                    is LockEngine.LockEvent.Started -> Unit
                }
            }
        }
    }

    /** Speaks [line] and shows its caption. Returns false when the cooldown skipped it. */
    fun speak(line: VoiceLine, bypassCooldown: Boolean = false): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!bypassCooldown && now - lastSpokenMs < COOLDOWN_MS) return false
        lastSpokenMs = now
        val language = store.settings.value.language
        mainHandler.post {
            val caption = VoiceText.render(context, language, line)
            showCaption(caption)
            queue.addLast(line to language)
            flush()
        }
        return true
    }

    private fun showCaption(text: String) {
        _caption.value = text
        captionJob?.cancel()
        captionJob = scope.launch {
            delay(CAPTION_MS)
            _caption.value = null
        }
    }

    private fun flush() {
        val current = tts
        if (current == null) {
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                mainHandler.post { flush() }
            }
            return
        }
        if (!ttsReady) return
        while (queue.isNotEmpty()) {
            val (line, language) = queue.removeFirst()
            val locale = Locale.forLanguageTag(language.code)
            val supported = current.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
            val speakLanguage = if (supported) language else AppLanguage.ENGLISH
            current.setLanguage(if (supported) locale else Locale.ENGLISH)
            val text = VoiceText.render(context, speakLanguage, line)
            current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sandpause-${System.nanoTime()}")
        }
    }

    companion object {
        const val COOLDOWN_MS = 3_000L
        private const val CAPTION_MS = 4_000L
    }
}

/** Maps a [VoiceLine] to localized text using the resources for that language. */
object VoiceText {

    fun render(context: Context, language: AppLanguage, line: VoiceLine): String {
        val localized = context.localizedFor(language)
        return when (line) {
            is VoiceLine.Remaining -> localized.resources.getQuantityString(
                R.plurals.voice_remaining, line.minutes, line.minutes,
            )
            VoiceLine.AlmostThere -> localized.getString(R.string.voice_almost_there)
            VoiceLine.Patience -> localized.getString(R.string.voice_patience)
            VoiceLine.Done -> localized.getString(R.string.voice_done)
            is VoiceLine.Milestone -> localized.resources.getQuantityString(
                R.plurals.voice_milestone, line.minutes, line.minutes,
            )
        }
    }

    private fun Context.localizedFor(language: AppLanguage): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.code))
        return createConfigurationContext(config)
    }
}
