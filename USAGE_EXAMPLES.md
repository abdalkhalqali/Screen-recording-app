# QA Snap SDK - Usage Examples

> **📝 Important**: Sejak versi terbaru, QA Snap SDK menggunakan **unified recording approach**.
> Pemanggilan `startRecording()` otomatis memulai video recording DAN log capture bersamaan untuk
> dokumentasi QA yang comprehensive.

Kumpulan contoh penggunaan SDK QA Snap untuk berbagai skenario testing dan debugging.

## Basic Usage Examples

### 1. Screen Recording + Log Capture (Default Behavior)

```kotlin
class SimpleRecordingActivity : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        qaSnapRecorder = QASnapRecorder.initialize(this)
        
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                showToast("Recording + Log capture started")
            }
            
            override fun onRecordingStopped(outputFile: File) {
                showToast("Video saved: ${outputFile.name}")
            }
            
            override fun onRecordingError(error: String) {
                showToast("Recording Error: $error")
            }
            
            override fun onLogCaptureStarted() {
                showToast("Log capture started automatically")
            }
            
            override fun onLogCaptureStopped(outputFile: File) {
                showToast("Logs saved: ${outputFile.name}")
            }
            
            override fun onLogCaptureError(error: String) {
                showToast("Log Error: $error")
            }
        })
        
        // Start unified recording (video + logs automatically)
        qaSnapRecorder.startRecording()
    }
    
    private fun stopRecording() {
        // Stop unified recording (video + logs automatically)
        qaSnapRecorder.stopRecording()
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
```

### 2. Log Capture Only (Individual Control)

```kotlin
class DebugActivity : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    private fun startDebugging() {
        qaSnapRecorder = QASnapRecorder.initialize(this)
        
        // Capture logs only (no video recording)
        qaSnapRecorder.startLogCaptureOnly(
            logLevel = "D",
            tagFilter = "MyApp",
            packageFilter = packageName
        )
        
        // Generate some test logs
        generateTestLogs()
    }
    
    private fun generateTestLogs() {
        Log.d("MyApp", "Debug: User clicked button A")
        Log.i("MyApp", "Info: Network request started")
        Log.w("MyApp", "Warning: Slow network detected")
        Log.e("MyApp", "Error: Failed to load data")
    }
    
    private fun stopDebugging() {
        // Stop log capture only
        qaSnapRecorder.stopLogCaptureOnly()
    }
}
```

## Advanced Usage Examples

### 3. Automated QA Testing Flow (Unified Approach)

```kotlin
class QATestRunner : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    private var testStartTime: Long = 0
    
    fun runTestCase(testName: String) {
        qaSnapRecorder = QASnapRecorder.initialize(this)
        setupTestListener(testName)
        
        // Start unified recording (comprehensive testing with default settings)
        qaSnapRecorder.startRecording()
        
        testStartTime = System.currentTimeMillis()
        
        // Run your test steps
        executeTestSteps()
    }
    
    fun runAdvancedTestCase(testName: String) {
        qaSnapRecorder = QASnapRecorder.initialize(this)
        setupTestListener(testName)
        
        // Start recording with custom log settings for advanced scenarios
        qaSnapRecorder.startRecordingWithCustomLogs(
            logLevel = "V", // Verbose for detailed debugging
            tagFilter = "Test|QA|Debug", // Multiple tags for testing
            packageFilter = packageName // Only this app's logs
        )
        
        testStartTime = System.currentTimeMillis()
        executeTestSteps()
    }
    
    private fun setupTestListener(testName: String) {
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                Log.i("QATest", "Test recording started for: $testName")
            }
            
            override fun onRecordingStopped(outputFile: File) {
                val duration = System.currentTimeMillis() - testStartTime
                Log.i("QATest", "Test video saved: ${outputFile.name} (${duration}ms)")
                
                // Move file to test results directory
                moveToTestResults(outputFile, testName, "video")
            }
            
            override fun onLogCaptureStopped(outputFile: File) {
                Log.i("QATest", "Test logs saved: ${outputFile.name}")
                
                // Move file to test results directory
                moveToTestResults(outputFile, testName, "logs")
            }
            
            override fun onRecordingError(error: String) {
                Log.e("QATest", "Test recording failed: $error")
            }
            
            override fun onLogCaptureError(error: String) {
                Log.e("QATest", "Test log capture failed: $error")
            }
            
            override fun onLogCaptureStarted() {
                Log.i("QATest", "Test log capture started for: $testName")
            }
        })
    }
    
    private fun executeTestSteps() {
        // Example test flow
        Log.i("QATest", "Step 1: Navigate to login screen")
        navigateToLogin()
        
        Log.i("QATest", "Step 2: Enter credentials")
        enterCredentials("test@example.com", "password123")
        
        Log.i("QATest", "Step 3: Submit login")
        submitLogin()
        
        Log.i("QATest", "Step 4: Verify dashboard")
        verifyDashboard()
        
        // Finish test (unified stop)
        finishTest()
    }
    
    private fun finishTest() {
        // Stop unified recording (video + logs)
        qaSnapRecorder.stopRecording()
    }
    
    private fun moveToTestResults(file: File, testName: String, type: String) {
        val testResultsDir = File(getExternalFilesDir(null), "TestResults/$testName")
        testResultsDir.mkdirs()
        
        val newFile = File(testResultsDir, "${testName}_$type.${file.extension}")
        file.copyTo(newFile, overwrite = true)
        file.delete()
    }
}
```

