package io.codingskuy.qa_snap_demo.base

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.codingskuy.qa_snap.QASnapActivity
import io.codingskuy.qa_snap.QASnapRecorder
import io.codingskuy.qa_snap_demo.utils.EnvironmentManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * QASnapHelper - Helper class to manage QA Snap SDK integration with proper permission flow
 *
 * This class provides:
 * - Environment-aware QA Snap initialization
 * - Proper permission handling (basic permissions first, then MediaProjection)
 * - Wrapper methods for QA Snap functionality
 * - Callback handling for BaseActivity
 */
class QASnapHelper(private val activity: AppCompatActivity) : QASnapRecorder.RecordingListener {

    companion object {
        private const val TAG = "QASnapHelper"
        private const val PERMISSION_REQUEST_CODE = 9001
    }

    private var qaSnapRecorder: QASnapRecorder? = null
    private var callback: QASnapCallback? = null
    private var pendingAutoStart = false

    init {
        callback = activity as? QASnapCallback
    }

    /**
     * Initialize QA Snap recorder with proper permission flow
     */
    fun initialize() {
        if (!EnvironmentManager.isQASnapEnabled()) {
            Log.w(TAG, "QA Snap not enabled for current environment")
            return
        }

        try {
            Log.d(TAG, "Initializing QA Snap recorder...")

            // Always initialize QASnapRecorder first to register ActivityResultLauncher early
            // This must be done while activity is in proper lifecycle state
            qaSnapRecorder = QASnapRecorder.getInstance()
            if (qaSnapRecorder == null) {
                Log.d(TAG, "No existing QA Snap instance, initializing new one...")
                qaSnapRecorder = QASnapRecorder.initialize(activity)
            }

            if (qaSnapRecorder != null) {
                Log.d(TAG, "QA Snap recorder initialized, setting up listener")
                qaSnapRecorder!!.setRecordingListener(this)
            } else {
                Log.e(TAG, "Failed to initialize QA Snap recorder - instance is null")
                callback?.onQARecordingError("Failed to initialize QA Snap recorder")
                return
            }

            // Check basic permissions after QASnapRecorder is initialized
            if (!hasBasicPermissions()) {
                Log.d(TAG, "Basic permissions not granted, requesting permissions first")
                requestBasicPermissions()
                return
            }

            // Basic permissions are granted, proceed with callback setup
            setupCallbacksAfterPermissions()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize QA Snap", e)
            callback?.onQARecordingError("Initialization failed: ${e.message}")
        }
    }

    /**
     * Setup callbacks after permissions are granted
     */
    private fun setupCallbacksAfterPermissions() {
        try {
            Log.d(TAG, "Setting up QA Snap callbacks after permissions granted")

            // Setup callbacks if available
            callback?.let { setupCallbacks(it) }

            // Handle pending auto start
            if (pendingAutoStart) {
                Log.d(TAG, "Processing pending auto-start after permissions")
                pendingAutoStart = false
                startRecording()
            }

            Log.d(TAG, "QA Snap helper setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup QA Snap callbacks", e)
            callback?.onQARecordingError("Callback setup failed: ${e.message}")
        }
    }

    /**
     * Setup callbacks for BaseActivity
     */
    private fun setupCallbacks(callback: QASnapCallback) {
        // Since we're using BaseActivity instead of QASnapActivity,
        // we need to handle the integration manually
        Log.d(TAG, "Setting up QA Snap callbacks for BaseActivity")

        // Trigger ready callback first
        callback.onQARecordingReady()

        // Check if auto-start is enabled
        if (callback.shouldAutoStartRecording()) {
            Log.d(TAG, "Auto-start recording is enabled, starting recording")
            startRecording()
        }
    }

