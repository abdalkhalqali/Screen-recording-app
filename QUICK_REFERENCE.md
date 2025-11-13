# 🚀 Quick Reference - Multi-Environment QA Snap SDK

## 📋 TL;DR Summary

**Multi-Environment Android App** dengan **Product Flavors** + **QA Snap SDK** yang hanya aktif di *
*staging environment**.

## 🎯 Environment Matrix

| Environment | App ID | QA Snap | Logging | Use Case |
|-------------|--------|---------|---------|----------|
| **development** | `.dev` | ❌ | ✅ | Daily development |
| **staging** | `.staging` | ✅ | ✅ | **QA Testing** |
| **production** | - | ❌ | ❌ | Production release |

## ⚡ Quick Build Commands

```bash
# 🔧 Development
./build_environments.sh dev debug

# 📹 Staging (QA enabled)  
./build_environments.sh staging debug

# 🚀 Production
./build_environments.sh prod release

# 📊 Show all environments
./build_environments.sh info
```

## 🏗️ Gradle Commands

```bash
# Individual builds
./gradlew assembleDevelopmentDebug    # Dev
./gradlew assembleStagingDebug        # QA enabled
./gradlew assembleProductionRelease   # Production

# All variants
./gradlew assembleDebug              # All flavors × debug
./gradlew assembleRelease            # All flavors × release
```

## 📱 Android Studio Integration

1. Open **Build Variants** panel
2. Select variant:
    - `developmentDebug` - Daily development
    - `stagingDebug` - **QA testing with recording**
    - `productionRelease` - Final production
3. Run/Debug uses selected variant

## 🔧 Code Usage

### Environment Detection

```kotlin
when (EnvironmentManager.getCurrentEnvironment()) {
    Environment.DEVELOPMENT -> {
        // Dev-specific code
    }
    Environment.STAGING -> {
        // Staging + QA Snap available
    }
    Environment.PRODUCTION -> {
        // Production code
    }
}
```

### QA Snap Usage

```kotlin
// Check if QA Snap available (only in staging)
if (EnvironmentManager.isQASnapEnabled()) {
    startQARecording()
} else {
    // Fallback for dev/prod
}
```

### Activity Integration

```kotlin
class MyActivity : BaseActivity() {
    // Automatically gets environment-aware QA Snap
    
    override fun onQARecordingStarted() {
        // Only called in staging
        super.onQARecordingStarted()
        // Your code here
    }
}
```

## 📂 Generated APKs

```
qa-snap-demo/build/outputs/apk/
├── development/debug/...development-debug.apk
├── staging/debug/...staging-debug.apk        # 📹 QA enabled
└── production/release/...production-release.apk
```

## 🧪 Testing Workflow

### For Developers

1. Use `developmentDebug` for daily work
2. Switch to `stagingDebug` to test QA integration
3. Build `productionRelease` for final testing

### For QA Team

1. Always use `stagingDebug` or `stagingRelease`
2. QA recording automatically available
3. Install staging APK: `io.codingskuy.qa_snap_demo.staging`

### For DevOps

1. `developmentDebug` - Dev deployment
2. `stagingRelease` - QA environment (recording enabled)
3. `productionRelease` - Production deployment (QA-free)

## 🔍 Debugging

### Environment Issues

```bash
# Check current environment
adb logcat | grep EnvironmentManager

# Verify QA Snap status
adb logcat | grep "QA Snap enabled"
```

### Build Issues

```bash
# Clean rebuild
./gradlew clean
./build_environments.sh staging debug

# Check build variants
./gradlew tasks --all | grep assemble
```

## 🚨 Common Issues & Solutions

### ❓ QA Snap not working in staging

**Check**: Build variant contains "staging" flavor

```bash
./gradlew assembleStagingDebug  # ✅ Correct
./gradlew assembleDebug         # ❌ Wrong (no flavor)
```

### ❓ App crashes on startup

**Check**: Binding initialization order in activities

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // ✅ Initialize binding FIRST
    binding = ActivityBinding.inflate(layoutInflater)
    setContentView(binding!!.root)
    
    // ✅ Then call super
    super.onCreate(savedInstanceState)
}
```

### ❓ Wrong environment detected

**Solution**: Clean and rebuild

```bash
./gradlew clean
./build_environments.sh [environment] [buildType]
```

## 📋 Checklist

### ✅ Development Setup

- [ ] Clone repository
- [ ] Open in Android Studio
- [ ] Sync Gradle (`./gradlew sync`)
- [ ] Select `developmentDebug` variant
- [ ] Run app - should show "🔧 Development Environment"

### ✅ QA Testing Setup

- [ ] Select `stagingDebug` variant
- [ ] Build and install
- [ ] Run app - should show "📹 Staging Environment"
- [ ] QA recording should be available
- [ ] Test screen recording + log capture

### ✅ Production Release

- [ ] Select `productionRelease` variant
- [ ] Build APK
- [ ] Verify no QA artifacts included
- [ ] Test final production behavior

## 🎯 Key Benefits

- **🔒 Secure**: QA never in production (compile-time)
- **🚀 Fast**: Zero QA overhead in dev/prod
- **🔧 Easy**: Simple environment switching
- **📱 Clean**: Environment-appropriate UX
- **🛠️ Maintainable**: Centralized configuration

## 📞 Need Help?

- 📖 [Full Documentation](MULTI_ENVIRONMENT_IMPLEMENTATION.md)
- 🔧 [Build Script Help](build_environments.sh help)
- 🐛 [Debugging Guide](DEBUGGING_GUIDE.md)
- 📊 [Implementation Status](FINAL_IMPLEMENTATION_STATUS.md)

---

**🎉 Happy Coding!** Multi-environment architecture dengan QA Snap ready to use!