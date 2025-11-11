package io.codingskuy.qa_snap_demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var isRecordingStarted = false
    private var arePermissionsGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize QA Snap SDK
        qaSnapRecorder = QASnapRecorder.initialize(this)
        setupRecordingListener()

        // Check and request permissions first
        if (checkPermissions()) {
            arePermissionsGranted = true
            startRecording()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // Audio recording permission
        permissions.add(Manifest.permission.RECORD_AUDIO)

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

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startRecording()
            } else {
                Toast.makeText(this, "Permissions required for recording", Toast.LENGTH_LONG).show()
                // Navigate anyway after showing error
                Handler(Looper.getMainLooper()).postDelayed({
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
        if (arePermissionsGranted) {
            qaSnapRecorder.startRecording()
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
        // Keep recording running when switching activities
    }
}