# QA Snap SDK - Project Summary

## Overview

QA Snap SDK adalah Android library untuk **unified recording** yang dirancang
khusus untuk Quality Assurance (QA) testing, debugging, dan dokumentasi user flow. SDK ini
menyediakan API sederhana untuk merekam layar perangkat dalam format MP4 dan menangkap logs system
dalam format TXT secara bersamaan.

## Key Features

### 🎬 Unified Recording

- ✅ **Full Screen Capture** - Merekam seluruh layar perangkat
- ✅ **High Quality Output** - Video MP4 dengan resolusi native device
- ✅ **Background Operation** - Recording berjalan stabil menggunakan foreground service
- ✅ **Auto File Management** - Penyimpanan otomatis dengan timestamp
- ✅ **Real-time Log Capture** - Menangkap logs system secara real-time

### 📋 Advanced Control

- ✅ **Individual Control** - Kontrol terpisah untuk recording dan log capture
- ✅ **Customizable Filters** - Filter berdasarkan log level, tag, atau package
- ✅ **TXT Format Output** - Logs disimpan dalam format text yang mudah dibaca
- ✅ **Comprehensive Logging** - Mendukung semua log levels (V, D, I, W, E, F, S)

### 🔄 Simultaneous Operation

- ✅ **Unified Recording** - Jalankan recording dan log capture secara bersamaan
- ✅ **Synchronized Control** - Start/stop operasi dengan satu pemanggilan
- ✅ **Independent Operation** - Dapat dijalankan terpisah sesuai kebutuhan

### 🛡️ Reliability & Safety

- ✅ **Emergency Stop** - Auto-stop saat crash atau force close aplikasi
- ✅ **Permission Handling** - Request dan validasi permissions secara otomatis
- ✅ **Error Recovery** - Robust error handling dan recovery mechanisms
- ✅ **Lifecycle Aware** - Terintegrasi dengan Android lifecycle management

## Architecture

### Module Structure
```
android-media-projection-sample/
├── qa-snap-sdk/                 # Core SDK Module
│   ├── src/main/java/io/codingskuy/qa_snap/
│   │   ├── QASnapRecorder.kt    # Main SDK Interface
│   │   └── service/
│   │       ├── ScreenRecordingService.kt  # Screen recording service
│   │       └── LogCaptureService.kt       # Log capture service
│   └── AndroidManifest.xml      # SDK permissions & services
├── qa-snap-demo/               # Demo Application
│   ├── src/main/java/io/codingskuy/qa_snap_demo/
│   │   ├── MainActivity.kt     # Entry point - starts recording+logging
│   │   ├── SignInActivity.kt   # Login screen
│   │   └── HomeActivity.kt     # Control panel - stop recording/logging
│   └── res/layout/            # Demo UI layouts
└── docs/
    ├── README.md              # Complete documentation
    └── USAGE_EXAMPLES.md      # Code examples & use cases
```

### Core Components

#### 1. QASnapRecorder (Main API)

```kotlin
class QASnapRecorder {
    // Unified Recording (Default Behavior)
    fun startRecording()        // Starts video + logs automatically
    fun stopRecording()         // Stops video + logs automatically
    fun isRecording(): Boolean
    
    // Individual Control (Advanced)
    fun startLogCaptureOnly(logLevel, tagFilter, packageFilter, bufferSize)
    fun stopLogCaptureOnly()
    fun isCapturingLogs(): Boolean
    
    // Custom Recording (Advanced)
    fun startRecordingWithCustomLogs(logLevel, tagFilter, packageFilter)
    fun emergencyStopAll(context)
    
    // File Management
    fun getOutputDirectory(): File
    fun getLogOutputDirectory(): File
}
```

#### 2. ScreenRecordingService

- Foreground service untuk screen recording
- MediaProjection API untuk screen capture
- MediaRecorder untuk encoding video
- Notification controls untuk user interaction
- Crash detection dan emergency stop

#### 3. LogCaptureService

- Foreground service untuk log capture
- Logcat process execution untuk mendapatkan logs
- Real-time filtering berdasarkan level, tag, package
- File buffering untuk performa optimal
- Auto-cleanup dan error recovery

