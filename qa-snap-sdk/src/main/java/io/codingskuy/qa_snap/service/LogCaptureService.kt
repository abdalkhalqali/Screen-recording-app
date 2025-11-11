package io.codingskuy.qa_snap.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.codingskuy.qa_snap.QASnapRecorder
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service for capturing ADB logs and saving them to .txt files
 */
class LogCaptureService : Service() {

    companion object {
        const val ACTION_START_LOG_CAPTURE = "io.codingskuy.qa_snap.START_LOG_CAPTURE"
        const val ACTION_STOP_LOG_CAPTURE = "io.codingskuy.qa_snap.STOP_LOG_CAPTURE"
        const val ACTION_EMERGENCY_STOP_LOG = "io.codingskuy.qa_snap.EMERGENCY_STOP_LOG"

        const val EXTRA_LOG_LEVEL = "log_level"
        const val EXTRA_TAG_FILTER = "tag_filter"
        const val EXTRA_PACKAGE_FILTER = "package_filter"
        const val EXTRA_BUFFER_SIZE = "buffer_size"

        private const val LOG_NOTIFICATION_ID = 1002
        private const val LOG_CHANNEL_ID = "qa_snap_log_channel"
        private const val TAG = "LogCaptureService"

        // Default log parameters
        const val DEFAULT_LOG_LEVEL = "V" // Verbose
        const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB buffer
    }

    private var isCapturing = false
    private var outputFile: File? = null
    private var logCaptureProcess: Process? = null
    private var logWriter: BufferedWriter? = null
    private var captureThread: Thread? = null
    private var logStartTime: Long = 0
    private val shouldStop = AtomicBoolean(false)

    private var notificationUpdateHandler: Handler? = null
    private var notificationUpdateRunnable: Runnable? = null

    // Log capture parameters
    private var logLevel: String = DEFAULT_LOG_LEVEL
    private var tagFilter: String? = null
    private var packageFilter: String? = null
    private var bufferSize: Int = DEFAULT_BUFFER_SIZE

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LogCaptureService onCreate()")
        createLogNotificationChannel()

        notificationUpdateHandler = Handler(Looper.getMainLooper())
        notificationUpdateRunnable = object : Runnable {
            override fun run() {
                updateLogNotification()
                if (isCapturing) {
                    notificationUpdateHandler?.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_LOG_CAPTURE -> {
                // Extract parameters
                logLevel = intent.getStringExtra(EXTRA_LOG_LEVEL) ?: DEFAULT_LOG_LEVEL
                tagFilter = intent.getStringExtra(EXTRA_TAG_FILTER)
                packageFilter = intent.getStringExtra(EXTRA_PACKAGE_FILTER)
                bufferSize = intent.getIntExtra(EXTRA_BUFFER_SIZE, DEFAULT_BUFFER_SIZE)

                startLogCapture()
            }

            ACTION_STOP_LOG_CAPTURE -> {
                Log.d(TAG, "Stop log capture action received")
                stopLogCapture()
            }

            ACTION_EMERGENCY_STOP_LOG -> {
                Log.d(TAG, "Emergency stop log action received")
                stopLogCapture()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createLogNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LOG_CHANNEL_ID,
                "QA Snap Log Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ADB log capture status"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Log notification channel created")
        }
    }

    private fun createLogNotification(): Notification {
        val stopIntent = Intent(this, LogCaptureService::class.java).apply {
            action = ACTION_STOP_LOG_CAPTURE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent().apply {
            setClassName(packageName, "io.codingskuy.qa_snap_demo.HomeActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 2, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, LOG_CHANNEL_ID)
            .setContentTitle("📋 Log Capture Active")
            .setContentText("Capturing ADB logs...")
            .setSubText("QA Snap Demo")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Capturing ADB logs. Tap STOP to end capture and save logs.")
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
            .setContentIntent(mainPendingIntent)
            .setDeleteIntent(null)
            .build()
    }

    private fun startLogCapture() {
        try {
            Log.d(TAG, "Starting log capture...")

            if (isCapturing) {
                Log.w(TAG, "Log capture is already in progress")
                return
            }

            // Start foreground service with notification
            val notification = createLogNotification()
            startForeground(LOG_NOTIFICATION_ID, notification)
            Log.d(TAG, "Log capture foreground service started")

            // Create output file
            createLogOutputFile()

            // Start log capture process
            startLogCaptureProcess()

            isCapturing = true
            logStartTime = System.currentTimeMillis()
            shouldStop.set(false)

            // Start notification updates
            notificationUpdateRunnable?.let { runnable ->
                notificationUpdateHandler?.post(runnable)
            }

            Log.d(TAG, "Log capture started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting log capture", e)
            isCapturing = false
            cleanupLogCapture()
            QASnapRecorder.getInstance()
                ?.notifyLogCaptureError("Failed to start log capture: ${e.message}")
            stopSelf()
        }
    }

    private fun createLogOutputFile() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "qa_snap_logs_$timestamp.txt"
        val outputDir = File(getExternalFilesDir(null), "QASnapLogs").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Log output directory created: $created, path: $absolutePath")
                if (!created && !exists()) {
                    throw IllegalStateException("Failed to create log output directory")
                }
            }
            if (!canWrite()) {
                throw IllegalStateException("Log output directory is not writable: $absolutePath")
            }
        }

        outputFile = File(outputDir, fileName)

        try {
            if (outputFile?.exists() == true) {
                outputFile?.delete()
            }
            outputFile?.createNewFile()
            Log.d(TAG, "Log output file created: ${outputFile?.absolutePath}")

            // Initialize file writer with header
            logWriter = BufferedWriter(FileWriter(outputFile, true), bufferSize)
            logWriter?.write("=== QA Snap Log Capture Started ===\n")
            logWriter?.write(
                "Timestamp: ${
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date())
                }\n"
            )
            logWriter?.write("Log Level: $logLevel\n")
            tagFilter?.let { logWriter?.write("Tag Filter: $it\n") }
            packageFilter?.let { logWriter?.write("Package Filter: $it\n") }
            logWriter?.write("=====================================\n\n")
            logWriter?.flush()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create log output file", e)
            throw IllegalStateException("Cannot create log output file: ${e.message}")
        }
    }

