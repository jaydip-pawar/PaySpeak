package com.pp.payspeak.utils

import android.util.Log

object DebugLogger {
    private const val TAG = "PaySpeak"
    private var debugEnabled = true

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }

    fun logServiceStart(serviceName: String) {
        if (debugEnabled) {
            Log.d(TAG, "[$serviceName] Service started")
        }
    }

    fun logServiceStop(serviceName: String) {
        if (debugEnabled) {
            Log.d(TAG, "[$serviceName] Service stopped")
        }
    }

    fun logNotificationDetected(text: String, source: String) {
        if (debugEnabled) {
            Log.d(TAG, "[Notification] Detected from $source: ${text.take(100)}")
        }
    }

    fun logAccessibilityDetected(text: String, className: String) {
        if (debugEnabled) {
            Log.d(TAG, "[Accessibility] Detected from $className: ${text.take(100)}")
        }
    }

    fun logSmsDetected(sender: String, message: String) {
        if (debugEnabled) {
            Log.d(TAG, "[SMS] Detected from $sender: ${message.take(100)}")
        }
    }

    fun logExtractionResult(amount: Long, sender: String, appName: String, success: Boolean, confidence: Float) {
        if (debugEnabled) {
            Log.d(TAG, "[Extraction] Amount: $amount, Sender: $sender, App: $appName, Success: $success, Confidence: $confidence")
        }
    }

    fun logDuplicateDetected(amount: Long, sender: String) {
        if (debugEnabled) {
            Log.d(TAG, "[Duplicate] Skipped duplicate payment: Amount=$amount, Sender=$sender")
        }
    }

    fun logLanguageSelection(languageCode: String, announcement: String) {
        if (debugEnabled) {
            Log.d(TAG, "[Language] Selected: $languageCode, Announcement: ${announcement.take(100)}")
        }
    }

    fun logSpeechStart(announcement: String, languageCode: String) {
        if (debugEnabled) {
            Log.d(TAG, "[Speech] Starting TTS in $languageCode: ${announcement.take(100)}")
        }
    }

    fun logSpeechComplete() {
        if (debugEnabled) {
            Log.d(TAG, "[Speech] TTS playback complete")
        }
    }

    fun logSpeechError(message: String, error: Throwable?) {
        Log.e(TAG, "[Speech] Error: $message", error)
    }

    fun logError(component: String, message: String, error: Throwable?) {
        Log.e(TAG, "[$component] Error: $message", error)
    }
}

