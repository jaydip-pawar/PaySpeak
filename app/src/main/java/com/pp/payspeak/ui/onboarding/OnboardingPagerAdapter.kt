package com.pp.payspeak.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> CoreValueFragment()
        1 -> HowItWorksFragment()
        2 -> PrivacyFragment()
        3 -> ImproveDetectionFragment()
        else -> CoreValueFragment()
    }
}
