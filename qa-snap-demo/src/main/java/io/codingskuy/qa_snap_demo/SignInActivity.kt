package io.codingskuy.qa_snap_demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import io.codingskuy.qa_snap.QASnapRecorder
import io.codingskuy.qa_snap_demo.base.BaseActivity
import io.codingskuy.qa_snap_demo.databinding.ActivitySignInBinding

/**
 * SignInActivity - User authentication screen
 */
class SignInActivity : BaseActivity() {

    companion object {
        private const val TAG = "SignInActivity"
    }

    private lateinit var binding: ActivitySignInBinding

    // Continue recording - don't stop when SignInActivity is destroyed  
    override val shouldCleanupOnDestroy: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "SignInActivity onCreate")

        // Check if recording is active from previous activity
        checkRecordingStatus()

        setupClickListeners()
    }

    private fun checkRecordingStatus() {
        val recorder = QASnapRecorder.getInstance()
        val isRecording = recorder?.isRecording() ?: false
        Log.d(TAG, "Recording status: $isRecording")

        if (isRecording) {
            Log.d(TAG, "Recording is active, continuing in background")
            Toast.makeText(this, "📹 QA Recording continues in background", Toast.LENGTH_SHORT)
                .show()
        } else {
            Log.d(TAG, "No active recording found")
        }
    }

    private fun setupClickListeners() {
        binding.btnSignIn.setOnClickListener {
            performSignIn()
        }

        binding.btnSkip.setOnClickListener {
            navigateToHome()
        }
    }

    private fun performSignIn() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Simulate sign in process
        binding.btnSignIn.isEnabled = false
        binding.btnSignIn.text = "Signing In..."

        // Simulate network delay
        binding.btnSignIn.postDelayed({
            // For demo purposes, any email/password combination works
            Toast.makeText(this, "Sign In Successful!", Toast.LENGTH_SHORT).show()
            navigateToHome()
        }, 1500)
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}