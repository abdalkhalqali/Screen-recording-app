# QA Snap SDK - Testing Guide

Panduan untuk menguji bahwa kedua masalah utama telah diperbaiki:

1. ✅ **Flow Permission - StartRecord - Splash - SignIn** konsisten
2. ✅ **Video Recording** berfungsi dengan baik

## 🎯 **Expected Flow (Fixed)**

### **Correct Sequence:**

1. **App Launch** → MainActivity onCreate
2. **Permission Check** → Basic permissions (audio, storage, notification)
3. **QA Ready** → onQARecordingReady callback
4. **MediaProjection Permission** → System dialog appears
5. **Recording Start** → onQARecordingStarted callback
6. **Splash Timer** → 3-second countdown
7. **Navigation** → Automatic navigate to SignInActivity
8. **Recording Active** → Both video + logs recording in background

## 🔍 **Testing Steps**

### **Step 1: Enable Debug Logging**

```bash
# Clear logs and start fresh
adb logcat -c

# Start logging with filter
adb logcat | grep -E "(MainActivity|QASnapHelper|QASnapRecorder|ScreenRecording)"
```

### **Step 2: Launch App & Observe Flow**

**Expected Log Sequence:**

```
MainActivity: MainActivity onCreate - initializing QA recording
QASnapHelper: Initializing QASnapHelper with autoStart: true
QASnapRecorder: Initializing QASnapRecorder...
QASnapRecorder: QASnapRecorder initialized and instance set
QASnapHelper: QASnapRecorder initialized: true
QASnapHelper: All basic permissions already granted
QASnapHelper: Permissions granted, calling onRecordingReady
MainActivity: onQARecordingReady() called
MainActivity: Calling startQARecording() to trigger MediaProjection permission
MainActivity: Starting splash delay timer (will wait for recording to start)
QASnapHelper: startRecording() called
QASnapRecorder: startRecording() called - current isRecording: false
QASnapRecorder: Starting log capture with default QA settings
QASnapRecorder: Creating media projection intent
QASnapRecorder: Launching media projection launcher
```

**At this point, you should see:**

- ✅ MediaProjection permission dialog appears
- ✅ "QA Recording is ready! Starting recording..." toast

### **Step 3: Grant MediaProjection Permission**

**Click "Start now" in the dialog**

**Expected Log Continuation:**

```
QASnapRecorder: startRecordingService called with data
QASnapRecorder: Screen dimensions: 1080x2400, density: 420
QASnapRecorder: Starting ScreenRecordingService as foreground service
QASnapRecorder: Service started successfully, isRecording set to true
QASnapRecorder: Calling onRecordingStarted callback
QASnapHelper: Recording started callback received
MainActivity: onQARecordingStarted() called - Recording is now active!
```

**At this point, you should see:**

- ✅ "✅ QA Recording is now active!" toast
- ✅ Recording notification in notification bar
- ✅ 3-second splash countdown continues
- ✅ Automatic navigation to SignInActivity after 3 seconds

### **Step 4: Verify Recording is Active**

**In SignInActivity, you should see:**

- ✅ Recording notification still active
- ✅ Can interact normally with UI
- ✅ Recording continues in background

### **Step 5: Stop Recording & Check Files**

**From notification bar:**

- ✅ Tap "Stop" in QA Recording notification
- ✅ Should see completion notifications
- ✅ Both video and log files should be created

**Check files:**

```bash
# Check video files
adb shell ls -la /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapRecordings/

# Check log files  
adb shell ls -la /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapLogs/

# Pull files to computer for verification
adb pull /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapRecordings/ ./test-videos/
adb pull /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapLogs/ ./test-logs/
```

## ✅ **Success Criteria**

### **Flow Consistency (Issue #1):**

- [ ] **Permissions requested first** (if needed)
- [ ] **MediaProjection dialog appears consistently**
- [ ] **Recording starts after permission granted**
- [ ] **Splash timer waits for recording to start**
- [ ] **Navigation happens after 3 seconds**
- [ ] **No duplicate navigations or early exits**

### **Video Recording (Issue #2):**

- [ ] **Video files are created** (not just log files)
- [ ] **Video files have content** (size > 0)
- [ ] **Video files are playable**
- [ ] **Recording notification shows duration**
- [ ] **Both video + logs saved on stop**

## 🚨 **Troubleshooting**

### **Issue: Permission Dialog Doesn't Appear**

**Check:**

```bash
# Look for permission-related logs
adb logcat | grep -i permission

# Check if activity can show dialogs
adb logcat | grep -i "permission denied\|not allowed"
```

**Solution:**

- Check if app has "Display over other apps" permission
- Try manual permission granting in Settings

### **Issue: Recording Starts But No Video File**

**Check:**

```bash
# Look for MediaRecorder issues
adb logcat | grep -i mediarecorder

# Look for ScreenRecordingService issues
adb logcat | grep ScreenRecordingService

# Check storage space
adb shell df -h
```

**Common causes:**

- Insufficient storage space
- MediaRecorder initialization failed
- Service killed by system

### **Issue: Flow Timing Problems**

**Check log timing:**

```bash
# Show timestamps in logs
adb logcat -v time | grep MainActivity
```

**Look for:**

- Early navigation before recording starts
- Missing callback invocations
- Race conditions in timing

## 🎮 **Manual Test Scenarios**

### **Scenario 1: Clean Install**

1. Uninstall app completely
2. Install fresh APK
3. Launch and observe first-run experience
4. Verify all permissions requested properly

### **Scenario 2: Permission Denial Recovery**

1. Launch app
2. Deny MediaProjection permission
3. Observe error handling
4. Retry and grant permission
5. Verify recovery works

### **Scenario 3: Background/Foreground**

1. Start recording
2. Navigate to other apps
3. Return to app
4. Verify recording continues
5. Stop and check files

### **Scenario 4: Multiple Recordings**

1. Complete one recording session
2. Start another session immediately
3. Verify no conflicts or issues
4. Check file naming and storage

## 📊 **Performance Verification**

### **Check Resource Usage:**

```bash
# CPU usage
adb shell top -p $(adb shell pidof io.codingskuy.qa_snap_demo)

# Memory usage
adb shell dumpsys meminfo io.codingskuy.qa_snap_demo

# Battery usage
adb shell dumpsys batterystats | grep -A 10 qa_snap_demo
```

### **File Quality Check:**

- **Video files** should be playable MP4 format
- **Log files** should contain readable text logs
- **File sizes** should be reasonable (not 0 bytes)
- **Timestamps** should be accurate

## 🎉 **Success Confirmation**

If all tests pass, you should have:

- ✅ **Consistent flow** - No random timing issues
- ✅ **Working video recording** - Both video + log files created
- ✅ **Proper UI feedback** - Clear toasts and notifications
- ✅ **Clean navigation** - Smooth app flow
- ✅ **Background recording** - Continues while using other features

**Both major issues should now be resolved! 🎬📋✨**

## 📝 **Test Results Template**

```
Test Date: ___________
Device: ___________
Android Version: ___________

✅/❌ Flow Consistency
✅/❌ MediaProjection Permission Dialog
✅/❌ Video File Creation  
✅/❌ Log File Creation
✅/❌ Navigation Timing
✅/❌ Background Recording
✅/❌ File Quality

Notes:
___________
___________
```