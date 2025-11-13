# Fix untuk Masalah QA Snap di Pembukaan Kedua Aplikasi

## Masalah

Fitur rekaman berjalan dengan baik pada flow saat pertama kali install aplikasi, namun ketika ke-2
kalinya user membuka aplikasi dari awal, proses QASnapActivity hanya 1 yang tertrigger yaitu
`shouldAutoStartRecording` saja. Callback `onQARecordingReady`, `onQARecordingStarted`, dan
`onQARecordingComplete` tidak terlihat di log console sehingga perekaman tidak bekerja seperti
pertama kali install.

## Root Cause Analysis

1. **Singleton Instance Tidak Direset**: `QASnapRecorder` menggunakan singleton pattern dengan
   `instance` yang tidak pernah di-reset ke `null` ketika activity di-destroy atau aplikasi ditutup.

2. **Activity Result Launcher Tidak Diregistrasi Ulang**: `mediaProjectionLauncher` di
   `QASnapRecorder` diregistrasi pada activity yang lama, sehingga saat activity baru dibuat,
   launcher tidak tersedia.

3. **Listener Callback Tidak Dipanggil**: Karena instance singleton masih menunjuk ke activity lama,
   callback tidak dipanggil pada activity baru.

## Perbaikan yang Dilakukan

### 1. QASnapActivity - Menambah Cleanup di onDestroy

```kotlin
override fun onDestroy() {
    super.onDestroy()
    // Cleanup QASnapHelper and reset singleton instance
    if (::qaSnapHelper.isInitialized) {
        qaSnapHelper.cleanup()
    }
    // Reset QASnapRecorder singleton instance to allow fresh initialization
    QASnapRecorder.resetInstance()
}
```

### 2. QASnapHelper - Menambah Method Cleanup

```kotlin
fun cleanup() {
    Log.d(TAG, "Cleaning up QASnapHelper...")
    
    // Reset callbacks
    onRecordingReady = null
    onRecordingStarted = null
    onComplete = null
    
    // Stop any ongoing recording
    if (qaSnapRecorder?.isRecording() == true) {
        Log.d(TAG, "Stopping ongoing recording during cleanup")
        qaSnapRecorder?.stopRecording()
    }
    
    // Clear recorder reference
    qaSnapRecorder = null
    
    Log.d(TAG, "QASnapHelper cleanup completed")
}
```

### 3. QASnapRecorder - Menambah Reset Instance dan Activity Recreation Handling

```kotlin
companion object {
    fun resetInstance() {
        Log.d(TAG, "Resetting QASnapRecorder singleton instance")
        instance?.cleanup()
        instance = null
        Log.d(TAG, "QASnapRecorder instance reset completed")
    }
    
    fun initialize(activity: AppCompatActivity): QASnapRecorder {
        Log.d(TAG, "Initializing QASnapRecorder...")
        
        // If instance exists but has different activity, reset it
        if (instance != null && instance?.activity != activity) {
            Log.d(TAG, "Activity changed, resetting instance")
            instance?.cleanup()
            instance = null
        }
        
        // Create new instance if needed
        if (instance == null) {
            instance = QASnapRecorder(activity)
            setupCrashHandler(activity)
            Log.d(TAG, "QASnapRecorder initialized with new activity and instance set")
        } else {
            Log.d(TAG, "QASnapRecorder instance already exists for same activity")
        }
        
        return instance!!
    }
}
```

### 4. ActivityResultLauncher Handling yang Lebih Robust

```kotlin
private var mediaProjectionLauncher: ActivityResultLauncher<Intent>? = null

init {
    // Register ActivityResultLauncher in constructor to ensure it's always available
    registerMediaProjectionLauncher()
}

private fun registerMediaProjectionLauncher() {
    try {
        mediaProjectionLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startRecordingService(result.data!!)
            } else {
                recordingListener?.onRecordingError("Media projection permission denied")
            }
        }
        Log.d(TAG, "MediaProjection launcher registered successfully")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to register ActivityResultLauncher", e)
        // Fallback: we'll handle this in startRecording with a different approach
    }
}
```

### 5. Fallback Mechanism untuk MediaProjection Launcher

```kotlin
if (mediaProjectionLauncher != null) {
    mediaProjectionLauncher?.launch(mediaProjectionIntent)
} else {
    Log.w(TAG, "MediaProjection launcher is null, attempting to re-register")
    registerMediaProjectionLauncher()
    if (mediaProjectionLauncher != null) {
        mediaProjectionLauncher?.launch(mediaProjectionIntent)
    } else {
        Log.e(TAG, "Failed to register MediaProjection launcher, cannot start recording")
        recordingListener?.onRecordingError("Failed to initialize MediaProjection launcher. Try restarting the app.")
    }
}
```

## Testing Steps

### Langkah 1: Install Fresh App

1. Uninstall aplikasi sepenuhnya
2. Install ulang dari Android Studio
3. Buka aplikasi - recording harus bekerja normal
4. Lihat log untuk memastikan semua callback dipanggil

### Langkah 2: Test Second Launch

1. Close aplikasi (bukan kill, tapi exit normal)
2. Buka aplikasi lagi dari launcher
3. Recording harus bekerja seperti pertama kali
4. Lihat log untuk memastikan:
    - `shouldAutoStartRecording()` dipanggil
    - `onQARecordingReady()` dipanggil
    - `onQARecordingStarted()` dipanggil
    - `onQARecordingComplete()` dipanggil ketika selesai

### Langkah 3: Test Kill and Restart

1. Force kill aplikasi dari recent apps
2. Buka aplikasi lagi
3. Recording harus bekerja normal
4. Verify semua callback dipanggil

## Expected Logs

Saat pembukaan kedua, log yang diharapkan:

```
MainActivity onCreate - initializing QA recording
Is this a fresh app launch? true/false
Current process ID: [PID]
shouldAutoStartRecording() -> true
Initializing QASnapHelper with autoStart: true
Activity: MainActivity
QASnapRecorder initialized with new activity and instance set
Setting up default listener
Available callbacks - onRecordingReady: true, onRecordingStarted: true, onComplete: true
onQARecordingReady() called
Current thread: main
Activity finishing: false
Calling startQARecording() to trigger MediaProjection permission
```

## Kesimpulan

Perbaikan ini memastikan bahwa:

1. Singleton instance direset dengan benar saat activity destroyed
2. ActivityResultLauncher diregistrasi ulang untuk setiap activity baru
3. Callback listener di-setup dengan benar untuk activity baru
4. Ada fallback mechanism jika ada masalah dengan launcher registration

Dengan perbaikan ini, QA Snap akan bekerja konsisten baik di pembukaan pertama maupun pembukaan
berikutnya.