# QA Snap SDK - Screen Recording & Log Capture

Android SDK untuk screen recording dan log capture yang mudah digunakan untuk keperluan QA testing
dan debugging.

## Fitur

- ✅ **Screen Recording**: Merekam layar dalam format MP4
- ✅ **ADB Log Capture**: Menangkap dan menyimpan ADB logs dalam format TXT
- ✅ **Simultaneous Operation**: Menjalankan recording dan log capture bersamaan
- ✅ **Foreground Service**: Berjalan stabil di background dengan notification
- ✅ **Emergency Stop**: Penghentian otomatis saat crash atau force close
- ✅ **Easy Integration**: API sederhana dan mudah digunakan
- ✅ **Customizable**: Filter logs berdasarkan level, tag, atau package
- ✅ **Auto File Management**: Penyimpanan otomatis dengan timestamp

## Installation

### 1. Tambahkan dependency dalam `build.gradle` (Module: app)

```kotlin
dependencies {
    implementation project(':qa-snap-sdk')
}
```

### 2. Tambahkan permissions dalam `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.READ_LOGS" />

<!-- Android 13+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## Simplified Integration Methods

Untuk memudahkan integrasi, QA Snap SDK menawarkan beberapa metode sederhana untuk memulai screen
recording dan log capture.

### Method 1: One-Line Integration (Easiest!)

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ONE LINE - AUTO START RECORDING!
        QASnap.start(this)
    }
}
```

### Method 2: Extend QASnapActivity (Zero Setup!)

```kotlin
class MainActivity : QASnapActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ZERO LINES OF CODE!
        // Recording starts automatically
    }
    
    // Optional: Handle completion  
    override fun onQARecordingComplete(videoFile: File?, logFile: File?) {
        Toast.makeText(this, "QA files saved!", Toast.LENGTH_SHORT).show()
    }
}
```

### Method 3: Manual Setup (Full Control)

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize SDK
        qaSnapRecorder = QASnapRecorder.initialize(this)
        
        // Set listener
        qaSnapRecorder.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingStarted() {
                // Screen recording dimulai
            }
            
            override fun onRecordingStopped(outputFile: File) {
                // Screen recording selesai, file tersimpan
            }
            
            override fun onRecordingError(error: String) {
                // Error saat recording
            }
            
            override fun onLogCaptureStarted() {
                // Log capture dimulai
            }
            
            override fun onLogCaptureStopped(outputFile: File) {
                // Log capture selesai, file tersimpan
            }
            
            override fun onLogCaptureError(error: String) {
                // Error saat log capture
            }
        })
    }
}
```

> **💡 Recommendation**: Use Method 1 or 2 for 90% of use cases. Only use Method 3 if you need
> advanced customization.

## Penggunaan

> **📝 Important Note**: QA Snap SDK menggunakan **unified recording approach**. Ketika Anda
> memanggil `startRecording()`, SDK secara otomatis akan memulai screen recording DAN log capture
> bersamaan. **Satu kontrol untuk kedua output** - tidak perlu mengontrol video dan logs secara
> terpisah.

### Default Behavior - Unified Recording

QA Snap SDK dirancang dengan **unified recording** sebagai default behavior. Setiap kali Anda
memulai screen recording, log capture akan otomatis dimulai bersamaan. **Satu tombol kontrol
mengatur kedua operasi sekaligus**.

#### Mulai Recording (Video + Logs)
```kotlin
// Ini akan memulai video recording DAN log capture secara bersamaan
qaSnapRecorder.startRecording()
```

#### Stop Recording (Video + Logs)
```kotlin
// Ini akan menghentikan video recording DAN log capture secara bersamaan
qaSnapRecorder.stopRecording()
```

#### Cek Status
```kotlin
val isRecording = qaSnapRecorder.isRecording()
val isCapturingLogs = qaSnapRecorder.isCapturingLogs()
```

### UI Experience - Single Control

- **Satu Notification** - Hanya satu notification bar untuk mengontrol kedua operasi
- **Satu Tombol Stop** - UI hanya menampilkan satu tombol stop yang mengatur video + logs
- **Status Terpadu** - Status display menunjukkan kondisi recording secara unified
- **Dialog Terpadu** - Confirmation dialog mencerminkan operasi yang sedang aktif

### Advanced Control - Individual Operations

Jika Anda membutuhkan kontrol individual untuk video atau log saja:

#### Log Capture Saja
```kotlin
// Mulai log capture saja (tanpa video)
qaSnapRecorder.startLogCaptureOnly(
    logLevel = "D",
    tagFilter = "MyApp",
    packageFilter = packageName
)

// Stop log capture saja
qaSnapRecorder.stopLogCaptureOnly()
```

#### Custom Recording dengan Log Settings
```kotlin
// Mulai recording dengan custom log settings
qaSnapRecorder.startRecordingWithCustomLogs(
    logLevel = "V",           // Log level custom
    tagFilter = "NetworkManager", // Tag filter custom
    packageFilter = packageName   // Package filter custom
)
```

#### Stop Keduanya Bersamaan

```kotlin
qaSnapRecorder.stopRecordingWithLogs()
```

### Emergency Stop

#### Emergency Stop Recording (bisa dipanggil dari mana saja)

```kotlin
QASnapRecorder.emergencyStopRecording(context)
```

#### Emergency Stop Semua (recording + log capture)

```kotlin
QASnapRecorder.getInstance()?.emergencyStopAll(context)
```

### File Management

#### Dapatkan Directory Output untuk Video

```kotlin
val videoDir = qaSnapRecorder.getOutputDirectory()
// Default: /Android/data/[package]/files/QASnapRecordings/
```

#### Dapatkan Directory Output untuk Logs

