package com.pp.payspeak.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.pp.payspeak.R
import com.pp.payspeak.services.PaymentAnnouncerService
import com.pp.payspeak.ui.onboarding.OnboardingPagerAdapter
import com.pp.payspeak.utils.PreferenceManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var viewPager: ViewPager2
    private lateinit var btnBack: ImageView
    private lateinit var tvSkip: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var dots: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        preferenceManager = PreferenceManager(this)

        initViews()
        setupViewPager()
        setupListeners()
        updateDots(0)
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        btnBack = findViewById(R.id.btnBack)
        tvSkip = findViewById(R.id.tvSkip)
        btnAction = findViewById(R.id.btnAction)
        dots = listOf(
            findViewById(R.id.dot0),
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3)
        )
    }

    private fun setupViewPager() {
        viewPager.adapter = OnboardingPagerAdapter(this)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                btnBack.visibility = if (position > 0) View.VISIBLE else View.GONE
                btnAction.text = if (position == 3) getString(R.string.onboarding_get_started)
                                 else getString(R.string.onboarding_continue)
            }
        })
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            val current = viewPager.currentItem
            if (current > 0) viewPager.currentItem = current - 1
        }

        tvSkip.setOnClickListener { finishOnboarding() }

        btnAction.setOnClickListener {
            val current = viewPager.currentItem
            if (current < 3) {
                viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updateDots(position: Int) {
        val activeWidth = resources.getDimensionPixelSize(R.dimen.dot_active_width)
        val inactiveWidth = resources.getDimensionPixelSize(R.dimen.dot_inactive_width)

        dots.forEachIndexed { i, dot ->
            val params = dot.layoutParams as LinearLayout.LayoutParams
            if (i == position) {
                params.width = activeWidth
                dot.setBackgroundResource(R.drawable.bg_dot_active)
            } else {
                params.width = inactiveWidth
                dot.setBackgroundResource(R.drawable.bg_dot_inactive)
            }
            dot.layoutParams = params
        }
    }

    private fun finishOnboarding() {
        preferenceManager.setOnboardingCompleted(true)
        startForegroundService(Intent(this, PaymentAnnouncerService::class.java))
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
