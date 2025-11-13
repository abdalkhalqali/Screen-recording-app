package io.codingskuy.qa_snap_demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import io.codingskuy.qa_snap.QASnapActivity
import java.io.File

/**
 * MainActivity - Entry point using simplified QA Snap integration
 *
 * This demonstrates how easy it is to integrate QA Snap SDK:
 * - Extend QASnapActivity (zero setup!)
 * - Recording starts automatically
 * - Permissions handled automatically
 * - Files saved automatically
 */
class MainActivity : QASnapActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY = 3000L // 3 seconds splash
    }

    private var hasNavigated = false
    private var isRecordingStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "MainActivity onCreate - initializing QA recording")
        Log.d(TAG, "Is this a fresh app launch? ${savedInstanceState == null}")
        Log.d(TAG, "Current process ID: ${android.os.Process.myPid()}")
        Log.d(TAG, "About to call super.onCreate() which will initialize QASnapHelper")

        // Don't navigate immediately - wait for recording to be ready
        // Navigation will be triggered by onQARecordingReady()

        Log.d(TAG, "MainActivity onCreate completed")
    }

    // Override to enable recording always for demo purposes
    override fun shouldAutoStartRecording(): Boolean {
        Log.d(TAG, "shouldAutoStartRecording() -> true")
        return true // Always record in demo app
    }

    // Called when QA recording is ready to start (after permissions granted)
    override fun onQARecordingReady() {
        Log.d(TAG, "onQARecordingReady() called")
        Log.d(TAG, "Current thread: ${Thread.currentThread().name}")
        Log.d(TAG, "Activity finishing: $isFinishing")
        Toast.makeText(this, "QA Recording is ready! Starting recording...", Toast.LENGTH_SHORT)
            .show()

        // Start recording explicitly (this will trigger MediaProjection permission)
        Log.d(TAG, "Calling startQARecording() to trigger MediaProjection permission")
        startQARecording()

        // ALWAYS start navigation timer - don't wait for recording
        Log.d(TAG, "Starting navigation timer immediately")
        startNavigationTimer()
    }

    // Called when QA recording actually starts
    override fun onQARecordingStarted() {
        Log.d(TAG, "onQARecordingStarted() called - Recording is now active!")
        Log.d(TAG, "Current thread: ${Thread.currentThread().name}")
        Log.d(TAG, "Activity finishing: $isFinishing")
        isRecordingStarted = true
        Toast.makeText(this, "✅ QA Recording is now active!", Toast.LENGTH_SHORT).show()
        navigateToSignIn()

        // Recording has started successfully, we can proceed with normal flow
        // Navigation will happen via the navigation timer
        Log.d(TAG, "Recording started successfully, navigation timer will handle navigation")
    }

    // Called when QA recording completes (both video and logs saved)
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        val message = when {
            videoFile != null && logFile != null -> "QA Session completed! Video & logs saved."
            videoFile != null -> "Video saved: ${videoFile.name}"
            logFile != null -> "Logs saved: ${logFile.name}"
            else -> "QA Recording completed"
        }

        Log.d(TAG, "onQARecordingComplete() - $message")
        Log.d(TAG, "Current thread: ${Thread.currentThread().name}")
        Log.d(TAG, "Activity finishing: $isFinishing")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Optional: You could upload files, show detailed dialog, etc.
        // uploadQAFilesToServer(videoFile, logFile)
        // showDetailedCompletionDialog(videoFile, logFile)
    }

    private fun onQARecordingError(error: String) {
        Log.e(TAG, "QA Recording error: $error")
        Toast.makeText(this, "Recording error: $error", Toast.LENGTH_LONG).show()

        // Even if recording fails, continue with app flow after splash delay
        if (!hasNavigated) {
            Log.d(TAG, "Recording failed, but continuing with navigation after delay")
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
        Log.d(TAG, "Setting hasNavigated = true, navigating to SignInActivity")

        try {
            val intent = Intent(this, SignInActivity::class.java)
            Log.d(TAG, "Created intent for SignInActivity")
            startActivity(intent)
            Log.d(TAG, "Started SignInActivity, calling finish()")
            finish()
            Log.d(TAG, "MainActivity.finish() called")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to SignInActivity", e)
            hasNavigated = false // Reset on error
        }
    }

    private fun startFallbackNavigation() {
        Log.d(TAG, "Starting fallback navigation timer (10 seconds)")

        Handler(Looper.getMainLooper()).postDelayed({
            if (!hasNavigated && !isFinishing) {
                Log.w(TAG, "Fallback navigation triggered - recording took too long")
                Toast.makeText(this, "Continuing without recording...", Toast.LENGTH_SHORT).show()
                navigateToSignIn()
            }
        }, 10000L) // 10-second fallback
    }

    // Optional: Manual control methods if needed for debugging
    private fun startRecordingManually() {
        Log.d(TAG, "Manual start recording requested")
        startQARecording()
    }

    private fun stopRecordingManually() {
        Log.d(TAG, "Manual stop recording requested")
        stopQARecording()
    }

    private fun checkRecordingStatus() {
        val isRecording = isQARecording()
        Log.d(TAG, "Recording status check: $isRecording")
        Toast.makeText(this, "Recording status: $isRecording", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity onDestroy")
        super.onDestroy()
    }
}