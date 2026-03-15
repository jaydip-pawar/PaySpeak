package com.pp.payspeak.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.pp.payspeak.R
import com.pp.payspeak.utils.PreferenceManager

class OnboardingActivity : AppCompatActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private var currentStep = 0
    private val totalSteps = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        preferenceManager = PreferenceManager(this)

        setupUI()
    }

    private fun setupUI() {
        val titleText: TextView = findViewById(R.id.onboardingTitle)
        val descriptionText: TextView = findViewById(R.id.onboardingDescription)
        val actionButton: Button = findViewById(R.id.onboardingActionBtn)
        val skipButton: Button = findViewById(R.id.onboardingSkipBtn)

        updateStepUI(titleText, descriptionText, actionButton)

        actionButton.setOnClickListener {
            handleStepAction()
        }

        skipButton.setOnClickListener {
            currentStep++
            if (currentStep >= totalSteps) {
                finishOnboarding()
            } else {
                updateStepUI(titleText, descriptionText, actionButton)
            }
        }
    }

    private fun updateStepUI(title: TextView, description: TextView, action: Button) {
        when (currentStep) {
            0 -> {
                title.text = "Welcome to PaySpeak"
                description.text = "PaySpeak announces your payment transactions in your preferred language. Let's set up the required permissions."
                action.text = "Get Started"
            }
            1 -> {
                title.text = "Notification Access"
                description.text = "PaySpeak needs notification access to detect payment notifications from apps like Paytm, Google Pay, PhonePe, etc."
                action.text = "Enable Notification Access"
            }
            2 -> {
                title.text = "Accessibility Service"
                description.text = "The accessibility service helps detect payment information from apps more reliably."
                action.text = "Enable Accessibility"
            }
            3 -> {
                title.text = "SMS Permission"
                description.text = "Allow SMS permission to detect bank transaction messages."
                action.text = "Grant SMS Permission"
            }
        }
    }

    private fun handleStepAction() {
        val titleText: TextView = findViewById(R.id.onboardingTitle)
        val descriptionText: TextView = findViewById(R.id.onboardingDescription)
        val actionButton: Button = findViewById(R.id.onboardingActionBtn)

        when (currentStep) {
            0 -> {
                currentStep++
                updateStepUI(titleText, descriptionText, actionButton)
            }
            1 -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                preferenceManager.setNotificationListenerEnabled(true)
                currentStep++
                updateStepUI(titleText, descriptionText, actionButton)
            }
            2 -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                preferenceManager.setAccessibilityEnabled(true)
                currentStep++
                updateStepUI(titleText, descriptionText, actionButton)
            }
            3 -> {
                requestSmsPermission()
            }
        }
    }

    private fun requestSmsPermission() {
        val titleText: TextView = findViewById(R.id.onboardingTitle)
        val descriptionText: TextView = findViewById(R.id.onboardingDescription)
        val actionButton: Button = findViewById(R.id.onboardingActionBtn)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 100)
        } else {
            preferenceManager.setSmsPermissionEnabled(true)
            finishOnboarding()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                preferenceManager.setSmsPermissionEnabled(true)
                Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SMS permission denied - you can enable it later", Toast.LENGTH_SHORT).show()
            }
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        preferenceManager.setOnboardingCompleted(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

