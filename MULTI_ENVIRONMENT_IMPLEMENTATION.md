# Multi-Environment Implementation dengan QA Snap SDK (Product Flavors)

## Overview

Proyek ini mendemonstrasikan implementasi **Multi-Environment Android App** dengan integrasi **QA
Snap SDK** yang hanya aktif di environment Staging menggunakan **Product Flavors** (bukan Build
Types).

## 🔄 Product Flavors vs Build Types

### ✅ Product Flavors (Environment Configuration)

- **Purpose**: Different environments/configurations
- **Examples**: development, staging, production
- **Use for**: API endpoints, feature flags, application IDs

### ✅ Build Types (Optimization Level)

- **Purpose**: Debug vs Release optimizations
- **Examples**: debug, release
- **Use for**: Debugging, minification, obfuscation

### 📊 Matrix Combination

```
Product Flavor + Build Type = Build Variant
development + debug = developmentDebug
staging + debug = stagingDebug (QA enabled)
staging + release = stagingRelease (QA enabled)
production + release = productionRelease
```

## 🏗️ Architecture Structure

### Environment Configuration (Product Flavors)

```
┌─────────────────┬─────────────────────────────┬─────────────┬─────────────┬──────────────────────────┐
│ Flavor          │ Application ID              │ QA Snap     │ Logging     │ App Name                 │
├─────────────────┼─────────────────────────────┼─────────────┼─────────────┼──────────────────────────┤
│ development     │ io.codingskuy.qa_snap_demo.dev     │ ❌ DISABLED │ ✅ ENABLED  │ QA Snap Demo (Dev)       │
│ staging         │ io.codingskuy.qa_snap_demo.staging │ ✅ ENABLED  │ ✅ ENABLED  │ QA Snap Demo (Staging)   │
│ production      │ io.codingskuy.qa_snap_demo         │ ❌ DISABLED │ ❌ DISABLED │ QA Snap Demo             │
└─────────────────┴─────────────────────────────┴─────────────┴─────────────┴──────────────────────────┘
```

## 🔧 Build Configuration

### 1. Product Flavors Configuration (`build.gradle`)

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
        debug {
            debuggable true
            minifyEnabled false
        }
        
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

## 🚀 Build Commands

### Individual Flavor + Build Type Combinations

```bash
# Development Flavor
./gradlew assembleDevelopmentDebug    # Dev + Debug
./gradlew assembleDevelopmentRelease  # Dev + Release

# Staging Flavor (QA Snap enabled)
./gradlew assembleStagingDebug        # Staging + Debug (QA enabled)
./gradlew assembleStagingRelease      # Staging + Release (QA enabled)

# Production Flavor  
./gradlew assembleProductionDebug     # Prod + Debug
./gradlew assembleProductionRelease   # Prod + Release
```

### Using Build Script

```bash
# Build specific environment with default build type
./build_environments.sh dev           # developmentDebug
./build_environments.sh staging       # stagingDebug (QA enabled)
./build_environments.sh prod          # productionRelease

# Build specific environment with specific build type
./build_environments.sh dev debug     # developmentDebug
./build_environments.sh staging release  # stagingRelease (QA enabled)
./build_environments.sh prod release  # productionRelease

# Build all combinations
./build_environments.sh all
```

## 📱 Generated APK Structure

### APK Directory Structure

```
qa-snap-demo/build/outputs/apk/
├── development/
│   ├── debug/qa-snap-demo-development-debug.apk
│   └── release/qa-snap-demo-development-release.apk
├── staging/
│   ├── debug/qa-snap-demo-staging-debug.apk          # QA Snap enabled
│   └── release/qa-snap-demo-staging-release.apk      # QA Snap enabled
└── production/
    ├── debug/qa-snap-demo-production-debug.apk
    └── release/qa-snap-demo-production-release.apk
```

### Build Variant Naming

- **Format**: `{flavor}{BuildType}` (e.g., `developmentDebug`, `stagingRelease`)
- **APK Naming**: `qa-snap-demo-{flavor}-{buildType}.apk`

## 🧪 Testing Different Combinations

### Development Flavor
```bash
# Debug version - for daily development
./gradlew assembleDevelopmentDebug
# Features: Debug tools, Logging, No QA Snap

# Release version - for development testing
./gradlew assembleDevelopmentRelease  
# Features: Optimized, Logging, No QA Snap
```

