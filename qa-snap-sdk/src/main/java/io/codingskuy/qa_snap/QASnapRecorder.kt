package io.codingskuy.qa_snap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.codingskuy.qa_snap.service.ScreenRecordingService
import java.io.File

/**
 * QASnapRecorder - Main SDK class for screen recording functionality
 *
 * This SDK provides easy-to-use screen recording capabilities for entire screen capture
 * with video output to .mp4 files.
 */
class QASnapRecorder private constructor(private val activity: AppCompatActivity) {

    companion object {
        private var instance: QASnapRecorder? = null

        /**
         * Initialize QASnapRecorder with activity context
         * @param activity The activity context
         * @return QASnapRecorder instance
         */
        fun initialize(activity: AppCompatActivity): QASnapRecorder {
            instance = QASnapRecorder(activity)
            return instance!!
        }

        /**
         * Get current instance
         * @return QASnapRecorder instance or null if not initialized
         */
        fun getInstance(): QASnapRecorder? = instance
    }

    private val mediaProjectionManager =
        activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var isRecording = false
    private var recordingListener: RecordingListener? = null

    // Activity result launcher for media projection permission
    private val mediaProjectionLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startRecordingService(result.data!!)
            } else {
                recordingListener?.onRecordingError("Media projection permission denied")
            }
        }

    /**
     * Set recording event listener
     * @param listener RecordingListener implementation
     */
    fun setRecordingListener(listener: RecordingListener) {
        this.recordingListener = listener
    }

    /**
     * Start screen recording
     * This will request media projection permission if not already granted
     */
    fun startRecording() {
        if (isRecording) {
            recordingListener?.onRecordingError("Recording is already in progress")
            return
        }

        val mediaProjectionIntent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(mediaProjectionIntent)
    }

    /**
     * Stop screen recording
     */
    fun stopRecording() {
        if (!isRecording) {
            recordingListener?.onRecordingError("No recording in progress")
            return
        }

        val stopIntent = Intent(activity, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP_RECORDING
        }
        activity.stopService(stopIntent)
        isRecording = false
    }

    /**
     * Stop screen recording from external Activity context
     *
     * Can be called from other activities (not the one used for initialize).
     * @param context Activity context from requester (external)
     */
    fun stopRecordingExternally(context: Context) {
        val stopIntent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP_RECORDING
        }
        context.stopService(stopIntent)
    }

    /**
     * Check if currently recording
     * @return true if recording, false otherwise
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Get output directory for recorded videos
     * @return File directory where videos are saved
     */
    fun getOutputDirectory(): File {
        val outputDir = File(activity.getExternalFilesDir(null), "QASnapRecordings")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return outputDir
    }

    private fun startRecordingService(data: Intent) {
        val displayMetrics = DisplayMetrics()
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val densityDpi = displayMetrics.densityDpi

        val serviceIntent = Intent(activity, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START_RECORDING
            putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenRecordingService.EXTRA_WIDTH, width)
            putExtra(ScreenRecordingService.EXTRA_HEIGHT, height)
            putExtra(ScreenRecordingService.EXTRA_DENSITY_DPI, densityDpi)
        }

        ContextCompat.startForegroundService(activity, serviceIntent)
        isRecording = true
        recordingListener?.onRecordingStarted()
    }

    /**
     * Internal method for service to notify recording stopped
     */
    internal fun notifyRecordingStopped(outputFile: File) {
        isRecording = false
        recordingListener?.onRecordingStopped(outputFile)
    }

    /**
     * Internal method for service to notify recording error
     */
    internal fun notifyRecordingError(error: String) {
        isRecording = false
        recordingListener?.onRecordingError(error)
    }

    /**
     * Interface for recording events
     */
    interface RecordingListener {
        /**
         * Called when recording starts successfully
         */
        fun onRecordingStarted()

        /**
         * Called when recording stops successfully
         * @param outputFile The recorded video file
         */
        fun onRecordingStopped(outputFile: File)

        /**
         * Called when an error occurs during recording
         * @param error Error message
         */
        fun onRecordingError(error: String)
    }
}