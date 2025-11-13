package io.codingskuy.qa_snap_demo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap.QASnapHelper
import io.codingskuy.qa_snap_demo.databinding.ActivityHomeBinding
import java.io.File

/**
 * HomeActivity - Main home screen with simplified QA Snap integration
 *
 * This demonstrates how to interact with QA recording in progress using
 * the simplified helper approach.
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HomeActivity"
    }

    private lateinit var binding: ActivityHomeBinding
    private var qaSnapHelper: QASnapHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "HomeActivity onCreate - setting up QA Snap integration")

        // Get helper instance if recording was started from MainActivity
        // In a real app, you might pass this via intent or use a singleton
        setupQASnapHelper()

        setupClickListeners()
        updateStatus()
    }

    private fun setupQASnapHelper() {
        Log.d(TAG, "Setting up QASnapHelper")
        // Create helper for this activity (will detect if recording is already in progress)
        qaSnapHelper = QASnapHelper(this).apply {
            // Don't auto-start since recording should already be running from MainActivity
            initialize(autoStart = false)
        }

        // Set completion callback
        qaSnapHelper?.onComplete { videoFile, logFile ->
            Log.d(TAG, "QA recording completed in HomeActivity")
            showCompletionDialog("QA Recording Complete!", videoFile, logFile)
            updateStatus()
        }

        Log.d(TAG, "QASnapHelper setup completed")
    }

    private fun setupClickListeners() {
        Log.d(TAG, "Setting up click listeners")

        binding.btnStopRecording.setOnClickListener {
            Log.d(TAG, "Stop recording button clicked")
            showStopRecordingDialog()
        }

        binding.btnViewProfile.setOnClickListener {
            Log.d(TAG, "View profile button clicked")
            Toast.makeText(
                this,
                "Profile feature - Recording continues in background",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnSettings.setOnClickListener {
            Log.d(TAG, "Settings button clicked")
            Toast.makeText(
                this,
                "Settings feature - Recording continues in background",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnActivity1.setOnClickListener {
            Log.d(TAG, "Debug file system button clicked")
            checkFileSystem()
        }

        binding.btnActivity2.setOnClickListener {
            Log.d(TAG, "Activity 2 button clicked")
            Toast.makeText(this, "Activity 2 performed - Recorded!", Toast.LENGTH_SHORT).show()
        }

        binding.btnActivity3.setOnClickListener {
            Log.d(TAG, "Activity 3 button clicked")
            Toast.makeText(this, "Activity 3 performed - Recorded!", Toast.LENGTH_SHORT).show()
        }

        // New log capture test buttons
        binding.btnLogTest1.setOnClickListener {
            Log.d(TAG, "Log test 1 button clicked")
            android.util.Log.d("QASnapDemo", "Test log message 1 - Debug level from HomeActivity")
            Toast.makeText(this, "Debug log generated", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogTest2.setOnClickListener {
            Log.d(TAG, "Log test 2 button clicked")
            android.util.Log.i("QASnapDemo", "Test log message 2 - Info level from HomeActivity")
            Toast.makeText(this, "Info log generated", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogTest3.setOnClickListener {
            Log.d(TAG, "Log test 3 button clicked")
            android.util.Log.w("QASnapDemo", "Test log message 3 - Warning level from HomeActivity")
            Toast.makeText(this, "Warning log generated", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogError.setOnClickListener {
            Log.d(TAG, "Log error button clicked")
            android.util.Log.e("QASnapDemo", "Test error log message from HomeActivity")
            Toast.makeText(this, "Error log generated", Toast.LENGTH_SHORT).show()
        }

        Log.d(TAG, "Click listeners setup completed")
    }

    private fun updateStatus() {
        val isRecording = qaSnapHelper?.isRecording() ?: false

        binding.tvRecordingStatus.text = when {
            isRecording -> "🔴📋 QA Recording Active (Video & Logs)"
            else -> "⭕ Ready to Record"
        }

        binding.btnStopRecording.isEnabled = isRecording

        // Update button text based on what's active
        binding.btnStopRecording.text = when {
            isRecording -> "🛑 Stop QA Recording"
            else -> "🛑 No Recording Active"
        }
    }

    private fun showStopRecordingDialog() {
        val isRecording = qaSnapHelper?.isRecording() ?: false

        if (!isRecording) {
            Toast.makeText(this, "No recording active to stop", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Stop QA Recording")
            .setMessage("Are you sure you want to stop QA recording? Both screen video and system logs will be saved to your device.")
            .setPositiveButton("Stop") { _, _ ->
                qaSnapHelper?.stopRecording()
                updateStatus()
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    private fun showCompletionDialog(title: String, videoFile: File?, logFile: File?) {
        val message = buildString {
            append("QA recording has been completed successfully!\n\n")

            videoFile?.let {
                append("📹 Video: ${it.name}\n")
                append("📁 Size: ${it.length() / 1024} KB\n")
                append("📍 Location: ${it.parent}\n\n")
            }

            logFile?.let {
                append("📋 Logs: ${it.name}\n")
                append("📁 Size: ${it.length() / 1024} KB\n")
                append("📍 Location: ${it.parent}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkFileSystem() {
        val recorder = qaSnapHelper?.getRecorder()
        val videoDir = recorder?.getOutputDirectory()
        val logDir = recorder?.getLogOutputDirectory()

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
            
            Recording Status: ${qaSnapHelper?.isRecording()}
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
        Log.d(TAG, "HomeActivity onResume - updating status")
        updateStatus()
    }
}