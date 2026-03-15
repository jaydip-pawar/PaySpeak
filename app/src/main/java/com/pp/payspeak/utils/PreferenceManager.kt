package com.pp.payspeak.utils

import android.content.Context
import android.content.SharedPreferences
import com.pp.payspeak.core.language.Language

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("com.pp.payspeak.preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_ENABLED = "service_enabled"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_NOTIFICATION_LISTENER_ENABLED = "notification_listener"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val KEY_SMS_PERMISSION_ENABLED = "sms_permission"
    }

    fun getLanguage(): Language {
        val languageCode = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        return Language.values().find { it.code == languageCode } ?: Language.ENGLISH
    }

    fun setLanguage(language: Language) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    fun getTTSSpeed(): Float = prefs.getFloat(KEY_TTS_SPEED, 1.0f).coerceIn(0.5f, 2.0f)
    fun setTTSSpeed(speed: Float) { prefs.edit().putFloat(KEY_TTS_SPEED, speed.coerceIn(0.5f, 2.0f)).apply() }

    fun getTTSPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 1.0f).coerceIn(0.5f, 2.0f)
    fun setTTSPitch(pitch: Float) { prefs.edit().putFloat(KEY_TTS_PITCH, pitch.coerceIn(0.5f, 2.0f)).apply() }

    fun getVoiceVolume(): Int = prefs.getInt(KEY_VOICE_VOLUME, 80).coerceIn(0, 100)
    fun setVoiceVolume(volume: Int) { prefs.edit().putInt(KEY_VOICE_VOLUME, volume.coerceIn(0, 100)).apply() }

    fun isServiceEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setServiceEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_ENABLED, enabled).apply() }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    fun setOnboardingCompleted(completed: Boolean) { prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply() }

    fun isNotificationListenerEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATION_LISTENER_ENABLED, false)
    fun setNotificationListenerEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_NOTIFICATION_LISTENER_ENABLED, enabled).apply() }

    fun isAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
    fun setAccessibilityEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, enabled).apply() }

    fun isSmsPermissionEnabled(): Boolean = prefs.getBoolean(KEY_SMS_PERMISSION_ENABLED, false)
    fun setSmsPermissionEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SMS_PERMISSION_ENABLED, enabled).apply() }

    fun clearAll() { prefs.edit().clear().apply() }
}

