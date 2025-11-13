package io.codingskuy.qa_snap_demo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap_demo.utils.EnvironmentManager

/**
 * QATesterOnboardingActivity - Onboarding page for QA Software Testers
 * 
 * This activity only appears in STAGING environment and provides:
 * 1. Welcome message for testers
 * 2. Instructions for QA Snap feature usage
 * 3. Test case information input form
 * 4. Guidance to select "Entire screen" instead of "Single app"
 * 
 * All information is logged by the SDK with device and timestamp information
 */
class QATesterOnboardingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "QATesterOnboarding"
        private const val PREFS_NAME = "qa_tester_onboarding"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        /**
         * Check if onboarding should be shown
         * Only show in staging environment and if not completed before
         */
        fun shouldShowOnboarding(context: android.content.Context): Boolean {
            // Only show in staging environment
            if (!EnvironmentManager.isQASnapEnabled()) {
                return false
            }

            // Check if already completed
            val prefs =
                context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val completed = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

            Log.d(
                TAG,
                "Should show onboarding: ${!completed} (Environment: ${EnvironmentManager.getEnvironmentDisplayName()})"
            )
            return !completed
        }

        /**
         * Reset onboarding status (for testing purposes)
         */
        fun resetOnboarding(context: android.content.Context) {
            val prefs =
                context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.d(TAG, "Onboarding status reset")
        }
    }

    private lateinit var etTestCaseTitle: EditText
    private lateinit var etTestCaseId: EditText
    private lateinit var etReference: EditText
    private lateinit var btnContinue: Button
    private lateinit var btnSkip: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qa_tester_onboarding)

        Log.d(TAG, "QA Tester Onboarding started - Environment: ${EnvironmentManager.getEnvironmentDisplayName()}")

        initViews()
        setupListeners()
        
        // Pre-fill default values
        etTestCaseTitle.setText("Bug Hunting")
    }

    private fun initViews() {
        etTestCaseTitle = findViewById(R.id.etTestCaseTitle)
        etTestCaseId = findViewById(R.id.etTestCaseId)
        etReference = findViewById(R.id.etReference)
        btnContinue = findViewById(R.id.btnContinue)
        btnSkip = findViewById(R.id.btnSkip)
    }

    private fun setupListeners() {
        btnContinue.setOnClickListener {
            saveTestCaseInfo()
            markOnboardingCompleted()
            navigateToMainApp()
        }

        btnSkip.setOnClickListener {
            Log.d(TAG, "Onboarding skipped by tester")
            markOnboardingCompleted()
            navigateToMainApp()
        }
    }

    private fun saveTestCaseInfo() {
        val testCaseTitle = etTestCaseTitle.text.toString().trim().ifEmpty { "Bug Hunting" }
        val testCaseId = etTestCaseId.text.toString().trim()
        val reference = etReference.text.toString().trim()

        // Save to SharedPreferences for SDK access
        val prefs = getSharedPreferences("qa_snap_test_info", MODE_PRIVATE)
        prefs.edit().apply {
            putString("test_case_title", testCaseTitle)
            putString("test_case_id", testCaseId)
            putString("reference", reference)
            putLong("setup_timestamp", System.currentTimeMillis())
            putString("device_model", android.os.Build.MODEL)
            putString("device_manufacturer", android.os.Build.MANUFACTURER)
            putString("android_version", android.os.Build.VERSION.RELEASE)
            apply()
        }

        Log.d(TAG, "Test case info saved:")
        Log.d(TAG, "Title: $testCaseTitle")
        Log.d(TAG, "ID: $testCaseId")
        Log.d(TAG, "Reference: $reference")
        Log.d(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.d(TAG, "Android: ${android.os.Build.VERSION.RELEASE}")
        Log.d(TAG, "Timestamp: ${System.currentTimeMillis()}")
    }

    private fun markOnboardingCompleted() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        Log.d(TAG, "Onboarding marked as completed")
    }

    private fun navigateToMainApp() {
        Log.d(TAG, "Navigating to MainActivity")
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}