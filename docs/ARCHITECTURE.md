# SS7 Guardian Architecture

This document describes the technical architecture of SS7 Guardian.

## Overview

SS7 Guardian is an Android application that detects potential SS7 network attacks through indirect monitoring of cellular network behavior.

```
┌──────────────────────────────────────────────────────────────┐
│                      User Interface (UI Layer)                   │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │  MainActivity  │  │   Adapters    │  │   Fragments   │ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     Service Layer                                │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │  GuardianService    │  │       AlertManager            │ │
│  │  (Foreground)       │  │  - Notifications              │ │
│  │  - Coordinates all  │  │  - Threat assessment          │ │
│  │    monitoring       │  └──────────────────────────────┘ │
│  └──────────────────────┘                                   │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     Monitor Layer                                │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │  CellMonitor   │  │ NetworkMonitor │  │   SmsMonitor   │ │
│  │  - Cell ID     │  │ - Network type │  │  - Class 0 SMS │ │
│  │  - LAC/TAC     │  │ - 2G detection │  │  - Type 0 SMS  │ │
│  │  - Signal dBm  │  │ - Downgrade    │  │  - WAP Push    │ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                       Data Layer                                 │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │     Repositories     │  │   Room Database (SQLite)     │ │
│  │  - CellTowerRepo     │  │  - cell_towers table         │ │
│  │  - SecurityEventRepo │  │  - security_events table     │ │
│  └──────────────────────┘  └──────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## Layer Details

### 1. UI Layer

**Responsibility:** Display data and handle user interactions.

**Components:**
- `MainActivity` - Main dashboard with status and controls
- `SecurityEventAdapter` - RecyclerView adapter for event list
- XML Layouts - Material Design 3 layouts

**Data Flow:**
- Observes `Flow` from repositories
- Updates UI reactively on data changes
- Sends user actions to service layer

### 2. Service Layer

**Responsibility:** Coordinate monitoring and manage background tasks.

**Components:**
- `GuardianService` - Foreground service that orchestrates monitoring
- `AlertManager` - Handles notifications and threat assessment

**Key Features:**
- Runs as foreground service (required for background location)
- Coordinates all three monitors
- Manages notification channels
- Auto-restarts if killed (START_STICKY)

### 3. Monitor Layer

**Responsibility:** Detect anomalies in cellular network behavior.

#### CellMonitor
```kotlin
class CellMonitor(context: Context) {
    // Tracks:
    // - Cell ID (CID)
    // - Location Area Code (LAC/TAC)
    // - Signal strength (dBm)
    // - MCC/MNC (country/operator codes)
    
    // Detects:
    // - Unknown/new cell towers
    // - Signal strength anomalies (>20 dBm jump)
    // - Rapid cell changes (>10 in 60s)
}
```

#### NetworkMonitor
```kotlin
class NetworkMonitor(context: Context) {
    // Monitors network generation:
    // 5G (NR) -> 4G (LTE) -> 3G (UMTS) -> 2G (GSM)
    
    // Detects:
    // - Forced 2G downgrades (attack indicator)
    // - Network type changes
}
```

#### SmsMonitor
```kotlin
class SmsMonitor(context: Context) {
    // Monitors incoming SMS for:
    // - Class 0 (Flash) SMS - display only, no storage
    // - Type 0 (Silent) SMS - no user indication
    // - WAP Push - potential config attacks
    // - Binary SMS - OTA commands
}
```

### 4. Data Layer

**Responsibility:** Persist and retrieve data.

**Components:**
- `AppDatabase` - Room database configuration
- `CellTowerDao` / `SecurityEventDao` - Data access objects
- `CellTowerRepository` / `SecurityEventRepository` - Repository pattern

**Database Schema:**

```sql
-- Cell tower history
CREATE TABLE cell_towers (
    id INTEGER PRIMARY KEY,
    cellId INTEGER NOT NULL,
    lac INTEGER NOT NULL,
    mcc INTEGER NOT NULL,
    mnc INTEGER NOT NULL,
    networkType TEXT NOT NULL,
    signalStrengthDbm INTEGER,
    latitude REAL,
    longitude REAL,
    firstSeen INTEGER,
    lastSeen INTEGER,
    timesObserved INTEGER DEFAULT 1,
    trustScore REAL DEFAULT 0.5,
    UNIQUE(cellId, lac, mcc, mnc)
);

