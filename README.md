# 🛡️ SS7 Guardian

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-purple.svg)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-0.1.0-orange.svg)](https://github.com/nabz0r/SS7-Guardian/releases)

**Open-source Android app for detecting SS7/cellular network attacks through indirect monitoring.**

> ⚠️ **Note**: This is a detection tool, NOT a protection tool. It cannot block SS7 attacks or prevent IMSI capture.

## 🎯 The Problem

SS7 (Signaling System 7) vulnerabilities allow attackers to:
- 📍 **Track your location** via silent SMS pings
- 📞 **Intercept calls and SMS** through network manipulation
- 📶 **Force 2G downgrades** to exploit weaker encryption
- 🆔 **Clone your identity** via IMSI harvesting

SS7 Guardian uses publicly available Android APIs to detect anomalies suggesting an attack.

## 📸 Screenshots

<!-- TODO: Add screenshots when UI is finalized -->
| Dashboard | Alert | Settings |
|:---------:|:-----:|:--------:|
| Coming Soon | Coming Soon | Coming Soon |

## 🔍 Detection Capabilities

| Threat | Detection Method | Root Required |
|--------|-----------------|---------------|
| IMSI Catcher | Cell tower anomaly analysis | ❌ No |
| 2G Downgrade Attack | Network type monitoring | ❌ No |
| Silent SMS (Class 0) | Broadcast receiver | ❌ No |
| Location Tracking | Cell change frequency | ❌ No |
| Stingray Detection | Signal strength analysis | ❌ No |

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      SS7 Guardian                            │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │  Cell Monitor  │  │Network Monitor │  │  SMS Monitor   │ │
│  │ • Cell ID/LAC  │  │ • Network Type │  │ • Class 0 SMS  │ │
│  │ • Signal RSSI  │  │ • 2G Detection │  │ • WAP Push     │ │
│  │ • MCC/MNC      │  │ • Downgrade    │  │ • Binary SMS   │ │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘ │
│          └───────────────────┼───────────────────┘          │
│                              ▼                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │             Anomaly Detection Engine                   │  │
│  │  • Baseline Learning    • Pattern Matching            │  │
│  │  • Threat Scoring       • Historical Analysis         │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              ▼                               │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │ Alert System   │  │ Local Database │  │ Trust Scoring  │ │
│  │ • Notifications│  │ • Cell History │  │ • Tower Trust  │ │
│  │ • Threat Level │  │ • Events Log   │  │ • Risk Assess  │ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

For detailed architecture documentation, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## 🚀 Quick Start

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11 or newer
- Android SDK 34
- An Android device or emulator (API 26+)

### Build & Install

```bash
# Clone the repository
git clone https://github.com/nabz0r/SS7-Guardian.git
cd SS7-Guardian

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or simply open the project in Android Studio and click Run.

## 📁 Project Structure

```
SS7-Guardian/
├── app/
│   ├── src/main/
│   │   ├── java/com/ss7guardian/
│   │   │   ├── data/                    # Data layer
│   │   │   │   ├── dao/                 # Room DAOs
│   │   │   │   ├── entity/              # Database entities
│   │   │   │   ├── repository/          # Repository pattern
│   │   │   │   └── AppDatabase.kt       # Room database
│   │   │   ├── monitor/                 # Detection monitors
│   │   │   │   ├── CellMonitor.kt       # Cell tower monitoring
│   │   │   │   ├── NetworkMonitor.kt    # Network type monitoring
│   │   │   │   └── SmsMonitor.kt        # SMS monitoring
│   │   │   ├── receiver/                # Broadcast receivers
│   │   │   │   ├── BootReceiver.kt      # Auto-start on boot
│   │   │   │   └── SmsReceiver.kt       # SMS interception
│   │   │   ├── service/                 # Background services
│   │   │   │   ├── AlertManager.kt      # Notification handling
│   │   │   │   └── GuardianService.kt   # Main monitoring service
│   │   │   ├── ui/                      # User interface
│   │   │   │   ├── adapter/             # RecyclerView adapters
│   │   │   │   └── MainActivity.kt      # Main screen
│   │   │   ├── util/                    # Utility classes
│   │   │   │   ├── DateUtils.kt         # Date formatting
│   │   │   │   ├── NetworkUtils.kt      # Network type utils
│   │   │   │   └── PermissionUtils.kt   # Permission helpers
│   │   │   └── SS7GuardianApp.kt        # Application class
│   │   └── res/                         # Resources
│   │       ├── layout/                  # XML layouts
│   │       ├── drawable/                # Icons and shapes
│   │       └── values/                  # Colors, strings, themes
│   └── build.gradle.kts                 # App build config
├── docs/                                # Documentation
│   ├── ARCHITECTURE.md                  # Technical architecture
│   └── SECURITY.md                      # Security policy
├── gradle/                              # Gradle wrapper
├── build.gradle.kts                     # Root build config
├── settings.gradle.kts                  # Project settings
├── CONTRIBUTING.md                      # Contribution guide
├── LICENSE                              # MIT License
└── README.md                            # This file
```

## 📱 Features

### Cell Tower Monitoring
- 📡 Continuous monitoring of connected cell towers
- 📊 Historical database of known trusted towers
- 🔍 Detection of new/unknown towers
- 📈 Signal strength anomaly detection
- 🎯 Trust score algorithm for towers

### Network Security
- 📶 Real-time network type monitoring (5G/4G/3G/2G)
- ⚠️ Alerts on forced 2G downgrades
- 🔒 Option to auto-disable 2G (Android 12+)

### SMS Protection
- 💬 Detection of Class 0 (Flash) SMS
- 📲 WAP Push notification monitoring
- 🔢 Binary SMS detection

## 🔬 How Detection Works

### IMSI Catcher Detection
IMSI catchers (Stingrays) typically exhibit:
1. **Unusually strong signal** - Overpowering legitimate towers
2. **New Cell ID** - Not in historical database
3. **Rapid handoffs** - Forcing reconnection
4. **Missing neighbors** - Real towers broadcast neighbor lists

### 2G Downgrade Detection
```
Normal: LTE (4G) ────────────────────────► LTE (4G)
Attack: LTE (4G) ──► [Jamming] ──► GSM (2G) ⚠️ ALERT
```

2G uses A5/1 encryption which can be cracked in real-time, enabling call/SMS interception.

### Trust Score Algorithm
```kotlin
Trust Score = Base(0.5) + Observation Bonus + Age Bonus
// More observations + older tower = higher trust
```

## 📊 Threat Levels

| Level | Color | Meaning | Action |
|-------|-------|---------|--------|
| 0 | 🟢 Green | Safe - No anomalies | None needed |
| 1 | 🟡 Yellow | Low - Minor anomaly | Monitor |
| 2 | 🟠 Orange | Medium - Multiple indicators | Investigate |
| 3 | 🔴 Red | High - Strong attack indicators | Take precautions |
| 4 | ⚫ Black | Critical - Active attack likely | Avoid sensitive comms |

## 🔐 Privacy

- 🏠 **No data leaves your device** by default
- 🔒 All processing happens locally
- 📂 SQLite database stored in private app storage
- 🚫 No analytics or tracking
- 👁️ Open source - verify our claims

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) before submitting PRs.

Areas we need help:
- 🧪 Testing on different devices
- 🌍 Translations
- 📱 UI/UX improvements
- 🔬 Detection algorithm refinement

## ⚠️ Disclaimer

SS7 Guardian provides **detection**, not **protection**. It cannot:
- Block SS7 attacks
- Prevent IMSI capture
- Encrypt your communications
- Guarantee detection of all attacks

Always use additional security measures for sensitive communications.

## 📚 Resources

- [EFF: IMSI Catchers Explained](https://www.eff.org/wp/gotta-catch-em-all-understanding-how-imsi-catchers-exploit-cell-networks)
- [SRLabs: SnoopSnitch](https://opensource.srlabs.de/projects/snoopsnitch)
- [3GPP TS 23.040](https://www.3gpp.org/ftp/Specs/archive/23_series/23.040/) - SMS Protocol Specification
- [Project Architecture](docs/ARCHITECTURE.md)
- [Security Policy](docs/SECURITY.md)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <b>Made with ❤️ for privacy advocates</b><br>
  <a href="https://github.com/nabz0r/SS7-Guardian/issues">Report Bug</a> •
  <a href="https://github.com/nabz0r/SS7-Guardian/issues">Request Feature</a>
</p>
