# QA Snap SDK - Screen Recording SDK for Android

QA Snap SDK adalah Android library yang menyediakan functionality screen recording untuk entire
screen dengan output berupa file video MP4. SDK ini dirancang khusus untuk keperluan Quality
Assurance (QA) testing dan dokumentasi user flow.

## Features

- ✅ **Entire Screen Recording** - Merekam seluruh layar perangkat
- ✅ **MP4 Output** - Video disimpan dalam format MP4 berkualitas tinggi
- ✅ **Easy Integration** - API yang mudah digunakan dengan lifecycle management
- ✅ **Foreground Service** - Recording berjalan stabil di background
- ✅ **Modern Android Support** - Mendukung Android API 21 sampai terbaru
- ✅ **Kotlin First** - Ditulis dalam Kotlin dengan Java interoperability

## Architecture

Project ini terdiri dari 2 module utama:

### 1. QA Snap SDK (`qa-snap-sdk`)

- **Package**: `io.codingskuy.qa_snap`
- **Type**: Android Library Module
- **Main Class**: `QASnapRecorder`
- **Service**: `ScreenRecordingService`

### 2. QA Snap Demo (`qa-snap-demo`)

- **Package**: `io.codingskuy.qa_snap_demo`
- **Type**: Android Application
- **Flow**: Splash Screen → Sign In → Home (with recording controls)

## Quick Start

### 1. Add Dependency

Tambahkan module dependency ke `build.gradle` app Anda:

```gradle
dependencies {
    implementation project(':qa-snap-sdk')
}
```

### 2. Add Permissions

Tambahkan permissions berikut ke `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

### 3. Initialize SDK

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var qaSnapRecorder: QASnapRecorder
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize QA Snap SDK
        qaSnapRecorder = QASnapRecorder.initialize(this)
        setupRecordingListener()
    }
    
    private fun setupRecordingListener() {
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                // Recording dimulai
                Log.d("QASnap", "Recording started")
            }
            
            override fun onRecordingStopped(outputFile: File) {
                // Recording selesai
                Log.d("QASnap", "Recording saved: ${outputFile.absolutePath}")
            }
            
            override fun onRecordingError(error: String) {
                // Error saat recording
                Log.e("QASnap", "Recording error: $error")
            }
        })
    }
}
```

### 4. Start/Stop Recording

```kotlin
// Mulai recording
qaSnapRecorder.startRecording()

// Berhenti recording  
qaSnapRecorder.stopRecording()

// Cek status recording
val isRecording = qaSnapRecorder.isRecording()

// Get output directory
val outputDir = qaSnapRecorder.getOutputDirectory()
```

## API Reference

### QASnapRecorder

Main class untuk mengontrol screen recording.

#### Methods

| Method | Description |
|--------|-------------|
| `initialize(activity: AppCompatActivity)` | Initialize SDK dengan activity context |
| `getInstance()` | Get current SDK instance |
| `setRecordingListener(listener: RecordingListener)` | Set callback listener untuk recording events |
| `startRecording()` | Mulai screen recording (akan meminta permission) |
| `stopRecording()` | Berhenti screen recording |
| `isRecording(): Boolean` | Cek apakah sedang recording |
| `getOutputDirectory(): File` | Get direktori tempat video disimpan |

#### RecordingListener Interface

```kotlin
interface RecordingListener {
    fun onRecordingStarted()
    fun onRecordingStopped(outputFile: File)
    fun onRecordingError(error: String)
}
```

## Demo Application Flow

Demo aplikasi menunjukkan implementasi SDK dalam user flow yang kompleks:

1. **MainActivity** (Splash Screen)
    - Menampilkan splash screen 2 detik
    - Initialize SDK dan mulai recording otomatis
    - Navigate ke SignInActivity

2. **SignInActivity**
    - Form login sederhana (email/password)
    - Button "Skip" untuk bypass login
    - Recording tetap berjalan di background

3. **HomeActivity**
    - Dashboard dengan berbagai button aktivitas
    - Display status recording real-time
    - Button "Stop Recording" untuk mengakhiri recording
    - Konfirmasi dialog saat stop recording

## File Output

- **Location**: `{ExternalFilesDir}/QASnapRecordings/`
- **Format**: `qa_snap_recording_yyyyMMdd_HHmmss.mp4`
- **Quality**: 1080p, 30fps, 6Mbps bitrate
- **Example**: `qa_snap_recording_20241211_143052.mp4`

## Technical Requirements

- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin 1.9.10
- **Gradle**: 8.5+
- **Android Gradle Plugin**: 8.3.1

## Android Version Compatibility

| Android Version | API Level | Status |
|----------------|-----------|---------|
| Android 5.0+   | 21-22     | ✅ Supported |
| Android 6.0+   | 23-25     | ✅ Supported |
| Android 7.0+   | 24-25     | ✅ Supported |
| Android 8.0+   | 26-27     | ✅ Supported |
| Android 9.0    | 28        | ✅ Supported |
| Android 10     | 29        | ✅ Supported |
| Android 11     | 30        | ✅ Supported |
| Android 12+    | 31-34     | ✅ Supported |

## Permissions Handling

SDK akan otomatis meminta permission yang diperlukan:

1. **Media Projection Permission** - Untuk screen capture
2. **Storage Permission** - Untuk menyimpan video file
3. **Audio Permission** - Untuk audio recording (optional)
4. **Foreground Service** - Untuk recording di background

## Best Practices

1. **Initialize Early** - Initialize SDK di onCreate() activity utama
2. **Single Instance** - Gunakan singleton pattern yang sudah disediakan
3. **Proper Cleanup** - Pastikan stop recording sebelum app keluar
4. **Storage Management** - Monitor ukuran file output directory
5. **User Experience** - Berikan feedback visual saat recording aktif

## Sample Usage in QA Testing

```kotlin
class QATestCase : AppCompatActivity() {
    private val qaSnap = QASnapRecorder.initialize(this)
    
    fun startTestingFlow() {
        // Mulai recording di awal test
        qaSnap.startRecording()
        
        // Lakukan test case steps...
        performUserActions()
        
        // Stop recording di akhir test
        qaSnap.stopRecording()
    }
    
    private fun performUserActions() {
        // Login flow
        clickLogin()
        enterCredentials()
        
        // Main app flow  
        navigateToFeature()
        performCriticalAction()
        verifyResults()
    }
}
```

## Troubleshooting

### Common Issues

1. **Permission Denied Error**
    - Pastikan semua permission sudah ditambahkan di manifest
    - Test pada device fisik, bukan emulator

2. **Recording File Empty**
    - Cek available storage space
    - Pastikan external storage permission granted

3. **Service Not Starting**
    - Cek target SDK compatibility
    - Pastikan foreground service permission ada

### Debug Mode

Enable logging untuk debugging:

```kotlin
// Enable di development build
if (BuildConfig.DEBUG) {
    Log.d("QASnap", "Debug mode enabled")
}
```

## License

MIT License - Lihat file LICENSE untuk detail lengkap.

## Contributing

1. Fork repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

## Support

Untuk pertanyaan dan support:

- Email: support@codingskuy.io
- GitHub Issues: [Create Issue](https://github.com/codingskuy/qa-snap-sdk/issues)

---

**QA Snap SDK v1.0** - Making QA testing easier with automated screen recording 🎬