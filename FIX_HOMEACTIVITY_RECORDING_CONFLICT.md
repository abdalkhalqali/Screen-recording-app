# Fix HomeActivity Recording Conflict

## Problem

Ketika masuk ke HomeActivity, snap video tiba-tiba langsung stop recording sedangkan snap log masih
berjalan. Ada inkonsistensi dimana video recording berhenti tapi log capture tetap aktif.

## Root Cause Analysis

### Masalah Utama: Listener Conflict

HomeActivity extends `QASnapActivity` yang membuat `QASnapHelper` baru. Ketika `QASnapHelper`
dipanggil method `isRecording()` atau `connectToExistingSession()`, method `setupDefaultListener()`
dipanggil yang **override listener yang sudah ada** di `QASnapRecorder`.

### Flow Masalah SEBELUM Fix:

```
MainActivity: 
├── Creates QASnapHelper A
├── Sets RecordingListener A
├── Starts recording (video + logs)
└── Navigate to SignIn → HomeActivity

HomeActivity:
├── Extends QASnapActivity  ❌
├── Creates QASnapHelper B  ❌
├── Calls connectToExistingSession()
├── Calls setupDefaultListener()  ❌
├── OVERRIDES RecordingListener A with RecordingListener B  ❌
└── Video stops, logs continue (inconsistent state)  ❌
```

### Mengapa Video Stop tapi Log Continue?

1. **Video Recording Service** bergantung pada callback dari `RecordingListener`
2. **Log Capture Service** berjalan independent sebagai foreground service
3. Ketika listener di-override, video service kehilangan callback connection
4. Log service tetap berjalan karena tidak bergantung pada listener

---

## Fix Implementation

### Approach: Direct QASnapRecorder Access

Mengganti HomeActivity dari extend `QASnapActivity` menjadi extend `AppCompatActivity` dan mengakses
`QASnapRecorder` secara langsung tanpa membuat `QASnapHelper` baru.

### SEBELUM Fix:

```kotlin
class HomeActivity : QASnapActivity() {  // ❌ Creates new QASnapHelper
    
    private fun setupExistingRecordingSession() {
        if (isRecordingActive) {
            qaSnapHelper.connectToExistingSession()  // ❌ Overrides listener
        }
    }
    
    private fun updateStatus() {
        val isRecording = qaSnapHelper.isRecording()  // ❌ Triggers listener override
    }
}
```

### SESUDAH Fix:

```kotlin
class HomeActivity : AppCompatActivity() {  // ✅ No automatic QASnapHelper creation
    
    private var existingRecorder: QASnapRecorder? = null
    
    private fun setupExistingRecordingSession() {
        // ✅ Get existing recorder instance directly to avoid listener conflicts
        existingRecorder = QASnapRecorder.getInstance()
        val isRecordingActive = existingRecorder?.isRecording() ?: false
        val isLogsActive = existingRecorder?.isCapturingLogs() ?: false
        
        if (isRecordingActive || isLogsActive) {
            Log.d(TAG, "Will manage existing session without interfering with listeners")
        }
    }
    
    private fun updateStatus() {
        val isRecording = existingRecorder?.isRecording() ?: false  // ✅ Direct access
    }
    
    private fun showStopRecordingDialog() {
        existingRecorder?.stopRecording()  // ✅ Direct control
    }
}
```

---

## Key Changes

### 1. HomeActivity Class Declaration

```kotlin
// SEBELUM
class HomeActivity : QASnapActivity() {
    // Automatically creates QASnapHelper and overrides listeners
}

// SESUDAH  
class HomeActivity : AppCompatActivity() {
    // No automatic helper creation, no listener conflicts
}
```

### 2. Recording Session Access

```kotlin
// SEBELUM
private var qaSnapHelper: QASnapHelper? = null
qaSnapHelper = QASnapHelper(this)
qaSnapHelper.connectToExistingSession()  // ❌ Overrides listener

// SESUDAH
private var existingRecorder: QASnapRecorder? = null
existingRecorder = QASnapRecorder.getInstance()  // ✅ Direct access
```

### 3. Status Checking & Control

```kotlin
// SEBELUM  
qaSnapHelper?.isRecording()  // ❌ Triggers setupDefaultListener()
qaSnapHelper?.stopRecording()

// SESUDAH
existingRecorder?.isRecording()  // ✅ Direct status check
existingRecorder?.stopRecording()  // ✅ Direct control
```

---

## Flow SESUDAH Fix:

```
MainActivity:
├── Creates QASnapHelper A
├── Sets RecordingListener A  
├── Starts recording (video + logs)
└── Navigate to SignIn → HomeActivity

HomeActivity:
├── Extends AppCompatActivity  ✅
├── Gets existing QASnapRecorder instance directly  ✅
├── NO new QASnapHelper creation  ✅
├── NO listener override  ✅
├── RecordingListener A remains intact  ✅
└── Video continues, logs continue (consistent state)  ✅
```

---

## Benefits of This Fix

### ✅ **No Listener Conflicts**

- HomeActivity tidak membuat QASnapHelper baru
- Listener yang sudah ada tetap utuh
- Video dan log capture tetap sinkron

### ✅ **Direct Control**

- Akses langsung ke QASnapRecorder instance
- Kontrol recording tanpa interference
- Status checking yang akurat

### ✅ **Consistent State**

- Video recording tetap aktif
- Log capture tetap aktif
- Notification tetap muncul
- Stop button berfungsi dengan benar

### ✅ **Simplified Architecture**

- Menghindari kompleksitas multiple QASnapHelper
- Clear separation of concerns
- Easier debugging dan maintenance

---

## Testing Scenarios

### Test 1: Recording Continuation ✅

1. Start recording di MainActivity
2. Navigate to SignIn → HomeActivity
3. **Expected**: Video recording continues, logs continue
4. **Actual**: ✅ Both continue without interruption

### Test 2: HomeActivity Controls ✅

1. Recording active from previous activity
2. Open HomeActivity
3. **Expected**: Stop button enabled and functional
4. **Actual**: ✅ Button works, can stop recording properly

### Test 3: Status Display ✅

1. Recording active from previous activity
2. Check HomeActivity status
3. **Expected**: "🔴📋 QA Recording Active (Video & Logs)"
4. **Actual**: ✅ Status displayed correctly

---

## Result

✅ **Fixed**: Video recording tidak lagi stop di HomeActivity
✅ **Fixed**: Log capture dan video recording tetap sinkron
✅ **Fixed**: HomeActivity dapat control recording dengan benar
✅ **Fixed**: No more listener conflicts antar activities
✅ **Improved**: Cleaner architecture dan better performance

**Video recording dan log capture sekarang tetap konsisten dan berjalan dengan baik sampai user
eksplisit stop recording di HomeActivity.**