### Staging Flavor (QA Snap Enabled)
```bash
# Debug version - for QA testing with debug info
./gradlew assembleStagingDebug
# Features: QA Snap, Debug tools, Logging

# Release version - for QA testing in production-like environment
./gradlew assembleStagingRelease
# Features: QA Snap, Optimized, Logging
```

### Production Flavor

```bash
# Debug version - for production debugging (rare)
./gradlew assembleProductionDebug
# Features: Debug tools, No logging, No QA Snap

# Release version - final production build
./gradlew assembleProductionRelease
# Features: Fully optimized, No logging, No QA Snap
```

## 🎯 Use Cases per Build Variant

### developmentDebug

- ✅ Daily development work
- ✅ Local testing
- ✅ Feature development
- ❌ No QA overhead

### stagingDebug

- ✅ QA testing with debug symbols
- ✅ Bug investigation
- ✅ Screen recording + logs
- ✅ Debug-friendly QA sessions

### stagingRelease

- ✅ QA testing in production-like performance
- ✅ Performance testing with QA recording
- ✅ Pre-release validation
- ✅ Client demos with recording

### productionRelease

- ✅ Final production deployment
- ✅ App store releases
- ✅ Zero QA overhead
- ✅ Maximum performance

## 🔍 Environment Detection in Code

```kotlin
// Check current flavor
when (EnvironmentManager.getCurrentEnvironment()) {
    Environment.DEVELOPMENT -> {
        // Development-specific code
        // QA Snap not available
    }
    Environment.STAGING -> {
        // Staging-specific code
        // QA Snap available in both debug & release
    }
    Environment.PRODUCTION -> {
        // Production-specific code  
        // QA Snap never available
    }
}

// Check QA Snap availability (only true in staging flavor)
if (EnvironmentManager.isQASnapEnabled()) {
    startQARecording()  // Only works in staging*
} else {
    // Fallback behavior for dev/prod
}
```

## 🛡️ Security Benefits

### ✅ Product Flavor Advantages

- **Compile-time Safety**: QA code completely excluded from production flavor
- **Zero Runtime Overhead**: No QA artifacts in production builds
- **Clear Separation**: Environment logic separated at build level
- **Impossible Mistakes**: No way to accidentally enable QA in production

### ❌ Build Type Limitations (Previous Approach)

- Runtime checks required
- QA code still included in all builds
- Potential security risks
- Performance overhead

## 📊 Build Variant Comparison

| Build Variant      | QA Snap | Debug Symbols | Optimization | Use Case               |
|--------------------|---------|---------------|--------------|------------------------|
| developmentDebug   | ❌       | ✅             | ❌            | Daily development      |
| developmentRelease | ❌       | ❌             | ✅            | Dev testing            |
| stagingDebug       | ✅       | ✅             | ❌            | QA debugging           |
| stagingRelease     | ✅       | ❌             | ✅            | QA performance testing |
| productionDebug    | ❌       | ✅             | ❌            | Production debugging   |
| productionRelease  | ❌       | ❌             | ✅            | **Final production**   |

## 🎉 Migration Benefits

### Before (Build Types Approach)
```kotlin
// Runtime checks everywhere
if (BuildConfig.BUILD_TYPE == "staging") {
    // QA code still compiled for all builds
}
```

### After (Product Flavors Approach)

```kotlin
// Compile-time separation
if (EnvironmentManager.isQASnapEnabled()) {
    // QA code only exists in staging builds
}
```

## 🚀 Android Studio Integration

### Build Variants Panel

1. Open **Build Variants** panel in Android Studio
2. Select desired combination:
    - `developmentDebug`
    - `stagingDebug` (QA enabled)
    - `stagingRelease` (QA enabled)
    - `productionRelease`

### Run Configurations

- Each variant creates separate run configuration
- Easy switching between environments
- Different app icons per flavor

## 📋 Best Practices

### ✅ Do

- Use Product Flavors untuk environments
- Use Build Types untuk optimization levels
- Keep QA code only in staging flavor
- Test all important flavor+buildType combinations

### ❌ Don't

- Mix environment logic with build types
- Include QA code in production flavor
- Use build types for environment switching
- Forget to test staging+release combination

---