### 4. Performance Monitoring (Log Only)

```kotlin
class PerformanceMonitor : Application() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    override fun onCreate() {
        super.onCreate()
        
        if (BuildConfig.DEBUG) {
            startPerformanceMonitoring()
        }
    }
    
    private fun startPerformanceMonitoring() {
        // Start log capture only for performance monitoring (no video needed)
        qaSnapRecorder.startLogCaptureOnly(
            logLevel = "W", // Warning and above
            tagFilter = "Performance",
            packageFilter = packageName
        )
        
        // Monitor memory usage
        startMemoryMonitoring()
        
        // Monitor ANR
        startANRMonitoring()
    }
    
    private fun startMemoryMonitoring() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val usagePercentage = (usedMemory * 100 / maxMemory)
                
                if (usagePercentage > 80) {
                    Log.w("Performance", "High memory usage: ${usagePercentage}%")
                }
                
                handler.postDelayed(this, 5000) // Check every 5 seconds
            }
        }
        handler.post(runnable)
    }
    
    private fun startANRMonitoring() {
        // Simple ANR detection
        val mainHandler = Handler(Looper.getMainLooper())
        val watchdog = Thread {
            while (true) {
                val startTime = System.currentTimeMillis()
                
                mainHandler.post {
                    val endTime = System.currentTimeMillis()
                    val delay = endTime - startTime
                    
                    if (delay > 5000) { // 5 second threshold
                        Log.e("Performance", "Potential ANR detected: ${delay}ms delay")
                    }
                }
                
                Thread.sleep(1000)
            }
        }
        watchdog.isDaemon = true
        watchdog.start()
    }
}
```

### 5. Bug Report Generator (Unified)

```kotlin
class BugReportActivity : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    fun generateBugReport(bugDescription: String) {
        qaSnapRecorder = QASnapRecorder.initialize(this)
        
        // Start comprehensive capture (video + detailed logs)
        qaSnapRecorder.startRecordingWithCustomLogs(
            logLevel = "V", // All logs for comprehensive bug report
            tagFilter = null, // All tags
            packageFilter = null, // All apps (system logs too)
        )
        
        // Generate bug report logs
        generateBugReportLogs(bugDescription)
        
        // Let user reproduce the bug with video + logs running
        // Stop manually or after timeout
        Handler(Looper.getMainLooper()).postDelayed({
            qaSnapRecorder.stopRecording() // Unified stop
        }, 30000) // Capture for 30 seconds
    }
    
    private fun generateBugReportLogs(description: String) {
        Log.i("BugReport", "=== BUG REPORT START ===")
        Log.i("BugReport", "Description: $description")
        Log.i("BugReport", "Timestamp: ${System.currentTimeMillis()}")
        Log.i("BugReport", "App Version: ${BuildConfig.VERSION_NAME}")
        Log.i("BugReport", "Device: ${Build.MODEL}")
        Log.i("BugReport", "Android Version: ${Build.VERSION.RELEASE}")
        
        // Device info
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        Log.i("BugReport", "Available Memory: ${memoryInfo.availMem / 1024 / 1024} MB")
        Log.i("BugReport", "Total Memory: ${memoryInfo.totalMem / 1024 / 1024} MB")
        Log.i("BugReport", "Low Memory: ${memoryInfo.lowMemory}")
        
        // Network info
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        Log.i("BugReport", "Network Connected: ${activeNetwork?.isConnected}")
        Log.i("BugReport", "Network Type: ${activeNetwork?.typeName}")
        
        Log.i("BugReport", "=== BUG REPORT END ===")
    }
}
```

