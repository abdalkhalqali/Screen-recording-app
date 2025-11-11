package io.codingskuy.qa_snap_demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap.QASnapRecorder
import io.codingskuy.qa_snap_demo.databinding.ActivityHomeBinding
import java.io.File

/**
 * HomeActivity - Main home screen where user can interact and end recording/log capture
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
        updateStatus()
        setupRecordingListener()
    }

    private fun setupClickListeners() {
        binding.btnStopRecording.setOnClickListener {
            showStopRecordingDialog()
        }

        binding.btnStopLogCapture.setOnClickListener {
            showStopRecordingDialog() // Same as stop recording since it's now unified
        }

        binding.btnStopBoth.setOnClickListener {
            showStopRecordingDialog() // Same as stop recording since it's now unified
        }

        binding.btnViewProfile.setOnClickListener {
            Toast.makeText(this, "Profile feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnActivity1.setOnClickListener {
            checkFileSystem()
        }

        binding.btnActivity2.setOnClickListener {
            Toast.makeText(this, "Activity 2 performed", Toast.LENGTH_SHORT).show()
        }

        binding.btnActivity3.setOnClickListener {
            Toast.makeText(this, "Activity 3 performed", Toast.LENGTH_SHORT).show()
        }

        // New log capture test buttons
        binding.btnLogTest1.setOnClickListener {
            android.util.Log.d("QASnapDemo", "Test log message 1 - Debug level")
            Toast.makeText(this, "Debug log generated", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogTest2.setOnClickListener {
            android.util.Log.i("QASnapDemo", "Test log message 2 - Info level")
            Toast.makeText(this, "Info log generated", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogTest3.setOnClickListener {
            android.util.Log.w("QASnapDemo", "Test log message 3 - Warning level")
            Toast.makeText(this, "Warning log generated", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogError.setOnClickListener {
            android.util.Log.e("QASnapDemo", "Test error log message")
            Toast.makeText(this, "Error log generated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecordingListener() {
        qaSnapRecorder?.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                runOnUiThread {
                    Toast.makeText(
                        this@HomeActivity,
                        "Screen recording started",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateStatus()
                }
            }

            override fun onRecordingStopped(outputFile: File) {
                runOnUiThread {
                    showRecordingCompletedDialog(outputFile)
                    updateStatus()
                }
            }

            override fun onRecordingError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@HomeActivity, "Recording Error: $error", Toast.LENGTH_LONG)
                        .show()
                    updateStatus()
                }
            }

            override fun onLogCaptureStarted() {
                runOnUiThread {
                    Toast.makeText(this@HomeActivity, "Log capture started", Toast.LENGTH_SHORT)
                        .show()
                    updateStatus()
                }
            }

            override fun onLogCaptureStopped(outputFile: File) {
                runOnUiThread {
                    showLogCaptureCompletedDialog(outputFile)
                    updateStatus()
                }
            }

            override fun onLogCaptureError(error: String) {
                runOnUiThread {
                    Toast.makeText(
                        this@HomeActivity,
                        "Log Capture Error: $error",
                        Toast.LENGTH_LONG
                    ).show()
                    updateStatus()
                }
            }
        })
    }

    private fun updateStatus() {
        val isRecording = qaSnapRecorder?.isRecording() ?: false
        val isCapturingLogs = qaSnapRecorder?.isCapturingLogs() ?: false

        binding.tvRecordingStatus.text = when {
            isRecording && isCapturingLogs -> "🔴📋 Recording & Capturing logs..."
            isRecording -> "🔴 Recording in progress..."
            isCapturingLogs -> "📋 Capturing logs..."
            else -> "⭕ Not recording or capturing"
        }

        binding.btnStopRecording.isEnabled = isRecording
        binding.btnStopLogCapture.isEnabled = isCapturingLogs
        binding.btnStopBoth.isEnabled = isRecording || isCapturingLogs
    }

    private fun showStopRecordingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Stop Recording")
            .setMessage("Are you sure you want to stop the screen recording and log capture? All data will be saved to your device.")
            .setPositiveButton("Stop Recording") { _, _ ->
                qaSnapRecorder?.stopRecording() // This now stops both video and logs
            }
            .setNegativeButton("Continue Recording", null)
            .show()
    }

    private fun showStopLogCaptureDialog() {
        // Redirect to main stop dialog since operations are now unified
        showStopRecordingDialog()
    }

    private fun showStopBothDialog() {
        // Redirect to main stop dialog since operations are now unified  
        showStopRecordingDialog()
    }

    private fun showRecordingCompletedDialog(outputFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Recording Complete!")
            .setMessage("Your screen recording has been saved successfully!\n\nFile: ${outputFile.name}\nLocation: ${outputFile.absolutePath}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogCaptureCompletedDialog(outputFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Log Capture Complete!")
            .setMessage("Your log capture has been saved successfully!\n\nFile: ${outputFile.name}\nLocation: ${outputFile.absolutePath}\nSize: ${outputFile.length() / 1024} KB")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkFileSystem() {
        val videoDir = qaSnapRecorder?.getOutputDirectory()
        val logDir = qaSnapRecorder?.getLogOutputDirectory()

        val videoFiles = videoDir?.listFiles()?.size ?: 0
        val logFiles = logDir?.listFiles()?.size ?: 0

        val message = """
            File System Status:
            
            Video Directory: ${videoDir?.absolutePath}
            Video Files: $videoFiles
            Directory Exists: ${videoDir?.exists()}
            Directory Writable: ${videoDir?.canWrite()}
            
            Log Directory: ${logDir?.absolutePath}
            Log Files: $logFiles
            Directory Exists: ${logDir?.exists()}
            Directory Writable: ${logDir?.canWrite()}
            
            Recording Status: ${qaSnapRecorder?.isRecording()}
            Log Capture Status: ${qaSnapRecorder?.isCapturingLogs()}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Debug: File System Status")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()

        android.util.Log.d("QASnapDemo", message)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}