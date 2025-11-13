# Android Media Projection Sample dengan QA Snap SDK

## Overview

Proyek ini adalah contoh implementasi **Multi-Environment Android App** menggunakan **Product
Flavors** dengan integrasi **QA Snap SDK** yang hanya aktif di environment Staging. Memberikan
solusi aman untuk QA recording tanpa mempengaruhi production build.

## 🌍 Multi-Environment Support (Product Flavors)

### Environment Configuration

| Flavor          | Application ID                       | QA Snap       | Logging    | App Name               |
|-----------------|--------------------------------------|---------------|------------|------------------------|
| **development** | `io.codingskuy.qa_snap_demo.dev`     | ❌ DISABLED    | ✅ ENABLED  | QA Snap Demo (Dev)     |
| **staging**     | `io.codingskuy.qa_snap_demo.staging` | ✅ **ENABLED** | ✅ ENABLED  | QA Snap Demo (Staging) |
| **production**  | `io.codingskuy.qa_snap_demo`         | ❌ DISABLED    | ❌ DISABLED | QA Snap Demo           |

### Build Variants Matrix

```
Product Flavor + Build Type = Build Variant
development + debug = developmentDebug
staging + debug = stagingDebug (QA Snap enabled)
staging + release = stagingRelease (QA Snap enabled)  
production + release = productionRelease
```

### Key Features

- 🔒 **Security First**: QA Snap never active in production flavor
- 🎯 **Targeted Testing**: QA recording only in staging flavor
- 🛠️ **Developer Friendly**: Easy flavor switching with build variants
- 📱 **Clean UX**: Environment-appropriate user experience
- 🔧 **Maintainable**: Centralized flavor configuration

## 🚀 Quick Start

### Option 1: Using Build Script (Recommended)

```bash
# Build staging flavor (QA Snap enabled)
./build_environments.sh staging debug    # stagingDebug
./build_environments.sh staging release  # stagingRelease

# Build all flavor combinations  
./build_environments.sh all

# Show flavor comparison
./build_environments.sh info
```

### Option 2: Using Gradle Directly

```bash
# Development flavor
./gradlew assembleDevelopmentDebug    # Daily development
./gradlew assembleDevelopmentRelease  # Dev testing

# Staging flavor (QA Snap enabled)
./gradlew assembleStagingDebug        # QA testing with debug
./gradlew assembleStagingRelease      # QA testing optimized

# Production flavor  
./gradlew assembleProductionDebug     # Prod debugging
./gradlew assembleProductionRelease   # Final production
```

### Option 3: Using Android Studio

1. Open project in Android Studio
2. Go to **Build Variants** panel
3. Select flavor+buildType: `developmentDebug` | `stagingDebug` | `productionRelease`
4. Build APK

## 📱 Testing QA Snap Integration

### Development Flavor

- Build: `./gradlew assembleDevelopmentDebug`
- ❌ QA Snap will NOT appear
- Toast: "🔧 Development environment - QA Snap disabled"
- App functions normally without recording

### Staging Flavor

- Build: `./gradlew assembleStagingDebug`
- ✅ QA Snap will automatically activate
- MediaProjection permission dialog appears
- Toast: "📹 Staging Environment - QA Recording Active"
- Screen recording and logs capture active

### Production Flavor

- Build: `./gradlew assembleProductionRelease`
- ❌ QA Snap will NOT appear
- No debug toasts
- Clean production experience

## 🏗️ Architecture

### Product Flavors vs Build Types

```kotlin
// ✅ Product Flavors (Environment Configuration)
productFlavors {
    development { /* Dev environment config */ }
    staging { /* Staging environment + QA Snap */ }
    production { /* Production environment */ }
}

// ✅ Build Types (Optimization Level)  
buildTypes {
    debug { /* Debug symbols, no optimization */ }
    release { /* Optimized, no debug symbols */ }
}
```

### Environment-Aware Base Classes

```kotlin
// Old way - tied to QA Snap in all builds
class MainActivity : QASnapActivity() {
    // Always includes QA Snap overhead
}

// New way - flavor-aware with compile-time separation
class MainActivity : BaseActivity() {
    // QA Snap only compiled in staging flavor
    
    override fun onQARecordingStarted() {
        // Only called in staging builds
        super.onQARecordingStarted()
        startNavigationTimer()
    }
}
```

### Environment Detection

```kotlin
// Check current flavor at runtime
when (EnvironmentManager.getCurrentEnvironment()) {
    Environment.DEVELOPMENT -> {
        // Development-specific code
    }
    Environment.STAGING -> {
        // Staging-specific code (QA Snap available)
    }
    Environment.PRODUCTION -> {
        // Production-specific code
    }
}

// Check if QA Snap is available (compile-time + runtime)
if (EnvironmentManager.isQASnapEnabled()) {
    startQARecording()  // Only possible in staging builds
} else {
    // Fallback behavior for dev/prod
}
```

## 📂 Project Structure

