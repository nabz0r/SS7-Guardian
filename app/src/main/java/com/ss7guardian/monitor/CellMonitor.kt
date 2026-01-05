package com.ss7guardian.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.core.content.ContextCompat
import com.ss7guardian.data.entity.CellTowerEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CellMonitor - Cell Tower Monitoring and Analysis
 * ==================================================
 * 
 * Monitors cellular network connections and detects anomalies that
 * may indicate IMSI catcher (Stingray) attacks.
 * 
 * Detection Methods:
 * 1. Unknown Tower: Connected to tower not in trusted database
 * 2. Signal Anomaly: Unusual signal strength changes
 * 3. Rapid Changes: Too many cell handoffs in short period
 * 
 * @param context Application context for system services
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class CellMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "CellMonitor"
        private const val MAX_HISTORY_SIZE = 1000
        private const val SIGNAL_ANOMALY_THRESHOLD_DB = 20
        private const val RAPID_CHANGE_THRESHOLD = 10
        private const val RAPID_CHANGE_WINDOW_MS = 60_000L
    }
    
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    // Observable state
    private val _cellHistory = MutableStateFlow<List<CellObservation>>(emptyList())
    val cellHistory: StateFlow<List<CellObservation>> = _cellHistory.asStateFlow()
    
    private var lastLocation: Location? = null
    private var callback: CellMonitorCallback? = null
    
    /**
     * Single cell tower observation
     */
    data class CellObservation(
        val cellId: Int,
        val lac: Int,
        val mcc: Int,
        val mnc: Int,
        val networkType: String,
        val signalStrength: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun getCGI(): String = "$mcc-$mnc-$lac-$cellId"
        fun isSameTower(other: CellObservation) =
            cellId == other.cellId && lac == other.lac && mcc == other.mcc && mnc == other.mnc
    }
    
    /**
     * Callback interface for cell events
     */
    interface CellMonitorCallback {
        fun onCellChanged(oldCell: CellObservation?, newCell: CellObservation)
        fun onUnknownTowerDetected(cell: CellObservation)
        fun onSignalAnomaly(cell: CellObservation, prev: Int, curr: Int)
        fun onRapidCellChanges(count: Int, windowMs: Long)
    }
    
    fun setCallback(cb: CellMonitorCallback) { callback = cb }
    fun updateLocation(loc: Location) { lastLocation = loc }
    
    /**
     * Get current cell info from TelephonyManager
     * Requires ACCESS_FINE_LOCATION permission
     */
    fun getCurrentCellInfo(): List<CellInfo>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing location permission")
            return null
        }
        return try {
            telephonyManager.allCellInfo
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting cell info", e)
            null
        }
    }
    
    /**
     * Parse CellInfo into our observation format
     * Handles GSM, WCDMA, LTE, and NR (5G) cell types
     */
    fun parseCellInfo(info: CellInfo): CellObservation? = when (info) {
        // GSM (2G)
        is CellInfoGsm -> info.cellIdentity.let { id ->
            if (id.cid != Int.MAX_VALUE && id.lac != Int.MAX_VALUE) {
                CellObservation(id.cid, id.lac, id.mcc, id.mnc, "GSM", info.cellSignalStrength.dbm)
            } else null
        }
        // WCDMA (3G)
        is CellInfoWcdma -> info.cellIdentity.let { id ->
            if (id.cid != Int.MAX_VALUE && id.lac != Int.MAX_VALUE) {
                CellObservation(id.cid, id.lac, id.mcc, id.mnc, "WCDMA", info.cellSignalStrength.dbm)
            } else null
        }
        // LTE (4G)
        is CellInfoLte -> info.cellIdentity.let { id ->
            if (id.ci != Int.MAX_VALUE && id.tac != Int.MAX_VALUE) {
                CellObservation(id.ci, id.tac, id.mcc, id.mnc, "LTE", info.cellSignalStrength.dbm)
            } else null
        }
        // NR (5G) - requires Android Q+
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
            (info.cellIdentity as? CellIdentityNr)?.let { id ->
                (info.cellSignalStrength as? CellSignalStrengthNr)?.let { sig ->
                    val mcc = id.mccString?.toIntOrNull() ?: 0
                    val mnc = id.mncString?.toIntOrNull() ?: 0
                    if (mcc > 0 && mnc > 0) {
                        CellObservation(id.nci.toInt(), id.tac, mcc, mnc, "NR", sig.dbm)
                    } else null
                }
            }
        } else null
    }
    
    /**
     * Convert observation to database entity
     */
    fun toEntity(obs: CellObservation, loc: Location?) = CellTowerEntity(
        cellId = obs.cellId,
        lac = obs.lac,
        mcc = obs.mcc,
        mnc = obs.mnc,
        networkType = obs.networkType,
        signalStrengthDbm = obs.signalStrength,
        latitude = loc?.latitude ?: 0.0,
        longitude = loc?.longitude ?: 0.0
    )
    
    /**
     * Analyze cell change for anomalies
     * @return List of detected anomaly types
     */
    fun analyzeChange(
        prev: CellObservation?,
        curr: CellObservation,
        isKnown: Boolean,
        threshold: Int = SIGNAL_ANOMALY_THRESHOLD_DB
    ): List<String> {
        val anomalies = mutableListOf<String>()
        
        // Check: Unknown tower
        if (!isKnown) {
            anomalies.add("UNKNOWN_TOWER")
            callback?.onUnknownTowerDetected(curr)
            Log.w(TAG, "Unknown tower detected: ${curr.getCGI()}")
        }
        
        // Check: Signal anomaly
        if (prev != null && kotlin.math.abs(curr.signalStrength - prev.signalStrength) > threshold) {
            anomalies.add("SIGNAL_ANOMALY")
            callback?.onSignalAnomaly(curr, prev.signalStrength, curr.signalStrength)
            Log.w(TAG, "Signal anomaly: ${prev.signalStrength}dBm -> ${curr.signalStrength}dBm")
        }
        
        // Check: Rapid cell changes
        val recentChanges = _cellHistory.value.count {
            it.timestamp > System.currentTimeMillis() - RAPID_CHANGE_WINDOW_MS
        }
        if (recentChanges > RAPID_CHANGE_THRESHOLD) {
            anomalies.add("RAPID_CHANGES")
            callback?.onRapidCellChanges(recentChanges, RAPID_CHANGE_WINDOW_MS)
            Log.w(TAG, "Rapid cell changes: $recentChanges in ${RAPID_CHANGE_WINDOW_MS/1000}s")
        }
        
        return anomalies
    }
    
    /**
     * Record an observation in history
     */
    fun recordObservation(obs: CellObservation) {
        _cellHistory.value = listOf(obs) + _cellHistory.value.take(MAX_HISTORY_SIZE - 1)
        Log.d(TAG, "Recorded: ${obs.getCGI()} @ ${obs.signalStrength}dBm")
    }
}