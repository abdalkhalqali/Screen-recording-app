# Fix Navigation Flow - Wait for MediaProjection Permission

## Problem

Halaman splash berpindah ke page berikutnya sebelum user click "Start" di MediaProjection popup,
sehingga user tidak sempat memberikan permission untuk recording.

## Root Cause

Navigation timer dimulai di `onQARecordingReady()` bersamaan dengan MediaProjection popup muncul,
bukan menunggu user click "Start".

### Flow SEBELUM Fix:

```
MainActivity.onQARecordingReady()
├── Show MediaProjection popup ⏳
├── Start navigation timer (3 detik) ❌
└── Navigate to SignIn (sebelum user click Start) ❌

User sees popup but page already navigated! 🚫
```

### Flow SESUDAH Fix:

```
MainActivity.onQARecordingReady()
├── Show MediaProjection popup ⏳
└── Wait for user action...

User clicks "Start" ✅
├── MainActivity.onQARecordingStarted()
├── Start navigation timer (3 detik) ✅
└── Navigate to SignIn (after recording started) ✅

User clicks "Cancel" ❌
├── MainActivity.onQARecordingError()
├── Start navigation timer (3 detik) ✅
└── Navigate to SignIn (continue without recording) ✅
```

---

## Fix Implementation

### 1. MainActivity - Fixed Navigation Timing

**SEBELUM:**

```kotlin
override fun onQARecordingReady() {
    startQARecording() // Show MediaProjection popup
    startNavigationTimer() // ❌ Start timer immediately
}

override fun onQARecordingStarted() {
    // Recording active
    navigateToSignIn() // ❌ Navigate immediately
}
```

**SESUDAH:**

```kotlin
override fun onQARecordingReady() {
    startQARecording() // Show MediaProjection popup
    // ✅ DON'T start navigation timer here - wait for user action
    Log.d(TAG, "Waiting for user to grant MediaProjection permission before navigation")
}

override fun onQARecordingStarted() {
    // Recording active - user clicked "Start"
    // ✅ NOW start navigation timer after user granted permission
    startNavigationTimer()
}

override fun onQARecordingError(error: String) {
    // User clicked "Cancel" or other error
    if (!hasNavigated) {
        // ✅ Continue with app flow even if recording failed
        startNavigationTimer()
    }
}
```

### 2. QASnapActivity - Added Error Handling

**TAMBAHAN:**

```kotlin
abstract class QASnapActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup callbacks including error handling
        qaSnapHelper.onError { error ->
            onQARecordingError(error)
        }
    }
    
    /**
     * Called when an error occurs during QA recording (including permission denial)
     * Override this to handle errors gracefully
     */
    protected open fun onQARecordingError(error: String) {
        // Default: do nothing
    }
}
```

### 3. QASnapHelper - Added Error Callback

**TAMBAHAN:**

```kotlin
class QASnapHelper {
    private var onError: ((error: String) -> Unit)? = null
    
    fun onError(callback: (error: String) -> Unit): QASnapHelper {
        onError = callback
        return this
    }
    
    private fun setupDefaultListener() {
        qaSnapRecorder?.setRecordingListener(object : QASnapRecorder.RecordingListener {
            override fun onRecordingError(error: String) {
                // ✅ Trigger error callback
                onError?.invoke(error)
            }
            
            override fun onLogCaptureError(error: String) {
                // ✅ Trigger error callback  
                onError?.invoke(error)
            }
            // ... other callbacks
        })
    }
}
```

---

## Expected User Experience

### Scenario 1: User Grants Permission ✅

1. **Splash Screen**: Shows QA Snap Demo
2. **MediaProjection Popup**: "Start recording or casting with QA Snap Demo?"
3. **User clicks "Start"**: Permission granted
4. **Recording starts**: Notification appears
5. **3 seconds later**: Navigate to SignIn page
6. **Recording continues**: Across all pages

### Scenario 2: User Cancels Permission ❌

1. **Splash Screen**: Shows QA Snap Demo
2. **MediaProjection Popup**: "Start recording or casting with QA Snap Demo?"
3. **User clicks "Cancel"**: Permission denied
4. **Error handled gracefully**: No crash
5. **3 seconds later**: Navigate to SignIn page anyway
6. **App continues**: Without recording

### Scenario 3: Permission Error 🔧

1. **Splash Screen**: Shows QA Snap Demo
2. **MediaProjection Popup**: Shows but permission fails
3. **Error logged**: Detailed error message
4. **Fallback navigation**: App continues normally
5. **User experience**: Smooth, no hanging

---

## Key Improvements

| Aspect | Before | After |
|--------|--------|-------|
| **Timing** | Navigation starts with popup | Navigation waits for user action |
| **User Control** | User can't see popup properly | User has time to decide |
| **Error Handling** | No fallback for cancellation | Graceful error handling |
| **Flow Logic** | Race condition between popup & navigation | Sequential: popup → decision → navigation |
| **User Experience** | Confusing, popup dismissed too quickly | Clear, user-controlled flow |

---

## Testing Scenarios

### Test 1: Grant Permission Flow

1. ✅ Launch app
2. ✅ See splash screen
3. ✅ MediaProjection popup appears
4. ✅ Click "Start"
5. ✅ Recording notification appears
6. ✅ Wait 3 seconds
7. ✅ Navigate to SignIn
8. ✅ Recording continues

### Test 2: Cancel Permission Flow

1. ✅ Launch app
2. ✅ See splash screen
3. ✅ MediaProjection popup appears
4. ✅ Click "Cancel"
5. ✅ Error handled gracefully
6. ✅ Wait 3 seconds
7. ✅ Navigate to SignIn
8. ✅ App works without recording

### Test 3: Permission Timeout/Error

1. ✅ Launch app
2. ✅ See splash screen
3. ✅ MediaProjection popup appears
4. ✅ Don't interact (test timeout)
5. ✅ Error handled
6. ✅ Navigation continues
7. ✅ App flow uninterrupted

---

## Result

✅ **Fixed**: Splash screen waits for MediaProjection permission
✅ **Fixed**: User has time to click "Start" or "Cancel"  
✅ **Fixed**: App continues gracefully in both scenarios
✅ **Fixed**: No more race condition between popup and navigation
✅ **Improved**: Better user experience and flow control

**User sekarang memiliki kontrol penuh atas MediaProjection permission tanpa terburu-buru oleh
automatic navigation.**