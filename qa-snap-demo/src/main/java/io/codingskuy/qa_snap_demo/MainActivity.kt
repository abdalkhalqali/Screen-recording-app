package io.codingskuy.qa_snap_demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.codingskuy.qa_snap.QASnapRecorder
import java.io.File

/**
 * MainActivity - Entry point that shows splash screen and navigates to SignIn
 */
class MainActivity : AppCompatActivity() {

    private lateinit var qaSnapRecorder: QASnapRecorder
    private val PERMISSION_REQUEST_CODE = 1001
    private var arePermissionsGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize QA Snap SDK
        qaSnapRecorder = QASnapRecorder.initialize(this)
        setupRecordingListener()

        Log.d("MainActivity", "Checking permissions...")
        // Check permissions first, don't start recording yet
        if (checkPermissions()) {
            arePermissionsGranted = true
            Log.d("MainActivity", "All permissions already granted, starting unified recording")
            Toast.makeText(this, "Starting recording...", Toast.LENGTH_SHORT).show()
            // All permissions already granted, start unified recording
            startRecording()
        } else {
            Log.d("MainActivity", "Permissions not granted, requesting permissions")
            Toast.makeText(this, "Requesting permissions...", Toast.LENGTH_SHORT).show()
            // Request permissions first, recording will start in onRequestPermissionsResult
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        val granted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        Log.d("MainActivity", "Permission check result: $granted")
        return granted
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

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("MainActivity", "Permission result received for request code: $requestCode")

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Log.d("MainActivity", "All permissions granted: $allGranted")

            if (allGranted) {
                arePermissionsGranted = true
                Toast.makeText(
                    this,
                    "Permissions granted, starting recording...",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d("MainActivity", "Starting recording after permission grant")
                startRecording()
            } else {
                Log.w("MainActivity", "Some permissions were denied")
                Toast.makeText(this, "Permissions required for recording", Toast.LENGTH_LONG).show()
                // Navigate anyway after showing error
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d("MainActivity", "Navigating to SignIn after permission denial")
                    navigateToSignIn()
                }, 2000)
            }
        }
    }

    private fun setupRecordingListener() {
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Recording started successfully")

                // Navigate to next screen after recording starts
                proceedToNextScreen()
            }

            override fun onRecordingStopped(outputFile: File) {
                Toast.makeText(
                    this@MainActivity,
                    "Recording saved: ${outputFile.name}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d("MainActivity", "Recording saved: ${outputFile.absolutePath}")
            }

            override fun onRecordingError(error: String) {
                Toast.makeText(this@MainActivity, "Recording error: $error", Toast.LENGTH_LONG)
                    .show()
                Log.e("MainActivity", "Recording error: $error")

                // Even if recording fails, proceed to next screen after showing error
                Handler(Looper.getMainLooper()).postDelayed({
                    proceedToNextScreen()
                }, 2000)
            }

            override fun onLogCaptureStarted() {
                Log.d("MainActivity", "Log capture started automatically")
            }

            override fun onLogCaptureStopped(outputFile: File) {
                Toast.makeText(
                    this@MainActivity,
                    "Logs saved: ${outputFile.name}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d("MainActivity", "Log capture saved: ${outputFile.absolutePath}")
            }

            override fun onLogCaptureError(error: String) {
                Log.e("MainActivity", "Log capture error: $error")
            }
        })
    }

    private fun startRecording() {
        if (!arePermissionsGranted) {
            Log.w("MainActivity", "Attempting to start recording without permissions")
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            return
        }

        if (checkPermissions()) {
            Log.d("MainActivity", "Starting unified recording (video + logs)")
            // Start unified recording (video + logs automatically)
            qaSnapRecorder.startRecording()
        } else {
            Log.w("MainActivity", "Permissions check failed in startRecording")
            Toast.makeText(this, "Missing permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun proceedToNextScreen() {
        // Add a small delay to show the status messages
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                navigateToSignIn()
            }
        }, 1500) // 1.5 second delay to show the toast messages
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }
}