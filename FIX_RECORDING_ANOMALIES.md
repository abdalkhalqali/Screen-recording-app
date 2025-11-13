# Fix untuk 3 Anomali QA Snap Recording

## Anomali yang Diperbaiki

### 1. ✅ Snap log berjalan duluan meninggalkan snap video

### 2. ✅ Snap video dan log notifikasi muncul namun langsung stop saat pindah ke signin page

### 3. ✅ Tombol stop snap record di halaman home disable

---

## **Anomali 1: Log Capture Starts Before Video Recording**

### Root Cause

Log capture dimulai SEBELUM user memberikan MediaProjection permission, sehingga log sudah berjalan
sementara video belum dimulai.

### Lokasi Masalah

```kotlin
// SEBELUM (di QASnapRecorder.startRecording())
fun startRecording() {
    // ❌ Log capture dimulai sebelum MediaProjection permission
    startLogCaptureInternal(...)  
    
    // Baru request MediaProjection permission
    mediaProjectionLauncher?.launch(mediaProjectionIntent)
}
```

### Fix yang Diterapkan

```kotlin
// SESUDAH 
fun startRecording() {
    // ✅ DON'T start log capture here - wait for MediaProjection permission first
    Log.d(TAG, "Creating media projection intent - log capture will start after permission granted")
    
    // Request MediaProjection permission first
    mediaProjectionLauncher?.launch(mediaProjectionIntent)
}

private fun startRecordingService(data: Intent) {
    // Start video recording service
    ContextCompat.startForegroundService(activity, serviceIntent)
    
    // ✅ Start log capture here after MediaProjection permission granted
    startLogCaptureInternal(...)
}
```

### Hasil

- ✅ Log capture dan video recording dimulai bersamaan
- ✅ Sinkronisasi yang tepat antara video dan log
- ✅ User experience yang konsisten

---

## **Anomali 2: Recording Stops When Navigating to SignIn Page**

### Root Cause

MainActivity memanggil `super.onDestroy()` yang trigger cleanup QASnapHelper dan reset
QASnapRecorder singleton, sehingga recording berhenti.

### Lokasi Masalah

```kotlin
// SEBELUM (MainActivity extends QASnapActivity)
override fun onDestroy() {
    super.onDestroy() // ❌ Ini trigger cleanup dan stop recording
}

// QASnapActivity.onDestroy()
override fun onDestroy() {
    qaSnapHelper.cleanup()           // ❌ Stop recording
    QASnapRecorder.resetInstance()   // ❌ Reset singleton
}
```

### Fix yang Diterapkan

#### 1. QASnapActivity - Controllable Cleanup

```kotlin
abstract class QASnapActivity : AppCompatActivity() {
    protected var shouldCleanupOnDestroy = true  // ✅ Flag untuk kontrol cleanup
    
    override fun onDestroy() {
        if (shouldCleanupOnDestroy) {
            // Normal cleanup
            qaSnapHelper.cleanup()
            QASnapRecorder.resetInstance()
        } else {
            // ✅ Skip cleanup - let recording continue
            Log.d("QASnapActivity", "Skipping QA recording cleanup - recording continues")
        }
        super.onDestroy()
    }
}
```

#### 2. MainActivity - Prevent Cleanup

```kotlin
class MainActivity : QASnapActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ Prevent cleanup on destroy so recording continues to next activity
        shouldCleanupOnDestroy = false
        super.onCreate(savedInstanceState)
    }
    // onDestroy() handled by parent with flag
}
```

#### 3. SignInActivity - Recording Continuation

```kotlin
class SignInActivity : AppCompatActivity() {
    private fun checkRecordingStatus() {
        val recorder = QASnapRecorder.getInstance()
        val isRecording = recorder?.isRecording() ?: false
        
        if (isRecording) {
            // ✅ Show user that recording continues
            Toast.makeText(this, "📹 QA Recording continues in background", Toast.LENGTH_SHORT).show()
        }
    }
}
```

#### 4. HomeActivity - Final Cleanup

```kotlin
class HomeActivity : QASnapActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ Allow cleanup on destroy since this is typically the final activity
        shouldCleanupOnDestroy = true
        super.onCreate(savedInstanceState)
    }
}
```

### Hasil

- ✅ Recording continues from MainActivity → SignInActivity → HomeActivity
- ✅ Services tetap berjalan independent dari activity lifecycle
- ✅ User dapat melihat notifikasi recording yang persist
- ✅ Cleanup hanya terjadi di activity terakhir (HomeActivity)

