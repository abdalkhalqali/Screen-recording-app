package io.codingskuy.qa_snap_demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import io.codingskuy.qa_snap_demo.base.BaseActivity
import io.codingskuy.qa_snap_demo.utils.EnvironmentManager
import java.io.File

/**
 * MainActivity - Entry point with environment-aware QA Snap integration
 *
 * This demonstrates the new multi-environment approach:
 * - Extends BaseActivity (environment-aware)
 * - QA Snap only works in staging environment
 * - Automatic environment detection and configuration
 * - Clean fallback for development and production
 */
class MainActivity : BaseActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY = 3000L // 3 seconds splash
    }

    private var hasNavigated = false
    private var isRecordingStarted = false

    override val shouldCleanupOnDestroy: Boolean = false // Continue recording to next activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(
            TAG,
            "MainActivity onCreate - Environment: ${EnvironmentManager.getEnvironmentDisplayName()}"
        )
        Log.d(TAG, "QA Snap enabled: ${EnvironmentManager.isQASnapEnabled()}")
        Log.d(TAG, "Is fresh app launch? ${savedInstanceState == null}")
        Log.d(TAG, "Process ID: ${android.os.Process.myPid()}")

        // Show environment info to user
        showEnvironmentInfo()

        // Navigation will be triggered by QA callbacks or fallback timer
        if (!EnvironmentManager.isQASnapEnabled()) {
            // No QA Snap in this environment, start navigation timer immediately
            Log.d(TAG, "QA Snap disabled, starting immediate navigation")
            startNavigationTimer()
        }
        // If QA Snap is enabled, we'll handle it in onQARecordingReady()
    }

    private fun showEnvironmentInfo() {
        val environmentName = EnvironmentManager.getEnvironmentDisplayName()
        val appName = EnvironmentManager.getAppName(this)

        supportActionBar?.title = appName

        val message = when (EnvironmentManager.getCurrentEnvironment()) {
            EnvironmentManager.Environment.DEVELOPMENT -> " Development Environment"
            EnvironmentManager.Environment.STAGING -> " Staging Environment - QA Recording Available"
            EnvironmentManager.Environment.PRODUCTION -> " Production Environment"
        }

        Log.d(TAG, "Environment: $environmentName")
        if (EnvironmentManager.isLoggingEnabled()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    // Override to manually control when to start recording
    override fun shouldAutoStartRecording(): Boolean {
        // Enable auto-start so QASnapHelper handles the proper permission flow
        return true
    }

    // Called when QA recording is ready to start (only in staging)
    override fun onQARecordingReady() {
        super.onQARecordingReady()
        Log.d(TAG, "onQARecordingReady() - Staging environment detected")

        // Don't manually start recording here - let shouldAutoStartRecording() handle it
        // The QASnapHelper will handle the permission flow and auto-start recording
        Log.d(TAG, "QA recording ready - auto-start is enabled, waiting for proper permission flow")
    }

    // Called when QA recording actually starts (only in staging)
    override fun onQARecordingStarted() {
        super.onQARecordingStarted()
        Log.d(TAG, "onQARecordingStarted() - Recording is now active!")
        isRecordingStarted = true

        // Now start navigation timer after recording starts
        Log.d(TAG, "Recording started, now starting navigation timer")
        startNavigationTimer()
    }

    // Called when QA recording completes (only in staging)
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        super.onQARecordingComplete(videoFile, logFile)
        Log.d(TAG, "QA recording session completed")

        // Show additional completion info in staging
        if (EnvironmentManager.isLoggingEnabled()) {
            val details = buildString {
                append("QA Session Complete!\n")
                videoFile?.let { append("Video: ${it.name}\n") }
                logFile?.let { append("Logs: ${it.name}\n") }
                append("Environment: ${EnvironmentManager.getEnvironmentDisplayName()}")
            }
            Toast.makeText(this, details, Toast.LENGTH_LONG).show()
        }
    }

    override fun onQARecordingError(error: String) {
        super.onQARecordingError(error)
        Log.e(TAG, "QA Recording error in MainActivity: $error")

        // Continue with app flow even if recording fails
        if (!hasNavigated) {
            Log.d(TAG, "Recording failed, continuing with navigation")
            startNavigationTimer()
        }
    }

    private fun startNavigationTimer() {
        Log.d(TAG, "Starting navigation timer for ${SPLASH_DELAY}ms")

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Navigation timer expired - attempting navigation")
            navigateToSignIn()
        }, SPLASH_DELAY)
    }

    private fun navigateToSignIn() {
        if (hasNavigated) {
            Log.d(TAG, "Navigation already done, skipping")
            return
        }

        hasNavigated = true
        Log.d(TAG, "Navigating to SignInActivity")

        try {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
            Log.d(TAG, "Navigation completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to SignInActivity", e)
            hasNavigated = false // Reset on error
        }
    }

    // Debug methods for development
    private fun logEnvironmentDetails() {
        if (EnvironmentManager.isLoggingEnabled()) {
            val info = getEnvironmentInfo()
            Log.d(TAG, "=== ENVIRONMENT DETAILS ===")
            info.forEach { (key, value) ->
                Log.d(TAG, "$key: $value")
            }
            Log.d(TAG, "===========================")
        }
    }

    override fun onResume() {
        super.onResume()

        // Log additional details in development/staging
        if (EnvironmentManager.getCurrentEnvironment() != EnvironmentManager.Environment.PRODUCTION) {
            logEnvironmentDetails()
        }
    }
}