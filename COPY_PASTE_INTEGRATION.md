# QA Snap SDK - Copy-Paste Integration Guide

**Ready-to-use code snippets untuk integrasi instant ke proyek Android apapun.**

## 🚀 Quick Integration (Choose One Method)

### Method 1: Extend QASnapActivity (Recommended)

#### Step 1: Update your Activity

```kotlin
// REPLACE YOUR EXISTING ACTIVITY:
// class MainActivity : AppCompatActivity() {
// WITH THIS:
class MainActivity : QASnapActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // QA recording starts automatically!
        // No additional code needed
    }
    
    // Optional: Handle file completion
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        Toast.makeText(this, "QA files saved!", Toast.LENGTH_SHORT).show()
        
        // Optional: Upload files, show dialog, etc.
        // uploadToServer(videoFile, logFile)
    }
    
    // Optional: Control when to record
    override fun shouldAutoStartRecording(): Boolean {
        return BuildConfig.DEBUG // Only debug builds
    }
}
```

#### Step 2: Add Import

```kotlin
// ADD THIS IMPORT:
import io.codingskuy.qa_snap.QASnapActivity
import java.io.File
```

### Method 2: One-Line Integration

#### Step 1: Add to onCreate()

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ADD THIS ONE LINE:
        QASnap.start(this)
            .onComplete { videoFile, logFile ->
                Toast.makeText(this, "QA files saved!", Toast.LENGTH_SHORT).show()
            }
    }
    
    // ADD THIS METHOD:
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // QASnapHelper handles permissions automatically
    }
}
```

#### Step 2: Add Imports

```kotlin
// ADD THESE IMPORTS:
import io.codingskuy.qa_snap.QASnap
import java.io.File
```

## 📋 Copy-Paste Setup Files

### 1. build.gradle (Module: app)

```kotlin
// ADD TO dependencies block:
dependencies {
    implementation project(':qa-snap-sdk')
    // ... your existing dependencies
}
```

### 2. AndroidManifest.xml

```xml
<!-- ADD THESE PERMISSIONS: -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.READ_LOGS" />

<!-- Android 13+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 3. settings.gradle

```kotlin
// ADD SDK MODULE:
include ':qa-snap-sdk'
```

## 🎯 Specific Use Case Templates

### Template 1: QA Testing Activity

```kotlin
class QATestActivity : QASnapActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qa_test)
        
        // Recording starts automatically for QA testing
    }
    
    override fun shouldAutoStartRecording(): Boolean {
        return BuildConfig.BUILD_TYPE in listOf("debug", "qa", "staging")
    }
    
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        // Send to QA team automatically
        sendToQATeam(videoFile, logFile)
    }
    
    private fun sendToQATeam(video: File?, logs: File?) {
        // Your upload logic here
    }
}
```

### Template 2: Bug Reporting Activity

```kotlin
class BugReportActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bug_report)
        
        // Start recording when user reports a bug
        QASnap.start(this)
            .onComplete { video, logs ->
                // Attach files to bug report
                attachFilesToBugReport(video, logs)
            }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Handled automatically
    }
    
    private fun attachFilesToBugReport(video: File?, logs: File?) {
        // Your bug report logic
    }
}
```

### Template 3: Manual Control Activity

```kotlin
class ManualControlActivity : AppCompatActivity() {
    
    private lateinit var qaHelper: QASnapHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)
        
        // Manual control setup
        qaHelper = QASnapHelper(this).apply {
            initialize(autoStart = false) // Don't auto-start
            onComplete { video, logs ->
                handleCompletion(video, logs)
            }
        }
        
        // Setup buttons
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            qaHelper.startRecording()
        }
        
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            qaHelper.stopRecording()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        qaHelper.handlePermissionResult(requestCode, grantResults)
    }
    
    private fun handleCompletion(video: File?, logs: File?) {
        // Your completion logic
    }
}
```

## 🛡️ Advanced Templates

### Template 4: Conditional Recording by Build Type

```kotlin
class SmartActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart)
        
        // Smart conditional recording
        when (BuildConfig.BUILD_TYPE) {
            "debug" -> {
                // Full recording for debug
                QASnap.start(this)
            }
            "qa", "staging" -> {
                // Recording with custom settings for QA
                QASnap.createSession(this)
                    .logLevel("I") // Info level
                    .packageFilter(packageName) // This app only
                    .build()
            }
            "release" -> {
                // No recording in production
                // Or only crash recording:
                // QASnap.start(this, autoStart = false)
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Handled automatically
    }
}
```

### Template 5: Custom Application Class

```kotlin
class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Enable QA recording globally for debug builds
        QASnap.enableForDebug(this)
    }
}
```

## 📱 UI Templates

### Template 1: Simple Stop Button

```xml
<!-- ADD TO YOUR LAYOUT: -->
<Button
    android:id="@+id/btnStopRecording"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:text="🛑 Stop QA Recording"
    android:enabled="false" />

<TextView
    android:id="@+id/tvRecordingStatus"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="⭕ Ready to Record"
    android:textSize="16sp" />
```

### Template 2: Button Handler

```kotlin
// ADD TO YOUR ACTIVITY:
private fun setupRecordingControl() {
    val btnStop = findViewById<Button>(R.id.btnStopRecording)
    val tvStatus = findViewById<TextView>(R.id.tvRecordingStatus)
    
    btnStop.setOnClickListener {
        // Stop QA recording (video + logs)
        qaHelper.stopRecording()
        
        // Or if using QASnapActivity:
        // stopQARecording()
    }
    
    // Update status periodically
    val handler = Handler(Looper.getMainLooper())
    val statusUpdater = object : Runnable {
        override fun run() {
            val isRecording = qaHelper.isRecording() // or isQARecording()
            tvStatus.text = if (isRecording) "🔴 Recording..." else "⭕ Ready"
            btnStop.isEnabled = isRecording
            handler.postDelayed(this, 1000)
        }
    }
    handler.post(statusUpdater)
}
```

## ⚡ Ultra-Quick Integration (30 seconds)

### For Existing Apps:

1. **Copy this to your main activity:**

```kotlin
// CHANGE THIS LINE:
class MainActivity : AppCompatActivity() {
// TO THIS:  
class MainActivity : QASnapActivity() {

// ADD THIS IMPORT:
import io.codingskuy.qa_snap.QASnapActivity
```

2. **Add permissions to AndroidManifest.xml** (copy from above)

3. **Run app** - Recording starts automatically! 🎉

### For New Apps:

1. **Use this as your MainActivity template:**

```kotlin
class MainActivity : QASnapActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Done! QA recording active
    }
}
```

## 🎉 Success Checklist

After integration, you should see:

- ✅ **Permission dialog** appears automatically
- ✅ **"QA Recording started"** toast message
- ✅ **Recording notification** in notification bar
- ✅ **Stop button** in notification works
- ✅ **Files saved** in `/Android/data/[package]/files/` directories

**If you see all of the above, integration is successful! 🎬📋✨**

## 🆘 Troubleshooting

### Issue: No permission dialog

- **Fix**: Check AndroidManifest.xml permissions

### Issue: No recording notification

- **Fix**: Grant notification permission (Android 13+)

### Issue: Files not saved

- **Fix**: Grant storage permissions and check available space

### Issue: Import errors

- **Fix**: Add `implementation project(':qa-snap-sdk')` to build.gradle

**Need help? Check the complete documentation in README.md!**