# QA Snap SDK - Behavior Changes (v1.2.0)

## Summary of Changes

QA Snap SDK telah diperbarui dengan **Unified Recording Approach** untuk memberikan pengalaman yang
lebih seamless dan sesuai dengan kebutuhan QA testing yang comprehensive.

## Key Behavior Changes

### ⚡ **Unified Recording (Default Behavior)**

#### Before (v1.1.0):

```kotlin
// Harus memanggil dua method terpisah
qaSnapRecorder.startRecording()        // Hanya video
qaSnapRecorder.startLogCapture()       // Hanya logs

// Atau menggunakan combined method
qaSnapRecorder.startRecordingWithLogs() // Video + logs
```

#### After (v1.2.0):

```kotlin
// Satu method untuk semua (default behavior)
qaSnapRecorder.startRecording()        // Video + logs otomatis

// Untuk kontrol individual (jika diperlukan)
qaSnapRecorder.startLogCaptureOnly()   // Hanya logs
```

### 🎮 **Single Control Interface**

#### UI/UX Changes:

- **Before**: Multiple buttons - "Stop Recording", "Stop Log Capture", "Stop Both"
- **After**: Single button - "🛑 Stop QA Recording" (controls both)

#### Notification Changes:

- **Before**: Potentially multiple notifications or complex notification text
- **After**: Single notification - "🔴 QA Recording Active - Recording screen & logs..."

#### Status Display:

- **Before**: Separate status for video and logs
- **After**: Unified status - "🔴📋 QA Recording Active (Video & Logs)"

### 🎯 **API Method Changes**

| Method (v1.1.0) | Method (v1.2.0) | Status | Behavior |
|------------------|------------------|---------|----------|
| `startRecording()` | `startRecording()` | **Changed** | Now includes log capture automatically |
| `stopRecording()` | `stopRecording()` | **Changed** | Now stops both video and logs |
| `startLogCapture()` | `startLogCaptureOnly()` | **Renamed** | Individual log capture control |
| `stopLogCapture()` | `stopLogCaptureOnly()` | **Renamed** | Individual log stop control |
| `startRecordingWithLogs()` | `startRecordingWithCustomLogs()` | **Deprecated** | Use for custom log settings |
| `stopRecordingWithLogs()` | `stopRecording()` | **Deprecated** | Default behavior now |

### 📋 **Migration Guide**

#### Simple Migration (Recommended):

```kotlin
// OLD CODE (v1.1.0)
qaSnapRecorder.startRecordingWithLogs()
qaSnapRecorder.stopRecordingWithLogs()

// NEW CODE (v1.2.0) - Simplified!
qaSnapRecorder.startRecording()  // Automatically includes logs
qaSnapRecorder.stopRecording()   // Automatically stops both
```

#### Advanced Migration (Custom Settings):

```kotlin
// OLD CODE (v1.1.0)
qaSnapRecorder.startRecordingWithLogs(
    logLevel = "D",
    tagFilter = "MyApp",
    packageFilter = packageName
)

// NEW CODE (v1.2.0)
qaSnapRecorder.startRecordingWithCustomLogs(
    logLevel = "D",
    tagFilter = "MyApp", 
    packageFilter = packageName
)
```

#### Individual Control Migration:

```kotlin
// OLD CODE (v1.1.0)
qaSnapRecorder.startLogCapture()
qaSnapRecorder.stopLogCapture()

// NEW CODE (v1.2.0)
qaSnapRecorder.startLogCaptureOnly()
qaSnapRecorder.stopLogCaptureOnly()
```

### 🔧 **Default Settings for Unified Recording**

When you call `startRecording()`, the following log capture settings are applied automatically:

```kotlin
// Default log settings for unified recording
logLevel = "I"              // Info level and above
tagFilter = null           // All tags
packageFilter = packageName // Current app only
bufferSize = 1MB           // Default buffer
```

### 📱 **Demo App Changes**

#### UI Updates:

- All stop buttons now perform unified stop operation
- Button labels updated to reflect unified behavior
- Status display shows both video and log capture status

#### Flow Changes:

1. **MainActivity**: `startRecording()` automatically starts video + logs
2. **HomeActivity**: All stop buttons perform the same unified stop operation
3. **Real-time Status**: Shows unified status for both operations

### 🎯 **Use Case Examples**

#### **Basic QA Testing** (Most Common):

```kotlin
// Simple and effective - one call does it all
qaSnapRecorder.startRecording()  // Video + logs with QA-friendly defaults

// When done testing
qaSnapRecorder.stopRecording()   // Stops both, saves files
```

#### **Performance Debugging** (Logs Only):

```kotlin
// When you only need logs, not video
qaSnapRecorder.startLogCaptureOnly(
    logLevel = "W",
    tagFilter = "Performance"
)
```

#### **Custom QA Testing** (Advanced):

```kotlin
// When you need custom log settings
qaSnapRecorder.startRecordingWithCustomLogs(
    logLevel = "V",              // Verbose logging
    tagFilter = "Network|UI",    // Specific tags
    packageFilter = null         // All packages
)
```

### 🔄 **Backward Compatibility**

#### Deprecated Methods:

- `startRecordingWithLogs()` - Use `startRecording()` or `startRecordingWithCustomLogs()`
- `stopRecordingWithLogs()` - Use `stopRecording()`

#### Warning Messages:

```kotlin
@Deprecated(
    message = "Use startRecording() for default behavior or startRecordingWithCustomLogs() for custom settings",
    replaceWith = ReplaceWith("startRecording()"),
    level = DeprecationLevel.WARNING
)
```

### 📊 **Benefits of Unified Approach**

1. **Simplified API**: Satu method call untuk operation yang paling umum
2. **Better UX**: Tidak perlu mengingat multiple method calls
3. **Comprehensive QA**: Video + logs secara default memberikan dokumentasi lengkap
4. **Reduced Errors**: Mengurangi kesalahan karena lupa start/stop salah satu operation
5. **Consistent Behavior**: Behavior yang konsisten untuk QA testing workflows

### 🚀 **Performance Impact**

#### Positive Impacts:

- **Reduced Method Calls**: Fewer API calls needed
- **Synchronized Operations**: Video and logs start/stop together
- **Optimized Defaults**: QA-friendly settings by default

#### Resource Usage:

- **Memory**: Slightly higher due to unified operations
- **Storage**: Both video and log files created by default
- **Battery**: Minimal additional impact (logs are lightweight)

### 📝 **Documentation Updates**

All documentation has been updated to reflect the new behavior:

- ✅ `README.md` - Updated with unified examples
- ✅ `USAGE_EXAMPLES.md` - 10+ updated examples
- ✅ `PROJECT_SUMMARY.md` - Architecture documentation
- ✅ Demo app - Updated UI and flow

### 🔮 **Future Considerations**

#### Planned Enhancements:

1. **Smart Defaults**: Adaptive log levels based on build type
2. **Conditional Logging**: Optional log capture based on device capabilities
3. **Performance Presets**: Predefined settings for different use cases
4. **Cloud Integration**: Optional cloud backup for unified recordings

#### Breaking Changes (Future):

- Deprecated methods will be removed in v2.0.0
- Consider making log capture truly optional in some scenarios

## Conclusion

The unified recording approach makes QA Snap SDK more intuitive and aligned with common QA testing
workflows. The single `startRecording()` call now provides comprehensive documentation (video +
logs) while still offering granular control when needed.

**Migration Recommendation**: Start using `startRecording()` for new code, and gradually migrate
existing code to take advantage of the simplified API.