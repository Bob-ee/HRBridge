package com.example.runh10.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceCoach(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                ready = true
            }
        }
    }

    fun say(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null; ready = false }
}