### 6. Custom Log Filtering (Individual Control)

```kotlin
class CustomLogFilter : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    // Scenario 1: Network debugging (logs only)
    fun startNetworkDebugging() {
        qaSnapRecorder.startLogCaptureOnly(
            logLevel = "D",
            tagFilter = "OkHttp|Retrofit|NetworkManager", // Multiple tags
            packageFilter = packageName
        )
    }
    
    // Scenario 2: Full QA session with network focus
    fun startFullQAWithNetworkFocus() {
        qaSnapRecorder.startRecordingWithCustomLogs(
            logLevel = "D",
            tagFilter = "OkHttp|Retrofit|NetworkManager", // Focus on network
            packageFilter = packageName
        )
    }
    
    // Scenario 3: Database debugging (logs only)
    fun startDatabaseDebugging() {
        qaSnapRecorder.startLogCaptureOnly(
            logLevel = "V",
            tagFilter = "SQLite|Database|Room",
            packageFilter = packageName
        )
    }
    
    // Scenario 4: UI testing with full capture
    fun startUITesting() {
        // Default unified recording is perfect for UI testing
        qaSnapRecorder.startRecording()
        
        // Generate UI event logs
        logUIEvents()
    }
    
    private fun logUIEvents() {
        // Button clicks
        findViewById<Button>(R.id.button1).setOnClickListener {
            Log.i("UI", "Button1 clicked")
        }
        
        // Fragment transitions
        supportFragmentManager.addOnBackStackChangedListener {
            Log.i("UI", "Fragment back stack changed: ${supportFragmentManager.backStackEntryCount}")
        }
        
        // Activity lifecycle
        Log.i("Activity", "Activity created: ${this.javaClass.simpleName}")
    }
    
    // Scenario 5: Error-only logging (logs only)
    fun startErrorOnlyLogging() {
        qaSnapRecorder.startLogCaptureOnly(
            logLevel = "E", // Only errors
            tagFilter = null,
            packageFilter = null // All apps to catch system errors
        )
    }
}
```

### 7. Integration dengan Testing Framework

```kotlin
// JUnit Test with QA Snap
@RunWith(AndroidJUnit4::class)
class QASnapInstrumentedTest {
    
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java, false, false)
    
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    @Before
    fun setup() {
        val activity = activityRule.launchActivity(Intent())
        qaSnapRecorder = QASnapRecorder.initialize(activity)
    }
    
    @Test
    fun testUserFlow_withRecording() {
        // Start recording test
        qaSnapRecorder.startRecordingWithLogs(
            logLevel = "D",
            tagFilter = "Test",
            packageFilter = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        )
        
        Log.d("Test", "Starting user flow test")
        
        // Perform test actions
        onView(withId(R.id.loginButton)).perform(click())
        onView(withId(R.id.emailInput)).perform(typeText("test@example.com"))
        onView(withId(R.id.passwordInput)).perform(typeText("password123"))
        onView(withId(R.id.submitButton)).perform(click())
        
        Log.d("Test", "Login flow completed")
        
        // Verify results
        onView(withId(R.id.welcomeMessage)).check(matches(isDisplayed()))
        
        Log.d("Test", "Test completed successfully")
        
        // Stop recording
        qaSnapRecorder.stopRecordingWithLogs()
        
        // Wait for files to be saved
        Thread.sleep(2000)
    }
    
    @After
    fun tearDown() {
        if (qaSnapRecorder.isRecording() || qaSnapRecorder.isCapturingLogs()) {
            qaSnapRecorder.stopRecordingWithLogs()
        }
    }
}
```

### 8. Conditional Recording berdasarkan Build Type

