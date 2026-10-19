# Rivora

Rivora is a multi-network download manager and the Android client for Plexoo.

It accelerates downloads by splitting files into parallel chunks, distributing streams across multiple concurrent network interfaces (Wi-Fi and Cellular simultaneously), and streaming directly to Android storage with complete resume capability.

---

## Architecture and Core Capabilities

### Multi-Network Stream Bonding
Rivora leverages Android connectivity APIs to bind sockets directly to underlying network interfaces. By concurrently dispatching HTTP range requests over both Wi-Fi and Cellular (4G/5G) connections, bandwidth is combined to maximize transfer throughput.

### Liquid Chunk Progress Matrix
A custom Canvas component provides real-time visual feedback of chunk distribution and download progress. Active chunks fill progressively from bottom to top, reflecting true byte-level completion rates.

### Resumable Chunked Storage Engine
Downloads are written directly to pre-allocated target storage using random-access seek-and-write operations. Each completed chunk updates persistent state, ensuring transfers can be paused, resumed, or recovered across application restarts without file corruption.

### Interface Throughput Telemetry
Real-time telemetry computes combined and per-interface transfer rates. A dedicated throughput monitor visualizes historical speed curves and interface contribution ratios.

### Adaptive Precision Formatting
File sizes and transfer speeds scale dynamically across bytes, kilobytes, megabytes, and gigabytes, ensuring accurate values without artificial rounding errors.

### Background Transfer Service
A foreground Android service with status bar notifications provides continuous background downloads, displaying active progress, transfer speeds, and chunk completion metrics.

---

## Technical Specifications

- **Target Architecture**: Modern Android (API 24+)
- **UI Framework**: Jetpack Compose with Material 3 Design
- **Navigation**: Jetpack Navigation 3
- **Networking**: OkHttp multi-interface socket routing
- **Asynchronous Processing**: Kotlin Coroutines and StateFlow reactive state management
- **Build Configuration**: Gradle with Kotlin DSL

---

## Building and Verification

### Requirements
- Android SDK 36
- Java Development Kit (JDK) 17
- Android Studio Ladybug or later

### Compiling Debug Build
```bash
./gradlew assembleDebug
```

The output artifact will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Installing via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## License

Rivora is open-source software licensed under the Apache License 2.0.
