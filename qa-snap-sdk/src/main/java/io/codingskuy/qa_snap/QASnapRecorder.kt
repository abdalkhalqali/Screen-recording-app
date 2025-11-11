package io.codingskuy.qa_snap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.codingskuy.qa_snap.service.ScreenRecordingService
import io.codingskuy.qa_snap.service.LogCaptureService
import java.io.File

/**
 * QASnapRecorder - Main SDK class for screen recording functionality
 *
 * This SDK provides easy-to-use screen recording capabilities for entire screen capture
 * with video output to .mp4 files and ADB log capture to .txt files.
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
            // Setup crash handler when initializing
            setupCrashHandler(activity)
            return instance!!
        }

        /**
         * Get current instance
         * @return QASnapRecorder instance or null if not initialized
         */
        fun getInstance(): QASnapRecorder? = instance

        /**
         * Emergency stop recording - can be called from anywhere
         * Useful for handling crashes and force closes
         */
        fun emergencyStopRecording(context: Context) {
            val stopIntent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_EMERGENCY_STOP
            }
            try {
                context.stopService(stopIntent)
                Log.d("QASnapRecorder", "Emergency stop recording triggered")
            } catch (e: Exception) {
                Log.e("QASnapRecorder", "Failed to emergency stop recording", e)
            }
        }

        /**
         * Setup global crash handler to stop recording on app crash
         */
        private fun setupCrashHandler(context: Context) {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
                Log.e("QASnapRecorder", "App crashed, stopping recording", exception)
                try {
                    emergencyStopRecording(context)
                } catch (e: Exception) {
                    Log.e("QASnapRecorder", "Failed to stop recording on crash", e)
                }

                // Call original handler to maintain crash behavior
                originalHandler?.uncaughtException(thread, exception)
            }
        }
    }

    private val mediaProjectionManager =
        activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var isRecording = false
    private var isCapturingLogs = false
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
     * This will automatically start log capture alongside video recording for comprehensive QA testing
     * This will request media projection permission if not already granted
     */
    fun startRecording() {
        if (isRecording) {
            recordingListener?.onRecordingError("Recording is already in progress")
            return
        }

        // Start log capture first (with default QA-friendly settings)
        startLogCaptureInternal(
            logLevel = "I", // Info level and above for QA testing
            tagFilter = null, // Capture all tags
            packageFilter = activity.packageName // Focus on current app
        )

        val mediaProjectionIntent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(mediaProjectionIntent)
    }

    /**
     * Stop screen recording
     * This will automatically stop log capture alongside video recording
     */
    fun stopRecording() {
        if (!isRecording) {
            recordingListener?.onRecordingError("No recording in progress")
            return
        }

        // Stop both recording and log capture as unified operation
        val stopRecordingIntent = Intent(activity, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP_RECORDING
        }
        activity.stopService(stopRecordingIntent)

        // Stop log capture too
        if (isCapturingLogs) {
            val stopLogIntent = Intent(activity, LogCaptureService::class.java).apply {
                action = LogCaptureService.ACTION_STOP_LOG_CAPTURE
            }
            activity.stopService(stopLogIntent)
        }

        isRecording = false
        isCapturingLogs = false
    }

    /**
     * Stop screen recording from external Activity context
     *
     * Can be called from other activities (not the one used for initialize).
     * @param context Activity context from requester (external)
     */
    fun stopRecordingExternally(context: Context) {
        // Stop both recording and log capture as unified operation
        val stopRecordingIntent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP_RECORDING
        }
        context.stopService(stopRecordingIntent)

        val stopLogIntent = Intent(context, LogCaptureService::class.java).apply {
            action = LogCaptureService.ACTION_STOP_LOG_CAPTURE
        }
        context.stopService(stopLogIntent)
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

        Log.d(
            "QASnapRecorder",
            "Starting recording service with dimensions: ${width}x${height}, density: $densityDpi"
        )

        val serviceIntent = Intent(activity, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START_RECORDING
            putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenRecordingService.EXTRA_WIDTH, width)
            putExtra(ScreenRecordingService.EXTRA_HEIGHT, height)
            putExtra(ScreenRecordingService.EXTRA_DENSITY_DPI, densityDpi)
        }

        try {
            ContextCompat.startForegroundService(activity, serviceIntent)
            isRecording = true
            Log.d("QASnapRecorder", "Recording service started, calling onRecordingStarted")
            recordingListener?.onRecordingStarted()
        } catch (e: Exception) {
            Log.e("QASnapRecorder", "Failed to start recording service", e)
            isRecording = false
            recordingListener?.onRecordingError("Failed to start recording service: ${e.message}")
        }
    }

    /**
     * Internal method for service to notify recording stopped
     */
    internal fun notifyRecordingStopped(outputFile: File) {
        Log.d(
            "QASnapRecorder",
            "Recording stopped notification received: ${outputFile.absolutePath}"
        )
        isRecording = false
        recordingListener?.onRecordingStopped(outputFile)
    }

    /**
     * Internal method for service to notify recording error
     */
    internal fun notifyRecordingError(error: String) {
        Log.e("QASnapRecorder", "Recording error notification received: $error")
        isRecording = false
        recordingListener?.onRecordingError(error)
    }

    /**
     * Start ADB log capture only (individual control)
     * @param logLevel Log level filter (V, D, I, W, E, F, S). Default is V (Verbose)
     * @param tagFilter Optional tag filter to capture logs from specific tags only
     * @param packageFilter Optional package filter to capture logs from specific package only
     * @param bufferSize Buffer size for log capture in bytes. Default is 1MB
     */
    fun startLogCaptureOnly(
        logLevel: String = LogCaptureService.DEFAULT_LOG_LEVEL,
        tagFilter: String? = null,
        packageFilter: String? = null,
        bufferSize: Int = LogCaptureService.DEFAULT_BUFFER_SIZE
    ) {
        if (isCapturingLogs) {
            recordingListener?.onLogCaptureError("Log capture is already in progress")
            return
        }

        startLogCaptureInternal(logLevel, tagFilter, packageFilter, bufferSize)
    }

    /**
     * Internal method to start log capture
     */
    private fun startLogCaptureInternal(
        logLevel: String = LogCaptureService.DEFAULT_LOG_LEVEL,
        tagFilter: String? = null,
        packageFilter: String? = null,
        bufferSize: Int = LogCaptureService.DEFAULT_BUFFER_SIZE
    ) {
        val serviceIntent = Intent(activity, LogCaptureService::class.java).apply {
            action = LogCaptureService.ACTION_START_LOG_CAPTURE
            putExtra(LogCaptureService.EXTRA_LOG_LEVEL, logLevel)
            tagFilter?.let { putExtra(LogCaptureService.EXTRA_TAG_FILTER, it) }
            packageFilter?.let { putExtra(LogCaptureService.EXTRA_PACKAGE_FILTER, it) }
            putExtra(LogCaptureService.EXTRA_BUFFER_SIZE, bufferSize)
        }

        ContextCompat.startForegroundService(activity, serviceIntent)
        isCapturingLogs = true
        recordingListener?.onLogCaptureStarted()
    }

    /**
     * Stop ADB log capture only (individual control)
     */
    fun stopLogCaptureOnly() {
        if (!isCapturingLogs) {
            recordingListener?.onLogCaptureError("No log capture in progress")
            return
        }

        val stopIntent = Intent(activity, LogCaptureService::class.java).apply {
            action = LogCaptureService.ACTION_STOP_LOG_CAPTURE
        }
        activity.stopService(stopIntent)
        isCapturingLogs = false
    }

    /**
     * Stop log capture from external Activity context (individual control)
     * @param context Activity context from requester (external)
     */
    fun stopLogCaptureExternally(context: Context) {
        val stopIntent = Intent(context, LogCaptureService::class.java).apply {
            action = LogCaptureService.ACTION_STOP_LOG_CAPTURE
        }
        context.stopService(stopIntent)
    }

    /**
     * Start both screen recording and log capture with custom settings (advanced control)
     * @param logLevel Log level for log capture
     * @param tagFilter Optional tag filter for log capture
     * @param packageFilter Optional package filter for log capture
     */
    fun startRecordingWithCustomLogs(
        logLevel: String = LogCaptureService.DEFAULT_LOG_LEVEL,
        tagFilter: String? = null,
        packageFilter: String? = null
    ) {
        // Start log capture with custom settings first
        startLogCaptureInternal(logLevel, tagFilter, packageFilter)

        // Then start screen recording (without additional log capture)
        if (isRecording) {
            recordingListener?.onRecordingError("Recording is already in progress")
            return
        }

        val mediaProjectionIntent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(mediaProjectionIntent)
    }

    /**
     * @deprecated Use startRecording() for unified operation or startRecordingWithCustomLogs() for custom settings
     */
    @Deprecated(
        message = "Use startRecording() for default behavior or startRecordingWithCustomLogs() for custom settings",
        replaceWith = ReplaceWith("startRecording()"),
        level = DeprecationLevel.WARNING
    )
    fun startRecordingWithLogs(
        logLevel: String = LogCaptureService.DEFAULT_LOG_LEVEL,
        tagFilter: String? = null,
        packageFilter: String? = null
    ) {
        startRecordingWithCustomLogs(logLevel, tagFilter, packageFilter)
    }

    /**
     * @deprecated Use stopRecording() for unified operation
     */
    @Deprecated(
        message = "Use stopRecording() for unified operation",
        replaceWith = ReplaceWith("stopRecording()"),
        level = DeprecationLevel.WARNING
    )
    fun stopRecordingWithLogs() {
        stopRecording()
    }

    /**
     * Emergency stop both recording and log capture - can be called from anywhere
     */
    fun emergencyStopAll(context: Context) {
        emergencyStopRecording(context)

        val stopLogIntent = Intent(context, LogCaptureService::class.java).apply {
            action = LogCaptureService.ACTION_EMERGENCY_STOP_LOG
        }
        try {
            context.stopService(stopLogIntent)
            Log.d("QASnapRecorder", "Emergency stop log capture triggered")
        } catch (e: Exception) {
            Log.e("QASnapRecorder", "Failed to emergency stop log capture", e)
        }
    }

    /**
     * Internal method for service to notify log capture started
     */
    internal fun notifyLogCaptureStarted() {
        isCapturingLogs = true
        recordingListener?.onLogCaptureStarted()
    }

    /**
     * Internal method for service to notify log capture stopped
     */
    internal fun notifyLogCaptureStopped(outputFile: File) {
        isCapturingLogs = false
        recordingListener?.onLogCaptureStopped(outputFile)
    }

    /**
     * Internal method for service to notify log capture error
     */
    internal fun notifyLogCaptureError(error: String) {
        isCapturingLogs = false
        recordingListener?.onLogCaptureError(error)
    }

    /**
     * Check if currently capturing logs
     * @return true if capturing logs, false otherwise
     */
    fun isCapturingLogs(): Boolean = isCapturingLogs

    /**
     * Get output directory for captured logs
     * @return File directory where logs are saved
     */
    fun getLogOutputDirectory(): File {
        val outputDir = File(activity.getExternalFilesDir(null), "QASnapLogs")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return outputDir
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

        /**
         * Called when log capture starts successfully
         */
        fun onLogCaptureStarted()

        /**
         * Called when log capture stops successfully
         * @param outputFile The captured log file
         */
        fun onLogCaptureStopped(outputFile: File)

        /**
         * Called when an error occurs during log capture
         * @param error Error message
         */
        fun onLogCaptureError(error: String)
    }
}