```kotlin
class ConditionalRecording : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        when (BuildConfig.BUILD_TYPE) {
            "debug" -> {
                // Full logging untuk development
                startDebugLogging()
            }
            "staging" -> {
                // Warning dan error saja untuk staging
                startStagingLogging()
            }
            "release" -> {
                // Tidak ada logging untuk release
                // atau hanya error critical
                if (BuildConfig.ENABLE_CRASH_REPORTING) {
                    startCrashOnlyLogging()
                }
            }
        }
    }
    
    private fun startDebugLogging() {
        val activity = getCurrentActivity() ?: return
        val qaSnap = QASnapRecorder.initialize(activity)
        
        qaSnap.startLogCapture(
            logLevel = "V",
            tagFilter = null,
            packageFilter = packageName
        )
    }
    
    private fun startStagingLogging() {
        val activity = getCurrentActivity() ?: return
        val qaSnap = QASnapRecorder.initialize(activity)
        
        qaSnap.startLogCapture(
            logLevel = "W", // Warning dan error
            tagFilter = null,
            packageFilter = packageName
        )
    }
    
    private fun startCrashOnlyLogging() {
        val activity = getCurrentActivity() ?: return
        val qaSnap = QASnapRecorder.initialize(activity)
        
        qaSnap.startLogCapture(
            logLevel = "E", // Hanya error
            tagFilter = "CRASH|FATAL|ERROR",
            packageFilter = null // System crashes juga
        )
    }
    
    private fun getCurrentActivity(): AppCompatActivity? {
        // Implementation to get current activity
        // You can use activity lifecycle callbacks
        return null
    }
}
```

## File Management Examples

### 9. Custom File Organization

```kotlin
class FileManager {
    
    fun organizeRecordingsByDate(qaSnap: QASnapRecorder) {
        val recordingsDir = qaSnap.getOutputDirectory()
        val logsDir = qaSnap.getLogOutputDirectory()
        
        organizeFiles(recordingsDir, "recordings")
        organizeFiles(logsDir, "logs")
    }
    
    private fun organizeFiles(directory: File, type: String) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                // Extract date from filename: qa_snap_recording_20241111_143052.mp4
                val datePattern = "\\d{8}".toRegex()
                val dateMatch = datePattern.find(file.name)
                
                if (dateMatch != null) {
                    val date = dateMatch.value
                    val year = date.substring(0, 4)
                    val month = date.substring(4, 6)
                    val day = date.substring(6, 8)
                    
                    // Create directory structure: /2024/11/11/
                    val targetDir = File(directory.parent, "$type/$year/$month/$day")
                    targetDir.mkdirs()
                    
                    // Move file
                    val targetFile = File(targetDir, file.name)
                    file.renameTo(targetFile)
                }
            }
        }
    }
    
    fun cleanupOldFiles(qaSnap: QASnapRecorder, maxAgeMillis: Long) {
        val currentTime = System.currentTimeMillis()
        
        listOf(qaSnap.getOutputDirectory(), qaSnap.getLogOutputDirectory()).forEach { dir ->
            dir.walkTopDown().forEach { file ->
                if (file.isFile && (currentTime - file.lastModified()) > maxAgeMillis) {
                    file.delete()
                    Log.i("FileManager", "Deleted old file: ${file.name}")
                }
            }
        }
    }
    
    fun getFileSizeReport(qaSnap: QASnapRecorder): String {
        val videoDir = qaSnap.getOutputDirectory()
        val logDir = qaSnap.getLogOutputDirectory()
        
        val videoSize = calculateDirectorySize(videoDir)
        val logSize = calculateDirectorySize(logDir)
        val totalSize = videoSize + logSize
        
        return """
            QA Snap Storage Report:
            Video Files: ${formatFileSize(videoSize)}
            Log Files: ${formatFileSize(logSize)}
            Total: ${formatFileSize(totalSize)}
        """.trimIndent()
    }
    
    private fun calculateDirectorySize(directory: File): Long {
        return directory.walkTopDown().sumOf { file ->
            if (file.isFile) file.length() else 0L
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> "%.1f GB".format(gb)
            mb >= 1 -> "%.1f MB".format(mb)
            kb >= 1 -> "%.1f KB".format(kb)
            else -> "$bytes bytes"
        }
    }
}
```

## Error Handling Examples

### 10. Robust Error Handling