---

## **Anomali 3: Stop Button Disabled in HomeActivity**

### Root Cause

HomeActivity membuat QASnapHelper baru dengan `autoStart = false` dan tidak mendeteksi recording
yang sudah berjalan dari activity sebelumnya.

### Lokasi Masalah

```kotlin
// SEBELUM (HomeActivity)
private fun setupQASnapHelper() {
    qaSnapHelper = QASnapHelper(this).apply {
        initialize(autoStart = false)  // ❌ Tidak deteksi existing recording
    }
}

private fun updateStatus() {
    val isRecording = qaSnapHelper?.isRecording() ?: false  // ❌ Always false
    binding.btnStopRecording.isEnabled = isRecording        // ❌ Always disabled
}
```

### Fix yang Diterapkan

#### 1. QASnapHelper - Connect to Existing Session

```kotlin
class QASnapHelper {
    fun connectToExistingSession(): QASnapHelper {
        // ✅ Get existing instance
        qaSnapRecorder = QASnapRecorder.getInstance()
        
        if (qaSnapRecorder != null) {
            Log.d(TAG, "Connected to existing QASnapRecorder instance")
            setupDefaultListener()  // ✅ Setup callbacks for existing session
        }
        return this
    }
    
    fun isRecording(): Boolean {
        // ✅ Try to get existing instance if we don't have reference
        if (qaSnapRecorder == null) {
            qaSnapRecorder = QASnapRecorder.getInstance()
        }
        return qaSnapRecorder?.isRecording() ?: false
    }
}
```

#### 2. HomeActivity - Extend QASnapActivity

```kotlin
class HomeActivity : QASnapActivity() {  // ✅ Extend QASnapActivity instead of AppCompatActivity
    
    override fun shouldAutoStartRecording(): Boolean {
        return false  // ✅ Don't auto-start since we're connecting to existing session
    }
    
    private fun setupExistingRecordingSession() {
        val existingRecorder = QASnapRecorder.getInstance()
        val isRecordingActive = existingRecorder?.isRecording() ?: false
        
        if (isRecordingActive) {
            // ✅ Connect to existing session after QASnapActivity initializes
            qaSnapHelper.connectToExistingSession()
        }
    }
    
    private fun updateStatus() {
        val isRecording = qaSnapHelper.isRecording()  // ✅ Now detects existing recording
        binding.btnStopRecording.isEnabled = isRecording  // ✅ Properly enabled
    }
}
```

### Hasil

- ✅ HomeActivity mendeteksi existing recording session
- ✅ Stop button enabled ketika recording aktif
- ✅ Dapat stop recording dari HomeActivity
- ✅ Status recording ditampilkan dengan benar

---

## **Testing Scenarios**

### Scenario 1: Normal Flow

1. **MainActivity**: Start recording → Navigate to SignIn
2. **SignInActivity**: Recording continues in background → Navigate to Home
3. **HomeActivity**: Stop button enabled → Can stop recording

### Scenario 2: Recording Persistence

1. Recording services continue running across activity transitions
2. Notification remains visible throughout navigation
3. Both video and log capture remain synchronized

### Scenario 3: Proper Cleanup

1. Recording can be stopped from HomeActivity
2. All resources cleaned up properly when stopped
3. Files saved correctly to device storage

---

## **Key Changes Summary**

| Component | Change | Purpose |
|-----------|--------|---------|
| **QASnapRecorder** | Defer log capture until after MediaProjection permission | Fix timing synchronization |
| **QASnapActivity** | Add `shouldCleanupOnDestroy` flag | Control cleanup behavior |
| **MainActivity** | Set `shouldCleanupOnDestroy = false` | Prevent recording stop on navigation |
| **SignInActivity** | Check and display recording status | User feedback for continuation |  
| **HomeActivity** | Extend QASnapActivity + connect to existing session | Enable proper recording control |
| **QASnapHelper** | Add `connectToExistingSession()` method | Connect to running recording |

---

## **Result**

✅ **Anomali 1 Fixed**: Log dan video dimulai bersamaan
✅ **Anomali 2 Fixed**: Recording persist across activity navigation  
✅ **Anomali 3 Fixed**: Stop button enabled dan berfungsi di HomeActivity

Dengan fix ini, QA Snap SDK sekarang bekerja dengan benar untuk:

- Synchronized video dan log capture
- Persistent recording across activities
- Proper recording control di semua halaman