package com.pp.payspeak.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.android.material.button.MaterialButton
import com.pp.payspeak.R
import com.pp.payspeak.services.PaymentAnnouncerService
import com.pp.payspeak.utils.OemAutoStartHelper
import com.pp.payspeak.utils.PreferenceManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import android.widget.ScrollView

class SetupActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager

    private lateinit var btnNotificationAccess: MaterialButton
    private lateinit var btnPostNotifications: MaterialButton
    private lateinit var btnSmsAccess: MaterialButton
    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnBackground: MaterialButton
    private lateinit var btnAutoStart: MaterialButton
    private lateinit var btnContinueHome: MaterialButton
    private lateinit var btnSetupLater: Button
    private lateinit var tvSetupProgress: TextView
    private lateinit var llScrollContent: LinearLayout
    private lateinit var llFooterButtons: LinearLayout
    private lateinit var bottomFooterBar: LinearLayout
    private lateinit var setupScrollView: ScrollView

    // Set true when user taps the OEM autostart button; cleared in onResume after acknowledging.
    // Prevents falsely marking OEM autostart as granted on first screen load.
    private var pendingOemAck = false

    // Computed dynamically so it reflects whether the OEM card is included
    private val totalPermissions get() = cards.size
    private var expandedCardIndex = 0

    /**
     * @param card          root ConstraintLayout of the card
     * @param iconFrame     FrameLayout holding the icon (background changes when granted)
     * @param title         single title TextView (animates position via ConstraintSet)
     * @param desc          description TextView
     * @param button        action button
     * @param collapsedSet  title inline next to icon, desc+button gone
     * @param expandedSet   title below icon, desc+button visible
     * @param grantedSet    title inline, badge gone, check visible
     * @param isGranted     lambda returning current grant state
     */
    private data class CardInfo(
        val card: ConstraintLayout,
        val iconFrame: FrameLayout,
        val title: TextView,
        val desc: TextView,
        val button: MaterialButton,
        val collapsedSet: ConstraintSet,
        val expandedSet: ConstraintSet,
        val grantedSet: ConstraintSet,
        val isGranted: () -> Boolean
    )

    private lateinit var cards: List<CardInfo>

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshButtonStates() }

    private val postNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshButtonStates() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        preferenceManager = PreferenceManager(this)

        initViews()

        // Edge-to-edge: footer paddingBottom = max(design spacing, nav bar inset)
        // Scroll view paddingBottom = actual footer height so last card isn't hidden
        val spacingXxl = resources.getDimensionPixelSize(R.dimen.spacing_xxl)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setupRoot)) { _, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val footerBottomPad = maxOf(spacingXxl, navBar)
            llFooterButtons.setPadding(
                llFooterButtons.paddingStart,
                llFooterButtons.paddingTop,
                llFooterButtons.paddingEnd,
                footerBottomPad
            )
            // After footer re-measures, sync scroll view bottom padding to footer height
            bottomFooterBar.doOnLayout {
                setupScrollView.setPadding(0, 0, 0, bottomFooterBar.height)
            }
            insets
        }

        buildCards()
        setupListeners()
        refreshButtonStates()
    }

    override fun onResume() {
        super.onResume()
        // Only acknowledge for non-MIUI OEM devices AFTER the user explicitly tapped the button
        // and navigated to the OEM settings screen. Without this guard, isGranted() would return
        // true immediately on first load before the user does anything.
        if (pendingOemAck && OemAutoStartHelper.isRequired()) {
            pendingOemAck = false
            // Always acknowledge: for MIUI this acts as fallback if reflection fails;
            // for other OEMs it is the only signal that the user visited settings.
            OemAutoStartHelper.acknowledge(this)
        }
        refreshButtonStates()
    }

    private fun initViews() {
        btnNotificationAccess = findViewById(R.id.btnNotificationAccess)
        btnPostNotifications = findViewById(R.id.btnPostNotifications)
        btnSmsAccess = findViewById(R.id.btnSmsAccess)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnBackground = findViewById(R.id.btnBackground)
        btnAutoStart = findViewById(R.id.btnAutoStart)
        btnContinueHome = findViewById(R.id.btnContinueHome)
        btnSetupLater = findViewById(R.id.btnSetupLater)
        llFooterButtons = findViewById(R.id.llFooterButtons)
        bottomFooterBar = findViewById(R.id.bottomFooterBar)
        setupScrollView = findViewById(R.id.setupScrollView)
        tvSetupProgress = findViewById(R.id.tvSetupProgress)
        llScrollContent = findViewById(R.id.llScrollContent)
    }

    private fun buildCards() {
        val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)

        fun makeCard(
            cardId: Int,
            iconFrameId: Int,
            titleId: Int,
            badgeId: Int,
            checkId: Int,
            descId: Int,
            buttonId: Int,
            button: MaterialButton,
            isGranted: () -> Boolean
        ): CardInfo {
            val card = findViewById<ConstraintLayout>(cardId)
            val iconFrame = findViewById<FrameLayout>(iconFrameId)
            val title = findViewById<TextView>(titleId)
            val desc = findViewById<TextView>(descId)

            // Collapsed: title inline with icon (XML default)
            val collapsedSet = ConstraintSet().apply { clone(card) }

            // Expanded: title below iconFrame full width, desc + button visible
            val expandedSet = ConstraintSet().apply {
                clone(card)
                clear(titleId, ConstraintSet.TOP)
                clear(titleId, ConstraintSet.BOTTOM)
                clear(titleId, ConstraintSet.START)
                clear(titleId, ConstraintSet.END)
                connect(titleId, ConstraintSet.TOP, iconFrameId, ConstraintSet.BOTTOM, spacingSm)
                connect(titleId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                connect(titleId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                setVisibility(descId, View.VISIBLE)
                setVisibility(buttonId, View.VISIBLE)
            }

            // Granted: title inline, badge hidden, check shown
            val grantedSet = ConstraintSet().apply {
                clone(collapsedSet)
                setVisibility(badgeId, View.GONE)
                setVisibility(checkId, View.VISIBLE)
            }

            return CardInfo(card, iconFrame, title, desc, button, collapsedSet, expandedSet, grantedSet, isGranted)
        }

        // Card order: Recommended → Reliability → Optional
        // Recommended tier
        val recommendedCards = mutableListOf(
            makeCard(R.id.cardNotificationAccess, R.id.iconFrameNotificationAccess,
                R.id.tvTitleNotificationAccess, R.id.tvBadgeNotificationAccess,
                R.id.ivCheckNotificationAccess, R.id.tvDescNotificationAccess,
                R.id.btnNotificationAccess, btnNotificationAccess, ::isNotificationListenerGranted)
        )

        // OEM autostart is also Recommended — insert after NotificationAccess
        if (OemAutoStartHelper.isRequired()) {
            findViewById<ConstraintLayout>(R.id.cardAutoStart).visibility = View.VISIBLE
            recommendedCards.add(
                makeCard(R.id.cardAutoStart, R.id.iconFrameAutoStart,
                    R.id.tvTitleAutoStart, R.id.tvBadgeAutoStart,
                    R.id.ivCheckAutoStart, R.id.tvDescAutoStart,
                    R.id.btnAutoStart, btnAutoStart, ::isOemAutoStartGranted)
            )
        }

        // Reliability tier
        val reliabilityCards = mutableListOf(
            makeCard(R.id.cardBackground, R.id.iconFrameBackground,
                R.id.tvTitleBackground, R.id.tvBadgeBackground,
                R.id.ivCheckBackground, R.id.tvDescBackground,
                R.id.btnBackground, btnBackground, ::isBatteryOptimizationDisabled)
        )

        // Optional tier
        val optionalCards = mutableListOf(
            makeCard(R.id.cardPostNotifications, R.id.iconFramePostNotifications,
                R.id.tvTitlePostNotifications, R.id.tvBadgePostNotifications,
                R.id.ivCheckPostNotifications, R.id.tvDescPostNotifications,
                R.id.btnPostNotifications, btnPostNotifications, ::isPostNotificationsGranted),
            makeCard(R.id.cardSmsAccess, R.id.iconFrameSmsAccess,
                R.id.tvTitleSmsAccess, R.id.tvBadgeSmsAccess,
                R.id.ivCheckSmsAccess, R.id.tvDescSmsAccess,
                R.id.btnSmsAccess, btnSmsAccess, ::isSmsGranted),
            makeCard(R.id.cardAccessibility, R.id.iconFrameAccessibility,
                R.id.tvTitleAccessibility, R.id.tvBadgeAccessibility,
                R.id.ivCheckAccessibility, R.id.tvDescAccessibility,
                R.id.btnAccessibility, btnAccessibility, ::isAccessibilityGranted)
        )

        cards = (recommendedCards + reliabilityCards + optionalCards).toMutableList()

        // Physically reorder views in the LinearLayout to match the cards list.
        // The XML defines a fixed order; this overrides it so Recommended cards always appear first.
        // Indices 0 and 1 in llScrollContent are the description text and tip card — skip them.
        cards.forEach { llScrollContent.removeView(it.card) }
        cards.forEachIndexed { i, info -> llScrollContent.addView(info.card, 2 + i) }

        cards.forEachIndexed { index, cardInfo ->
            cardInfo.card.setOnClickListener {
                if (cardInfo.card.tag != "granted") expandCard(index)
            }
        }

        // Initial state: all collapsed, first card expanded (no animation)
        cards.forEach { it.collapsedSet.applyTo(it.card) }
        cards[0].expandedSet.applyTo(cards[0].card)
        expandedCardIndex = 0
    }

    private fun expandCard(targetIndex: Int) {
        if (targetIndex == expandedCardIndex &&
            cards[targetIndex].desc.isVisible) return

        TransitionManager.beginDelayedTransition(
            llScrollContent, TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(ChangeBounds())
                duration = 300
            }
        )

        cards.getOrNull(expandedCardIndex)
            ?.takeIf { it.card.tag != "granted" }
            ?.collapsedSet?.applyTo(cards[expandedCardIndex].card)

        cards[targetIndex].expandedSet.applyTo(cards[targetIndex].card)
        expandedCardIndex = targetIndex
    }

    private fun setupListeners() {
        btnNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btnPostNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        btnSmsAccess.setOnClickListener {
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnBackground.setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
        }
        btnAutoStart.setOnClickListener {
            pendingOemAck = true  // onResume will acknowledge after user returns from settings
            startActivity(OemAutoStartHelper.getAutoStartIntent(this))
        }
        btnContinueHome.setOnClickListener { finishSetup() }
        btnSetupLater.setOnClickListener { finishSetup() }
    }

    private fun refreshButtonStates() {
        var reorderNeeded = false

        cards.forEach { cardInfo ->
            val isGrantedNow = cardInfo.isGranted()
            val wasGranted = cardInfo.card.tag == "granted"

            if (isGrantedNow && !wasGranted) {
                collapseCardToGranted(cardInfo)
                reorderNeeded = true
            } else if (!isGrantedNow && wasGranted) {
                resetCardToUngranted(cardInfo)
                reorderNeeded = true
            }
        }

        if (reorderNeeded) {
            reorderCards()
            val expanded = cards.getOrNull(expandedCardIndex)
            if (expanded == null || expanded.card.tag == "granted") {
                val next = cards.indexOfFirst { it.card.tag != "granted" }
                if (next >= 0) {
                    expandedCardIndex = next
                    cards[next].expandedSet.applyTo(cards[next].card)
                }
            }
        }

        updateProgress()
    }

    private fun collapseCardToGranted(cardInfo: CardInfo) {
        // Apply granted ConstraintSet: title stays inline, badge gone, check appears
        cardInfo.grantedSet.applyTo(cardInfo.card)
        // Shrink padding to make granted cards more compact
        val smallPadding = resources.getDimensionPixelSize(R.dimen.spacing_md)
        cardInfo.card.setPadding(smallPadding, smallPadding, smallPadding, smallPadding)
        cardInfo.card.tag = "granted"
        cardInfo.card.isClickable = false
    }

    private fun resetCardToUngranted(cardInfo: CardInfo) {
        // Apply collapsed ConstraintSet to reset state
        cardInfo.collapsedSet.applyTo(cardInfo.card)
        // Restore standard padding
        val defaultPadding = resources.getDimensionPixelSize(R.dimen.spacing_lg)
        cardInfo.card.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
        cardInfo.card.tag = null
        cardInfo.card.isClickable = true
    }

    private fun reorderCards() {
        TransitionManager.beginDelayedTransition(
            llScrollContent, ChangeBounds().apply { duration = 350 }
        )

        val granted = cards.filter { it.card.tag == "granted" }.map { it.card }
        val ungranted = cards.filter { it.card.tag != "granted" }.map { it.card }

        (granted + ungranted).forEach { llScrollContent.removeView(it) }
        (granted + ungranted).forEachIndexed { i, cardView ->
            llScrollContent.addView(cardView, 2 + i)
        }
    }

    private fun updateProgress() {
        val granted = cards.count { it.isGranted() }
        tvSetupProgress.text = getString(R.string.setup_progress, granted, totalPermissions)

        // Animate footer buttons transition
        TransitionManager.beginDelayedTransition(
            llFooterButtons,
            android.transition.AutoTransition().apply { duration = 300 }
        )

        when (granted) {
            0 -> {
                btnContinueHome.isEnabled = false
                btnSetupLater.visibility = View.VISIBLE
            }
            totalPermissions -> {
                btnContinueHome.isEnabled = true
                btnSetupLater.visibility = View.GONE
            }
            else -> {
                btnContinueHome.isEnabled = true
                btnSetupLater.visibility = View.VISIBLE
            }
        }
    }

    private fun finishSetup() {
        preferenceManager.setSetupCompleted(true)
        startForegroundService(Intent(this, PaymentAnnouncerService::class.java))
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun isNotificationListenerGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun isPostNotificationsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PermissionChecker.PERMISSION_GRANTED
        else true

    private fun isSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PermissionChecker.PERMISSION_GRANTED

    private fun isAccessibilityGranted(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName, ignoreCase = true)
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val pm = getSystemService(android.os.PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isOemAutoStartGranted(): Boolean =
        OemAutoStartHelper.isGranted(this)
}
