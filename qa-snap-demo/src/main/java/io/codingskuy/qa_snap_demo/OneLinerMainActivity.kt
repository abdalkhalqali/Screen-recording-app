package io.codingskuy.qa_snap_demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.codingskuy.qa_snap.QASnap
import java.io.File

/**
 * OneLinerMainActivity - Alternative MainActivity using one-liner integration
 *
 * This demonstrates the simplest possible QA Snap integration:
 * - One line of code: QASnap.start(this)
 * - Everything else handled automatically
 * - Perfect for quick testing or minimal integration
 */
class OneLinerMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ONE LINE INTEGRATION - Everything automatic!
        QASnap.start(this)
            .onComplete { videoFile, logFile ->
                // Optional: Handle completion
                val message = when {
                    videoFile != null && logFile != null -> "QA files saved! Video: ${videoFile.name}, Logs: ${logFile.name}"
                    videoFile != null -> "Video saved: ${videoFile.name}"
                    logFile != null -> "Logs saved: ${logFile.name}"
                    else -> "QA Recording completed"
                }

                Toast.makeText(this, message, Toast.LENGTH_LONG).show()

                // Optional: Upload files, analytics, etc.
                // uploadToServer(videoFile, logFile)
            }

        // Show splash screen and navigate after delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                navigateToSignIn()
            }
        }, 2000) // 2-second splash screen
    }

    // Handle permission results automatically
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // QASnapHelper handles this automatically - no code needed here!
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }
}