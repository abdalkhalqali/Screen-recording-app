# QA Snap SDK - Simple Integration Examples

Dokumentasi ini menunjukkan berbagai cara super mudah untuk mengintegrasikan QA Snap SDK ke proyek
Android apapun.

## 🚀 Method 1: One-Line Integration

### Dengan QASnapHelper - Paling Simple!

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ONE LINE - AUTO START RECORDING!
        QASnap.start(this)
        
        // That's it! Recording starts automatically after permissions
        // Video + logs will be saved when user stops from notification
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // No need to handle - QASnapHelper handles everything automatically
    }
}
```

### Dengan Callbacks (2-3 Lines)

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // TWO LINES - WITH COMPLETION CALLBACK
        QASnap.start(this)
            .onComplete { videoFile, logFile ->
                // Handle completed files
                Toast.makeText(this, "QA files saved!", Toast.LENGTH_SHORT).show()
            }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Still no handling needed!
    }
}
```

## 🏗️ Method 2: Extend QASnapActivity - Zero Setup!

### Super Simple - Just Extend!

```kotlin
class MainActivity : QASnapActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ZERO LINES OF CODE!
        // Recording starts automatically
        // Permissions handled automatically  
        // Files saved automatically
    }
    
    // Optional: Handle completion
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        Toast.makeText(this, "QA Recording completed!", Toast.LENGTH_SHORT).show()
    }
    
    // Optional: Control auto-start behavior
    override fun shouldAutoStartRecording(): Boolean {
        return BuildConfig.DEBUG // Only record in debug builds
    }
}
```

### With Manual Control

```kotlin
class MainActivity : QASnapActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Setup UI
        findViewById<Button>(R.id.btnStartRecording).setOnClickListener {
            startQARecording()
        }
        
        findViewById<Button>(R.id.btnStopRecording).setOnClickListener {
            stopQARecording()
        }
    }
    
    override fun shouldAutoStartRecording(): Boolean = false // Manual control
    
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        // Upload files to server, show dialog, etc.
        uploadQAFiles(videoFile, logFile)
    }
}
```

## 🛠️ Method 3: Builder Pattern - Full Customization

### Advanced Configuration

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // BUILDER PATTERN - FULL CONTROL
        QASnap.createSession(this)
            .autoStart(false)           // Manual start
            .logLevel("D")              // Debug level logs
            .tagFilter("MyApp|Network") // Filter specific tags
            .packageFilter(packageName) // Only this app's logs
            .onReady {
                // Called when ready to record
                showRecordingDialog()
            }
            .onComplete { video, logs ->
                // Handle completion
                handleQACompletion(video, logs)
            }
            .build()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Builder handles permissions automatically
    }
}
```

## 📋 Method 4: Annotation-Based (Future Feature)

### Planned for v2.0

```kotlin
@EnableQARecording(
    autoStart = true,
    logLevel = "I",
    buildTypes = ["debug", "staging"]
)
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ZERO CODE - ANNOTATION HANDLES EVERYTHING!
    }
}
```

## 🔧 Integration Comparison

### Before (Manual Integration)

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    private val PERMISSION_REQUEST_CODE = 1001
    private var arePermissionsGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        qaSnapRecorder = QASnapRecorder.initialize(this)
        setupRecordingListener()

        if (checkPermissions()) {
            arePermissionsGranted = true
            startRecording()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // ... more permission logic
        return permissions
    }

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                arePermissionsGranted = true
                startRecording()
            } else {
                // Handle permission denial
            }
        }
    }

    private fun setupRecordingListener() {
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                // Handle start
            }
            override fun onRecordingStopped(outputFile: File) {
                // Handle video completion
            }
            override fun onRecordingError(error: String) {
                // Handle error
            }
            override fun onLogCaptureStarted() {
                // Handle log start
            }
            override fun onLogCaptureStopped(outputFile: File) {
                // Handle log completion
            }
            override fun onLogCaptureError(error: String) {
                // Handle log error
            }
        })
    }

    private fun startRecording() {
        if (checkPermissions()) {
            qaSnapRecorder.startRecording()
        }
    }
}

// TOTAL: ~80-100 lines of boilerplate code!
```

