package io.codingskuy.qa_snap

import android.os.Bundle
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

        // Initialize QA Snap with auto-start (can be overridden)
        qaSnapHelper = QASnapHelper.quickStart(this, shouldAutoStartRecording())
            .onRecordingReady {
                onQARecordingReady()
            }
            .onComplete { videoFile, logFile ->
                onQARecordingComplete(videoFile, logFile)
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        qaSnapHelper.handlePermissionResult(requestCode, grantResults)
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