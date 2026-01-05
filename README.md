# 🛡️ SS7 Guardian

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Open-source Android app for detecting SS7/cellular network attacks through indirect monitoring.**

## 🎯 The Problem

SS7 vulnerabilities allow attackers to:
- **Track your location** via silent SMS pings
- **Intercept calls and SMS** through network manipulation
- **Force 2G downgrades** to exploit weaker encryption
- **Clone your identity** via IMSI harvesting

SS7 Guardian uses publicly available Android APIs to detect anomalies suggesting an attack.

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
│  │ Alert System   │  │ Local Database │  │ Optional API   │ │
│  │ • Notifications│  │ • Cell History │  │ • HLR Lookup   │ │
│  │ • Threat Level │  │ • Events Log   │  │ • Reporting    │ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

```bash
git clone https://github.com/nabz0r/SS7-Guardian.git
cd SS7-Guardian
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Features

### Cell Tower Monitoring
- Continuous monitoring of connected cell towers
- Historical database of known good towers
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

## 🔬 How Detection Works

### IMSI Catcher Detection
IMSI catchers typically exhibit:
1. **Unusually strong signal** - Overpowering legitimate towers
2. **New Cell ID** - Not in historical database
3. **Rapid handoffs** - Forcing reconnection
4. **Missing neighbors** - Real towers broadcast neighbor lists

### 2G Downgrade Detection
```
Normal: LTE (4G) ────────────────────────► LTE (4G)
Attack: LTE (4G) ──► [Jamming] ──► GSM (2G) ⚠️ ALERT
```

## 📊 Threat Levels

| Level | Color | Meaning |
|-------|-------|--------|
| 0 | 🟢 Green | Safe - No anomalies |
| 1 | 🟡 Yellow | Low - Minor anomaly |
| 2 | 🟠 Orange | Medium - Multiple indicators |
| 3 | 🔴 Red | High - Strong attack indicators |
| 4 | ⚫ Black | Critical - Active attack likely |

## 🔐 Privacy

- **No data leaves your device** by default
- All processing happens locally
- Open source - verify our claims

## ⚠️ Disclaimer

SS7 Guardian provides **detection**, not **protection**. It cannot block SS7 attacks or prevent IMSI capture.

## 📚 Resources

- [EFF: IMSI Catchers Explained](https://www.eff.org/wp/gotta-catch-em-all-understanding-how-imsi-catchers-exploit-cell-networks)
- [SRLabs: SnoopSnitch](https://opensource.srlabs.de/projects/snoopsnitch)

## 📄 License

MIT License

---

**Made with ❤️ for privacy advocates**