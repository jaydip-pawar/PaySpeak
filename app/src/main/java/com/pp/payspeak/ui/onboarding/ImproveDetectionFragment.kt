package com.pp.payspeak.ui.onboarding

import android.graphics.drawable.PictureDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.caverock.androidsvg.SVG
import com.google.android.material.materialswitch.MaterialSwitch
import com.pp.payspeak.R
import com.pp.payspeak.utils.PreferenceManager

class ImproveDetectionFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding_improve_detection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preferenceManager = PreferenceManager(requireContext())
        val switchImprove = view.findViewById<MaterialSwitch>(R.id.switchImproveDetection)
        switchImprove.isChecked = preferenceManager.isImproveDetectionEnabled()
        switchImprove.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setImproveDetectionEnabled(isChecked)
        }

        val ivVisualization = view.findViewById<ImageView>(R.id.ivSensitiveVisualization)
        try {
            val svg = SVG.getFromResource(requireContext(), R.raw.sensitive_visualization)
            val drawable = PictureDrawable(svg.renderToPicture())
            ivVisualization.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            ivVisualization.setImageDrawable(drawable)
        } catch (_: Exception) {
            ivVisualization.visibility = View.GONE
        }
    }
}
