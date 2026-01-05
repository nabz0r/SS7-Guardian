package com.ss7guardian.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * CellTowerEntity - Cell Tower Database Record
 * =============================================
 * 
 * Represents a single cell tower observation in the local database.
 * Used to build a trusted tower database for anomaly detection.
 * 
 * Cell Tower Identification:
 * - cellId (CID): Cell ID within the location area (0-65535 for GSM, larger for LTE)
 * - lac (LAC/TAC): Location Area Code (GSM/WCDMA) or Tracking Area Code (LTE)
 * - mcc: Mobile Country Code (e.g., 228 for Switzerland, 270 for Luxembourg)
 * - mnc: Mobile Network Code (e.g., 01 for Swisscom, 01 for POST)
 * 
 * Trust Score Algorithm:
 * - New towers start at 0.5 (neutral)
 * - Score increases with observation frequency and duration
 * - Score decreases for towers with anomalous characteristics
 * - Towers with score < 0.3 trigger warnings
 * 
 * Database Indices:
 * - Unique index on (cellId, lac, mcc, mnc) for fast lookups
 * - lastSeen for time-based queries
 * 
 * Storage Considerations:
 * - Expected ~1000-5000 towers for typical user
 * - Each record ~100 bytes
 * - Consider cleanup of old entries (not seen in 6+ months)
 * 
 * @property id Auto-generated primary key
 * @property cellId Cell identifier (CID for GSM, CI for LTE)
 * @property lac Location Area Code (LAC) or Tracking Area Code (TAC)
 * @property mcc Mobile Country Code
 * @property mnc Mobile Network Code
 * @property networkType Network technology (GSM, WCDMA, LTE, NR)
 * @property signalStrengthDbm Signal strength in dBm (typically -50 to -120)
 * @property latitude GPS latitude where tower was first observed
 * @property longitude GPS longitude where tower was first observed
 * @property firstSeen Timestamp of first observation
 * @property lastSeen Timestamp of most recent observation
 * @property timesObserved Number of times this tower has been seen
 * @property trustScore Calculated trust score (0.0 = untrusted, 1.0 = fully trusted)
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
@Entity(
    tableName = "cell_towers",
    indices = [
        // Unique composite index for tower identification
        // This combination uniquely identifies a cell tower worldwide
        Index(
            value = ["cellId", "lac", "mcc", "mnc"],
            unique = true
        ),
        // Index for time-based queries (recently seen towers)
        Index(value = ["lastSeen"])
    ]
)
data class CellTowerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // ========================================
    // Cell Tower Identification (CGI)
    // ========================================
    
    /**
     * Cell ID (CID/CI)
     * - GSM: 0-65535 (16 bit)
     * - WCDMA: 0-268435455 (28 bit)
     * - LTE: 0-268435455 (28 bit)
     * - NR: Extended range
     */
    val cellId: Int,
    
    /**
     * Location Area Code (LAC) or Tracking Area Code (TAC)
     * - GSM/WCDMA: LAC (0-65535)
     * - LTE/NR: TAC (0-65535)
     */
    val lac: Int,
    
    /**
     * Mobile Country Code
     * 3 digits, e.g., 228 (CH), 270 (LU), 262 (DE)
     * Full list: https://mcc-mnc.com/
     */
    val mcc: Int,
    
    /**
     * Mobile Network Code
     * 2-3 digits, identifies operator within country
     */
    val mnc: Int,
    
    // ========================================
    // Technical Characteristics
    // ========================================
    
    /**
     * Network technology type
     * Values: "GSM", "WCDMA", "LTE", "NR" (5G)
     */
    val networkType: String,
    
    /**
     * Signal strength in dBm
     * - Excellent: > -65 dBm
     * - Good: -65 to -75 dBm
     * - Fair: -75 to -85 dBm
     * - Poor: -85 to -95 dBm
     * - Very Poor: < -95 dBm
     * 
     * IMSI catchers often have unusually strong signals (> -50 dBm)
     */
    val signalStrengthDbm: Int,
    
    // ========================================
    // Geographic Information
    // ========================================
    
    /**
     * Approximate latitude where tower was observed
     * Note: This is the device location, not the actual tower location
     */
    val latitude: Double,
    
    /**
     * Approximate longitude where tower was observed
     */
    val longitude: Double,
    
    // ========================================
    // Observation Metadata
    // ========================================
    
    /**
     * When this tower was first observed
     * Useful for identifying newly appeared towers
     */
    val firstSeen: Long = System.currentTimeMillis(),
    
    /**
     * When this tower was most recently observed
     * Updated on each observation
     */
    val lastSeen: Long = System.currentTimeMillis(),
    
    /**
     * How many times this tower has been observed
     * Higher counts indicate more trustworthy towers
     */
    val timesObserved: Int = 1,
    
    /**
     * Calculated trust score (0.0 to 1.0)
     * 
     * Factors:
     * - Observation frequency (more = higher)
     * - Observation duration (longer = higher)
     * - Network consistency (same type = higher)
     * - Signal consistency (stable = higher)
     * 
     * Thresholds:
     * - < 0.3: Suspicious, may trigger warning
     * - 0.3-0.7: Neutral, needs more data
     * - > 0.7: Trusted tower
     */
    val trustScore: Float = 0.5f
) {
    /**
     * Get the full Cell Global Identity (CGI) as string
     * Format: MCC-MNC-LAC-CID
     */
    fun getCGI(): String = "$mcc-$mnc-$lac-$cellId"
    
    /**
     * Check if this tower is considered trusted
     */
    fun isTrusted(): Boolean = trustScore >= 0.7f
    
    /**
     * Check if this tower is suspicious
     */
    fun isSuspicious(): Boolean = trustScore < 0.3f
}