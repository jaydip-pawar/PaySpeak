package com.pp.payspeak.ui

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pp.payspeak.R
import com.pp.payspeak.core.speech.SpeechEngine
import com.pp.payspeak.utils.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private var speechEngine: SpeechEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferenceManager = PreferenceManager(this)
        speechEngine = SpeechEngine(this)

        setupUI()
    }

    private fun setupUI() {
        val speedSeekBar: SeekBar = findViewById(R.id.speedSeekBar)
        val speedValue: TextView = findViewById(R.id.speedValue)
        val pitchSeekBar: SeekBar = findViewById(R.id.pitchSeekBar)
        val pitchValue: TextView = findViewById(R.id.pitchValue)
        val volumeSeekBar: SeekBar = findViewById(R.id.volumeSeekBar)
        val volumeValue: TextView = findViewById(R.id.volumeValue)
        val testButton: Button = findViewById(R.id.testSpeechBtn)
        val resetButton: Button = findViewById(R.id.resetSettingsBtn)

        // Speech Speed (0.5 to 2.0)
        val currentSpeed = preferenceManager.getTTSSpeed()
        speedSeekBar.max = 150
        speedSeekBar.progress = ((currentSpeed - 0.5f) * 100).toInt()
        speedValue.text = String.format("%.1fx", currentSpeed)

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress / 100f)
                speedValue.text = String.format("%.1fx", speed)
                preferenceManager.setTTSSpeed(speed)
                speechEngine?.setSpeechRate(speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Speech Pitch (0.5 to 2.0)
        val currentPitch = preferenceManager.getTTSPitch()
        pitchSeekBar.max = 150
        pitchSeekBar.progress = ((currentPitch - 0.5f) * 100).toInt()
        pitchValue.text = String.format("%.1fx", currentPitch)

        pitchSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress / 100f)
                pitchValue.text = String.format("%.1fx", pitch)
                preferenceManager.setTTSPitch(pitch)
                speechEngine?.setPitch(pitch)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Volume (0 to 100)
        val currentVolume = preferenceManager.getVoiceVolume()
        volumeSeekBar.max = 100
        volumeSeekBar.progress = currentVolume
        volumeValue.text = "$currentVolume%"

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeValue.text = "$progress%"
                preferenceManager.setVoiceVolume(progress)
                speechEngine?.setVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        testButton.setOnClickListener {
            val testMessage = "Received payment of 500 rupees from Test User on PaySpeak"
            speechEngine?.announcePayment(testMessage, "en") {
                runOnUiThread {
                    Toast.makeText(this, "Test announcement complete", Toast.LENGTH_SHORT).show()
                }
            }
        }

        resetButton.setOnClickListener {
            preferenceManager.setTTSSpeed(1.0f)
            preferenceManager.setTTSPitch(1.0f)
            preferenceManager.setVoiceVolume(80)

            speedSeekBar.progress = 50
            pitchSeekBar.progress = 50
            volumeSeekBar.progress = 80

            speedValue.text = "1.0x"
            pitchValue.text = "1.0x"
            volumeValue.text = "80%"

            speechEngine?.setSpeechRate(1.0f)
            speechEngine?.setPitch(1.0f)
            speechEngine?.setVolume(0.8f)

            Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechEngine?.release()
    }
}

