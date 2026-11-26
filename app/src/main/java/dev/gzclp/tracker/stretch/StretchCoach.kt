package dev.gzclp.tracker.stretch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Plays the stretch player's [Cue]s: a tap on the phone when [haptics] is on and, when [sound] is on, the words spoken by the
 * on-device voice, or a short tone. Sound plays at media volume and ducks music while it speaks.
 */
class StretchCoach(context: Context, private val sound: Boolean, private val haptics: Boolean) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
    @Volatile private var ready = false

    /** The last words cued before the voice finished starting up, said as soon as it has. */
    private var waiting: String? = null
    private val tts: TextToSpeech? = if (sound) TextToSpeech(context.applicationContext) {
        ready = it == TextToSpeech.SUCCESS
        if (ready) waiting?.let(::say)
        waiting = null
    } else null
    private val tone: ToneGenerator? = if (sound) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() else null

    init {
        tts?.setAudioAttributes(attributes)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = release(id)
            @Deprecated("Deprecated in Java") override fun onError(id: String?) = release(id)
            // A flushed utterance stops for the next one, which still holds the focus; hush() lets go itself.
            override fun onStop(id: String?, interrupted: Boolean) {}
        })
    }

    /** The id of the latest utterance: only its end gives the music back. */
    @Volatile private var latest = 0

    private fun release(id: String?) {
        if (id == "$latest") audio?.abandonAudioFocusRequest(focus)
    }

    fun play(cue: Cue) {
        if (haptics) vibrator?.vibrate(VibrationEffect.createOneShot(if (cue.words == null) 30 else 50, VibrationEffect.DEFAULT_AMPLITUDE))
        if (!sound) return
        val words = cue.words ?: return run { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90) }
        if (ready) say(words) else waiting = words
    }

    private fun say(words: String) {
        audio?.requestAudioFocus(focus)
        // Flush, so a late word never talks over the next: the count always says the second that is on the clock.
        tts?.speak(words, TextToSpeech.QUEUE_FLUSH, null, "${++latest}")
    }

    /** Cuts off anything still being said: on pause, skip, or leaving the player. */
    fun hush() {
        waiting = null
        tts?.stop()
        audio?.abandonAudioFocusRequest(focus)
    }

    fun close() {
        hush()
        tts?.shutdown()
        tone?.release()
    }
}
