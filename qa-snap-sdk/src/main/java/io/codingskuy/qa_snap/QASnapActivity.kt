package io.codingskuy.qa_snap

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * QASnapActivity - Base activity with built-in QA Snap integration
 *
 * Extend this activity to automatically get QA recording capabilities:
 * - Automatic permission handling
 * - Automatic recording start
 * - Built-in stop controls
 * - File completion callbacks
 */
abstract class QASnapActivity : AppCompatActivity() {

    protected lateinit var qaSnapHelper: QASnapHelper
    private var autoStartRecording = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("QASnapActivity", "QASnapActivity onCreate() started")
        Log.d("QASnapActivity", "shouldAutoStartRecording(): ${shouldAutoStartRecording()}")

        // Initialize QA Snap with auto-start (can be overridden)
        Log.d("QASnapActivity", "Creating QASnapHelper with quickStart")
        qaSnapHelper = QASnapHelper.quickStart(this, shouldAutoStartRecording())

        Log.d("QASnapActivity", "Setting onRecordingReady callback")
        qaSnapHelper.onRecordingReady {
            Log.d("QASnapActivity", "onRecordingReady callback chain triggered")
            onQARecordingReady()
        }

        Log.d("QASnapActivity", "Setting onRecordingStarted callback")
        qaSnapHelper.onRecordingStarted {
            Log.d("QASnapActivity", "onRecordingStarted callback chain triggered")
            onQARecordingStarted()
        }

        Log.d("QASnapActivity", "Setting onComplete callback")
        qaSnapHelper.onComplete { videoFile, logFile ->
            Log.d("QASnapActivity", "onComplete callback chain triggered")
            onQARecordingComplete(videoFile, logFile)
        }

        Log.d("QASnapActivity", "QASnapActivity onCreate() completed - all callbacks set")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        qaSnapHelper.handlePermissionResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup QASnapHelper and reset singleton instance
        if (::qaSnapHelper.isInitialized) {
            qaSnapHelper.cleanup()
        }
        // Reset QASnapRecorder singleton instance to allow fresh initialization
        QASnapRecorder.resetInstance()
    }

    /**
     * Override this to control whether recording should start automatically
     * Default: true
     */
    protected open fun shouldAutoStartRecording(): Boolean = true

    /**
     * Called when QA recording is ready to start (after permissions granted)
     * Override this for custom behavior before recording starts
     */
    protected open fun onQARecordingReady() {
        // Default: do nothing, recording starts automatically
    }

    /**
     * Called when QA recording actually starts (after media projection permission granted)
     * Override this for custom behavior when recording begins
     */
    protected open fun onQARecordingStarted() {
        // Default: do nothing
    }

    /**
     * Called when QA recording completes (both video and logs saved)
     * Override this to handle completion (e.g., upload files, show dialog, etc.)
     */
    protected open fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        // Default: do nothing
    }

    /**
     * Start QA recording manually
     */
    protected fun startQARecording() {
        qaSnapHelper.startRecording()
    }

    /**
     * Stop QA recording manually
     */
    protected fun stopQARecording() {
        qaSnapHelper.stopRecording()
    }

    /**
     * Check if currently recording
     */
    protected fun isQARecording(): Boolean = qaSnapHelper.isRecording()

    /**
     * Get QASnapRecorder for advanced control
     */
    protected fun getQARecorder(): QASnapRecorder? = qaSnapHelper.getRecorder()
}