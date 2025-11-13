# QA Tester Onboarding Implementation

## Overview

Implementasi QA Software Tester Onboarding page yang hanya aktif pada **STAGING environment** dan
muncul sebelum splash screen/MainActivity. Page ini menyediakan sambutan untuk tester dan pengaturan
informasi test case yang akan diintegrasikan dengan QA Snap SDK.

## Fitur Utama

### 1. Environment-Aware Activation

- **AKTIF HANYA DI STAGING**: Onboarding page hanya muncul di staging environment
- **AUTO-SKIP**: Di development dan production environment, langsung ke MainActivity
- **ONE-TIME SHOW**: Setelah completed, tidak akan muncul lagi (tersimpan di SharedPreferences)

### 2. QA Tester Onboarding Page

**File**: `QATesterOnboardingActivity.kt` + `activity_qa_tester_onboarding.xml`

**Konten Page**:

- 🧪 **Welcome Message**: "Selamat Datang QA Tester!"
- 📝 **Petunjuk Penggunaan QA Snap**:
    - Isi informasi test case di bawah ini
    - Pilih 'Entire Screen' saat recording (bukan Single App)
    - Semua aktivitas akan tercatat lengkap dengan info device dan waktu
    - File log akan tersimpan otomatis untuk analisis

**Form Input**:

1. **Judul Test Case** (default: "Bug Hunting")
2. **ID Test Case** (opsional)
3. **Reference** (link atau no issue - opsional)

**Important Warning**: Instruksi untuk memilih "Entire Screen" bukan "Single App"

### 3. Enhanced Navigation Flow

```
LauncherActivity (New Entry Point)
├── Staging Environment + Onboarding Not Completed
│   └── QATesterOnboardingActivity
│       └── MainActivity (after form completion)
└── Other Environments OR Onboarding Completed
    └── MainActivity (direct)
```

### 4. Test Case Information Integration

**QASnapHelper Enhancement**:

- Membaca informasi test case dari SharedPreferences
- Log komprehensif saat recording dimulai:
    - Test case title, ID, reference
    - Device specifications
    - Session timestamp
    - Environment info
    - App version, process ID, memory info
- Log session end dengan durasi recording

**SharedPreferences Keys**:

```
"qa_snap_test_info":
- test_case_title: String
- test_case_id: String (optional)
- reference: String (optional)
- setup_timestamp: Long
- device_model: String
- device_manufacturer: String
- android_version: String
- last_session_start: Long
- last_session_end: Long
- last_session_duration: Long
```

### 5. Comprehensive Logging

**Saat Recording Dimulai**:

```
=== QA SNAP RECORDING SESSION STARTED ===
Test Case Title: Bug Hunting
Test Case ID: TC-001
Reference: https://github.com/issue/123
Session Start Time: 2024-11-13 20:30:45
Setup Time: 2024-11-13 20:25:12
Device: Samsung SM-A057F
Android Version: 13
Environment: Staging
App Version: 1.0-staging
Process ID: 12345
Thread ID: 2
Available Memory: 256 MB
==========================================

=== DEVICE SPECIFICATIONS ===
Brand: Samsung
Model: SM-A057F
Device: a05s
Product: a05sxx
Hardware: mt6765
Board: a05s
SDK Version: 33
Android Version: 13
Build ID: TP1A.220624.014
=============================
```

**Saat Recording Berakhir**:

```
=== QA SNAP RECORDING SESSION ENDED ===
Session End Time: 2024-11-13 20:35:20
Session Duration: 4m 35s
Total Duration (ms): 275000
=======================================
```

## File Structure

### New Files Created:

```
qa-snap-demo/src/main/java/io/codingskuy/qa_snap_demo/
├── LauncherActivity.kt                 # New entry point
├── QATesterOnboardingActivity.kt       # Onboarding page
└── base/QASnapHelper.kt               # Enhanced with test case logging

qa-snap-demo/src/main/res/layout/
├── activity_launcher.xml              # Simple launcher layout
└── activity_qa_tester_onboarding.xml  # Onboarding form layout
```

### Modified Files:

```
qa-snap-demo/src/main/
├── AndroidManifest.xml                # LauncherActivity as main entry
└── java/io/codingskuy/qa_snap_demo/utils/
    └── EnvironmentManager.kt          # Added debug utilities
```

## Usage & Testing

### Testing di Staging Environment:

1. **First Launch**: Akan muncul onboarding page
2. **Fill Form**: Isi judul test case, ID (optional), reference (optional)
3. **Continue**: Onboarding completed, masuk ke MainActivity
4. **Subsequent Launches**: Langsung ke MainActivity (onboarding sudah completed)

### Testing di Development/Production:

- Langsung ke MainActivity (onboarding di-skip)

### Reset Onboarding (Debug):

```kotlin
// Untuk testing ulang onboarding
EnvironmentManager.resetQAOnboarding(context)
```

### Debug Information:

```kotlin
val debugInfo = EnvironmentManager.getDebugInfo(context)
// Returns comprehensive environment and onboarding status
```

## Integration Points

### 1. Dengan QA Snap SDK:

- Test case info disimpan di SharedPreferences dengan key `"qa_snap_test_info"`
- QASnapHelper membaca info saat recording dimulai
- Semua informasi di-log dengan format yang konsisten

### 2. Dengan Environment System:

- Menggunakan `EnvironmentManager.isQASnapEnabled()`
- Hanya aktif saat `ENABLE_QA_SNAP = true` dan environment = "staging"

### 3. Dengan Navigation Flow:

- LauncherActivity sebagai entry point baru
- Menentukan flow berdasarkan environment dan onboarding status

## Benefits

1. **User Experience**: Tester mendapat guidance yang jelas
2. **Data Collection**: Informasi test case tercatat lengkap
3. **Environment Safety**: Tidak mengganggu production
4. **One-time Setup**: Setelah setup, tidak mengganggu workflow
5. **Comprehensive Logging**: Device info, session timing, test case details
6. **Proper Guidance**: Instruksi untuk pilih "Entire Screen"

## Configuration

Onboarding hanya aktif jika:

- Environment = STAGING
- BuildConfig.ENABLE_QA_SNAP = true
- Onboarding belum pernah completed

Untuk reset onboarding (debug only):

```kotlin
QATesterOnboardingActivity.resetOnboarding(context)
// atau
EnvironmentManager.resetQAOnboarding(context)
```