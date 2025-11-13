# Changelog

All notable changes to the QA Snap SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Maven publishing configuration
- JitPack support
- Comprehensive publishing guide

## [1.0.0] - 2024-11-13

### Added

- Initial release of QA Snap SDK
- Screen recording functionality with MediaProjection API
- Log capture with real-time filtering
- Environment-aware initialization (Development/Staging/Production)
- Permission management system
- Activity lifecycle integration
- QASnapRecorder with recording listener callbacks
- QASnapHelper for easy integration
- QASnapActivity base class
- Service-based architecture (ScreenRecordingService, LogCaptureService)
- Support for Android API 21+ (Android 5.0 Lollipop)
- ProGuard rules for release builds
- Multi-flavor support (development, staging, production)

### Features

- **Screen Recording**: High-quality screen recording using MediaProjection
- **Log Capture**: Real-time log capture with filtering capabilities
- **Environment Management**: Different behavior per environment
- **Permission Handling**: Automatic permission request flow
- **Lifecycle Aware**: Proper integration with Activity lifecycle
- **Callback System**: Comprehensive callback system for recording events
- **Error Handling**: Robust error handling and reporting
- **Notification Support**: Recording status notifications
- **File Management**: Automatic file naming and storage management

### Technical Details

- Minimum SDK: API 21 (Android 5.0)
- Target SDK: API 34 (Android 14)
- Language: Kotlin
- Dependencies: AndroidX, Material Design Components
- Architecture: Service-based with helper classes
- Thread Safe: All operations are thread-safe
- Memory Optimized: Efficient memory usage during recording

### Documentation

- Complete integration guide
- API documentation
- ProGuard configuration
- Permission requirements
- Usage examples
- Troubleshooting guide

[Unreleased]: https://github.com/Coding-Skuy/qa-snap-sdk/compare/v1.0.0...HEAD

[1.0.0]: https://github.com/Coding-Skuy/qa-snap-sdk/releases/tag/v1.0.0