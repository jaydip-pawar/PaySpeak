package com.pp.payspeak.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.pp.payspeak.R
import com.pp.payspeak.utils.OemAutoStartHelper

class HomeFragment : Fragment() {

    // ── Views ──────────────────────────────────────────────────────────────
    private lateinit var vPulseRipple: View
    private lateinit var tvStatusSubtitle: View
    private lateinit var llActionRequired: View
    private lateinit var llPermissionCards: LinearLayout

    // Chips
    private lateinit var chipNotification: LinearLayout
    private lateinit var chipSms: LinearLayout
    private lateinit var chipAccessibility: LinearLayout
    private lateinit var ivChipNotifIcon: ImageView
    private lateinit var tvChipNotif: TextView
    private lateinit var ivChipSmsIcon: ImageView
    private lateinit var tvChipSms: TextView
    private lateinit var ivChipAccessIcon: ImageView
    private lateinit var tvChipAccessibility: TextView

    // ── Permission info ────────────────────────────────────────────────────
    private data class PermissionInfo(
        val titleRes: Int,
        val descRes: Int,
        val iconRes: Int,
        val isGranted: () -> Boolean,
        val onFix: () -> Unit
    )

    private lateinit var permissions: List<PermissionInfo>
    private var pulseAnimator: ObjectAnimator? = null
    private val dismissedIndices = mutableSetOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        buildPermissions()
        startPulseAnimation()
    }

    override fun onResume() {
        super.onResume()
        dismissedIndices.clear()  // reset dismiss state on every resume
        updatePermissionCard()
        updateChips()
    }

    override fun onDestroyView() {
        pulseAnimator?.cancel()
        super.onDestroyView()
    }

    // ── Bind ───────────────────────────────────────────────────────────────
    private fun bindViews(v: View) {
        vPulseRipple = v.findViewById(R.id.vPulseRipple)
        tvStatusSubtitle = v.findViewById(R.id.tvStatusSubtitle)
        llActionRequired = v.findViewById(R.id.llActionRequired)
        llPermissionCards = v.findViewById(R.id.llPermissionCards)

        chipNotification = v.findViewById(R.id.chipNotification)
        chipSms = v.findViewById(R.id.chipSms)
        chipAccessibility = v.findViewById(R.id.chipAccessibility)
        ivChipNotifIcon = v.findViewById(R.id.ivChipNotifIcon)
        tvChipNotif = v.findViewById(R.id.tvChipNotif)
        ivChipSmsIcon = v.findViewById(R.id.ivChipSmsIcon)
        tvChipSms = v.findViewById(R.id.tvChipSms)
        ivChipAccessIcon = v.findViewById(R.id.ivChipAccessIcon)
        tvChipAccessibility = v.findViewById(R.id.tvChipAccessibility)
    }

    // ── Build permission list ──────────────────────────────────────────────
    private fun buildPermissions() {
        permissions = listOf(
            PermissionInfo(
                titleRes = R.string.home_perm_notif_access_title,
                descRes = R.string.home_perm_notif_access_desc,
                iconRes = R.drawable.ic_notifications_active,
                isGranted = ::isNotificationListenerGranted,
                onFix = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            ),
            PermissionInfo(
                titleRes = R.string.home_perm_post_notif_title,
                descRes = R.string.home_perm_post_notif_desc,
                iconRes = R.drawable.ic_notifications,
                isGranted = ::isPostNotificationsGranted,
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${requireContext().packageName}")
                            }
                        )
                    }
                }
            ),
            PermissionInfo(
                titleRes = R.string.home_perm_sms_title,
                descRes = R.string.home_perm_sms_desc,
                iconRes = R.drawable.ic_sms,
                isGranted = ::isSmsGranted,
                onFix = {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${requireContext().packageName}")
                        }
                    )
                }
            ),
            PermissionInfo(
                titleRes = R.string.home_perm_accessibility_title,
                descRes = R.string.home_perm_accessibility_desc,
                iconRes = R.drawable.ic_accessibility,
                isGranted = ::isAccessibilityGranted,
                onFix = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            ),
            PermissionInfo(
                titleRes = R.string.home_perm_battery_title,
                descRes = R.string.home_perm_battery_desc,
                iconRes = R.drawable.ic_bolt,
                isGranted = ::isBatteryOptimizationDisabled,
                onFix = {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${requireContext().packageName}")
                        }
                    )
                }
            )
        )
        // Only show the OEM autostart card on devices that actually enforce the restriction.
        // On stock Android there is no such setting and showing the card would confuse users.
        if (OemAutoStartHelper.isRequired()) {
            permissions = permissions + PermissionInfo(
                titleRes = R.string.home_perm_autostart_title,
                descRes = R.string.home_perm_autostart_desc,
                iconRes = R.drawable.ic_bolt,
                isGranted = { OemAutoStartHelper.isGranted(requireContext()) },
                onFix = {
                    startActivity(OemAutoStartHelper.getAutoStartIntent(requireContext()))
                }
            )
        }
    }

    // ── Animation ──────────────────────────────────────────────────────────
    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            vPulseRipple,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 2.0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 2.0f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f)
        ).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 300
        }
        pulseAnimator!!.start()
    }

    // ── Update permission card ─────────────────────────────────────────────
    private fun updatePermissionCard() {
        val totalCount = permissions.size
        val grantedCount = permissions.count { it.isGranted() }
        val anyMissing = grantedCount < totalCount

        // Hide everything if all granted
        if (!anyMissing) {
            llActionRequired.visibility = View.GONE
            llPermissionCards.visibility = View.GONE
            llPermissionCards.removeAllViews()
            (tvStatusSubtitle as? TextView)?.text =
                getString(R.string.home_status_subtitle_all_active)
            return
        }

        // Action required header
        llActionRequired.visibility = View.VISIBLE
        (tvStatusSubtitle as? TextView)?.text = ""

        // Build a card for each missing permission (excluding dismissed ones)
        val pending = permissions.withIndex()
            .filter { !it.value.isGranted() && it.index !in dismissedIndices }
        
        if (pending.isEmpty()) {
            llActionRequired.visibility = View.GONE
            llPermissionCards.visibility = View.GONE
            llPermissionCards.removeAllViews()
            (tvStatusSubtitle as? TextView)?.text = ""
            return
        }

        llPermissionCards.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val cards = mutableListOf<View>()

        for ((cardIndex, indexedPerm) in pending.withIndex()) {
            val perm = indexedPerm.value
            val permIndex = indexedPerm.index
            val card = inflater.inflate(R.layout.item_permission_fix_card, llPermissionCards, false)
            card.findViewById<ImageView>(R.id.ivPermIcon).setImageResource(perm.iconRes)
            card.findViewById<TextView>(R.id.tvPermTitle).setText(perm.titleRes)
            card.findViewById<TextView>(R.id.tvPermDesc).setText(perm.descRes)
            card.findViewById<MaterialButton>(R.id.btnPermFixNow).setOnClickListener { perm.onFix() }
            card.findViewById<Button>(R.id.btnPermLater).setOnClickListener {
                dismissedIndices.add(permIndex)
                // Phase 1: slide left + fade out
                card.animate()
                    .alpha(0f)
                    .translationX(-card.width.toFloat())
                    .setDuration(250)
                    .withEndAction {
                        // Phase 2: smoothly collapse the card's height to 0
                        val startHeight = card.measuredHeight
                        val heightAnim = ValueAnimator.ofInt(startHeight, 0).apply {
                            duration = 200
                            addUpdateListener { anim ->
                                card.layoutParams.height = anim.animatedValue as Int
                                card.requestLayout()
                            }
                            addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    cards.remove(card)
                                    llPermissionCards.removeView(card)
                                    // If no cards left, hide the entire section
                                    if (llPermissionCards.childCount == 0) {
                                        llActionRequired.animate()
                                            .alpha(0f).setDuration(200)
                                            .withEndAction {
                                                llActionRequired.visibility = View.GONE
                                                llActionRequired.alpha = 1f
                                                llPermissionCards.visibility = View.GONE
                                                (tvStatusSubtitle as? TextView)?.text = ""
                                            }.start()
                                    } else {
                                        // Auto-expand the first remaining card if none is expanded
                                        val anyExpanded = cards.any {
                                            it.findViewById<View>(R.id.llCardBody).visibility == View.VISIBLE
                                        }
                                        if (!anyExpanded && cards.isNotEmpty()) {
                                            val first = cards[0]
                                            first.findViewById<View>(R.id.llCardBody).visibility = View.VISIBLE
                                            first.findViewById<ImageView>(R.id.ivExpandArrow).rotation = 180f
                                        }
                                    }
                                }
                            })
                        }
                        heightAnim.start()
                    }.start()
            }

            val body = card.findViewById<View>(R.id.llCardBody)
            val arrow = card.findViewById<ImageView>(R.id.ivExpandArrow)

            // First card expanded, rest collapsed
            if (cardIndex == 0) {
                body.visibility = View.VISIBLE
                arrow.rotation = 180f
            } else {
                body.visibility = View.GONE
                arrow.rotation = 0f
            }

            card.findViewById<View>(R.id.llCardHeader).setOnClickListener {
                val isExpanded = body.visibility == View.VISIBLE
                if (isExpanded) {
                    // Collapse this card
                    body.visibility = View.GONE
                    arrow.rotation = 0f
                } else {
                    // Collapse all others first
                    for (other in cards) {
                        val otherBody = other.findViewById<View>(R.id.llCardBody)
                        val otherArrow = other.findViewById<ImageView>(R.id.ivExpandArrow)
                        if (otherBody.visibility == View.VISIBLE) {
                            otherBody.visibility = View.GONE
                            otherArrow.rotation = 0f
                        }
                    }
                    // Expand this card
                    body.visibility = View.VISIBLE
                    arrow.rotation = 180f
                }
            }

            cards.add(card)
            llPermissionCards.addView(card)
        }
        llPermissionCards.visibility = View.VISIBLE
    }

    // ── Update chips ───────────────────────────────────────────────────────
    private fun updateChips() {
        val notifGranted = isNotificationListenerGranted()
        val smsGranted = isSmsGranted()
        val accessGranted = isAccessibilityGranted()

        applyChipState(
            chipNotification, ivChipNotifIcon, tvChipNotif,
            granted = notifGranted,
            grantedLabel = getString(R.string.home_chip_notifications) + " ✓",
            deniedLabel = getString(R.string.home_chip_notifications) + " ✕"
        )
        applyChipState(
            chipSms, ivChipSmsIcon, tvChipSms,
            granted = smsGranted,
            grantedLabel = getString(R.string.home_chip_sms) + " ✓",
            deniedLabel = getString(R.string.home_chip_sms) + " ✕"
        )
        applyChipState(
            chipAccessibility, ivChipAccessIcon, tvChipAccessibility,
            granted = accessGranted,
            grantedLabel = getString(R.string.home_chip_accessibility) + " ✓",
            deniedLabel = getString(R.string.home_chip_accessibility) + " Off"
        )
    }

    private fun applyChipState(
        chip: LinearLayout, icon: ImageView, label: TextView,
        granted: Boolean, grantedLabel: String, deniedLabel: String
    ) {
        chip.setBackgroundResource(
            if (granted) R.drawable.bg_chip_active else R.drawable.bg_chip_inactive
        )
        val tintColor = if (granted)
            ContextCompat.getColor(requireContext(), R.color.md_theme_secondary)
        else
            ContextCompat.getColor(requireContext(), R.color.md_theme_errorSoft)
        icon.setColorFilter(tintColor)
        label.text = if (granted) grantedLabel else deniedLabel
        label.setTextColor(
            if (granted)
                ContextCompat.getColor(requireContext(), R.color.md_theme_secondary)
            else
                ContextCompat.getColor(requireContext(), R.color.md_theme_errorSoft)
        )
    }

    // ── Permission checks ──────────────────────────────────────────────────
    private fun isNotificationListenerGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)

    private fun isPostNotificationsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PermissionChecker.PERMISSION_GRANTED
        else true

    private fun isSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECEIVE_SMS
        ) == PermissionChecker.PERMISSION_GRANTED

    private fun isAccessibilityGranted(): Boolean {
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(requireContext().packageName, ignoreCase = true)
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val pm = requireContext().getSystemService(android.os.PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(requireContext().packageName)
    }
}
