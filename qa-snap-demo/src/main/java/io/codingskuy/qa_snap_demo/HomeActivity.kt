package io.codingskuy.qa_snap_demo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.codingskuy.qa_snap.QASnapHelper
import io.codingskuy.qa_snap.QASnapRecorder
import io.codingskuy.qa_snap_demo.base.BaseActivity
import io.codingskuy.qa_snap_demo.databinding.ActivityHomeBinding
import io.codingskuy.qa_snap_demo.utils.EnvironmentManager
import java.io.File

/**
 * HomeActivity - Main home screen with environment-aware QA recording
 *
 * This demonstrates:
 * - Environment-aware BaseActivity usage
 * - Managing existing QA recording session (staging only)
 * - Environment-specific features and UI
 * - Clean fallback for non-staging environments
 */
class HomeActivity : BaseActivity() {

    companion object {
        private const val TAG = "HomeActivity"
    }

    private var binding: ActivityHomeBinding? = null
    private var existingRecorder: QASnapRecorder? = null
    private var isViewReady = false

    // Continue recording - don't stop when HomeActivity is created or destroyed
    override val shouldCleanupOnDestroy: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize binding FIRST, before calling super.onCreate()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        // Mark view as ready
        isViewReady = true

        // Now call super.onCreate() which may trigger QA callbacks
        super.onCreate(savedInstanceState)

        Log.d(TAG, "HomeActivity onCreate - Environment: ${EnvironmentManager.getEnvironmentDisplayName()}")

        // Setup environment-specific UI
        setupEnvironmentUI()

        // Connect to existing recording session (only in staging)
        if (EnvironmentManager.isQASnapEnabled()) {
            setupExistingRecordingSession()
        }