#### 4. RecordingListener Interface
```kotlin
interface RecordingListener {
    // Screen Recording Events
    fun onRecordingStarted()
    fun onRecordingStopped(outputFile: File)
    fun onRecordingError(error: String)
    
    // Log Capture Events
    fun onLogCaptureStarted()
    fun onLogCaptureStopped(outputFile: File)
    fun onLogCaptureError(error: String)
}
```

## Technical Specifications

### Requirements

- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin 1.9.10
- **Gradle**: 8.5+
- **Permissions**: Media projection, Storage, Audio recording, Logs access

### Output Formats

#### Video Files

- **Format**: MP4 (H.264 encoding)
- **Resolution**: Native device resolution
- **Frame Rate**: 24 FPS (configurable)
- **Bitrate**: Dynamic based on resolution
- **Audio**: Optional (requires RECORD_AUDIO permission)
- **Naming**: `qa_snap_recording_yyyyMMdd_HHmmss.mp4`

#### Log Files

- **Format**: Plain text (.txt)
- **Content**: Timestamped log entries
- **Encoding**: UTF-8
- **Naming**: `qa_snap_logs_yyyyMMdd_HHmmss.txt`
- **Structure**:
  ```
  === QA Snap Log Capture Started ===
  Timestamp: 2024-11-11 22:30:15
  Log Level: V
  Package Filter: com.myapp
  =====================================
  
  11-11 22:30:16.123 D/MyApp: Debug log message
  11-11 22:30:16.456 I/MyApp: Info log message
  ...
  
  === QA Snap Log Capture Ended ===
  End Timestamp: 2024-11-11 22:35:20
  ```

### Storage Locations

- **Video Output**: `/Android/data/[package]/files/QASnapRecordings/`
- **Log Output**: `/Android/data/[package]/files/QASnapLogs/`
- **Auto-cleanup**: Configurable retention policies
- **Backup Support**: Optional file backup mechanisms

## Implementation Examples

### Basic Usage

```kotlin
// Initialize SDK
val qaSnapRecorder = QASnapRecorder.initialize(activity)

// Set listener
qaSnapRecorder.setRecordingListener(recordingListener)

// Start unified recording
qaSnapRecorder.startRecording()

// Start log capture only  
qaSnapRecorder.startLogCaptureOnly("D", null, packageName, 1024)

// Start custom recording
qaSnapRecorder.startRecordingWithCustomLogs("V", null, packageName)
```

### Advanced Filtering

```kotlin
// Network debugging
qaSnapRecorder.startLogCaptureOnly(
    "D",
    "OkHttp|Retrofit|NetworkManager",
    packageName,
    2048
)

// Error-only logging
qaSnapRecorder.startLogCaptureOnly(
    "E",
    null,
    null, // Include system errors
    1024
)

// Performance monitoring
qaSnapRecorder.startLogCaptureOnly(
    "W",
    "Performance|Memory|ANR",
    packageName,
    4096
)
```

### QA Testing Integration

```kotlin
class QATestRunner {
    fun runTestCase(testName: String) {
        // Start comprehensive capture
        qaSnapRecorder.startRecording()
        
        // Execute test steps
        executeTestSteps()
        
        // Stop and save results
        qaSnapRecorder.stopRecording()
    }
}
```

## Demo Application Flow

### 1. MainActivity (Splash + Initialization)

- Initialize QASnapRecorder
- Request necessary permissions
- Start unified recording automatically
- Navigate to SignInActivity

### 2. SignInActivity (Login Screen)

- Simple login form
- Skip option for demo purposes
- Recording continues in background
- Navigate to HomeActivity

### 3. HomeActivity (Control Panel)

- Real-time status display
- Stop recording button
- Stop log capture button
- Stop both button
- Log generation test buttons
- User activity simulation buttons

### Demo Features

- **Permission Handling**: Auto-request all required permissions
- **Status Monitoring**: Real-time display of recording/logging status
- **Control Interface**: Easy stop controls with confirmation dialogs
- **Test Log Generation**: Buttons to generate different log levels
- **Error Handling**: Graceful error display and recovery
- **File Management**: Automatic file saving with user notifications

## Use Cases

### 1. QA Testing

- **User Flow Documentation**: Record user interactions + system logs
- **Bug Reproduction**: Capture exact steps and system state
- **Regression Testing**: Compare recordings across app versions
- **Performance Analysis**: Monitor logs for performance issues

### 2. Development & Debugging

