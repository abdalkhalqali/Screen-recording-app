# QA Snap SDK - Specific Testing Steps

Testing steps khusus untuk mengatasi 2 masalah yang dialami:

1. ✅ Permission - startRecord() - splash berfungsi rapih, tetapi **tidak melanjut ke signin page**
2. ✅ Berfungsi baik namun **belum diketahui uji fungsional dari home page**

## 🔍 **Issue #1: Navigation ke SignIn Page**

### **Enable Debug Logging:**

```bash
# Clear logs first
adb logcat -c

# Monitor navigation-specific logs
adb logcat | grep -E "(MainActivity|SignInActivity|hasNavigated|navigating)"
```

### **Expected Log Flow for Navigation:**

```
MainActivity: onQARecordingReady() called
MainActivity: Starting splash delay timer (will wait for recording to start)
MainActivity: Starting fallback navigation timer (10 seconds)
MainActivity: onQARecordingStarted() called - Recording is now active!
MainActivity: Recording started successfully, splash timer will handle navigation
MainActivity: Splash timer expired - checking navigation conditions
MainActivity: isFinishing: false, hasNavigated: false
MainActivity: Conditions met, navigating to SignIn
MainActivity: Setting hasNavigated = true, navigating to SignInActivity
MainActivity: Created intent for SignInActivity
MainActivity: Started SignInActivity, calling finish()
MainActivity: MainActivity.finish() called
```

### **Testing Steps:**

1. **Launch App**
    - Should see splash screen
    - Should get MediaProjection permission dialog
    - Grant permission

2. **Observe Navigation**
    - Wait 3 seconds after recording starts
    - Should automatically navigate to SignInActivity
    - Check logs for navigation process

3. **If Navigation Fails:**
   ```bash
   # Check if SignInActivity exists
   adb logcat | grep "SignInActivity"
   
   # Check for navigation errors
   adb logcat | grep -i "error\|exception"
   ```

### **Possible Issues & Fixes:**

#### **A. SignInActivity Not Found:**

```bash
# Verify SignInActivity is registered in manifest
grep -r "SignInActivity" qa-snap-demo/src/main/
```

#### **B. Navigation Timer Issues:**

- Check if `hasNavigated` flag is being set correctly
- Look for premature activity finish

#### **C. Exception During Navigation:**

- Check for intent creation errors
- Verify activity context is valid

## 🎮 **Issue #2: Home Page Functional Testing**

### **Enable HomeActivity Logging:**

```bash
# Monitor HomeActivity-specific logs
adb logcat | grep -E "(HomeActivity|QASnapHelper|recording)"
```

### **Expected HomeActivity Flow:**

```
HomeActivity: HomeActivity onCreate - setting up QA Snap integration
HomeActivity: Setting up QASnapHelper
QASnapHelper: Initializing QASnapHelper with autoStart: false
HomeActivity: QASnapHelper setup completed
HomeActivity: Setting up click listeners
HomeActivity: Click listeners setup completed
HomeActivity: HomeActivity onResume - updating status
```

### **Testing Checklist for HomeActivity:**

#### **✅ Basic Functionality:**

- [ ] Activity loads without crashes
- [ ] All buttons are visible and clickable
- [ ] Recording status shows correctly
- [ ] Stop recording button is enabled when recording active

#### **✅ QA Integration Testing:**

- [ ] Recording status updates correctly
- [ ] Can stop recording from HomeActivity
- [ ] Files are saved when stopped from HomeActivity
- [ ] Recording continues in background while using other features

#### **✅ Button Testing:**

- [ ] **Stop Recording Button** - Shows dialog and stops recording
- [ ] **View Profile Button** - Shows toast, recording continues
- [ ] **Settings Button** - Shows toast, recording continues
- [ ] **Activity 2 Button** - Performs action, gets recorded
- [ ] **Activity 3 Button** - Performs action, gets recorded
- [ ] **Debug File System** - Shows file system info
- [ ] **Log Test Buttons** - Generate different log levels

