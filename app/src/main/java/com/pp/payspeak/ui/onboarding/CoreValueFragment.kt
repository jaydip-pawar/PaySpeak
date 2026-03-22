package com.pp.payspeak.ui.onboarding

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pp.payspeak.R

class CoreValueFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding_core_value, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        styleTitleText(view.findViewById(R.id.tvCoreValueTitle))
    }

    private fun styleTitleText(textView: TextView) {
        val title = getString(R.string.onboarding_core_value_title)
        val highlight = getString(R.string.onboarding_core_value_highlight)
        val spannable = SpannableString(title)
        val start = title.indexOf(highlight)
        if (start >= 0) {
            val color = ContextCompat.getColor(requireContext(), R.color.md_theme_secondary)
            spannable.setSpan(
                ForegroundColorSpan(color),
                start,
                start + highlight.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textView.text = spannable
    }
}