- **Feature Testing**: Record new features with detailed logs
- **Crash Investigation**: Capture logs leading to crashes
- **Network Debugging**: Filter logs for network-related issues
- **UI Testing**: Record UI interactions with event logs

### 3. Support & Documentation

- **Bug Reports**: Users can generate comprehensive bug reports
- **Training Materials**: Create training videos with system context
- **Issue Resolution**: Support teams get video + logs for issues
- **Process Documentation**: Document complex workflows

### 4. Automated Testing

- **CI/CD Integration**: Capture test execution with logs
- **Test Result Documentation**: Video evidence of test results
- **Failure Analysis**: Detailed logs for failed tests
- **Performance Monitoring**: Continuous performance log collection

## File Management & Organization

### Directory Structure
```
/Android/data/com.myapp/files/
├── QASnapRecordings/
│   ├── qa_snap_recording_20241111_143052.mp4
│   ├── qa_snap_recording_20241111_150234.mp4
│   └── backup/
│       └── 1699123456789_qa_snap_recording_20241111_143052.mp4
└── QASnapLogs/
    ├── qa_snap_logs_20241111_143052.txt
    ├── qa_snap_logs_20241111_150234.txt
    └── backup/
        └── 1699123456789_qa_snap_logs_20241111_143052.txt
```

### File Size Optimization

- **Video Compression**: Efficient H.264 encoding
- **Log Filtering**: Reduce file size with targeted filtering
- **Buffer Management**: Configurable buffer sizes
- **Auto Cleanup**: Automatic removal of old files

### Backup & Recovery

- **Automatic Backup**: Important files backed up automatically
- **Recovery Mechanisms**: Restore from backup on corruption
- **Storage Monitoring**: Monitor available storage space
- **Cleanup Policies**: Configurable retention and cleanup rules

## Security & Privacy Considerations

### Permission Management

- **Minimal Permissions**: Only request necessary permissions
- **Runtime Requests**: Request permissions when needed
- **Graceful Degradation**: Continue operation without optional permissions
- **User Control**: Clear explanation of permission usage

### Data Protection

- **Local Storage**: All files stored locally, no cloud upload
- **App-specific Directories**: Files isolated per application
- **User Consent**: Clear indication when recording/logging starts
- **Data Retention**: User-controlled file retention policies

### Log Privacy

- **Package Filtering**: Limit logs to specific applications
- **Sensitive Data**: No automatic PII filtering (developer responsibility)
- **System Logs**: Optional inclusion of system-wide logs
- **Custom Filters**: Developer-defined filtering rules

## Performance Impact

### CPU Usage

- **Efficient Encoding**: Optimized video encoding settings
- **Background Processing**: Services run with appropriate priority
- **Memory Management**: Configurable buffer sizes
- **Process Isolation**: Services isolated from main app process

### Storage Impact

- **Compressed Output**: Efficient file formats
- **Configurable Quality**: Adjustable video quality settings
- **Auto Cleanup**: Automatic removal of old files
- **Storage Monitoring**: Alerts for low storage space

### Battery Impact

- **Optimized Services**: Services designed for minimal battery drain
- **Conditional Recording**: Record only when needed
- **Efficient Notifications**: Low-impact notification updates
- **Power Management**: Respect system power management

## Future Roadmap

### Planned Features

- **Cloud Integration**: Optional cloud storage support
- **Advanced Filtering**: More sophisticated log filtering options
- **Analytics Integration**: Built-in analytics for captured data
- **Multi-format Export**: Additional video/log export formats

### Performance Improvements

- **Hardware Acceleration**: GPU-accelerated encoding
- **Adaptive Quality**: Dynamic quality adjustment based on device
- **Streaming Support**: Real-time streaming capabilities
- **Compression Options**: Advanced compression algorithms

### Developer Experience

- **Visual Studio Code Extension**: IDE integration
- **Gradle Plugin**: Build-time integration
- **Testing Framework**: Automated testing utilities
- **Documentation Generator**: Auto-generate documentation from recordings

## Integration Examples

The SDK is designed for easy integration into existing Android applications with minimal setup
required. See `USAGE_EXAMPLES.md` for comprehensive code examples covering all major use cases.

## Support & Maintenance

This SDK is actively maintained and designed for production use in QA testing environments. It
provides robust error handling, comprehensive documentation, and follows Android development best
practices for reliability and performance.