# QA Snap SDK

[![](https://jitpack.io/v/Coding-Skuy/qa-snap-sdk.svg)](https://jitpack.io/#Coding-Skuy/qa-snap-sdk)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)

QA Snap SDK adalah Android library yang memudahkan QA testing dengan fitur screen recording dan log
capture. SDK ini dirancang khusus untuk membantu tim QA dalam proses testing aplikasi Android.

## ✨ Features

- 📱 **Screen Recording**: High-quality screen recording menggunakan MediaProjection API
- 📝 **Log Capture**: Real-time log capture dengan filtering capabilities
- 🌍 **Environment Aware**: Behaviour berbeda per environment (Development/Staging/Production)
- 🔐 **Permission Management**: Automatic permission request flow
- 🔄 **Lifecycle Aware**: Proper integration dengan Activity lifecycle
- 📞 **Callback System**: Comprehensive callback system untuk recording events
- ⚡ **Error Handling**: Robust error handling dan reporting
- 🔔 **Notification Support**: Recording status notifications
- 📁 **File Management**: Automatic file naming dan storage management

## 🚀 Quick Start

### 1. Installation

Tambahkan repository di `settings.gradle` atau `build.gradle` (Project level):

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

Tambahkan dependency di `build.gradle` (App level):

```gradle
dependencies {
    implementation 'com.github.Coding-Skuy:qa-snap-sdk:1.0.0'
}
```

### 2. Permissions

Tambahkan permissions di `AndroidManifest.xml`:

```xml
<!-- Required permissions -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
                 android:maxSdkVersion="29" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                 android:maxSdkVersion="32" />

<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" 
                 android:minSdkVersion="33" />

<!-- Service declaration -->
<service android:name="io.codingskuy.qa_snap.service.ScreenRecordingService"
         android:enabled="true"
         android:exported="false"
         android:foregroundServiceType="mediaProjection" />

<service android:name="io.codingskuy.qa_snap.service.LogCaptureService"
         android:enabled="true"
         android:exported="false" />
```

### 3. Basic Implementation

#### Option A: Menggunakan QASnapActivity (Recommended)

```kotlin
class MainActivity : QASnapActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // QA Snap sudah diinisialisasi otomatis
    }
    
    override fun shouldAutoStartRecording(): Boolean {
        return false // atau true untuk auto-start
    }
    
    override fun onQARecordingReady() {
        // SDK siap digunakan
        Log.d("QASnap", "QA Snap ready!")
    }
    
    override fun onQARecordingStarted() {
        // Recording dimulai
        showToast("Recording started")
    }
    
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        // Recording selesai
        videoFile?.let { 
            Log.d("QASnap", "Video saved: ${it.absolutePath}") 
        }
        logFile?.let { 
            Log.d("QASnap", "Log saved: ${it.absolutePath}") 
        }
    }
    
    override fun onQARecordingError(error: String) {
        // Error handling
        Log.e("QASnap", "Recording error: $error")
    }
    
    // Control recording
    fun startRecording() {
        qaSnapHelper.startRecording()
    }
    
    fun stopRecording() {
        qaSnapHelper.stopRecording()
    }
}
```

#### Option B: Menggunakan QASnapHelper

```kotlin
class MainActivity : AppCompatActivity(), QASnapCallback {
    
    private lateinit var qaSnapHelper: QASnapHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize QA Snap
        qaSnapHelper = QASnapHelper(this)
        qaSnapHelper.initialize()
    }
    
    override fun onQARecordingReady() {
        // SDK ready
    }
    
    override fun shouldAutoStartRecording(): Boolean = false
    
    override fun onQARecordingStarted() {
        // Recording started
    }
    
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        // Recording completed
    }
    
    override fun onQARecordingError(error: String) {
        // Handle error
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        qaSnapHelper.handlePermissionResult(requestCode, grantResults)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        qaSnapHelper.cleanup()
    }
}
```

### 4. Environment Configuration

Buat class untuk environment management:

```kotlin
object EnvironmentManager {
    fun isQASnapEnabled(): Boolean {
        return when (BuildConfig.BUILD_TYPE) {
            "debug" -> true
            "release" -> false // atau true jika ingin enable di production
            else -> true
        }
    }
    
    fun isLoggingEnabled(): Boolean {
        return BuildConfig.DEBUG
    }
    
    fun getEnvironmentDisplayName(): String {
        return when (BuildConfig.BUILD_TYPE) {
            "debug" -> "Development"
            "release" -> "Production"
            else -> "Unknown"
        }
    }
}
```

## 📖 Advanced Usage

### Custom Recording Settings

```kotlin
// Custom initialization dengan settings
val recorder = QASnapRecorder.initialize(this).apply {
    // Custom settings jika diperlukan
    setRecordingListener(customListener)
}
```

### Handle Recording Events

```kotlin
class CustomRecordingListener : QASnapRecorder.RecordingListener {
    override fun onRecordingStarted() {
        // Custom logic saat recording dimulai
        showNotification("Recording started")
    }
    
    override fun onRecordingStopped(outputFile: File) {
        // Custom logic saat recording berhenti
        uploadToServer(outputFile)
    }
    
    override fun onRecordingError(error: String) {
        // Custom error handling
        sendErrorToAnalytics(error)
    }
    
    override fun onLogCaptureStarted() {
        // Log capture dimulai
    }
    
    override fun onLogCaptureStopped(outputFile: File) {
        // Log capture selesai
    }
    
    override fun onLogCaptureError(error: String) {
        // Log capture error
    }
}
```

### File Management

```kotlin
// Get output directory
val outputDir = qaSnapRecorder.getOutputDirectory()

// Custom file naming
val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    .format(Date())
val customFileName = "qa_test_${timestamp}.mp4"
```

## 🔧 Configuration

### ProGuard Rules

Jika menggunakan ProGuard, tambahkan rules berikut:

```proguard
# QA Snap SDK
-keep class io.codingskuy.qa_snap.** { *; }
-keepclassmembers class io.codingskuy.qa_snap.** { *; }

# MediaProjection
-keep class android.media.projection.** { *; }
```

### Gradle Configuration

```gradle
android {
    compileSdk 34
    
    defaultConfig {
        minSdk 21
        targetSdk 34
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
```

## 📱 Compatibility

- **Minimum SDK**: API 21 (Android 5.0 Lollipop)
- **Target SDK**: API 34 (Android 14)
- **Language**: Kotlin
- **Architecture**: Supports all architectures (arm64-v8a, armeabi-v7a, x86, x86_64)

## 🔍 Troubleshooting

### Common Issues

1. **MediaProjection Permission Denied**
   ```kotlin
   // Pastikan permission diminta sebelum recording
   if (!hasBasicPermissions()) {
       requestBasicPermissions()
   }
   ```

2. **File Not Found**
   ```kotlin
   // Check if external storage available
   if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
       // Storage available
   }
   ```

3. **Recording Tidak Dimulai**
   ```kotlin
   // Check if already recording
   if (!qaSnapRecorder.isRecording()) {
       qaSnapRecorder.startRecording()
   }
   ```

### Debug Mode

Enable debug logging:

```kotlin
if (BuildConfig.DEBUG) {
    Log.d("QASnap", "Debug mode enabled")
}
```

## 📄 API Reference

### QASnapRecorder

| Method | Description |
|--------|-------------|
| `initialize(activity)` | Initialize recorder dengan activity |
| `startRecording()` | Mulai screen recording |
| `stopRecording()` | Stop screen recording |
| `isRecording()` | Check recording status |
| `getOutputDirectory()` | Get output directory |
| `setRecordingListener(listener)` | Set recording listener |

### QASnapHelper

| Method | Description |
|--------|-------------|
| `initialize()` | Initialize helper |
| `startRecording()` | Start recording dengan permission check |
| `stopRecording()` | Stop recording |
| `isRecording()` | Check recording status |
| `cleanup()` | Cleanup resources |
| `handlePermissionResult()` | Handle permission result |

### QASnapCallback

| Callback | Description |
|----------|-------------|
| `onQARecordingReady()` | SDK ready untuk digunakan |
| `onQARecordingStarted()` | Recording dimulai |
| `onQARecordingComplete()` | Recording selesai |
| `onQARecordingError()` | Error occurred |
| `shouldAutoStartRecording()` | Auto-start configuration |

## 🎯 Best Practices

1. **Permission Handling**: Selalu check permission sebelum recording
2. **Lifecycle Management**: Cleanup resources di onDestroy()
3. **Error Handling**: Implementasikan proper error handling
4. **File Management**: Handle file cleanup untuk menghemat storage
5. **Performance**: Stop recording saat tidak diperlukan

## 📊 Performance

- **Memory Usage**: ~5-10MB saat recording
- **CPU Usage**: Minimal impact
- **Storage**: Video files ~1-5MB per menit (tergantung resolution)
- **Battery**: Moderate impact saat recording aktif

## 🔐 Security

- No data sent to external servers
- All files stored locally
- Permissions diminta sesuai kebutuhan
- No sensitive information logged

## 🤝 Contributing

1. Fork repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

## 📝 License

```
MIT License

Copyright (c) 2024 Coding Skuy

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 📞 Support

- 📧 Email: codingskuy.io@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/Coding-Skuy/qa-snap-sdk/issues)
- 📖 Documentation: [GitHub Wiki](https://github.com/Coding-Skuy/qa-snap-sdk/wiki)

## 🎉 Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

---

Made with ❤️ by [Coding Skuy Team](https://github.com/Coding-Skuy)