        setupClickListeners()
        updateStatus()
    }

    private fun setupEnvironmentUI() {
        if (!isViewReady || binding == null) return

        val environment = EnvironmentManager.getCurrentEnvironment()
        val appName = EnvironmentManager.getAppName(this)
        
        supportActionBar?.title = "$appName - Home"
        
        // Show environment-specific subtitle in non-production
        if (environment != EnvironmentManager.Environment.PRODUCTION && EnvironmentManager.isLoggingEnabled()) {
            supportActionBar?.subtitle = "${EnvironmentManager.getEnvironmentDisplayName()} Environment"
        }
        
        // Environment-specific welcome message
        val welcomeMessage = when (environment) {
            EnvironmentManager.Environment.DEVELOPMENT -> "Welcome to Development Environment"
            EnvironmentManager.Environment.STAGING -> "Welcome to Staging - QA Recording Available"
            EnvironmentManager.Environment.PRODUCTION -> "Welcome to Production"
        }
        
        // Show welcome toast in non-production environments
        if (environment != EnvironmentManager.Environment.PRODUCTION && EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(this, welcomeMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupExistingRecordingSession() {
        Log.d(TAG, "Checking for existing recording session in ${EnvironmentManager.getEnvironmentDisplayName()}")

        // Get existing recorder instance only in staging
        if (EnvironmentManager.isQASnapEnabled()) {
            existingRecorder = QASnapRecorder.getInstance()
            val isRecordingActive = existingRecorder?.isRecording() ?: false
            val isLogsActive = existingRecorder?.isCapturingLogs() ?: false

            Log.d(TAG, "Existing recording session - Video: $isRecordingActive, Logs: $isLogsActive")

            if (isRecordingActive || isLogsActive) {
                Log.d(TAG, "Active QA recording/logging detected")
                if (EnvironmentManager.isLoggingEnabled()) {
                    Toast.makeText(this, "📹 QA Recording continues...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d(TAG, "No active QA recording found")
            }
        } else {
            Log.d(TAG, "QA Recording not available in ${EnvironmentManager.getEnvironmentDisplayName()} environment")
        }
    }

    private fun setupClickListeners() {
        if (!isViewReady || binding == null) return

        Log.d(TAG, "Setting up click listeners for ${EnvironmentManager.getEnvironmentDisplayName()} environment")

        binding!!.btnStopRecording.setOnClickListener {
            Log.d(TAG, "Stop recording button clicked")
            if (EnvironmentManager.isQASnapEnabled()) {
                showStopRecordingDialog()
            } else {
                Toast.makeText(this, "QA Recording not available in ${EnvironmentManager.getEnvironmentDisplayName()}", Toast.LENGTH_SHORT).show()
            }
        }

        binding!!.btnViewProfile.setOnClickListener {
            Log.d(TAG, "View profile button clicked")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Profile feature - QA Recording continues in background"
            } else {
                "Profile feature - ${EnvironmentManager.getEnvironmentDisplayName()} environment"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding!!.btnSettings.setOnClickListener {
            Log.d(TAG, "Settings button clicked")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Settings feature - QA Recording continues in background"
            } else {
                "Settings feature - ${EnvironmentManager.getEnvironmentDisplayName()} environment"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding!!.btnActivity1.setOnClickListener {
            Log.d(TAG, "Debug file system button clicked")
            if (EnvironmentManager.isQASnapEnabled()) {
                checkFileSystem()
            } else {
                showEnvironmentInfo()
            }
        }

        binding!!.btnActivity2.setOnClickListener {
            Log.d(TAG, "Activity 2 button clicked")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Activity 2 performed - QA Recorded!"
            } else {
                "Activity 2 performed - ${EnvironmentManager.getEnvironmentDisplayName()}"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding!!.btnActivity3.setOnClickListener {
            Log.d(TAG, "Activity 3 button clicked")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Activity 3 performed - QA Recorded!"
            } else {
                "Activity 3 performed - ${EnvironmentManager.getEnvironmentDisplayName()}"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Environment-aware log test buttons
        binding!!.btnLogTest1.setOnClickListener {
            Log.d(TAG, "Log test 1 button clicked")
            android.util.Log.d("QASnapDemo", "Test debug log from HomeActivity - ${EnvironmentManager.getEnvironmentDisplayName()}")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Debug log generated and captured by QA"
            } else {
                "Debug log generated (${EnvironmentManager.getEnvironmentDisplayName()})"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding!!.btnLogTest2.setOnClickListener {
            Log.d(TAG, "Log test 2 button clicked")
            android.util.Log.i("QASnapDemo", "Test info log from HomeActivity - ${EnvironmentManager.getEnvironmentDisplayName()}")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Info log generated and captured by QA"
            } else {
                "Info log generated (${EnvironmentManager.getEnvironmentDisplayName()})"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding!!.btnLogTest3.setOnClickListener {
            Log.d(TAG, "Log test 3 button clicked")
            android.util.Log.w("QASnapDemo", "Test warning log from HomeActivity - ${EnvironmentManager.getEnvironmentDisplayName()}")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Warning log generated and captured by QA"
            } else {
                "Warning log generated (${EnvironmentManager.getEnvironmentDisplayName()})"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding!!.btnLogError.setOnClickListener {
            Log.d(TAG, "Log error button clicked")
            android.util.Log.e("QASnapDemo", "Test error log from HomeActivity - ${EnvironmentManager.getEnvironmentDisplayName()}")
            val message = if (EnvironmentManager.isQASnapEnabled()) {
                "Error log generated and captured by QA"
            } else {
                "Error log generated (${EnvironmentManager.getEnvironmentDisplayName()})"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        Log.d(TAG, "Click listeners setup completed for ${EnvironmentManager.getEnvironmentDisplayName()}")
    }

    private fun updateStatus() {
        // Safety check - don't update if view not ready
        if (!isViewReady || binding == null) {
            Log.d(TAG, "updateStatus() called but view not ready yet, skipping")
            return
        }

        val environment = EnvironmentManager.getCurrentEnvironment()
        
        if (EnvironmentManager.isQASnapEnabled()) {
            val isRecording = existingRecorder?.isRecording() ?: false

            binding!!.tvRecordingStatus.text = when {
                isRecording -> "🔴📋 QA Recording Active (Staging)"
                else -> "⭕ QA Ready (Staging)"
            }

            binding!!.btnStopRecording.isEnabled = isRecording
            binding!!.btnStopRecording.text = when {
                isRecording -> "🛑 Stop QA Recording"
                else -> "🛑 No Recording Active"
            }
        } else {
            // Non-staging environments
            binding!!.tvRecordingStatus.text = when (environment) {
                EnvironmentManager.Environment.DEVELOPMENT -> "🔧 Development Environment"
                EnvironmentManager.Environment.PRODUCTION -> "🚀 Production Environment"
                else -> "⭕ Environment: ${EnvironmentManager.getEnvironmentDisplayName()}"
            }

            binding!!.btnStopRecording.isEnabled = false
            binding!!.btnStopRecording.text = "QA Recording Not Available"
        }
    }

    private fun showStopRecordingDialog() {
        if (!EnvironmentManager.isQASnapEnabled()) {
            Toast.makeText(this, "QA Recording not available in ${EnvironmentManager.getEnvironmentDisplayName()}", Toast.LENGTH_SHORT).show()
            return
        }

        val isRecording = existingRecorder?.isRecording() ?: false

        if (!isRecording) {
            Toast.makeText(this, "No QA recording active to stop", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Stop QA Recording")
            .setMessage("Stop QA recording in ${EnvironmentManager.getEnvironmentDisplayName()} environment?\n\nBoth screen video and system logs will be saved to your device.")
            .setPositiveButton("Stop") { _, _ ->
                existingRecorder?.stopRecording()
                updateStatus()
                if (EnvironmentManager.isLoggingEnabled()) {
                    Toast.makeText(this, "QA Recording stopped", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    private fun showEnvironmentInfo() {
        val info = getEnvironmentInfo()
        val message = buildString {
            append("Environment Information:\n\n")
            info.forEach { (key, value) ->
                append("$key: $value\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Environment Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkFileSystem() {
        if (!EnvironmentManager.isQASnapEnabled()) {
            showEnvironmentInfo()
            return
        }

        val videoDir = existingRecorder?.getOutputDirectory()
        val logDir = existingRecorder?.getLogOutputDirectory()

        val videoFiles = videoDir?.listFiles()?.size ?: 0
        val logFiles = logDir?.listFiles()?.size ?: 0

        val message = """
            QA File System Status (${EnvironmentManager.getEnvironmentDisplayName()}):
            
            Video Directory: ${videoDir?.absolutePath}
            Video Files: $videoFiles
            Directory Exists: ${videoDir?.exists()}
            Directory Writable: ${videoDir?.canWrite()}
            
            Log Directory: ${logDir?.absolutePath}
            Log Files: $logFiles
            Directory Exists: ${logDir?.exists()}
            Directory Writable: ${logDir?.canWrite()}
            
            Recording Status: ${existingRecorder?.isRecording()}
            Environment: ${EnvironmentManager.getEnvironmentDisplayName()}
            QA Snap Enabled: ${EnvironmentManager.isQASnapEnabled()}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Debug: QA File System")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()

        if (EnvironmentManager.isLoggingEnabled()) {
            Log.d(TAG, message)
        }
    }

    // Override QA callbacks for HomeActivity-specific behavior
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        super.onQARecordingComplete(videoFile, logFile)
        
        if (EnvironmentManager.isLoggingEnabled()) {
            Log.d(TAG, "QA Recording completed in HomeActivity")
            showCompletionDialog("QA Recording Complete", videoFile, logFile)
        }
        
        updateStatus()
    }

    private fun showCompletionDialog(title: String, videoFile: File?, logFile: File?) {
        val message = buildString {
            append("QA recording completed in ${EnvironmentManager.getEnvironmentDisplayName()} environment!\n\n")

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

    override fun onQARecordingError(error: String) {
        super.onQARecordingError(error)
        Log.e(TAG, "QA Recording error in HomeActivity: $error")

        // Safe updateStatus call
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "HomeActivity onResume - Environment: ${EnvironmentManager.getEnvironmentDisplayName()}")
        updateStatus()
        
        // Log environment details in development/staging
        if (EnvironmentManager.getCurrentEnvironment() != EnvironmentManager.Environment.PRODUCTION) {
            if (EnvironmentManager.isLoggingEnabled()) {
                Log.d(TAG, "QA Recording active: ${isQARecording()}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
        isViewReady = false
    }
}