package com.pp.payspeak.core.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SpeechEngine"

class SpeechEngine(private val context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private data class SpeechTask(val text: String, val language: String)
    private val speechQueue = LinkedBlockingQueue<SpeechTask>()
    private var speechRate: Float = 0.9f
    private var pitch: Float = 1.0f
    private var volume: Float = 1.0f
    private var initializeAttempts = 0
    private val maxInitializeAttempts = 3
    @Volatile private var isInitializing = false
    private val isSpeaking = AtomicBoolean(false)
    @Volatile private var isReleased = false
    private var previousMusicVolume: Int? = null

    init { initialize() }

    private fun initialize() {
        if (isReleased) {
            Log.d(TAG, "initialize: engine already released, skipping")
            return
        }
        if (isInitializing) {
            Log.d(TAG, "initialize: already in progress, skipping")
            return
        }
        isInitializing = true
        isReady = false
        Log.d(TAG, "Initializing speech engine (attempt ${initializeAttempts + 1}/$maxInitializeAttempts)")
        try {
            textToSpeech = TextToSpeech(context) { status ->
                try {
                    isInitializing = false
                    if (status == TextToSpeech.SUCCESS) {
                        textToSpeech?.let { tts ->
                            tts.setSpeechRate(speechRate)
                            tts.setPitch(pitch)
                            tts.language = Locale.ENGLISH
                            isReady = true
                            initializeAttempts = 0
                            Log.d(TAG, "TextToSpeech initialized successfully")
                        }
                    } else {
                        Log.e(TAG, "TextToSpeech initialization failed with status: $status")
                        retryInitialize()
                    }
                } catch (e: Exception) {
                    isInitializing = false
                    Log.e(TAG, "Error in TTS initialization callback", e)
                    retryInitialize()
                }
            }
        } catch (e: Exception) {
            isInitializing = false
            Log.e(TAG, "Error creating TextToSpeech instance", e)
            retryInitialize()
        }

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PaySpeak::SpeechEngine")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    private fun retryInitialize() {
        if (initializeAttempts >= maxInitializeAttempts) {
            Log.e(TAG, "TTS init permanently failed after $maxInitializeAttempts attempts — speech disabled")
            return
        }
        initializeAttempts++
        Handler(Looper.getMainLooper()).postDelayed({
            isInitializing = false
            if (!isReleased) initialize()
        }, 500)
    }

    fun announcePayment(announcement: String, language: String = "en", onComplete: (() -> Unit)? = null) {
        if (!isReady) {
            Log.w(TAG, "Speech engine not ready, skipping announcement")
            onComplete?.invoke()
            return
        }
        speechQueue.offer(SpeechTask(announcement, language))
        processQueue(onComplete)
    }

    private fun processQueue(onComplete: (() -> Unit)?) {
        if (!isSpeaking.compareAndSet(false, true)) {
            Log.d(TAG, "processQueue: already speaking — task queued, will process after current finishes")
            return
        }
        val task = speechQueue.poll()
        if (task == null) {
            Log.d(TAG, "processQueue: queue is empty")
            isSpeaking.set(false)
            return
        }
        try {
            Log.d(TAG, "Announcing: ${task.text} in language: ${task.language}")
            boostVolumeToMax()
            wakeLock?.acquire(10000)
            requestAudioFocus()
            val locale = when (task.language) {
                "hi" -> Locale("hi", "IN")
                "mr" -> Locale("mr", "IN")
                else -> Locale.ENGLISH
            }
            textToSpeech?.let { tts ->
                val langResult = tts.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Language $locale not available (result=$langResult) — falling back to English")
                    tts.setLanguage(Locale.ENGLISH)
                } else {
                    Log.d(TAG, "TTS language set to $locale (result=$langResult)")
                }
                tts.setPitch(pitch)
                tts.setSpeechRate(speechRate)
                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) { Log.d(TAG, "Speech playback started") }
                    override fun onDone(utteranceId: String) {
                        Log.d(TAG, "Announcement complete")
                        onComplete?.invoke()
                        finishCurrent()
                    }
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "Speech playback error")
                        onComplete?.invoke()
                        finishCurrent()
                    }
                })
                val params = android.os.Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "payment_announcement_${System.currentTimeMillis()}")
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                }
                tts.speak(task.text, TextToSpeech.QUEUE_ADD, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
            } ?: run {
                Log.e(TAG, "TTS instance is null at speak time — dropping: '${task.text}'")
                finishCurrent()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error announcing payment", e)
            onComplete?.invoke()
            finishCurrent()
        }
    }

    private fun finishCurrent() {
        isSpeaking.set(false)
        restoreVolume()
        abandonAudioFocus()
        wakeLock?.release()
        processQueue(null)
    }

    private fun boostVolumeToMax() {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            previousMusicVolume = current
            if (current < max) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error boosting volume", e)
        }
    }

    private fun restoreVolume() {
        try {
            previousMusicVolume?.let { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring volume", e)
        } finally {
            previousMusicVolume = null
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                val result = audioManager.requestAudioFocus(focusRequest)
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(TAG, "Audio focus granted")
                    audioFocusRequest = focusRequest
                } else {
                    Log.w(TAG, "Audio focus denied (result=$result) — speech may not be heard")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
    }

    fun stop() {
        Log.d(TAG, "stop() — clearing queue and halting TTS")
        textToSpeech?.stop()
        speechQueue.clear()
        // TTS-02: if we were speaking, restore audio state that processQueue set up.
        // Without this, volume stays at max and audio focus is never abandoned
        // (e.g. navigation apps remain ducked) if the service is killed mid-speech.
        val wasSpeaking = isSpeaking.getAndSet(false)
        if (wasSpeaking) {
            restoreVolume()
            abandonAudioFocus()
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
    }

    fun release() {
        Log.d(TAG, "release() — shutting down TTS engine")
        // LIFECYCLE-04: set flag first so any in-flight retryInitialize() callbacks abort
        isReleased = true
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        isReady = false
    }

    fun isReady(): Boolean = isReady
    fun setSpeechRate(rate: Float) { speechRate = rate }
    fun setPitch(p: Float) { pitch = p }
    fun setVolume(v: Float) { volume = v.coerceIn(0.0f, 1.0f) }
}