    /**
     * Start QA recording with proper permission flow
     */
    fun startRecording() {
        if (!EnvironmentManager.isQASnapEnabled()) {
            Log.w(TAG, "Cannot start recording - QA Snap not enabled")
            callback?.onQARecordingError("QA Snap not enabled in current environment")
            return
        }

        // Check basic permissions first
        if (!hasBasicPermissions()) {
            Log.d(TAG, "Basic permissions not granted, requesting permissions before recording")
            pendingAutoStart = true
            requestBasicPermissions()
            return
        }

        try {
            Log.d(TAG, "Starting QA recording...")

            // Ensure we have a recorder instance
            if (qaSnapRecorder == null) {
                Log.e(TAG, "QA recorder is null - should have been initialized during initialize()")
                callback?.onQARecordingError("Recorder not initialized")
                return
            }

            // Log test case information before starting recording
            logTestCaseInformation()

            qaSnapRecorder?.let { recorder ->
                if (!recorder.isRecording()) {
                    // Basic permissions are granted, now start recording
                    // This will show MediaProjection permission dialog
                    Log.d(
                        TAG,
                        "Basic permissions granted, calling QA Snap SDK startRecording() - MediaProjection dialog will appear"
                    )
                    recorder.startRecording()
                } else {
                    Log.w(TAG, "QA recording already in progress")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start QA recording", e)
            callback?.onQARecordingError("Failed to start recording: ${e.message}")
        }
    }

    /**
     * Log test case information from onboarding setup
     */
    private fun logTestCaseInformation() {
        try {
            val prefs = activity.getSharedPreferences(
                "qa_snap_test_info",
                android.content.Context.MODE_PRIVATE
            )

            // Get test case information
            val testCaseTitle = prefs.getString("test_case_title", "Bug Hunting") ?: "Bug Hunting"
            val testCaseId = prefs.getString("test_case_id", "") ?: ""
            val reference = prefs.getString("reference", "") ?: ""
            val setupTimestamp = prefs.getLong("setup_timestamp", 0L)
            val deviceModel =
                prefs.getString("device_model", android.os.Build.MODEL) ?: android.os.Build.MODEL
            val deviceManufacturer =
                prefs.getString("device_manufacturer", android.os.Build.MANUFACTURER)
                    ?: android.os.Build.MANUFACTURER
            val androidVersion =
                prefs.getString("android_version", android.os.Build.VERSION.RELEASE)
                    ?: android.os.Build.VERSION.RELEASE

            // Current session information
            val sessionStartTime = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sessionStartFormatted = dateFormat.format(Date(sessionStartTime))
            val setupTimeFormatted =
                if (setupTimestamp > 0) dateFormat.format(Date(setupTimestamp)) else "Not available"

            // Log comprehensive test case information
            Log.i(TAG, "=== QA SNAP RECORDING SESSION STARTED ===")
            Log.i(TAG, "Test Case Title: $testCaseTitle")
            Log.i(TAG, "Test Case ID: ${testCaseId.ifEmpty { "Not specified" }}")
            Log.i(TAG, "Reference: ${reference.ifEmpty { "Not specified" }}")
            Log.i(TAG, "Session Start Time: $sessionStartFormatted")
            Log.i(TAG, "Setup Time: $setupTimeFormatted")
            Log.i(TAG, "Device: $deviceManufacturer $deviceModel")
            Log.i(TAG, "Android Version: $androidVersion")
            Log.i(TAG, "Environment: ${EnvironmentManager.getEnvironmentDisplayName()}")
            Log.i(
                TAG,
                "App Version: ${
                    activity.packageManager.getPackageInfo(
                        activity.packageName,
                        0
                    ).versionName
                }"
            )
            Log.i(TAG, "Process ID: ${android.os.Process.myPid()}")
            Log.i(TAG, "Thread ID: ${Thread.currentThread().id}")
            Log.i(TAG, "Available Memory: ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB")
            Log.i(TAG, "==========================================")

            // Also log device specifications
            Log.i(TAG, "=== DEVICE SPECIFICATIONS ===")
            Log.i(TAG, "Brand: ${android.os.Build.BRAND}")
            Log.i(TAG, "Model: ${android.os.Build.MODEL}")
            Log.i(TAG, "Device: ${android.os.Build.DEVICE}")
            Log.i(TAG, "Product: ${android.os.Build.PRODUCT}")
            Log.i(TAG, "Hardware: ${android.os.Build.HARDWARE}")
            Log.i(TAG, "Board: ${android.os.Build.BOARD}")
            Log.i(TAG, "SDK Version: ${android.os.Build.VERSION.SDK_INT}")
            Log.i(TAG, "Android Version: ${android.os.Build.VERSION.RELEASE}")
            Log.i(TAG, "Build ID: ${android.os.Build.ID}")
            Log.i(TAG, "=============================")

            // Save session information for later reference
            prefs.edit().apply {
                putLong("last_session_start", sessionStartTime)
                putString("last_session_start_formatted", sessionStartFormatted)
                apply()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to log test case information", e)
            // Don't fail the recording start if logging fails
        }
    }

    /**
     * Stop QA recording
     */
    fun stopRecording() {
        if (!EnvironmentManager.isQASnapEnabled()) {
            Log.w(TAG, "Cannot stop recording - QA Snap not enabled")
            return
        }

        try {
            Log.d(TAG, "Stopping QA recording...")

            // Log session end information
            logSessionEndInformation()

            qaSnapRecorder?.let { recorder ->
                if (recorder.isRecording()) {
                    // Stop actual QA Snap recording
                    Log.d(TAG, "Calling QA Snap SDK stopRecording()")
                    recorder.stopRecording()
                } else {
                    Log.w(TAG, "No active QA recording to stop")
                }
            } ?: run {
                Log.e(TAG, "QA recorder not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop QA recording", e)
            callback?.onQARecordingError("Failed to stop recording: ${e.message}")
        }
    }

    /**
     * Log session end information
     */
    private fun logSessionEndInformation() {
        try {
            val prefs = activity.getSharedPreferences(
                "qa_snap_test_info",
                android.content.Context.MODE_PRIVATE
            )
            val sessionStartTime = prefs.getLong("last_session_start", 0L)
            val sessionEndTime = System.currentTimeMillis()
            val sessionDuration = sessionEndTime - sessionStartTime

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sessionEndFormatted = dateFormat.format(Date(sessionEndTime))
            val durationSeconds = sessionDuration / 1000
            val durationMinutes = durationSeconds / 60

            Log.i(TAG, "=== QA SNAP RECORDING SESSION ENDED ===")
            Log.i(TAG, "Session End Time: $sessionEndFormatted")
            Log.i(TAG, "Session Duration: ${durationMinutes}m ${durationSeconds % 60}s")
            Log.i(TAG, "Total Duration (ms): $sessionDuration")
            Log.i(TAG, "=======================================")

            // Save end session information
            prefs.edit().apply {
                putLong("last_session_end", sessionEndTime)
                putLong("last_session_duration", sessionDuration)
                putString("last_session_end_formatted", sessionEndFormatted)
                apply()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to log session end information", e)
        }
    }

    /**
     * Check if recording is active
     */
    fun isRecording(): Boolean {
        return if (EnvironmentManager.isQASnapEnabled()) {
            qaSnapRecorder?.isRecording() ?: false
        } else {
            false
        }
    }

    /**
     * Check if basic permissions are granted
     */
    private fun hasBasicPermissions(): Boolean {
        val permissions = getRequiredBasicPermissions()
        val hasAll = permissions.all { permission ->
            val granted = ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: $granted")
            granted
        }
        Log.d(TAG, "hasBasicPermissions(): $hasAll")
        return hasAll
    }

    /**
     * Get required basic permissions (excluding MediaProjection)
     */
    private fun getRequiredBasicPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // Audio recording permission
        permissions.add(Manifest.permission.RECORD_AUDIO)

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        return permissions
    }

    /**
     * Request basic permissions
     */
    private fun requestBasicPermissions() {
        val permissions = getRequiredBasicPermissions()
        Log.d(TAG, "Requesting basic permissions: ${permissions.joinToString()}")

        if (EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(
                activity,
                "Requesting basic permissions for QA recording...",
                Toast.LENGTH_SHORT
            ).show()
        }

        ActivityCompat.requestPermissions(
            activity,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Handle permission result - should be called from activity's onRequestPermissionsResult
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        Log.d(TAG, "handlePermissionResult() called with requestCode: $requestCode")

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Log.d(TAG, "All basic permissions granted: $allGranted")

            if (allGranted) {
                if (EnvironmentManager.isLoggingEnabled()) {
                    Toast.makeText(
                        activity,
                        "Basic permissions granted. QA recording ready!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Setup callbacks after permissions are granted
                setupCallbacksAfterPermissions()
            } else {
                Log.w(TAG, "Some basic permissions were denied")
                val message =
                    "Basic permissions required for QA recording. Please grant them in Settings."
                if (EnvironmentManager.isLoggingEnabled()) {
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
                callback?.onQARecordingError("Basic permissions denied")
            }
        } else {
            Log.d(TAG, "handlePermissionResult called with different requestCode: $requestCode")
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        if (!EnvironmentManager.isQASnapEnabled()) {
            return
        }

        try {
            Log.d(TAG, "Cleaning up QA Snap resources...")
            if (isRecording()) {
                stopRecording()
            }
            qaSnapRecorder = null
            pendingAutoStart = false
            Log.d(TAG, "QA Snap cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during QA Snap cleanup", e)
        }
    }

    // QASnapRecorder.RecordingListener implementation
    override fun onRecordingStarted() {
        Log.d(TAG, "QA Snap SDK onRecordingStarted callback received")
        callback?.onQARecordingStarted()
    }

    override fun onRecordingStopped(outputFile: File) {
        Log.d(
            TAG,
            "QA Snap SDK onRecordingStopped callback received - Video file: ${outputFile.absolutePath}"
        )
        // We'll get the log file from onLogCaptureStopped, so pass video file and null log file for now
        callback?.onQARecordingComplete(outputFile, null)
    }

    override fun onRecordingError(error: String) {
        Log.e(TAG, "QA Snap SDK onRecordingError callback received: $error")
        callback?.onQARecordingError(error)
    }

    override fun onLogCaptureStarted() {
        Log.d(TAG, "QA Snap SDK onLogCaptureStarted callback received")
        // Log capture starts automatically with recording in QA Snap SDK
    }

    override fun onLogCaptureStopped(outputFile: File) {
        Log.d(
            TAG,
            "QA Snap SDK onLogCaptureStopped callback received - Log file: ${outputFile.absolutePath}"
        )
        // When log capture stops, we should have both video and log files
        // Get the video output directory to find the latest video file
        val videoDir = qaSnapRecorder?.getOutputDirectory()
        val videoFile = videoDir?.listFiles()?.maxByOrNull { it.lastModified() }

        callback?.onQARecordingComplete(videoFile, outputFile)
    }

    override fun onLogCaptureError(error: String) {
        Log.e(TAG, "QA Snap SDK onLogCaptureError callback received: $error")
        callback?.onQARecordingError("Log capture error: $error")
    }
}