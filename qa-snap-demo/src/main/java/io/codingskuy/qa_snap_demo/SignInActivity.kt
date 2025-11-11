package io.codingskuy.qa_snap_demo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap_demo.databinding.ActivitySignInBinding

/**
 * SignInActivity - User authentication screen
 */
class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
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