#### **✅ Log Generation Testing:**

Test each log button and verify logs are captured:

```bash
# Test Debug log
adb logcat | grep "Test log message 1 - Debug level from HomeActivity"

# Test Info log  
adb logcat | grep "Test log message 2 - Info level from HomeActivity"

# Test Warning log
adb logcat | grep "Test log message 3 - Warning level from HomeActivity"

# Test Error log
adb logcat | grep "Test error log message from HomeActivity"
```

### **Detailed Testing Steps:**

#### **Step 1: Navigation to HomeActivity**

1. Complete signin process from SignInActivity
2. Should navigate to HomeActivity
3. Verify recording is still active

#### **Step 2: Status Verification**

1. Check recording status display
2. Should show "🔴📋 QA Recording Active (Video & Logs)"
3. Stop button should be enabled

#### **Step 3: Interactive Testing**

1. **Click each button** and verify:
    - Button responds
    - Appropriate toast/dialog appears
    - Action is logged
    - Recording continues

2. **Test Log Generation:**
    - Click each log test button
    - Verify different log levels are generated
    - Check that logs will be captured in final log file

#### **Step 4: Recording Control Testing**

1. **Test Stop Recording:**
    - Click stop recording button
    - Should show confirmation dialog
    - Confirm stop
    - Should see completion dialog with file info
    - Verify both video and log files are created

#### **Step 5: File Verification**

```bash
# Check video files created
adb shell ls -la /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapRecordings/

# Check log files created
adb shell ls -la /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapLogs/

# Pull files for verification
adb pull /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapRecordings/ ./test-output/
adb pull /sdcard/Android/data/io.codingskuy.qa_snap_demo/files/QASnapLogs/ ./test-output/
```

## 🧪 **Complete Test Scenario**

### **Full Flow Test:**

1. **Launch app** → MainActivity splash
2. **Grant permissions** → MediaProjection dialog
3. **Recording starts** → Notification appears
4. **Auto-navigate** → SignInActivity (3 seconds)
5. **Complete signin** → Navigate to HomeActivity
6. **Test interactions** → All buttons work
7. **Generate logs** → Test different log levels
8. **Stop recording** → Both files saved
9. **Verify files** → Video playable, logs readable

### **Success Criteria:**

- ✅ **Navigation works** - Auto-navigates to SignIn after 3 seconds
- ✅ **HomeActivity functional** - All buttons work, recording continues
- ✅ **Log generation** - All log levels captured
- ✅ **File creation** - Both video and log files created
- ✅ **No crashes** - Smooth operation throughout

### **Common Issues & Solutions:**

#### **Navigation Issue:**

```bash
# If navigation doesn't happen, check:
adb logcat | grep "hasNavigated\|navigationTimer\|startSplashTimer"

# Look for timer completion
adb logcat | grep "Splash timer expired"
```

#### **HomeActivity Issues:**

```bash
# If HomeActivity doesn't work properly, check:
adb logcat | grep "HomeActivity\|setupQASnapHelper"

# Look for setup errors
adb logcat | grep -i "error\|exception" | grep HomeActivity
```

## 📋 **Test Report Template**

```
Test Date: ___________
Device: ___________

=== NAVIGATION TEST ===
✅/❌ Splash screen shows
✅/❌ MediaProjection permission dialog
✅/❌ Recording starts notification
✅/❌ Auto-navigation to SignIn (3 seconds)
✅/❌ SignIn to Home navigation

=== HOME ACTIVITY TEST ===
✅/❌ HomeActivity loads properly
✅/❌ Recording status shows correctly
✅/❌ Stop recording button works
✅/❌ All interaction buttons work
✅/❌ Log test buttons generate logs
✅/❌ Recording continues in background
✅/❌ File creation on stop

Issues Found:
___________

Notes:
___________
```

**Test kedua issue ini dan report hasilnya menggunakan template di atas!** 🧪✅