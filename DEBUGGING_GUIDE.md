# QA Snap SDK - Debugging Guide

Panduan untuk debugging masalah yang mungkin terjadi setelah simplifikasi SDK, khususnya jika video
recording tidak berfungsi tetapi log capture masih bekerja.

## 🔍 **Debugging Steps**

### **Step 1: Enable Debug Logging**

Tambahkan filter log untuk melihat semua QA Snap logs:

```bash
# Filter QA Snap logs only
adb logcat | grep -E "(QASnap|ScreenRecording|LogCapture)"

# Or specific to our helper
adb logcat | grep QASnapHelper

# Or specific to recorder  
adb logcat | grep QASnapRecorder
```

### **Step 2: Check Expected Log Flow**

Skenario normal untuk video recording harus menghasilkan log sequence seperti ini:

```
QASnapHelper: Initializing QASnapHelper with autoStart: true
QASnapHelper: QASnapRecorder initialized: true
QASnapHelper: All permissions already granted
QASnapHelper: Starting recording automatically
QASnapHelper: startRecording() called
QASnapRecorder: startRecording() called - current isRecording: false
QASnapRecorder: Starting log capture with default QA settings
QASnapRecorder: Creating media projection intent
QASnapRecorder: Launching media projection launcher
QASnapRecorder: startRecordingService called with data
QASnapRecorder: Screen dimensions: 1080x2400, density: 420
QASnapRecorder: Starting ScreenRecordingService as foreground service
QASnapRecorder: Service started successfully, isRecording set to true
QASnapRecorder: Calling onRecordingStarted callback
QASnapHelper: Recording started callback received
```

### **Step 3: Identify Missing Parts**

#### **A. Jika log stop pada "Launching media projection launcher":**

- Media projection permission dialog tidak muncul
- Check AndroidManifest.xml permissions
- Check if activity can show system dialogs

#### **B. Jika log stop pada "Starting ScreenRecordingService":**

- Service tidak bisa dimulai
- Check if service registered in manifest
- Check foreground service permissions

#### **C. Jika callback tidak dipanggil:**

- Instance communication problem
- Check singleton instance availability

## 🚨 **Common Issues & Solutions**

### **Issue 1: QASnapRecorder getInstance() returns null**

**Symptoms:**

```
QASnapRecorder: getInstance() called - instance is null
```

**Solution:**

```kotlin
// Ensure QASnapHelper.initialize() is called before any operations
qaSnapHelper = QASnapHelper(this).apply {
    initialize(autoStart = true) // This calls QASnapRecorder.initialize()
}
```

### **Issue 2: Media Projection Permission Denied**

**Symptoms:**

```
QASnapHelper: Recording error: Media projection permission denied
```

**Solution:**

- Check if permission dialog appeared
- Grant permission manually
- Check if activity can show system dialogs

### **Issue 3: Service Start Failure**

**Symptoms:**

```
QASnapRecorder: Failed to start recording service: [error message]
```

**Solutions:**

1. **Check AndroidManifest.xml:**

```xml
<service
    android:name="io.codingskuy.qa_snap.service.ScreenRecordingService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection" />
```

2. **Check Permissions:**

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

### **Issue 4: Only Log Capture Works**

**Symptoms:**

- Log files are created ✅
- Video files are not created ❌
- No video recording errors in logs

**Debugging:**

```bash
# Check if ScreenRecordingService started
adb logcat | grep ScreenRecordingService

# Check if MediaRecorder issues
adb logcat | grep MediaRecorder

# Check file creation
adb shell ls /sdcard/Android/data/[your.package]/files/QASnapRecordings/
```

**Possible Causes:**

1. MediaRecorder initialization failed
2. Storage permission issues
3. Insufficient storage space
4. Screen recording hardware limitations

## 🛠️ **Advanced Debugging**

### **Enable Verbose SDK Logging**

Add this to your Activity for maximum debug info:

```kotlin
class MainActivity : QASnapActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Enable all logging
        Log.setLogLevel(Log.VERBOSE)
    }
    
    override fun onQARecordingReady() {
        Log.d("Debug", "QA Recording Ready!")
        super.onQARecordingReady()
    }
    
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        Log.d("Debug", "QA Recording Complete - Video: ${videoFile?.exists()}, Logs: ${logFile?.exists()}")
        super.onQARecordingComplete(videoFile, logFile)
    }
}
```

### **Manual Testing Steps**

1. **Test Log Capture Only:**

```kotlin
// Test if log capture works independently
val helper = QASnapHelper(this)
helper.initialize(autoStart = false)
helper.getRecorder()?.startLogCaptureOnly()
// Wait 5 seconds
helper.getRecorder()?.stopLogCaptureOnly()
```

2. **Test Video Recording Only:**

```kotlin
// Test if video recording works independently  
val recorder = QASnapRecorder.initialize(this)
recorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
    override fun onRecordingStarted() {
        Log.d("Test", "Video recording started")
    }
    override fun onRecordingStopped(outputFile: File) {
        Log.d("Test", "Video saved: ${outputFile.absolutePath}")
    }
    override fun onRecordingError(error: String) {
        Log.e("Test", "Video error: $error")
    }
    // ... other callbacks
})

// Start recording manually (without log capture)
// This bypasses the unified approach to test video only
```

### **Check File System**

```kotlin
private fun debugFileSystem() {
    val recorder = qaSnapHelper.getRecorder()
    val videoDir = recorder?.getOutputDirectory()
    val logDir = recorder?.getLogOutputDirectory()
    
    Log.d("Debug", "Video Dir: ${videoDir?.absolutePath}")
    Log.d("Debug", "Video Dir Exists: ${videoDir?.exists()}")
    Log.d("Debug", "Video Dir Writable: ${videoDir?.canWrite()}")
    Log.d("Debug", "Video Files: ${videoDir?.listFiles()?.size ?: 0}")
    
    Log.d("Debug", "Log Dir: ${logDir?.absolutePath}")
    Log.d("Debug", "Log Dir Exists: ${logDir?.exists()}")
    Log.d("Debug", "Log Dir Writable: ${logDir?.canWrite()}")
    Log.d("Debug", "Log Files: ${logDir?.listFiles()?.size ?: 0}")
}
```

## 🔧 **Quick Fixes**

### **Fix 1: Force Reinitialize**

```kotlin
// In your activity, if recording doesn't work:
override fun onResume() {
    super.onResume()
    
    // Force reinitialize if needed
    if (!qaSnapHelper.isRecording()) {
        qaSnapHelper = QASnapHelper(this)
        qaSnapHelper.initialize(autoStart = false)
    }
}
```

### **Fix 2: Fallback to Manual Mode**

```kotlin
// If automatic mode fails, try manual mode:
class MainActivity : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Manual initialization (fallback)
        qaSnapRecorder = QASnapRecorder.initialize(this)
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            // ... implement all callbacks
        })
        
        // Start manually when needed
        qaSnapRecorder.startRecording()
    }
}
```

### **Fix 3: Permission Double-Check**

```kotlin
private fun checkAllPermissions() {
    val permissions = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.FOREGROUND_SERVICE
    )
    
    permissions.forEach { permission ->
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        Log.d("Permissions", "$permission: $granted")
    }
}
```

## 📋 **Debugging Checklist**

- [ ] **Logs show QASnapRecorder initialized successfully**
- [ ] **All permissions granted**
- [ ] **Media projection intent launched**
- [ ] **ScreenRecordingService started**
- [ ] **Recording started callback received**
- [ ] **getInstance() returns valid instance**
- [ ] **Service can communicate back to recorder**
- [ ] **File directories exist and are writable**
- [ ] **Sufficient storage space available**

## 🎯 **Contact Points for Issues**

Jika masalah masih berlanjut, mohon share:

1. **Complete log output** dari `adb logcat | grep QASnap`
2. **AndroidManifest.xml** permissions section
3. **Build.gradle** dependencies
4. **Device info** (Android version, manufacturer)
5. **Test results** dari manual testing steps above

**Dengan informasi ini, kita bisa identify root cause dan fix issue dengan tepat!** 🔍🛠️