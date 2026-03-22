package com.pp.payspeak.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.pp.payspeak.R

class HowItWorksFragment : Fragment() {

    private lateinit var containerNotifications: FrameLayout
    private lateinit var iconNotifications: ImageView
    private lateinit var switchNotifications: MaterialSwitch

    private lateinit var containerSms: FrameLayout
    private lateinit var iconSms: ImageView
    private lateinit var switchSms: MaterialSwitch

    private lateinit var containerAccessibility: FrameLayout
    private lateinit var iconAccessibility: ImageView
    private lateinit var switchAccessibility: MaterialSwitch

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val granted = isSmsGranted()
        applyIconState(containerSms, iconSms, granted)
        switchSms.isChecked = granted
        if (granted) switchSms.isEnabled = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding_how_it_works, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        containerNotifications = view.findViewById(R.id.containerNotificationsIcon)
        iconNotifications = view.findViewById(R.id.iconNotifications)
        switchNotifications = view.findViewById(R.id.switchNotifications)

        containerSms = view.findViewById(R.id.containerSmsIcon)
        iconSms = view.findViewById(R.id.iconSms)
        switchSms = view.findViewById(R.id.switchSms)

        containerAccessibility = view.findViewById(R.id.containerAccessibilityIcon)
        iconAccessibility = view.findViewById(R.id.iconAccessibility)
        switchAccessibility = view.findViewById(R.id.switchAccessibility)

        setupToggles()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        if (::switchNotifications.isInitialized) refreshState()
    }

    private fun refreshState() {
        val notifGranted = isNotificationListenerGranted()
        val smsGranted = isSmsGranted()
        val accessGranted = isAccessibilityGranted()

        applyIconState(containerNotifications, iconNotifications, notifGranted)
        applyIconState(containerSms, iconSms, smsGranted)
        applyIconState(containerAccessibility, iconAccessibility, accessGranted)

        switchNotifications.isChecked = notifGranted
        switchSms.isChecked = smsGranted
        switchAccessibility.isChecked = accessGranted

        if (notifGranted) switchNotifications.isEnabled = false
        if (smsGranted) switchSms.isEnabled = false
        if (accessGranted) switchAccessibility.isEnabled = false
    }

    private fun setupToggles() {
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isNotificationListenerGranted()) {
                // Notification Listener is a special permission — must be granted in Settings
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else if (!isChecked && isNotificationListenerGranted()) {
                switchNotifications.isChecked = true
            }
        }

        switchSms.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isSmsGranted()) {
                smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            } else if (!isChecked && isSmsGranted()) {
                switchSms.isChecked = true
            }
        }

        switchAccessibility.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAccessibilityGranted()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else if (!isChecked && isAccessibilityGranted()) {
                switchAccessibility.isChecked = true
            }
        }
    }

    private fun applyIconState(container: FrameLayout, icon: ImageView, granted: Boolean) {
        if (granted) {
            container.setBackgroundResource(R.drawable.bg_icon_rounded_green)
            icon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.md_theme_onSecondaryContainer)
            )
        } else {
            container.setBackgroundResource(R.drawable.bg_icon_rounded_white)
            icon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.md_theme_secondary)
            )
        }
    }

    /** Checks if the app has Notification Listener access (to read notifications). */
    private fun isNotificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(
            requireContext().contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(requireContext().packageName, ignoreCase = true)
    }

    private fun isSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS) ==
                PermissionChecker.PERMISSION_GRANTED

    private fun isAccessibilityGranted(): Boolean {
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(requireContext().packageName, ignoreCase = true)
    }
}