```kotlin
val logDir = qaSnapRecorder.getLogOutputDirectory()
// Default: /Android/data/[package]/files/QASnapLogs/
```

## Format Output Files

### Video Files

- **Format**: MP4 (H.264)
- **Naming**: `qa_snap_recording_yyyyMMdd_HHmmss.mp4`
- **Location**: `/Android/data/[package]/files/QASnapRecordings/`

### Log Files

- **Format**: TXT (Plain text)
- **Naming**: `qa_snap_logs_yyyyMMdd_HHmmss.txt`
- **Location**: `/Android/data/[package]/files/QASnapLogs/`
- **Content**: Logs dengan format timestamp + log content

### Contoh Isi Log File

```
=== QA Snap Log Capture Started ===
Timestamp: 2024-11-11 22:30:15
Log Level: V
Package Filter: com.myapp
=====================================

11-11 22:30:16.123 D/MyApp: Debug log message
11-11 22:30:16.456 I/MyApp: Info log message
11-11 22:30:16.789 W/MyApp: Warning log message
11-11 22:30:17.012 E/MyApp: Error log message

=== QA Snap Log Capture Ended ===
End Timestamp: 2024-11-11 22:35:20
```

## Log Level Reference

| Level | Keterangan                                     |
|-------|------------------------------------------------|
| `V`   | Verbose - Semua logs                           |
| `D`   | Debug - Debug logs dan level yang lebih tinggi |
| `I`   | Info - Info logs dan level yang lebih tinggi   |
| `W`   | Warning - Warning dan error logs               |
| `E`   | Error - Hanya error logs                       |
| `F`   | Fatal - Hanya fatal error logs                 |
| `S`   | Silent - Tidak ada logs                        |

## Best Practices

### 1. Permission Handling
```kotlin
private fun checkAndRequestPermissions() {
    val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_LOGS
    )
    
    // Request permissions jika belum granted
    ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
}
```

### 2. Lifecycle Management

```kotlin
override fun onDestroy() {
    super.onDestroy()
    // Stop recording saat activity destroyed untuk cleanup
    qaSnapRecorder.stopRecordingWithLogs()
}
```

### 3. Error Handling

```kotlin
override fun onRecordingError(error: String) {
    Log.e("QASnap", "Recording error: $error")
    // Handle error dan beri tahu user
    Toast.makeText(this, "Recording failed: $error", Toast.LENGTH_LONG).show()
}

override fun onLogCaptureError(error: String) {
    Log.e("QASnap", "Log capture error: $error")
    // Handle error dan beri tahu user
    Toast.makeText(this, "Log capture failed: $error", Toast.LENGTH_LONG).show()
}
```

### 4. Filter Logs untuk Debugging

```kotlin
// Hanya capture logs dari app tertentu
qaSnapRecorder.startLogCaptureOnly(
    logLevel = "D",
    packageFilter = "com.mycompany.myapp"
)

// Hanya capture logs dengan tag tertentu
qaSnapRecorder.startLogCaptureOnly(
    logLevel = "I", 
    tagFilter = "NetworkManager"
)

// Capture logs error dan fatal saja
qaSnapRecorder.startLogCaptureOnly(
    logLevel = "E"
)
```

## Example App

Lihat implementasi lengkap di folder `qa-snap-demo` yang mencakup:

- Setup permissions
- Screen recording
- Log capture
- Error handling
- UI untuk testing

### Menjalankan Demo

```bash
git clone [repository]
cd qa-snap-sample
./gradlew qa-snap-demo:installDebug
```

## Requirements

- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Kotlin**: 1.9.0+
- **Gradle**: 8.0+

## Permissions yang Diperlukan

### Runtime Permissions

- `RECORD_AUDIO` - Untuk audio dalam screen recording
- `WRITE_EXTERNAL_STORAGE` - Untuk menyimpan files (Android ≤ 10)
- `READ_MEDIA_VIDEO` - Untuk akses media (Android ≥ 13)
- `POST_NOTIFICATIONS` - Untuk foreground service notification (Android ≥ 13)

### Manifest Permissions

- `FOREGROUND_SERVICE` - Untuk menjalankan foreground service
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - Untuk media projection service
- `FOREGROUND_SERVICE_DATA_SYNC` - Untuk log capture service
- `READ_LOGS` - Untuk membaca system logs

## Troubleshooting

### 1. Recording Tidak Mulai

- Pastikan permissions sudah granted
- Cek apakah device mendukung MediaProjection
- Pastikan tidak ada recording lain yang aktif

### 2. Log Capture Kosong

- Pastikan permission `READ_LOGS` granted
- Cek apakah ada logs yang sesuai dengan filter
- Pastikan aplikasi generate logs saat capture aktif

### 3. Files Tidak Tersimpan

- Cek permission storage
- Pastikan storage tidak penuh
- Cek path directory yang benar

### 4. Service Terhenti

- Pastikan app tidak di-kill oleh system
- Cek battery optimization settings
- Emergency stop akan dipanggil otomatis saat crash

## Contributing

1. Fork repository
2. Create feature branch
3. Commit changes
4. Push ke branch
5. Create Pull Request

## License

```
MIT License
Copyright (c) 2024 QA Snap SDK
```

## Changelog

### v1.1.0 (Current)

- ✅ Added ADB log capture functionality
- ✅ Added simultaneous recording + log capture
- ✅ Added customizable log filters (level, tag, package)
- ✅ Added emergency stop for all operations
- ✅ Improved error handling and notifications
- ✅ Added comprehensive documentation

### v1.0.0

- ✅ Initial release with screen recording functionality
- ✅ Foreground service implementation
- ✅ Basic error handling and emergency stop