### After (Simple Integration)

```kotlin
class MainActivity : QASnapActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Recording starts automatically!
    }
    
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        // Handle completion
        Toast.makeText(this, "QA files saved!", Toast.LENGTH_SHORT).show()
    }
}

// TOTAL: ~10 lines of actual code!
```

## 🎯 Use Cases by Complexity

### Level 1: Just Record Everything (90% of cases)

```kotlin
// ONE LINE in onCreate:
QASnap.start(this)
```

### Level 2: Handle Completion (Show dialog, upload, etc.)

```kotlin
// TWO LINES in onCreate:
QASnap.start(this)
    .onComplete { video, logs -> handleFiles(video, logs) }
```

### Level 3: Manual Control (Start/stop buttons)

```kotlin
// Extend QASnapActivity, override shouldAutoStartRecording() = false
// Use startQARecording() and stopQARecording() methods
```

### Level 4: Full Customization (Log levels, filters, etc.)

```kotlin
// Use QASnap.createSession(this).logLevel().tagFilter().build()
```

## 📱 Real-World Examples

### E-commerce App Testing

```kotlin
class ProductListActivity : QASnapActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)
        // Auto-records user browsing products
    }
    
    override fun shouldAutoStartRecording(): Boolean {
        return BuildConfig.BUILD_TYPE == "qa" // Only QA builds
    }
}
```

### Banking App QA

```kotlin
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        // Record sensitive login flow for QA review
        QASnap.createSession(this)
            .logLevel("W") // Only warnings and errors
            .tagFilter("Security|Auth") // Security-related logs only
            .onComplete { video, logs ->
                // Upload to secure QA server
                uploadToSecureServer(video, logs)
            }
            .build()
    }
}
```

### Game Testing

```kotlin
class GameActivity : QASnapActivity() {
    override fun shouldAutoStartRecording(): Boolean {
        // Record gameplay automatically in test builds
        return BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "beta"
    }
    
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        // Send to game analytics
        GameAnalytics.uploadGameplayVideo(videoFile)
        GameAnalytics.uploadGameLogs(logFile)
    }
}
```

## 🔧 Build.gradle Integration

### Conditional Integration by Build Type

```kotlin
android {
    buildTypes {
        debug {
            // QA recording enabled in debug
            buildConfigField "boolean", "QA_RECORDING_ENABLED", "true"
        }
        release {
            // QA recording disabled in release
            buildConfigField "boolean", "QA_RECORDING_ENABLED", "false"
        }
        qa {
            // QA recording enabled in QA builds
            buildConfigField "boolean", "QA_RECORDING_ENABLED", "true"
        }
    }
}
```

```kotlin
class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Conditional QA recording
        if (BuildConfig.QA_RECORDING_ENABLED) {
            QASnap.start(this)
        }
    }
}
```

## 🎉 Benefits Summary

### For Developers:

- **90% less code** - From ~100 lines to ~10 lines
- **Zero boilerplate** - No permission handling, no listener setup
- **Flexible** - Choose your integration level
- **Type-safe** - Kotlin-first with full type safety

### For QA Teams:

- **Consistent** - Same recording behavior across all apps
- **Automatic** - No manual steps needed
- **Comprehensive** - Always gets video + logs
- **Reliable** - Built-in error handling and recovery

### For Projects:

- **Easy adoption** - Add to any existing Android project
- **Non-intrusive** - Doesn't affect production builds
- **Maintainable** - Centralized recording logic
- **Scalable** - Works from single activity to large apps

## 🚀 Quick Start Checklist

1. **Add dependency** to `build.gradle`
2. **Add permissions** to `AndroidManifest.xml`
3. **Choose integration method:**
    - Extend `QASnapActivity` (easiest)
    - Use `QASnap.start(this)` (one-liner)
    - Use builder pattern (full control)
4. **Test** - Run app, grant permissions, recording starts!
5. **Stop recording** - Use notification or in-app controls
6. **Find files** - Check `/Android/data/[package]/files/` directories

**That's it! QA Snap SDK is now integrated and recording your app! 🎬📋✨**