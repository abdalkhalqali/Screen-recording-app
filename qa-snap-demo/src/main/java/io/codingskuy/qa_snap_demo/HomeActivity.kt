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
                    showCompletionDialog("Video Recording Complete!", outputFile, "video")
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
                    showCompletionDialog("Log Capture Complete!", outputFile, "logs")
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
            isRecording && isCapturingLogs -> "🔴📋 QA Recording Active (Video & Logs)"
            isRecording -> "🔴 Video Recording Active"
            isCapturingLogs -> "📋 Log Capture Active"
            else -> "⭕ Ready to Record"
        }

        binding.btnStopRecording.isEnabled = isRecording || isCapturingLogs

        // Update button text based on what's active
        binding.btnStopRecording.text = when {
            isRecording && isCapturingLogs -> "🛑 Stop QA Recording"
            isRecording -> "🛑 Stop Video Recording"
            isCapturingLogs -> "🛑 Stop Log Capture"
            else -> "🛑 Stop Recording"
        }
    }

    private fun showStopRecordingDialog() {
        val isRecording = qaSnapRecorder?.isRecording() ?: false
        val isCapturingLogs = qaSnapRecorder?.isCapturingLogs() ?: false

        val title = when {
            isRecording && isCapturingLogs -> "Stop QA Recording"
            isRecording -> "Stop Video Recording"
            isCapturingLogs -> "Stop Log Capture"
            else -> "Stop Recording"
        }

        val message = when {
            isRecording && isCapturingLogs -> "Are you sure you want to stop QA recording? Both screen video and system logs will be saved to your device."
            isRecording -> "Are you sure you want to stop video recording? The video file will be saved to your device."
            isCapturingLogs -> "Are you sure you want to stop log capture? The log file will be saved to your device."
            else -> "No active recording to stop."
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Stop") { _, _ ->
                qaSnapRecorder?.stopRecording() // This stops both video and logs
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    private fun showCompletionDialog(title: String, outputFile: File, fileType: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Your $fileType has been saved successfully!\n\nFile: ${outputFile.name}\nLocation: ${outputFile.absolutePath}\nSize: ${outputFile.length() / 1024} KB")
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