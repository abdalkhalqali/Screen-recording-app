package io.codingskuy.qa_snap.service

import android.app.*
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import io.codingskuy.qa_snap.QASnapRecorder
import io.codingskuy.qa_snap.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground service for handling screen recording functionality
 */
class ScreenRecordingService : Service() {

    companion object {
        const val ACTION_START_RECORDING = "io.codingskuy.qa_snap.START_RECORDING"
        const val ACTION_STOP_RECORDING = "io.codingskuy.qa_snap.STOP_RECORDING"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DENSITY_DPI = "density_dpi"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "qa_snap_recording_channel"
        private const val TAG = "ScreenRecordingService"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var mediaProjectionCallback: MediaProjection.Callback? = null

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                var width = intent.getIntExtra(EXTRA_WIDTH, 0)
                var height = intent.getIntExtra(EXTRA_HEIGHT, 0)
                var densityDpi = intent.getIntExtra(EXTRA_DENSITY_DPI, 0)

                // Fallback to default values if not provided
                if (width <= 0 || height <= 0 || densityDpi <= 0) {
                    Log.w(TAG, "Invalid display metrics received, using fallback values")
                    width = 1080  // Common default width
                    height = 1920 // Common default height
                    densityDpi = 420 // Common default density
                }

                resultData?.let { startRecording(it, width, height, densityDpi) }
            }

            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QA Snap Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen recording in progress"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QA Snap Recording")
            .setContentText("Screen recording in progress...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun startRecording(resultData: Intent, width: Int, height: Int, densityDpi: Int) {
        try {
            Log.d(TAG, "Starting screen recording...")

            // Start foreground service
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Foreground service started")

            // Initialize MediaProjection
            mediaProjection = mediaProjectionManager.getMediaProjection(
                Activity.RESULT_OK, resultData
            )
            mediaProjectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection stopped")
                    // Handle MediaProjection stop
                }
            }
            mediaProjectionCallback?.let { callback ->
                mediaProjection?.registerCallback(callback, Handler(Looper.getMainLooper()))
            }
            Log.d(TAG, "MediaProjection initialized: ${mediaProjection != null}")

            // Setup MediaRecorder
            setupMediaRecorder(width, height, densityDpi)
            Log.d(TAG, "MediaRecorder setup completed")

            // Create VirtualDisplay
            createVirtualDisplay(width, height, densityDpi)
            Log.d(TAG, "VirtualDisplay created: ${virtualDisplay != null}")

            // Start recording
            mediaRecorder?.start()
            isRecording = true
            Log.d(TAG, "Screen recording started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            // Clean up if recording failed to start
            isRecording = false
            mediaRecorder?.release()
            mediaRecorder = null
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjectionCallback?.let { callback ->
                mediaProjection?.unregisterCallback(callback)
            }
            mediaProjection = null

            QASnapRecorder.getInstance()
                ?.notifyRecordingError("Failed to start recording: ${e.message}")
            stopSelf()
        }
    }

    private fun setupMediaRecorder(width: Int, height: Int, densityDpi: Int) {
        Log.d(TAG, "Display metrics: ${width}x${height}, density: ${densityDpi}")

        // Create output file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "qa_snap_recording_$timestamp.mp4"
        val outputDir = File(getExternalFilesDir(null), "QASnapRecordings").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Output directory created: $created, path: $absolutePath")
                if (!created && !exists()) {
                    throw IllegalStateException("Failed to create output directory")
                }
            }
            // Check if directory is writable
            if (!canWrite()) {
                throw IllegalStateException("Output directory is not writable: $absolutePath")
            }
        }
        outputFile = File(outputDir, fileName)

        // Validate output file
        try {
            if (outputFile?.exists() == true) {
                outputFile?.delete()
            }
            outputFile?.createNewFile()
            Log.d(TAG, "Output file created: ${outputFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create output file", e)
            throw IllegalStateException("Cannot create output file: ${e.message}")
        }

        // Ensure dimensions are valid and even numbers (required by some encoders)
        var validWidth = if (width % 2 == 0) width else width - 1
        var validHeight = if (height % 2 == 0) height else height - 1

        // Clamp dimensions to reasonable bounds
        validWidth = validWidth.coerceIn(480, 1920)
        validHeight = validHeight.coerceIn(640, 1080)

        Log.d(TAG, "Adjusted dimensions: ${validWidth}x${validHeight}")

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            try {
                // Reset in case of reuse
                reset()

                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                // Try H.264 first, fallback to default if not available
                try {
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    Log.d(TAG, "Using H.264 encoder")
                } catch (e: Exception) {
                    Log.w(TAG, "H.264 not available, using default encoder")
                    setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
                }

                setVideoSize(validWidth, validHeight)
                setVideoFrameRate(24) // Use more conservative frame rate

                // Use more conservative bitrate
                val calculatedBitrate = (validWidth * validHeight * 24 * 0.15).toInt()
                val bitrate = minOf(4000000, calculatedBitrate) // Lower max bitrate
                setVideoEncodingBitRate(bitrate)
                Log.d(TAG, "Video bitrate set to: $bitrate")

                // Set output file path
                val outputPath = outputFile?.absolutePath
                if (outputPath.isNullOrEmpty()) {
                    throw IllegalStateException("Output path is null or empty")
                }
                setOutputFile(outputPath)

                Log.d(
                    TAG,
                    "MediaRecorder configured with settings: ${validWidth}x${validHeight}@24fps, bitrate: $bitrate"
                )
                prepare()
                Log.d(TAG, "MediaRecorder prepared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing MediaRecorder", e)
                throw e
            }
        }
    }

    private fun createVirtualDisplay(width: Int, height: Int, densityDpi: Int) {
        // Ensure we have a valid MediaRecorder surface
        val surface = mediaRecorder?.surface
        if (surface == null) {
            throw IllegalStateException("MediaRecorder surface is null")
        }

        Log.d(
            TAG,
            "Creating VirtualDisplay with dimensions: ${width}x${height}, density: $densityDpi"
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "QASnapRecording",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        if (virtualDisplay == null) {
            throw IllegalStateException("Failed to create VirtualDisplay")
        }

        Log.d(TAG, "VirtualDisplay created successfully")
    }

    private fun stopRecording() {
        try {
            Log.d(TAG, "Stopping recording, isRecording: $isRecording")

            if (isRecording && mediaRecorder != null) {
                try {
                    mediaRecorder?.stop()
                    Log.d(TAG, "MediaRecorder stopped successfully")
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Error stopping MediaRecorder: ${e.message}")
                    // Continue with cleanup even if stop fails
                }
            }

            // Always cleanup resources
            mediaRecorder?.release()
            mediaRecorder = null

            virtualDisplay?.release()
            virtualDisplay = null

            mediaProjection?.stop()
            mediaProjectionCallback?.let { callback ->
                mediaProjection?.unregisterCallback(callback)
            }
            mediaProjection = null

            if (isRecording) {
                isRecording = false
                Log.d(TAG, "Screen recording stopped")

                // Notify SDK about recording result
                outputFile?.let { file ->
                    if (file.exists() && file.length() > 0) {
                        Log.d(
                            TAG,
                            "Recording file saved: ${file.absolutePath}, size: ${file.length()}"
                        )
                        QASnapRecorder.getInstance()?.notifyRecordingStopped(file)
                    } else {
                        Log.w(TAG, "Recording file is empty or doesn't exist")
                        QASnapRecorder.getInstance()
                            ?.notifyRecordingError("Recording file is empty or doesn't exist")
                    }
                } ?: run {
                    Log.w(TAG, "Output file is null")
                    QASnapRecorder.getInstance()?.notifyRecordingError("Output file is null")
                }
            } else {
                Log.d(TAG, "No active recording to stop")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            QASnapRecorder.getInstance()
                ?.notifyRecordingError("Error stopping recording: ${e.message}")
        } finally {
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        if (isRecording) {
            stopRecording()
        } else {
            Log.d(TAG, "Service destroyed, no recording in progress")
        }
        super.onDestroy()
    }
}