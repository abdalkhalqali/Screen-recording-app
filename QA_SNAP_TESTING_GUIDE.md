# 🧪 QA Snap Testing Guide - Multi-Environment

## ✅ Fixed Issues

Kami telah memperbaiki integrasi QA Snap SDK untuk memastikan:

- ✅ **MediaProjection Permission Dialog** muncul
- ✅ **Actual Screen Recording** berfungsi
- ✅ **Log Capture** berfungsi bersamaan
- ✅ **Environment-specific behavior** bekerja dengan benar

## 🔧 What Was Fixed

### 1. QASnapHelper Integration

**Before**: Hanya simulasi recording

```kotlin
// ❌ Old code - simulation only
Log.d(TAG, "QA recording started successfully (simulated)")
callback?.onQARecordingStarted()
```

**After**: Actual QA Snap SDK integration

```kotlin
// ✅ New code - real integration
recorder.startRecording()  // Calls actual QA Snap SDK
```

### 2. RecordingListener Implementation

**Before**: No proper callback handling
**After**: Full QASnapRecorder.RecordingListener implementation

```kotlin
class QASnapHelper : QASnapRecorder.RecordingListener {
    override fun onRecordingStarted() { /* Real callback */ }
    override fun onRecordingStopped(outputFile: File) { /* Real callback */ }
    // ... all callbacks implemented
}
```

### 3. Permissions Added

**Added to AndroidManifest.xml**:

```xml
<!-- Log capture permission -->
<uses-permission android:name="android.permission.READ_LOGS" />

<!-- Data sync service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Log capture service -->
<service android:name="io.codingskuy.qa_snap.service.LogCaptureService"
         android:foregroundServiceType="dataSync" />
```

## 🧪 Testing Steps

### Step 1: Environment Verification

```bash
# Build staging flavor (QA enabled)
./build_environments.sh staging debug

# Build development flavor (QA disabled)  
./build_environments.sh dev debug
```

### Step 2: Install & Test Staging APK

1. Install staging APK: `qa-snap-demo-staging-debug.apk`
2. Launch app - should show "📹 Staging Environment - QA Recording Available"
3. Navigate through MainActivity → SignInActivity → HomeActivity

### Step 3: QA Recording Flow Testing

#### Expected Behavior in Staging:

1. **MainActivity Launch**:
    - Toast: "📹 Staging Environment - QA Recording Available"
    - Log shows: "QA Snap enabled: true"
    - After 500ms delay: MediaProjection permission dialog should appear

2. **MediaProjection Permission Dialog**:
    - System dialog appears asking "Start recording?"
    - User clicks "Start now"
    - Recording begins immediately

3. **Recording Active State**:
    - Toast: "✅ QA Recording started!"
    - Status bar notification appears for recording
    - App continues to SignInActivity and HomeActivity
    - Recording continues in background

4. **HomeActivity Recording Status**:
    - Shows: "🔴📋 QA Recording Active (Staging)"
    - Stop button enabled: "🛑 Stop QA Recording"
    - File system debug shows recording files

### Step 4: Log Verification

Check Android logcat for these key messages:

```bash
adb logcat | grep -E "(EnvironmentManager|QASnapHelper|QASnapRecorder)"
```

**Expected logs**:

```
QASnapHelper: Calling QA Snap SDK startRecording() - MediaProjection dialog should appear
QASnapRecorder: Creating media projection intent
QASnapRecorder: Launching media projection launcher  
QASnapHelper: QA Snap SDK onRecordingStarted callback received
QASnapHelper: QA Snap SDK onLogCaptureStarted callback received
```

### Step 5: File Output Verification

Recording files should be created in:

```
/Android/data/io.codingskuy.qa_snap_demo.staging/files/
├── QASnapRecordings/
│   └── qa_snap_recording_[timestamp].mp4
└── QASnapLogs/
    └── qa_snap_logs_[timestamp].txt
```

### Step 6: Stop Recording Test

1. Go to HomeActivity
2. Click "🛑 Stop QA Recording"
3. Recording should stop and files should be saved
4. Toast: "QA Recording stopped"

## 🔍 Troubleshooting

### Issue: MediaProjection dialog doesn't appear

**Check**:

```bash
# Verify staging build
adb shell dumpsys package io.codingskuy.qa_snap_demo.staging | grep versionName
# Should show: versionName=1.0-staging

# Check QA Snap status
adb logcat | grep "QA Snap enabled"
# Should show: QA Snap enabled: true
```

### Issue: Permission denied error

**Solution**: Grant permissions manually

```bash
# Grant notification permission  
adb shell pm grant io.codingskuy.qa_snap_demo.staging android.permission.POST_NOTIFICATIONS

# Grant audio recording permission
adb shell pm grant io.codingskuy.qa_snap_demo.staging android.permission.RECORD_AUDIO
```

### Issue: Recording files not created

**Check**: Storage permissions and directory

```bash
adb shell ls -la /Android/data/io.codingskuy.qa_snap_demo.staging/files/
```

## 📊 Environment Comparison Testing

### Development Flavor Test

```bash
./build_environments.sh dev debug
# Install and test - should show:
# - "🔧 Development Environment"  
# - No QA recording option
# - No MediaProjection dialog
```

### Production Flavor Test

```bash
./build_environments.sh prod release
# Install and test - should show:
# - "🚀 Production Environment"
# - No QA recording option
# - No debug toasts
```

## ✅ Success Criteria

### ✅ Staging Environment

- [ ] MediaProjection permission dialog appears
- [ ] User can grant permission by clicking "Start now"
- [ ] Screen recording actually starts (notification visible)
- [ ] Log capture starts automatically
- [ ] Recording continues across activities
- [ ] Recording can be stopped from HomeActivity
- [ ] Video file (.mp4) created in QASnapRecordings/
- [ ] Log file (.txt) created in QASnapLogs/

### ✅ Development Environment

- [ ] Shows "🔧 Development Environment"
- [ ] No MediaProjection dialog
- [ ] No recording functionality
- [ ] App functions normally

### ✅ Production Environment

- [ ] Shows "🚀 Production Environment"
- [ ] No MediaProjection dialog
- [ ] No debug toasts
- [ ] Clean production experience

## 🎯 Expected Results

### Working QA Snap Integration

After fixes, the staging flavor should now:

1. **Show MediaProjection Permission Dialog** ✅
2. **Actually Record Screen** when permission granted ✅
3. **Capture Logs Simultaneously** ✅
4. **Save Files to Device Storage** ✅
5. **Continue Recording Across Activities** ✅
6. **Allow Manual Stop from UI** ✅

### Environment Separation

- **Development**: QA Snap completely disabled ✅
- **Staging**: QA Snap fully functional ✅
- **Production**: QA Snap completely disabled ✅

---

## 🎉 Ready for Real QA Testing!

With these fixes, QA Snap SDK integration is now **fully functional** in staging environment while
remaining **completely disabled** in development and production environments.

**Team QA dapat sekarang menggunakan staging build untuk melakukan comprehensive testing dengan
screen recording dan log capture yang sebenarnya!**