```
qa-snap-demo/
├── src/main/java/.../
│   ├── base/
│   │   ├── BaseActivity.kt          # Environment-aware base activity
│   │   ├── QASnapHelper.kt          # QA Snap SDK wrapper
│   │   └── QASnapCallback.kt        # Callback interface
│   ├── utils/
│   │   └── EnvironmentManager.kt    # Environment configuration manager
│   ├── MainActivity.kt              # Splash with environment detection
│   ├── SignInActivity.kt            # Sign-in with environment awareness
│   └── HomeActivity.kt              # Home with QA recording controls
├── build.gradle                     # Multi-flavor build config
└── ...
```

## 🔧 Build Configuration

The multi-environment setup menggunakan Product Flavors di `build.gradle`:

```gradle
android {
    flavorDimensions = ["environment"]
    
    productFlavors {
        development {
            dimension "environment"
            applicationIdSuffix ".dev"
            buildConfigField "boolean", "ENABLE_QA_SNAP", "false"
            resValue "string", "app_name", "QA Snap Demo (Dev)"
        }
        
        staging {
            dimension "environment"
            applicationIdSuffix ".staging"
            buildConfigField "boolean", "ENABLE_QA_SNAP", "true"  // ONLY TRUE HERE
            resValue "string", "app_name", "QA Snap Demo (Staging)"
        }
        
        production {
            dimension "environment"
            buildConfigField "boolean", "ENABLE_QA_SNAP", "false"
            resValue "string", "app_name", "QA Snap Demo"
        }
    }

    buildTypes {
        debug { /* Debug config */ }
        release { /* Release config */ }
    }
}
```

## 🧪 QA Snap Features (Staging Flavor Only)

When running in staging flavor, the app provides:

- ✅ **Screen Recording**: Full screen capture with MediaProjection
- ✅ **Log Capture**: System logs and app-specific logs
- ✅ **Manual Controls**: Start/stop recording from UI
- ✅ **Automatic Sessions**: Recording starts automatically on app launch
- ✅ **File Management**: Automatic saving to device storage
- ✅ **Session Continuity**: Recording continues across activities

### QA Recording Controls (HomeActivity)

- 🛑 **Stop Recording**: Manual recording termination
- 📁 **File System Check**: Debug QA files and directories
- 📝 **Log Generation**: Test different log levels
- 📊 **Status Display**: Current recording state

## 📊 Build Variant Comparison

| Build Variant      | QA Snap | Debug Symbols | Optimization | Use Case               |
|--------------------|---------|---------------|--------------|------------------------|
| developmentDebug   | ❌       | ✅             | ❌            | Daily development      |
| developmentRelease | ❌       | ❌             | ✅            | Dev testing            |
| stagingDebug       | ✅       | ✅             | ❌            | QA debugging           |
| stagingRelease     | ✅       | ❌             | ✅            | QA performance testing |
| productionDebug    | ❌       | ✅             | ❌            | Production debugging   |
| productionRelease  | ❌       | ❌             | ✅            | **Final production**   |

## 📚 Documentation

- 📖 [Multi-Environment Implementation](MULTI_ENVIRONMENT_IMPLEMENTATION.md) - Detailed Product
  Flavors guide
- 🔧 [Build Commands](build_environments.sh) - Automated build script
- 📱 [Usage Examples](USAGE_EXAMPLES.md) - Integration examples
- 🐛 [Debugging Guide](DEBUGGING_GUIDE.md) - Troubleshooting help

## 🔍 Troubleshooting

### QA Snap tidak muncul di Staging

1. Check build variant contains "staging" flavor
2. Verify `BuildConfig.ENABLE_QA_SNAP` = true
3. Check `EnvironmentManager.isQASnapEnabled()` = true

### App crash saat switch environment

1. Uninstall previous APK (different application IDs)
2. Clean rebuild project: `./gradlew clean`
3. Check dependencies compatibility

### Flavor/BuildType confusion

1. Use Product Flavors untuk environments (dev/staging/prod)
2. Use Build Types untuk optimization (debug/release)
3. Build Variants = Flavor + BuildType combination

## 🎯 Use Cases

### For QA Teams

- Build stagingDebug untuk comprehensive testing with debug info
- Build stagingRelease untuk production-like performance testing
- Record user flows and bug reproductions
- Share QA sessions with development team

### For Developers

- Use developmentDebug untuk regular development (no QA overhead)
- Use stagingDebug untuk testing QA integration
- Easy flavor switching for different testing needs
- Safe production builds (QA code not even compiled)

### For DevOps/Release

- Production builds guaranteed QA-free at compile time
- Multiple APKs untuk different environments
- Clear flavor separation
- Automated build pipeline support dengan flavor matrix

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

## 🤝 Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/flavor-improvement`
3. Test all flavors: `./build_environments.sh all`
4. Commit changes: `git commit -am 'Add flavor feature'`
5. Push to branch: `git push origin feature/flavor-improvement`
6. Submit pull request

## 🔗 Related Projects

- [QA Snap SDK](qa-snap-sdk/) - Core SDK for screen recording
- [Android Media Projection API](https://developer.android.com/guide/topics/media/mediarecorder) -
  Android screen capture foundation

---

**⚠️ Important**: QA Snap SDK is ONLY compiled and active in **staging flavor** for security and
performance reasons. Production builds akan exclude QA recording functionality completely at
compile-time.

**✅ Correct Implementation**: Menggunakan **Product Flavors** untuk environment separation, bukan
Build Types. Ini memberikan compile-time safety dan zero runtime overhead di production.