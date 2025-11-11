package io.codingskuy.qa_snap_demo

import android.os.Bundle
import android.widget.Toast
import io.codingskuy.qa_snap.QASnap
import io.codingskuy.qa_snap.QASnapActivity
import java.io.File

/**
 * SimpleMainActivity - Example showing the simplest possible QA Snap integration
 *
 * This replaces the complex MainActivity with a super simple version that
 * any Android project can copy and use immediately.
 */
class SimpleMainActivity : QASnapActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // THAT'S IT! 
        // Recording starts automatically after permissions
        // No boilerplate code needed
        // Video + logs will be saved automatically
    }

    // Optional: Handle completion when recording finishes
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        val message = when {
            videoFile != null && logFile != null -> "Video & logs saved!"
            videoFile != null -> "Video saved: ${videoFile.name}"
            logFile != null -> "Logs saved: ${logFile.name}"
            else -> "Recording completed"
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Optional: Upload files, send to analytics, etc.
        // uploadToServer(videoFile, logFile)
        // showCompletionDialog(videoFile, logFile)
    }

    // Optional: Control when recording should start
    override fun shouldAutoStartRecording(): Boolean {
        // You can customize this based on build type, user preference, etc.
        return BuildConfig.DEBUG // Only record in debug builds
    }

    // Optional: Called when recording is ready to start (after permissions)
    override fun onQARecordingReady() {
        Toast.makeText(this, "QA Recording ready to start!", Toast.LENGTH_SHORT).show()
        // You could show a dialog, log analytics, etc.
    }
}

/**
 * Alternative: Even simpler with one-liner approach
 */
class OneLinerMainActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ONE LINE INTEGRATION
        QASnap.start(this)
            .onComplete { video, logs ->
                Toast.makeText(this, "QA files saved!", Toast.LENGTH_SHORT).show()
            }

        // That's it! Recording starts automatically
        // Everything else is handled by the SDK
    }
}