```kotlin
class RobustQASnapHandler : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    private var retryCount = 0
    private val maxRetries = 3
    
    private fun setupRobustRecording() {
        qaSnapRecorder = QASnapRecorder.initialize(this)
        
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                Log.i("QASnap", "Recording + Log capture started successfully")
                retryCount = 0 // Reset retry count on success
            }
            
            override fun onRecordingStopped(outputFile: File) {
                Log.i("QASnap", "Recording saved: ${outputFile.absolutePath}")
                validateAndBackupFile(outputFile)
            }
            
            override fun onRecordingError(error: String) {
                Log.e("QASnap", "Recording error: $error")
                handleRecordingError(error)
            }
            
            override fun onLogCaptureStarted() {
                Log.i("QASnap", "Log capture started automatically")
            }
            
            override fun onLogCaptureStopped(outputFile: File) {
                Log.i("QASnap", "Log capture saved: ${outputFile.absolutePath}")
                validateAndBackupFile(outputFile)
            }
            
            override fun onLogCaptureError(error: String) {
                Log.e("QASnap", "Log capture error: $error")
                handleLogCaptureError(error)
            }
        })
    }
    
    private fun handleRecordingError(error: String) {
        when {
            error.contains("permission", ignoreCase = true) -> {
                // Permission issue - request permissions again
                requestMissingPermissions()
            }
            error.contains("storage", ignoreCase = true) -> {
                // Storage issue - clean up old files
                cleanupStorageAndRetry()
            }
            error.contains("busy", ignoreCase = true) -> {
                // Device busy - wait and retry
                retryAfterDelay()
            }
            else -> {
                // Generic error - attempt retry or fail gracefully
                if (retryCount < maxRetries) {
                    retryRecording()
                } else {
                    showUserFriendlyError("Recording unavailable. Please try again later.")
                }
            }
        }
    }
    
    private fun handleLogCaptureError(error: String) {
        when {
            error.contains("permission", ignoreCase = true) -> {
                Log.w("QASnap", "Log capture permission denied - continuing without logs")
                // Continue without log capture
            }
            error.contains("buffer", ignoreCase = true) -> {
                // Reduce buffer size and retry
                retryLogCaptureWithSmallerBuffer()
            }
            else -> {
                Log.w("QASnap", "Log capture failed, continuing without logs: $error")
            }
        }
    }
    
    private fun validateAndBackupFile(file: File) {
        if (!file.exists()) {
            Log.e("QASnap", "File does not exist: ${file.absolutePath}")
            return
        }
        
        if (file.length() == 0L) {
            Log.e("QASnap", "File is empty: ${file.absolutePath}")
            file.delete()
            return
        }
        
        // Create backup if file is important
        if (file.length() > 1024 * 1024) { // Files larger than 1MB
            createBackup(file)
        }
        
        Log.i("QASnap", "File validated successfully: ${file.name} (${file.length()} bytes)")
    }
    
    private fun createBackup(file: File) {
        try {
            val backupDir = File(file.parent, "backup")
            backupDir.mkdirs()
            
            val backupFile = File(backupDir, "${System.currentTimeMillis()}_${file.name}")
            file.copyTo(backupFile, overwrite = true)
            
            Log.i("QASnap", "Backup created: ${backupFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("QASnap", "Failed to create backup", e)
        }
    }
    
    private fun retryRecording() {
        retryCount++
        Log.i("QASnap", "Retrying recording (attempt $retryCount/$maxRetries)")
        
        Handler(Looper.getMainLooper()).postDelayed({
            qaSnapRecorder.startRecording()
        }, 2000 * retryCount) // Exponential backoff
    }
    
    private fun retryLogCaptureWithSmallerBuffer() {
        val smallerBuffer = 512 * 1024 // 512KB instead of default 1MB
        
        qaSnapRecorder.startLogCaptureOnly(
            logLevel = "I", // Reduce verbosity too
            bufferSize = smallerBuffer
        )
    }
    
    private fun retryAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            qaSnapRecorder.startRecording()
        }, 5000) // Wait 5 seconds
    }
    
    private fun cleanupStorageAndRetry() {
        // Clean up old files
        val fileManager = FileManager()
        val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days
        fileManager.cleanupOldFiles(qaSnapRecorder, maxAge)
        
        // Retry recording
        Handler(Looper.getMainLooper()).postDelayed({
            qaSnapRecorder.startRecording()
        }, 1000)
    }
    
    private fun requestMissingPermissions() {
        // Check and request missing permissions
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }
    
    private fun showUserFriendlyError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Recording Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
```

## Tips dan Best Practices

1. **Always implement all RecordingListener methods** meskipun tidak semua digunakan
2. **Use appropriate log levels** - Verbose untuk development, Warning+ untuk production
3. **Filter logs by package** untuk fokus pada logs aplikasi Anda
4. **Monitor storage usage** - files video dan logs bisa besar
5. **Handle permissions gracefully** - berikan fallback jika permission ditolak
6. **Clean up old files** secara berkala untuk menghemat storage
7. **Use emergency stop** dalam crash handlers untuk memastikan data tersimpan
8. **Test on different devices** - beberapa device memiliki limitasi khusus
9. **Validate output files** sebelum menganggap operasi berhasil
10. **Provide user feedback** melalui notifications atau UI updates