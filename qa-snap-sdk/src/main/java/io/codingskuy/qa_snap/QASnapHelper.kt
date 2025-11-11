package io.codingskuy.qa_snap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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

        /**
         * Quick start method for instant QA recording
         * @param activity The activity context
         * @param autoStart Whether to automatically start recording after permissions
         * @return QASnapHelper instance
         */
        fun quickStart(activity: AppCompatActivity, autoStart: Boolean = true): QASnapHelper {
            val helper = QASnapHelper(activity)
            helper.initialize(autoStart)
            return helper
        }
    }

    private var qaSnapRecorder: QASnapRecorder? = null
    private var onRecordingReady: (() -> Unit)? = null
    private var onComplete: ((videoFile: File?, logFile: File?) -> Unit)? = null
    private var autoStartAfterPermissions = true

    /**
     * Initialize QA Snap with automatic permission handling
     * @param autoStart Whether to start recording automatically after permissions granted
     */
    fun initialize(autoStart: Boolean = true) {
        autoStartAfterPermissions = autoStart
        qaSnapRecorder = QASnapRecorder.initialize(activity)
        setupDefaultListener()

        if (hasAllPermissions()) {
            if (autoStart) {
                startRecording()
            } else {
                onRecordingReady?.invoke()
            }
        } else {
            requestPermissions()
        }
    }

    /**
     * Set callback for when recording is ready to start (after permissions granted)
     */
    fun onRecordingReady(callback: () -> Unit): QASnapHelper {
        onRecordingReady = callback
        return this
    }

    /**
     * Set callback for when recording completes
     */
    fun onComplete(callback: (videoFile: File?, logFile: File?) -> Unit): QASnapHelper {
        onComplete = callback
        return this
    }

    /**
     * Start recording manually
     */
    fun startRecording() {
        if (hasAllPermissions()) {
            qaSnapRecorder?.startRecording()
        } else {
            Toast.makeText(activity, "Permissions required for recording", Toast.LENGTH_SHORT)
                .show()
            requestPermissions()
        }
    }

    /**
     * Stop recording manually
     */
    fun stopRecording() {
        qaSnapRecorder?.stopRecording()
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = qaSnapRecorder?.isRecording() ?: false

    /**
     * Get QASnapRecorder instance for advanced control
     */
    fun getRecorder(): QASnapRecorder? = qaSnapRecorder

    /**
     * Handle permission result (call this from your activity's onRequestPermissionsResult)
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                Toast.makeText(activity, "Permissions granted", Toast.LENGTH_SHORT).show()
                if (autoStartAfterPermissions) {
                    startRecording()
                } else {
                    onRecordingReady?.invoke()
                }
            } else {
                Toast.makeText(activity, "Permissions required for QA recording", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
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
        var videoFile: File? = null
        var logFile: File? = null

        qaSnapRecorder?.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                Toast.makeText(activity, "QA Recording started", Toast.LENGTH_SHORT).show()
            }

            override fun onRecordingStopped(outputFile: File) {
                videoFile = outputFile
                Toast.makeText(activity, "Video saved: ${outputFile.name}", Toast.LENGTH_SHORT)
                    .show()
                checkCompletion(videoFile, logFile)
            }

            override fun onRecordingError(error: String) {
                Toast.makeText(activity, "Recording error: $error", Toast.LENGTH_LONG).show()
            }

            override fun onLogCaptureStarted() {
                // Silent - no need to show toast for automatic log capture
            }

            override fun onLogCaptureStopped(outputFile: File) {
                logFile = outputFile
                Toast.makeText(activity, "Logs saved: ${outputFile.name}", Toast.LENGTH_SHORT)
                    .show()
                checkCompletion(videoFile, logFile)
            }

            override fun onLogCaptureError(error: String) {
                Toast.makeText(activity, "Log capture error: $error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun checkCompletion(video: File?, logs: File?) {
        // Call completion callback when both files are available or when recording stops
        if ((video != null || logs != null) && onComplete != null) {
            onComplete?.invoke(video, logs)
        }
    }
}