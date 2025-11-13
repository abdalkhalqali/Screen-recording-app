package io.codingskuy.qa_snap_demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap_demo.utils.EnvironmentManager

/**
 * LauncherActivity - Entry point that determines navigation flow
 *
 * This activity decides which screen to show first:
 * - In STAGING: Shows QA Onboarding (if not completed) -> MainActivity
 * - In DEVELOPMENT/PRODUCTION: Goes directly to MainActivity
 */
class LauncherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LauncherActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        Log.d(
            TAG,
            "LauncherActivity started - Environment: ${EnvironmentManager.getEnvironmentDisplayName()}"
        )
        Log.d(TAG, "QA Snap enabled: ${EnvironmentManager.isQASnapEnabled()}")

        // Log environment info
        EnvironmentManager.logEnvironmentInfo()

        // Determine navigation flow
        navigateToAppropriateScreen()
    }

    private fun navigateToAppropriateScreen() {
        val shouldShowOnboarding = QATesterOnboardingActivity.shouldShowOnboarding(this)

        Log.d(TAG, "Should show QA onboarding: $shouldShowOnboarding")

        val intent = if (shouldShowOnboarding) {
            Log.d(TAG, "Navigating to QA Tester Onboarding")
            Intent(this, QATesterOnboardingActivity::class.java)
        } else {
            Log.d(TAG, "Navigating directly to MainActivity")
            Intent(this, MainActivity::class.java)
        }

        startActivity(intent)
        finish()
    }
}