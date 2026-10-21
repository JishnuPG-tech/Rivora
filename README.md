<p align="center">
  <img src="assets/logo.png" alt="Rivora Logo" width="112" height="112" />
</p>

<h1 align="center">Rivora</h1>

<p align="center">
  Multi-network download manager for Android and the official Android version of Plexoo (Plexo).
</p>

---

## About

Rivora is a modern, high-performance download manager and the official Android version of Plexoo (also referred to as Plexo). It is engineered to solve download throughput bottlenecks on mobile devices by aggregating multiple physical network interfaces simultaneously, enforcing strict link pre-validation, and rendering real-time byte telemetry.

### Early Preview Notice

This is an initial early preview release (v1.0.0). Testing has been performed on single physical mobile hardware. Given the diversity of Android manufacturer implementations, aggressive OEM background process limits, and network-specific carrier restrictions, minor defects or anomalies may be encountered. Constructive feedback, bug reports, and contributions are welcomed via GitHub Issues.

---

## Key Capabilities

### Multi-Network Stream Bonding
Binds network sockets directly to distinct physical and wireless interfaces. Transfers execute concurrently across Wi-Fi and Cellular (4G/5G) data paths to pool combined network bandwidth.

### Real-Time Link Validation
Probes target URLs before queuing to verify direct downloadable files (videos, audio, archives, software packages, documents, and torrents/magnets). Detects HTML web pages and connection failures, providing instant feedback without queuing non-binary content.

### Liquid Chunk Progress Matrix
A custom canvas rendering engine displays active chunk distribution and allocation metrics. Individual chunk segments fill smoothly based on byte-level completion rates.

### Resumable Storage Engine
Streams directly to pre-allocated files using seek-and-write operations. Chunk offsets and completion flags are persistently stored, allowing uninterrupted pause, resume, and failure recovery.

### Real-Time Throughput Telemetry
Calculates instantaneous and average throughput across all active connections. Displays interactive speed curves and individual interface contribution ratios.

### Adaptive Precision Formatting
Dynamically scales bytes, kilobytes, megabytes, and gigabytes without artificial rounding discrepancies.

### Foreground Download Service
Provides continuous background operations with status bar progress indicators, real-time transfer rates, and interactive notification controls.

---

## Technical Specifications

- **Target Platform**: Android 7.0+ (API Level 24+)
- **UI Architecture**: Jetpack Compose with Material 3 Design
- **Networking**: Multi-interface OkHttp socket routing
- **Concurrency**: Kotlin Coroutines and StateFlow reactive pipelines
- **Build System**: Gradle with Kotlin DSL

---

## Downloads and Releases

Pre-built binaries for both production release and debugging are available on the GitHub Releases page:
- **Official Release APK (`Rivora-v1.0.0.apk`)**: Optimized binary, zip-aligned and signed for direct installation.
- **Debuggable APK (`Rivora-v1.0.0-debug.apk`)**: Development binary with full logging and debugging symbols enabled.

---

## Building from Source

### Prerequisites
- Android SDK (API 36)
- Java Development Kit (JDK) 17
- Android Studio Ladybug or later

### Build Debug and Release APKs
```bash
./gradlew assembleRelease assembleDebug
```

The output binaries are generated at:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

### Install via ADB
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## License

Rivora is open-source software licensed under the Apache License 2.0.
