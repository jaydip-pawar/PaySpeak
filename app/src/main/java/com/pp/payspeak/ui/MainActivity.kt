package com.pp.payspeak.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pp.payspeak.R
import com.pp.payspeak.core.language.Language
import com.pp.payspeak.core.language.LanguageManager
import com.pp.payspeak.services.PaymentAnnouncerService
import com.pp.payspeak.utils.PreferenceManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var languageManager: LanguageManager
    private lateinit var preferenceManager: PreferenceManager
    private var isServiceToggling = false

    private val permissionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { updateStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager = PreferenceManager(this)

        if (!preferenceManager.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        languageManager = LanguageManager()
        languageManager.setLanguage(preferenceManager.getLanguage())

        setupUI()
    }

    private fun setupUI() {
        val statusText: TextView = findViewById(R.id.statusText)
        val serviceSwitch: Switch = findViewById(R.id.serviceSwitch)
        val notificationAccessBtn: Button = findViewById(R.id.notificationAccessBtn)
        val accessibilitySettingsBtn: Button = findViewById(R.id.accessibilitySettingsBtn)
        val languageSpinner: Spinner = findViewById(R.id.languageSpinner)
        val settingsBtn: Button = findViewById(R.id.settingsButton)

        serviceSwitch.isChecked = preferenceManager.isServiceEnabled()
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isServiceToggling) return@setOnCheckedChangeListener

            isServiceToggling = true
            lifecycleScope.launch {
                try {
                    if (isChecked) {
                        startPaymentAnnouncerService()
                        preferenceManager.setServiceEnabled(true)
                        showToast("PaySpeak service started ✓")
                    } else {
                        stopService(Intent(this@MainActivity, PaymentAnnouncerService::class.java))
                        preferenceManager.setServiceEnabled(false)
                        showToast("PaySpeak service stopped")
                    }
                } finally {
                    isServiceToggling = false
                }
                updateStatus()
            }
        }

        val languageOptions = Language.values().map { it.displayName }.toTypedArray()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageOptions)
        languageSpinner.adapter = adapter

        val currentLanguage = preferenceManager.getLanguage()
        val position = Language.values().indexOf(currentLanguage)
        languageSpinner.setSelection(position)

        languageSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val language = Language.values()[position]
                preferenceManager.setLanguage(language)
                languageManager.setLanguage(language)
                showToast("Language changed to ${language.displayName}")
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        notificationAccessBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        accessibilitySettingsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateStatus()
    }

    private fun startPaymentAnnouncerService() {
        val intent = Intent(this, PaymentAnnouncerService::class.java)
        startForegroundService(intent)
    }

    private fun updateStatus() {
        val statusText: TextView = findViewById(R.id.statusText)
        val isEnabled = preferenceManager.isServiceEnabled()
        val notificationEnabled = isNotificationListenerGranted()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val smsEnabled = isSmsPermissionGranted()

        val statusMessage = buildString {
            append("Service: ${if (isEnabled) "✓ Running" else "○ Stopped"}\n")
            append("Notification: ${if (notificationEnabled) "✓" else "○"} | ")
            append("Accessibility: ${if (accessibilityEnabled) "✓" else "○"} | ")
            append("SMS: ${if (smsEnabled) "✓" else "○"}\n\n")
            if (!notificationEnabled || !accessibilityEnabled) {
                append("⚠ Enable missing permissions for full functionality")
            } else {
                append("✓ All permissions enabled - ready to go!")
            }
        }
        statusText.text = statusMessage
    }

    /** Checks the real system state — not a cached flag — so the UI is always accurate. */
    private fun isNotificationListenerGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun isAccessibilityServiceEnabled(): Boolean {
        val setting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return setting?.contains(packageName) == true
    }

    private fun isSmsPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("enabled_notification_listeners"), false, permissionObserver)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, permissionObserver)
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(permissionObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