-- Security events
CREATE TABLE security_events (
    id INTEGER PRIMARY KEY,
    eventType TEXT NOT NULL,
    threatLevel INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    cellId INTEGER,
    lac INTEGER,
    networkType TEXT,
    timestamp INTEGER NOT NULL,
    notified INTEGER DEFAULT 0,
    dismissed INTEGER DEFAULT 0
);
```

## Detection Algorithms

### IMSI Catcher Detection

```
                    ┌─────────────────┐
                    │  Cell Change   │
                    └────────┬────────┘
                             │
              ┌─────────────┴─────────────┐
              │                           │
              ▼                           ▼
     ┌───────────────┐           ┌──────────────┐
     │ Tower Known? │           │ Signal Jump │
     └───────┬───────┘           │  > 20 dBm?  │
             │                   └──────┬───────┘
      ┌──────┴──────┐                  │
      │            │           ┌──────┴──────┐
      ▼            ▼           │            │
    [YES]        [NO]         ▼            ▼
      │            │        [YES]        [NO]
      ▼            ▼          │            │
   Update      ┌────────┐    ▼            ▼
   lastSeen    │ ALERT! │  ┌────────┐    OK
               │Unknown │  │ ALERT! │
               │ Tower  │  │ Signal │
               └────────┘  │Anomaly │
                          └────────┘
```

### 2G Downgrade Detection

```
Normal Operation:
  5G → 5G → 4G → 4G → 4G → 4G  (✓ OK)
  
Attack Pattern:
  4G → 4G → [JAM] → 2G → 2G    (⚠️ ALERT!)
                    ↑
              Forced downgrade
              to insecure 2G
```

### Trust Score Algorithm

```kotlin
fun calculateTrustScore(
    timesObserved: Int,
    firstSeenTimestamp: Long
): Float {
    val baseScore = 0.5f
    
    // More observations = more trusted
    val observationBonus = (timesObserved / 10f * 0.1f)
        .coerceAtMost(0.3f)
    
    // Older towers = more trusted  
    val ageMonths = (now - firstSeen) / MONTH_MS
    val ageBonus = (ageMonths * 0.1f)
        .coerceAtMost(0.2f)
    
    return (baseScore + observationBonus + ageBonus)
        .coerceIn(0f, 1f)
}
```

## Threading Model

```
┌──────────────────────────────────────────────────┐
│                Main Thread (UI)                  │
│  - UI updates                                    │
│  - Flow collection with lifecycleScope          │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│           Dispatchers.Default                   │
│  - Cell scanning loop                           │
│  - Anomaly detection                            │
│  - GuardianService coroutine scope              │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│              Dispatchers.IO                      │
│  - Database operations (Room)                   │
│  - Repository suspend functions                 │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│         SingleThreadExecutor                    │
│  - TelephonyCallback registration               │
│  - Network monitoring                           │
└──────────────────────────────────────────────────┘
```

## Security Considerations

### Data Privacy
- All data stored locally (no cloud sync)
- Database not exported in backups (`allowBackup=false`)
- No PII collected beyond cell tower IDs

### Permissions
- Location: Required for cell tower info
- Phone state: Required for network monitoring
- SMS: Optional, for silent SMS detection
- All permissions explained to user

### Limitations
- Cannot block actual SS7 attacks
- Detection only, not protection
- Some attacks may not be detectable
- False positives possible in poor coverage areas

## Future Improvements

1. **Machine Learning** - Train model on known attack patterns
2. **Crowd-sourced Database** - Share anonymous tower data
3. **VoLTE Detection** - Monitor voice over LTE changes
4. **Root Features** - Enhanced detection with root access
5. **Wear OS** - Companion app for smartwatches
