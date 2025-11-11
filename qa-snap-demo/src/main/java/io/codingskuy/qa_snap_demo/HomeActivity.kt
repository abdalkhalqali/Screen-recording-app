package io.codingskuy.qa_snap_demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap.QASnapRecorder
import io.codingskuy.qa_snap_demo.databinding.ActivityHomeBinding
import java.io.File

/**
 * HomeActivity - Main home screen where user can interact and end recording
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var qaSnapRecorder: QASnapRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get QA Snap recorder instance
        qaSnapRecorder = QASnapRecorder.getInstance()

        setupClickListeners()
        updateRecordingStatus()
    }

    private fun setupClickListeners() {
        binding.btnStopRecording.setOnClickListener {
            showStopRecordingDialog()
        }

        binding.btnViewProfile.setOnClickListener {
            Toast.makeText(this, "Profile feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnActivity1.setOnClickListener {
            Toast.makeText(this, "Activity 1 performed", Toast.LENGTH_SHORT).show()
        }

        binding.btnActivity2.setOnClickListener {
            Toast.makeText(this, "Activity 2 performed", Toast.LENGTH_SHORT).show()
        }

        binding.btnActivity3.setOnClickListener {
            Toast.makeText(this, "Activity 3 performed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRecordingStatus() {
        val isRecording = qaSnapRecorder?.isRecording() ?: false
        binding.tvRecordingStatus.text = if (isRecording) {
            "🔴 Recording in progress..."
        } else {
            "⭕ Not recording"
        }

        binding.btnStopRecording.isEnabled = isRecording
    }

    private fun showStopRecordingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Stop Recording")
            .setMessage("Are you sure you want to stop the screen recording? The video will be saved to your device.")
            .setPositiveButton("Stop Recording") { _, _ ->
                stopRecording()
            }
            .setNegativeButton("Continue Recording", null)
            .show()
    }

    private fun stopRecording() {
        qaSnapRecorder?.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {}

            override fun onRecordingStopped(outputFile: File) {
                runOnUiThread {
                    showRecordingCompletedDialog(outputFile)
                    updateRecordingStatus()
                }
            }

            override fun onRecordingError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@HomeActivity, "Error: $error", Toast.LENGTH_LONG).show()
                    updateRecordingStatus()
                }
            }
        })

        qaSnapRecorder?.stopRecording()
    }

    private fun showRecordingCompletedDialog(outputFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Recording Complete!")
            .setMessage("Your screen recording has been saved successfully!\n\nFile: ${outputFile.name}\nLocation: ${outputFile.absolutePath}")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateRecordingStatus()
    }
}