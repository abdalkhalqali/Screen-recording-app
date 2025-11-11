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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.codingskuy.qa_snap.QASnapRecorder
import java.io.File

/**
 * MainActivity - Entry point that shows splash screen and navigates to SignIn
 */
class MainActivity : AppCompatActivity() {

    private lateinit var qaSnapRecorder: QASnapRecorder
    private val PERMISSION_REQUEST_CODE = 1001
    private var isRecordingStarted = false
    private var arePermissionsGranted = false

    // Application lifecycle observer to handle force close
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            Log.d("MainActivity", "App went to background or was force closed")
            // Emergency stop recording if app is force closed or goes to background
            if (isRecordingStarted) {
                QASnapRecorder.emergencyStopRecording(this@MainActivity)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            Log.d("MainActivity", "App process is being destroyed")
            // Final emergency stop
            QASnapRecorder.emergencyStopRecording(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register app lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        // Initialize QA Snap SDK
        qaSnapRecorder = QASnapRecorder.initialize(this)
        setupRecordingListener()

        Log.d("MainActivity", "Checking permissions...")
        // Check permissions first, don't start recording yet
        if (checkPermissions()) {
            arePermissionsGranted = true
            Log.d("MainActivity", "All permissions already granted, starting recording")
            Toast.makeText(this, "Starting recording...", Toast.LENGTH_SHORT).show()
            // All permissions already granted, start recording
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
                // Recording started successfully
                isRecordingStarted = true
                Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()

                // Now that recording has started, proceed to next screen
                proceedToNextScreen()
            }

            override fun onRecordingStopped(outputFile: File) {
                // Recording stopped and file saved
                Toast.makeText(
                    this@MainActivity,
                    "Recording saved: ${outputFile.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onRecordingError(error: String) {
                // Handle recording error
                Toast.makeText(this@MainActivity, "Recording error: $error", Toast.LENGTH_LONG)
                    .show()

                // Even if recording fails, proceed to next screen after showing error
                Handler(Looper.getMainLooper()).postDelayed({
                    proceedToNextScreen()
                }, 2000)
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
            qaSnapRecorder.startRecording()
        } else {
            Log.w("MainActivity", "Permissions check failed in startRecording")
            Toast.makeText(this, "Missing permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun proceedToNextScreen() {
        // Add a small delay to show the "Recording started" message
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                navigateToSignIn()
            }
        }, 1000) // 1 second delay to show the toast
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister app lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        // Keep recording running when switching activities
    }
}