package io.codingskuy.qa_snap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * QASnapHelper - Simplifies QA Snap SDK integration with minimal code
 *
 * This helper handles all boilerplate code including:
 * - Permission management
 * - SDK initialization
 * - Default listeners
 * - Error handling
 */
class QASnapHelper(private val activity: AppCompatActivity) {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 9001
        private const val TAG = "QASnapHelper"

        /**
         * Quick start method for instant QA recording
         * @param activity The activity context
         * @param autoStart Whether to automatically start recording after permissions
         * @return QASnapHelper instance
         */
        fun quickStart(activity: AppCompatActivity, autoStart: Boolean = true): QASnapHelper {
            Log.d(TAG, "quickStart() called with autoStart: $autoStart")
            val helper = QASnapHelper(activity)
            // DON'T initialize immediately - wait for callbacks to be set first
            helper.autoStartAfterPermissions = autoStart
            Log.d(TAG, "QASnapHelper created, initialization deferred until callbacks are set")
            return helper
        }
    }

    private var qaSnapRecorder: QASnapRecorder? = null
    private var onRecordingReady: (() -> Unit)? = null
    private var onRecordingStarted: (() -> Unit)? = null
    private var onComplete: ((videoFile: File?, logFile: File?) -> Unit)? = null
    private var onError: ((error: String) -> Unit)? = null
    private var autoStartAfterPermissions = true
    private var isInitialized = false

    /**
     * Initialize QA Snap with automatic permission handling
     * @param autoStart Whether to start recording automatically after permissions granted
     */
    fun initialize(autoStart: Boolean = true) {
        if (isInitialized) {
            Log.d(TAG, "QASnapHelper already initialized, skipping")
            return
        }

        Log.d(TAG, "Initializing QASnapHelper with autoStart: $autoStart")
        Log.d(TAG, "Activity: ${activity::class.java.simpleName}")
        Log.d(TAG, "Activity finishing: ${activity.isFinishing}")
        autoStartAfterPermissions = autoStart

        // Initialize QASnapRecorder first
        qaSnapRecorder = QASnapRecorder.initialize(activity)
        Log.d(TAG, "QASnapRecorder initialized: ${qaSnapRecorder != null}")

        // Setup listener before any operations
        setupDefaultListener()

        // Mark as initialized
        isInitialized = true

        // Check if we have basic permissions first
        if (hasAllPermissions()) {
            Log.d(TAG, "All basic permissions already granted")
            if (autoStart) {
                Log.d(TAG, "Starting recording automatically (which will request MediaProjection)")
                // Don't call startRecording() directly here - let onRecordingReady handle it
                Log.d(TAG, "Calling onRecordingReady callback")
                onRecordingReady?.invoke()
            } else {
                Log.d(TAG, "Permissions granted, calling onRecordingReady")
                onRecordingReady?.invoke()
            }
        } else {
            Log.d(TAG, "Basic permissions not granted, requesting permissions")
            requestPermissions()
        }
    }

    /**
     * Set callback for when recording is ready to start (after permissions granted)
     */
    fun onRecordingReady(callback: () -> Unit): QASnapHelper {
        Log.d(TAG, "onRecordingReady callback set")
        onRecordingReady = callback
        checkAndInitialize()
        return this
    }

    /**
     * Set callback for when recording is started
     */
    fun onRecordingStarted(callback: () -> Unit): QASnapHelper {
        Log.d(TAG, "onRecordingStarted callback set")
        onRecordingStarted = callback
        checkAndInitialize()
        return this
    }

    /**
     * Set callback for when recording completes
     */
    fun onComplete(callback: (videoFile: File?, logFile: File?) -> Unit): QASnapHelper {
        Log.d(TAG, "onComplete callback set")
        onComplete = callback
        checkAndInitialize()
        return this
    }

    /**
     * Set callback for when recording encounters an error
     */
    fun onError(callback: (error: String) -> Unit): QASnapHelper {
        Log.d(TAG, "onError callback set")
        onError = callback
        return this
    }

    /**
     * Check if all callbacks are set and initialize if ready
     */
    private fun checkAndInitialize() {
        // Initialize when we have at least the onRecordingReady callback
        if (!isInitialized && onRecordingReady != null) {
            Log.d(TAG, "Essential callbacks set, triggering initialization")
            initialize(autoStartAfterPermissions)
        }
    }

    /**
     * Start recording manually
     */
    fun startRecording() {
        Log.d(TAG, "startRecording() called")

        if (qaSnapRecorder == null) {
            Log.e(TAG, "QASnapRecorder is null, reinitializing...")
            qaSnapRecorder = QASnapRecorder.initialize(activity)
            setupDefaultListener()
        }

        if (hasAllPermissions()) {
            Log.d(TAG, "All basic permissions granted, starting QA recording")

            // The startRecording() method will handle MediaProjection permission internally
            // This will show the MediaProjection permission dialog
            qaSnapRecorder?.startRecording()

            Log.d(TAG, "QA recording start requested (MediaProjection dialog should appear)")
        } else {
            Log.w(TAG, "Basic permissions not granted, showing toast and requesting permissions")
            Toast.makeText(activity, "Permissions required for recording", Toast.LENGTH_SHORT)
                .show()
            requestPermissions()
        }
    }

    /**
     * Start recording with explicit permission flow
     * This ensures MediaProjection permission is properly handled
     */
    fun startRecordingWithPermissionFlow() {
        Log.d(TAG, "startRecordingWithPermissionFlow() called")

        if (!hasAllPermissions()) {
            Log.d(TAG, "Basic permissions missing, requesting first")
            Toast.makeText(activity, "Granting basic permissions first...", Toast.LENGTH_SHORT)
                .show()
            requestPermissions()
            return
        }

        Log.d(TAG, "Basic permissions OK, starting recording (will request MediaProjection)")
        qaSnapRecorder?.startRecording() // This will show MediaProjection dialog
    }

    /**
     * Stop recording manually
     */
    fun stopRecording() {
        Log.d(TAG, "stopRecording() called")
        qaSnapRecorder?.stopRecording()
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean {
        // If we don't have a recorder reference, try to get existing instance
        if (qaSnapRecorder == null) {
            qaSnapRecorder = QASnapRecorder.getInstance()
        }

        val isRecording = qaSnapRecorder?.isRecording() ?: false
        Log.d(TAG, "isRecording(): $isRecording")
        return isRecording
    }

    /**
     * Get QASnapRecorder instance for advanced control
     */
    fun getRecorder(): QASnapRecorder? = qaSnapRecorder

    /**
     * Cleanup method to properly release resources and reset state
     * Should be called when the activity is being destroyed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up QASnapHelper...")

        // Reset callbacks
        onRecordingReady = null
        onRecordingStarted = null
        onComplete = null
        onError = null

        // Stop any ongoing recording
        if (qaSnapRecorder?.isRecording() == true) {
            Log.d(TAG, "Stopping ongoing recording during cleanup")
            qaSnapRecorder?.stopRecording()
        }

        // Clear recorder reference
        qaSnapRecorder = null

        // Reset initialization flag
        isInitialized = false

        Log.d(TAG, "QASnapHelper cleanup completed")
    }

    /**
     * Handle permission result (call this from your activity's onRequestPermissionsResult)
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        Log.d(TAG, "handlePermissionResult() called with requestCode: $requestCode")

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Log.d(TAG, "All permissions granted: $allGranted")

            if (allGranted) {
                Toast.makeText(activity, "Permissions granted", Toast.LENGTH_SHORT).show()
                if (autoStartAfterPermissions) {
                    Log.d(TAG, "Auto-starting recording after permissions granted")
                    startRecordingWithPermissionFlow()
                } else {
                    Log.d(TAG, "Permissions granted, calling onRecordingReady")
                    onRecordingReady?.invoke()
                }
            } else {
                Log.w(TAG, "Some permissions were denied")
                Toast.makeText(activity, "Permissions required for QA recording", Toast.LENGTH_LONG)
                    .show()
            }
        } else {
            Log.d(TAG, "handlePermissionResult called with different requestCode: $requestCode")
        }
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        val hasAll = permissions.all { permission ->
            val granted = ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: $granted")
            granted
        }
        Log.d(TAG, "hasAllPermissions(): $hasAll")
        return hasAll
    }

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        Log.d(TAG, "Requesting permissions: ${permissions.joinToString()}")
        ActivityCompat.requestPermissions(
            activity,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun getRequiredPermissions(): List<String> {
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

    private fun setupDefaultListener() {
        Log.d(TAG, "Setting up default listener")
        Log.d(TAG, "QASnapRecorder instance: ${qaSnapRecorder != null}")

        if (qaSnapRecorder == null) {
            Log.e(TAG, "Cannot setup listener - QASnapRecorder is null!")
            return
        }

        var videoFile: File? = null
        var logFile: File? = null

        qaSnapRecorder?.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                Log.d(TAG, "Recording started callback received")
                Log.d(TAG, "onRecordingStarted callback available: ${onRecordingStarted != null}")
                Toast.makeText(activity, "QA Recording started", Toast.LENGTH_SHORT).show()
                onRecordingStarted?.invoke()
                Log.d(TAG, "onRecordingStarted callback invoked")
            }

            override fun onRecordingStopped(outputFile: File) {
                Log.d(TAG, "Recording stopped callback received: ${outputFile.absolutePath}")
                videoFile = outputFile
                Toast.makeText(activity, "Video saved: ${outputFile.name}", Toast.LENGTH_SHORT)
                    .show()
                checkCompletion(videoFile, logFile)
            }

            override fun onRecordingError(error: String) {
                Log.e(TAG, "Recording error: $error")
                Toast.makeText(activity, "Recording error: $error", Toast.LENGTH_LONG).show()
                onError?.invoke(error)
            }

            override fun onLogCaptureStarted() {
                Log.d(TAG, "Log capture started callback received")
                // Silent - no need to show toast for automatic log capture
            }

            override fun onLogCaptureStopped(outputFile: File) {
                Log.d(TAG, "Log capture stopped callback received: ${outputFile.absolutePath}")
                logFile = outputFile
                Toast.makeText(activity, "Logs saved: ${outputFile.name}", Toast.LENGTH_SHORT)
                    .show()
                checkCompletion(videoFile, logFile)
            }

            override fun onLogCaptureError(error: String) {
                Log.e(TAG, "Log capture error: $error")
                Toast.makeText(activity, "Log capture error: $error", Toast.LENGTH_SHORT).show()
                onError?.invoke(error)
            }
        })

        Log.d(TAG, "Default listener setup completed")
        Log.d(
            TAG,
            "Available callbacks - onRecordingReady: ${onRecordingReady != null}, onRecordingStarted: ${onRecordingStarted != null}, onComplete: ${onComplete != null}, onError: ${onError != null}"
        )
    }

    private fun checkCompletion(video: File?, logs: File?) {
        Log.d(TAG, "checkCompletion called - video: ${video?.name}, logs: ${logs?.name}")
        // Call completion callback when both files are available or when recording stops
        if ((video != null || logs != null) && onComplete != null) {
            Log.d(TAG, "Calling completion callback")
            onComplete?.invoke(video, logs)
        }
    }

    /**
     * Connect to existing QASnapRecorder instance without initializing new session
     * Use this when you want to manage an already active recording from another activity
     */
    fun connectToExistingSession(): QASnapHelper {
        Log.d(TAG, "Connecting to existing QASnapRecorder session")

        // Get existing instance
        qaSnapRecorder = QASnapRecorder.getInstance()

        if (qaSnapRecorder != null) {
            Log.d(TAG, "Connected to existing QASnapRecorder instance")
            Log.d(TAG, "Recording active: ${qaSnapRecorder?.isRecording()}")
            Log.d(TAG, "Logs active: ${qaSnapRecorder?.isCapturingLogs()}")

            // Setup listener for the existing session
            setupDefaultListener()
        } else {
            Log.w(TAG, "No existing QASnapRecorder instance found")
        }

        return this
    }
}