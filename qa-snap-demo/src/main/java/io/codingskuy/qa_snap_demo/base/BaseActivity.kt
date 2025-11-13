package io.codingskuy.qa_snap_demo.base

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap_demo.BuildConfig
import io.codingskuy.qa_snap_demo.utils.EnvironmentManager
import java.io.File

/**
 * BaseActivity - Base class that conditionally enables QA Snap based on environment
 * 
 * This class provides:
 * - Environment-aware QA Snap integration
 * - Automatic QA recording for staging environment only
 * - Fallback to regular AppCompatActivity for other environments
 * - Centralized environment logging
 */
abstract class BaseActivity : AppCompatActivity(), QASnapCallback {

    companion object {
        private const val TAG = "BaseActivity"
    }

    protected open val shouldCleanupOnDestroy: Boolean = true
    private var qaSnapHelper: QASnapHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log environment info
        EnvironmentManager.logEnvironmentInfo()
        
        // Initialize QA Snap only if enabled for current environment
        if (EnvironmentManager.isQASnapEnabled()) {
            initializeQASnap()
        } else {
            logEnvironmentMessage()
        }
    }

    private fun initializeQASnap() {
        try {
            Log.d(TAG, "Initializing QA Snap for ${EnvironmentManager.getEnvironmentDisplayName()} environment")
            qaSnapHelper = QASnapHelper(this)
            qaSnapHelper?.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize QA Snap", e)
            if (EnvironmentManager.isLoggingEnabled()) {
                Toast.makeText(this, "QA Snap initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun logEnvironmentMessage() {
        val message = when (EnvironmentManager.getCurrentEnvironment()) {
            EnvironmentManager.Environment.DEVELOPMENT -> "Development environment - QA Snap disabled"
            EnvironmentManager.Environment.PRODUCTION -> "Production environment - QA Snap disabled"
            else -> "QA Snap not available in this environment"
        }
        
        Log.d(TAG, message)
        if (EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Override this method to control auto-start recording behavior
     * Default: Don't auto-start to avoid issues
     */
    override fun shouldAutoStartRecording(): Boolean {
        // Only auto-start for specific activities that are ready for it
        return false // Changed from true to false to prevent auto-start issues
    }

    /**
     * Called when QA recording is ready to start (only in staging)
     */
    override fun onQARecordingReady() {
        Log.d(TAG, "QA Recording is ready")
        if (EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(this, "QA Recording ready - Staging Environment", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Called when QA recording starts (only in staging)
     */
    override fun onQARecordingStarted() {
        Log.d(TAG, "QA Recording started")
        if (EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(this, "✅ QA Recording started!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Called when QA recording completes (only in staging)
     */
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        val message = when {
            videoFile != null && logFile != null -> "QA Session completed! Files saved."
            videoFile != null -> "QA Video saved: ${videoFile.name}"
            logFile != null -> "QA Logs saved: ${logFile.name}"
            else -> "QA Recording completed"
        }
        
        Log.d(TAG, message)
        if (EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Called when QA recording encounters an error (only in staging)
     */
    override fun onQARecordingError(error: String) {
        Log.e(TAG, "QA Recording error: $error")
        if (EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(this, "QA Recording error: $error", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Start QA recording manually (only works in staging)
     */
    protected fun startQARecording() {
        if (EnvironmentManager.isQASnapEnabled()) {
            qaSnapHelper?.startRecording()
        } else {
            Log.w(TAG, "QA Recording not available in ${EnvironmentManager.getEnvironmentDisplayName()} environment")
        }
    }

    /**
     * Stop QA recording (only works in staging)
     */
    protected fun stopQARecording() {
        if (EnvironmentManager.isQASnapEnabled()) {
            qaSnapHelper?.stopRecording()
        } else {
            Log.w(TAG, "QA Recording not available in ${EnvironmentManager.getEnvironmentDisplayName()} environment")
        }
    }

    /**
     * Check if QA recording is active
     */
    protected fun isQARecording(): Boolean {
        return if (EnvironmentManager.isQASnapEnabled()) {
            qaSnapHelper?.isRecording() ?: false
        } else {
            false
        }
    }

    override fun onDestroy() {
        if (shouldCleanupOnDestroy && EnvironmentManager.isQASnapEnabled()) {
            qaSnapHelper?.cleanup()
        }
        super.onDestroy()
    }

    /**
     * Handle permission results from QA Snap Helper
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward permission results to QA Snap Helper
        if (EnvironmentManager.isQASnapEnabled()) {
            qaSnapHelper?.handlePermissionResult(requestCode, grantResults)
        }
    }

    /**
     * Get environment info for debugging
     */
    protected fun getEnvironmentInfo(): Map<String, Any> {
        return mapOf(
            "environment" to EnvironmentManager.getEnvironmentDisplayName(),
            "baseUrl" to EnvironmentManager.getBaseUrl(),
            "qaSnapEnabled" to EnvironmentManager.isQASnapEnabled(),
            "loggingEnabled" to EnvironmentManager.isLoggingEnabled(),
            "applicationId" to BuildConfig.APPLICATION_ID,
            "versionName" to BuildConfig.VERSION_NAME,
            "features" to EnvironmentManager.getEnvironmentFeatures()
        )
    }
}