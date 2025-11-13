# QA Snap Permission Flow - Updated Implementation

## Overview

Request permission dan pop record snap telah dipindahkan dari splash screen ke onboarding page.
Sekarang **splash screen tetap bersih** tanpa logika staging mode, dan semua proses QA Snap hanya
terjadi saat tester klik "Lanjutkan" di onboarding page.

## Flow Changes

### ❌ **SEBELUM** (Old Flow):

```
LauncherActivity → QATesterOnboardingActivity → MainActivity (dengan BaseActivity + QASnapHelper)
                                                      ↓
                                              Request permissions + QA Snap init
                                              (di splash screen)
```

### ✅ **SESUDAH** (New Flow):

```
LauncherActivity → QATesterOnboardingActivity → MainActivity (clean splash)
                           ↓ (saat klik "Lanjutkan")
                   Request permissions + QA Snap init
                   (di onboarding page)
```

## Key Changes

### 1. **Clean MainActivity**

- **Tidak lagi extends BaseActivity** - sekarang extends AppCompatActivity
- **Tidak ada logika QA Snap** - hanya splash screen sederhana
- **Sama untuk semua environment** - tidak ada perbedaan behavior
- **Timer-based navigation** - 3 detik splash lalu ke SignInActivity

### 2. **Enhanced QATesterOnboardingActivity**

- **One-liner QA Snap integration** menggunakan `QASnap.start(this)`
- **Permission handling** - request microphone, storage, notifications
- **Progress indicators** - visual feedback untuk setiap step
- **Error handling** - dialog untuk retry atau skip jika permission ditolak

### 3. **Permission Flow di Onboarding**

Saat tester klik **"Lanjutkan"**:

1. **Save test case info** → Progress: "Menyimpan informasi test case..."
2. **Check permissions** → Progress: "Memeriksa izin yang diperlukan..."
3. **Request permissions** (jika belum ada) → Progress: "Meminta izin sistem..."
4. **Initialize QA Snap** → Progress: "Menginisialisasi QA Snap..."
5. **Setup complete** → Progress: "QA Snap siap digunakan!"
6. **Navigate to MainActivity** → Clean splash screen

## Technical Details

### **QATesterOnboardingActivity.kt**

```kotlin
// Saat klik "Lanjutkan"
btnContinue.setOnClickListener {
    saveTestCaseInfo()
    showProgress("Menyimpan informasi test case...")
    startQASnapSetup()
}

// Permission flow
private fun startQASnapSetup() {
    if (!hasBasicPermissions()) {
        showProgress("Meminta izin sistem...")
        requestBasicPermissions()
    } else {
        showProgress("Menginisialisasi QA Snap...")
        initializeQASnap()
    }
}

// QA Snap one-liner integration
private fun initializeQASnap() {
    QASnap.start(this)
        .onComplete { videoFile, logFile ->
            handleQASnapComplete(videoFile, logFile)
        }
}
```

### **MainActivity.kt** (Cleaned)

```kotlin
class MainActivity : AppCompatActivity() {  // Clean AppCompatActivity
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        showEnvironmentInfo()  // Simple environment display
        startNavigationTimer() // 3-second timer to SignInActivity
    }
    
    // No QA Snap logic - completely clean!
}
```

## User Experience Flow

### **Di Staging Environment:**

1. **LauncherActivity** → Detect staging + onboarding not completed
2. **QATesterOnboardingActivity** appears:
    - Welcome message
    - Instructions
    - Form: Test case title, ID, reference
    - Important note about "Entire Screen"
3. **User fills form** and clicks "Lanjutkan"
4. **Permission request sequence**:
    - Progress: "Menyimpan informasi test case..."
    - Progress: "Memeriksa izin yang diperlukan..."
    - System permission dialogs (microphone, storage, etc.)
    - Progress: "Menginisialisasi QA Snap..."
    - Progress: "QA Snap siap digunakan!"
    - Success toast: "✅ QA Snap berhasil diatur!"
5. **Navigate to MainActivity** → Clean 3-second splash
6. **Continue to SignInActivity** → QA Snap ready for use

### **Di Development/Production:**

1. **LauncherActivity** → Detect non-staging environment
2. **MainActivity** directly → Clean 3-second splash
3. **Continue to SignInActivity** → No QA Snap

## Benefits

### ✅ **Clean Separation of Concerns**

- **Splash screen** tetap sederhana untuk semua environment
- **QA Snap logic** hanya di onboarding page (staging only)
- **No mixing** antara app logic dan testing logic

### ✅ **Better User Experience**

- **Clear progress indicators** untuk setiap step
- **Proper error handling** dengan retry options
- **Visual feedback** saat permission request
- **Success confirmation** sebelum navigate

### ✅ **Simplified Debugging**

- **MainActivity bersih** - mudah debug app flow
- **QA Snap isolated** - mudah debug testing flow
- **Clear logging** untuk setiap step

### ✅ **Flexible Permission Handling**

- **Retry mechanism** jika permission ditolak
- **Skip option** untuk continue tanpa QA Snap
- **Proper error messages** dalam bahasa Indonesia

## Permission Requirements

### **Basic Permissions** (requested di onboarding):

- `RECORD_AUDIO` - untuk audio recording
- `POST_NOTIFICATIONS` - untuk Android 13+ notifications
- `READ_MEDIA_VIDEO` - untuk Android 13+ video access
- `READ_EXTERNAL_STORAGE` - untuk Android < 13
- `WRITE_EXTERNAL_STORAGE` - untuk Android ≤ 9

### **MediaProjection Permission** (requested saat recording):

- Akan diminta otomatis saat user start recording
- Tidak perlu handle manual di onboarding

## Testing Instructions

### **Test di Staging:**

1. Build staging variant
2. First launch → onboarding appears
3. Fill form → click "Lanjutkan"
4. Grant permissions → watch progress indicators
5. Success → navigate to clean splash screen
6. QA Snap ready untuk recording

### **Test di Development/Production:**

1. Build dev/prod variant
2. Launch → langsung ke clean splash screen
3. No onboarding, no QA Snap

### **Reset untuk Testing:**

```kotlin
// Reset onboarding status
EnvironmentManager.resetQAOnboarding(context)
```

## Summary

✅ **Permission dan QA Snap init berhasil dipindahkan** dari splash screen ke onboarding page  
✅ **Splash screen sekarang bersih** tanpa logika staging mode  
✅ **UX improved** dengan progress indicators dan error handling  
✅ **Clean separation** antara app flow dan testing flow  
✅ **One-liner QA Snap integration** untuk simplicity