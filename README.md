# 🛡️ SS7 Guardian

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

**Open-source Android application for detecting SS7/cellular network attacks through indirect monitoring.**

SS7 Guardian helps protect your privacy by detecting suspicious cellular network behavior that may indicate IMSI catching, silent SMS tracking, or forced network downgrades.

## 🎯 The Problem

SS7 (Signaling System 7) vulnerabilities allow attackers to:
- **Track your location** via silent SMS pings
- **Intercept calls and SMS** through network manipulation
- **Force 2G downgrades** to exploit weaker encryption
- **Clone your identity** via IMSI harvesting

Traditional detection requires root access and specific chipsets. **SS7 Guardian takes a different approach** - using publicly available Android APIs to detect anomalies that suggest an attack is occurring.

## 🔍 Detection Capabilities

| Threat | Detection Method | Root Required |
|--------|-----------------|---------------|
| IMSI Catcher | Cell tower anomaly analysis | ❌ No |
| 2G Downgrade Attack | Network type monitoring | ❌ No |
| Silent SMS (Class 0) | Broadcast receiver | ❌ No |
| Silent SMS (Type 0) | Log monitoring | ⚠️ ADB only |
| Location Tracking | Cell change frequency | ❌ No |
| Stingray Detection | Signal strength analysis | ❌ No |

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      SS7 Guardian                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Cell Monitor   │  │ Network Monitor │  │   SMS Monitor   │ │
│  │                 │  │                 │  │                 │ │
│  │ • Cell ID/LAC   │  │ • Network Type  │  │ • Class 0 SMS   │ │
│  │ • Signal RSSI   │  │ • 2G Detection  │  │ • WAP Push      │ │
│  │ • MCC/MNC       │  │ • Downgrade     │  │ • Binary SMS    │ │
│  │ • Neighbors     │  │ • Encryption    │  │                 │ │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘ │
│           │                    │                    │          │
│           └────────────────────┼────────────────────┘          │
│                                ▼                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Anomaly Detection Engine                    │  │
│  │                                                          │  │
│  │  • Baseline Learning    • Pattern Matching              │  │
│  │  • Threat Scoring       • Historical Analysis           │  │
│  │  • Geofencing           • Time-based Correlation        │  │
│  └────────────────────────────┬────────────────────────────┘  │
│                               │                                │
│           ┌───────────────────┼───────────────────┐           │
│           ▼                   ▼                   ▼           │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐  │
│  │  Alert System   │ │  Local Database │ │  Optional API   │  │
│  │                 │ │                 │ │                 │  │
│  │ • Notifications │ │ • Cell History  │ │ • HLR Lookup    │  │
│  │ • Sound/Vibrate │ │ • Events Log    │ │ • Reporting     │  │
│  │ • Threat Level  │ │ • Baselines     │ │                 │  │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Requirements
- Android 8.0 (API 26) or higher
- Location permission (for cell tower info)
- Phone state permission

### Build from Source

```bash
git clone https://github.com/nabz0r/SS7-Guardian.git
cd SS7-Guardian
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Features

### Cell Tower Monitoring
- Continuous monitoring of connected cell towers
- Historical database of "known good" towers per location
- Detection of new/unknown towers
- Signal strength anomaly detection

### Network Security
- Real-time network type monitoring (5G/4G/3G/2G)
- Alerts on forced 2G downgrades
- Option to auto-disable 2G (Android 12+)

### SMS Protection
- Detection of Class 0 (Flash) SMS
- WAP Push notification monitoring
- Binary SMS detection
- Logging of suspicious message patterns

## 🔬 How Detection Works

### IMSI Catcher Detection

IMSI catchers (Stingrays) typically exhibit these characteristics:

1. **Unusually strong signal** - They overpower legitimate towers
2. **New Cell ID** - Not in our historical database for this location
3. **Rapid handoffs** - Forcing your phone to reconnect repeatedly
4. **Missing neighbors** - Real towers broadcast neighbor lists

### 2G Downgrade Detection

```
Normal: LTE (4G) ──────────────────────────────────► LTE (4G)

Attack: LTE (4G) ──► [Jamming/Reject] ──► GSM (2G) ⚠️ ALERT
```

When your phone drops to 2G despite good LTE signal, it may indicate an active attack attempting to exploit 2G's weak encryption.

## 📊 Threat Levels

| Level | Color | Meaning |
|-------|-------|---------|
| 0 | 🟢 Green | Normal - No anomalies detected |
| 1 | 🟡 Yellow | Low - Minor anomaly, possibly benign |
| 2 | 🟠 Orange | Medium - Multiple indicators |
| 3 | 🔴 Red | High - Strong attack indicators |
| 4 | ⚫ Black | Critical - Active attack very likely |

## 🔐 Privacy

SS7 Guardian is designed with privacy in mind:

- **No data leaves your device** by default
- All processing happens locally
- Optional community features are opt-in only
- Open source - verify our claims yourself

## 🤝 Contributing

Contributions are welcome! Areas where help is needed:
- iOS port
- Additional detection heuristics
- UI/UX improvements
- Translations
- Testing on various devices

## 📚 Resources

### SS7 Security Research
- [EFF: Gotta Catch 'Em All - IMSI Catchers](https://www.eff.org/wp/gotta-catch-em-all-understanding-how-imsi-catchers-exploit-cell-networks)
- [SRLabs: SnoopSnitch](https://opensource.srlabs.de/projects/snoopsnitch)
- [31C3: SS7 Locate Track Manipulate](https://media.ccc.de/v/31c3_-_6249_-_en_-_saal_1_-_201412271715_-_ss7_locate_track_manipulate_-_tobias_engel)

## ⚠️ Disclaimer

SS7 Guardian provides **detection**, not **protection**. It cannot:
- Block SS7 attacks (these happen at network level)
- Prevent IMSI capture (requires baseband control)
- Guarantee detection of all attacks

For maximum security, use end-to-end encrypted communications and disable 2G when possible.

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.

---

**Made with ❤️ for privacy advocates everywhere**

*If this project helps you, consider starring ⭐ the repo!*