    private fun startLogCaptureProcess() {
        captureThread = Thread {
            try {
                // Build logcat command
                val command = buildLogcatCommand()
                Log.d(TAG, "Executing logcat command: ${command.joinToString(" ")}")

                // Start logcat process
                logCaptureProcess = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(logCaptureProcess?.inputStream))

                var line: String? = null
                var lineCount = 0

                while (!shouldStop.get()) {
                    line = reader.readLine()
                    if (line == null) break

                    // Filter and write log line
                    if (shouldIncludeLogLine(line)) {
                        logWriter?.write("$line\n")
                        lineCount++

                        // Flush every 10 lines to ensure data is written
                        if (lineCount % 10 == 0) {
                            logWriter?.flush()
                        }
                    }
                }

                Log.d(TAG, "Log capture thread finished, captured $lineCount lines")

            } catch (e: Exception) {
                if (!shouldStop.get()) {
                    Log.e(TAG, "Error in log capture thread", e)
                    Handler(Looper.getMainLooper()).post {
                        QASnapRecorder.getInstance()
                            ?.notifyLogCaptureError("Log capture error: ${e.message}")
                    }
                }
            }
        }

        captureThread?.start()
    }

    private fun buildLogcatCommand(): List<String> {
        val command = mutableListOf("logcat")

        // Add log level filter
        command.add("-v")
        command.add("time") // Use time format for better readability

        // Add log level
        command.add("*:$logLevel")

        // Add tag filter if specified
        tagFilter?.let { filter ->
            command.add("-s")
            command.add(filter)
        }

        // Clear log buffer first (optional)
        // command.add("-c") // Uncomment if you want to clear logs first

        return command
    }

    private fun shouldIncludeLogLine(logLine: String): Boolean {
        // Apply package filter if specified
        packageFilter?.let { filter ->
            if (!logLine.contains(filter, ignoreCase = true)) {
                return false
            }
        }

        // Additional filtering can be added here
        return true
    }

    private fun stopLogCapture() {
        try {
            Log.d(TAG, "Stopping log capture, isCapturing: $isCapturing")

            shouldStop.set(true)

            if (isCapturing) {
                // Stop capture thread
                captureThread?.interrupt()

                // Stop logcat process
                logCaptureProcess?.destroy()

                // Close log writer
                try {
                    logWriter?.write("\n=== QA Snap Log Capture Ended ===\n")
                    logWriter?.write(
                        "End Timestamp: ${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date())
                        }\n"
                    )
                    logWriter?.flush()
                    logWriter?.close()
                    Log.d(TAG, "Log writer closed successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing log writer: ${e.message}")
                }

                isCapturing = false

                // Stop notification updates
                notificationUpdateRunnable?.let { runnable ->
                    notificationUpdateHandler?.removeCallbacks(runnable)
                }

                Log.d(TAG, "Log capture stopped")

                // Show completion notification
                showLogCaptureCompletedNotification()

                // Notify SDK about log capture result
                outputFile?.let { file ->
                    if (file.exists() && file.length() > 0) {
                        Log.d(TAG, "Log file saved: ${file.absolutePath}, size: ${file.length()}")
                        QASnapRecorder.getInstance()?.notifyLogCaptureStopped(file)
                    } else {
                        Log.w(TAG, "Log file is empty or doesn't exist")
                        QASnapRecorder.getInstance()
                            ?.notifyLogCaptureError("Log file is empty or doesn't exist")
                    }
                } ?: run {
                    Log.w(TAG, "Output log file is null")
                    QASnapRecorder.getInstance()?.notifyLogCaptureError("Output log file is null")
                }
            } else {
                Log.d(TAG, "No active log capture to stop")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping log capture", e)
            QASnapRecorder.getInstance()
                ?.notifyLogCaptureError("Error stopping log capture: ${e.message}")
        } finally {
            cleanupLogCapture()

            // Remove the ongoing notification after a delay to show completion
            Handler(Looper.getMainLooper()).postDelayed({
                stopForeground(true)
                stopSelf()
            }, 3000)
        }
    }

    private fun cleanupLogCapture() {
        try {
            captureThread?.interrupt()
            logCaptureProcess?.destroy()
            logWriter?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup: ${e.message}")
        }
        captureThread = null
        logCaptureProcess = null
        logWriter = null
    }

    private fun showLogCaptureCompletedNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val completedNotification = NotificationCompat.Builder(this, LOG_CHANNEL_ID)
            .setContentTitle("QA Snap Log Capture Completed")
            .setContentText("ADB logs have been saved successfully")
            .setSubText("Log capture finished")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notificationManager.notify(LOG_NOTIFICATION_ID, completedNotification)
    }

    private fun updateLogNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val stopIntent = Intent(this, LogCaptureService::class.java).apply {
            action = ACTION_STOP_LOG_CAPTURE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent().apply {
            setClassName(packageName, "io.codingskuy.qa_snap_demo.HomeActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 2, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsedTime = System.currentTimeMillis() - logStartTime
        val minutes = elapsedTime / 60000
        val seconds = (elapsedTime % 60000) / 1000
        val formattedTime = String.format("%02d:%02d", minutes, seconds)

        val fileSize = outputFile?.length() ?: 0
        val fileSizeKB = fileSize / 1024

        val notification = NotificationCompat.Builder(this, LOG_CHANNEL_ID)
            .setContentTitle("📋 Log Capture Active")
            .setContentText("Capturing logs... ($formattedTime) - ${fileSizeKB}KB")
            .setSubText("QA Snap Demo")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Capturing ADB logs. File size: ${fileSizeKB}KB. Tap STOP to end capture.")
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
            .setContentIntent(mainPendingIntent)
            .setDeleteIntent(null)
            .build()

        notificationManager.notify(LOG_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Log.d(TAG, "LogCaptureService onDestroy called")

        // Stop notification updates
        notificationUpdateRunnable?.let { runnable ->
            notificationUpdateHandler?.removeCallbacks(runnable)
        }

        if (isCapturing) {
            Log.w(TAG, "Service destroyed while capturing logs, performing emergency stop")
            stopLogCapture()
        } else {
            Log.d(TAG, "Service destroyed, no log capture in progress")
        }

        cleanupLogCapture()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed - app was swiped away or force closed")
        if (isCapturing) {
            Log.w(TAG, "Log capture was active when task removed, performing emergency stop")
            stopLogCapture()
        }
        super.onTaskRemoved(rootIntent)
    }
}