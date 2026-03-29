package com.pp.payspeak.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pp.payspeak.R
import com.pp.payspeak.services.PaymentAnnouncerService
import com.pp.payspeak.utils.PreferenceManager

class MainActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnHeaderSettings: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager = PreferenceManager(this)

        if (!preferenceManager.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        if (!preferenceManager.isSetupCompleted()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Handle edge-to-edge insets: top padding on header, bottom on nav
        val appHeader = findViewById<View>(R.id.appHeader)
        val bottomNavView = findViewById<BottomNavigationView>(R.id.bottomNav)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appHeader.setPadding(
                appHeader.paddingStart,
                systemBars.top,
                appHeader.paddingEnd,
                appHeader.paddingBottom
            )
            bottomNavView.setPadding(
                0, 0, 0, systemBars.bottom
            )
            insets
        }

        bottomNav = bottomNavView
        btnHeaderSettings = findViewById(R.id.btnHeaderSettings)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_activity -> ActivityFragment()
                R.id.nav_voice -> VoiceFragment()
                R.id.nav_settings -> SettingsHomeFragment()
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }

        btnHeaderSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        startPaymentAnnouncerService()
        if (isNotificationListenerGranted()) {
            NotificationListenerService.requestRebind(
                ComponentName(this, com.pp.payspeak.services.NotificationListenerServiceImpl::class.java)
            )
        }
    }

    private fun startPaymentAnnouncerService() {
        startForegroundService(Intent(this, PaymentAnnouncerService::class.java))
    }

    private fun isNotificationListenerGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
