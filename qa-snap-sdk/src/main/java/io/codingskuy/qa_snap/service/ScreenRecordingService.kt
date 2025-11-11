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
    private var recordingStartTime: Long = 0
    private var notificationUpdateHandler: Handler? = null
    private var notificationUpdateRunnable: Runnable? = null

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenRecordingService onCreate()")
        createNotificationChannel()

        // Verify notification channel was created
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel != null) {
                Log.d(
                    TAG,
                    "Notification channel verified: ${channel.name}, importance: ${channel.importance}"
                )
            } else {
                Log.e(TAG, "Failed to create notification channel!")
            }
        }

        notificationUpdateHandler = Handler(Looper.getMainLooper())
        notificationUpdateRunnable = object : Runnable {
            override fun run() {
                updateNotification()
                if (isRecording) {
                    notificationUpdateHandler?.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

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

                // Test notification first
                showTestNotification()

                resultData?.let { startRecording(it, width, height, densityDpi) }
            }

            ACTION_STOP_RECORDING -> {
                Log.d(TAG, "Stop recording action received")
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
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Screen recording controls and status"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                enableLights(false)
                setSound(null, null) // No sound for recording notifications
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created with IMPORTANCE_DEFAULT")
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

        // Create a main app intent for when user taps the notification
        val mainActivityIntent = Intent().apply {
            setClassName(packageName, "io.codingskuy.qa_snap_demo.HomeActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 1, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Screen Recording Active")
            .setContentText("Recording your screen...")
            .setSubText("QA Snap Demo")
            .setSmallIcon(android.R.drawable.ic_media_play) // Recording icon
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Changed from LOW
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true) // Shows elapsed time automatically
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Screen recording is in progress. Tap STOP to end recording.")
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "STOP",
                    stopPendingIntent
                )
                    .setShowsUserInterface(false)
                    .build()
            )
            .setContentIntent(mainPendingIntent) // Tap notification to open app
            .setDeleteIntent(null) // Prevent accidental dismissal
            .build()
    }

    private fun startRecording(resultData: Intent, width: Int, height: Int, densityDpi: Int) {
        try {
            Log.d(TAG, "Starting screen recording...")

            // Start foreground service FIRST to show notification immediately
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Foreground service started with notification ID: $NOTIFICATION_ID")

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
            recordingStartTime = System.currentTimeMillis()
            notificationUpdateRunnable?.let { runnable ->
                notificationUpdateHandler?.post(runnable)
            }
            Log.d(TAG, "Screen recording started successfully, notification should be visible")

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

                // Stop notification updates
                notificationUpdateRunnable?.let { runnable ->
                    notificationUpdateHandler?.removeCallbacks(runnable)
                }

                Log.d(TAG, "Screen recording stopped")

                // Show completion notification
                showRecordingCompletedNotification()

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
            // Remove the ongoing notification after a delay to show completion
            Handler(Looper.getMainLooper()).postDelayed({
                stopForeground(true)
                stopSelf()
            }, 3000) // Show completion notification for 3 seconds
        }
    }

    private fun showRecordingCompletedNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val completedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QA Snap Recording Completed")
            .setContentText("Screen recording has been saved successfully")
            .setSubText("Recording finished")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notificationManager.notify(NOTIFICATION_ID, completedNotification)
    }

    private fun updateNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a main app intent for when user taps the notification
        val mainActivityIntent = Intent().apply {
            setClassName(packageName, "io.codingskuy.qa_snap_demo.HomeActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 1, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsedTime = System.currentTimeMillis() - recordingStartTime
        val minutes = elapsedTime / 60000
        val seconds = (elapsedTime % 60000) / 1000
        val formattedTime = String.format("%02d:%02d", minutes, seconds)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Screen Recording Active")
            .setContentText("Recording your screen... ($formattedTime)")
            .setSubText("QA Snap Demo")
            .setSmallIcon(android.R.drawable.ic_media_play) // Recording icon
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Changed from LOW
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true) // Shows elapsed time automatically
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Screen recording is in progress. Tap STOP to end recording.")
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "STOP",
                    stopPendingIntent
                )
                    .setShowsUserInterface(false)
                    .build()
            )
            .setContentIntent(mainPendingIntent) // Tap notification to open app
            .setDeleteIntent(null) // Prevent accidental dismissal
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showTestNotification() {
        Log.d(TAG, "Showing test notification to verify notification system")
        val testNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QA Snap Test")
            .setContentText("Testing notification system...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, testNotification)

        // Remove test notification after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(999)
            Log.d(TAG, "Test notification removed")
        }, 2000)
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