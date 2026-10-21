<p align="center">
  <img src="assets/logo.png" alt="Rivora Logo" width="112" height="112" />
</p>

<h1 align="center">Rivora</h1>

<p align="center">
  Multi-network download manager for Android and mobile client for Plexoo.
</p>

---

## Overview

Rivora accelerates file transfers by partitioning downloads into parallel segments, routing concurrent streams over both Wi-Fi and Cellular interfaces simultaneously, and writing directly to persistent Android storage with byte-accurate verification and resume capability.

---

## Architecture and Key Capabilities

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

## Building and Installation

### Prerequisites
- Android SDK (API 36)
- Java Development Kit (JDK) 17
- Android Studio Ladybug or later

### Build Debug APK
```bash
./gradlew assembleDebug
```

The output binary is located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install to Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## License

Rivora is open-source software licensed under the Apache License 2.0.
