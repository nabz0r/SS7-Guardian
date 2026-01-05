package com.ss7guardian.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import com.ss7guardian.data.entity.CellTowerEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CellMonitor(private val context: Context) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val _cellHistory = MutableStateFlow<List<CellObservation>>(emptyList())
    val cellHistory: StateFlow<List<CellObservation>> = _cellHistory.asStateFlow()
    private var lastLocation: Location? = null
    private var callback: CellMonitorCallback? = null

    data class CellObservation(
        val cellId: Int, val lac: Int, val mcc: Int, val mnc: Int,
        val networkType: String, val signalStrength: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    interface CellMonitorCallback {
        fun onCellChanged(oldCell: CellObservation?, newCell: CellObservation)
        fun onUnknownTowerDetected(cell: CellObservation)
        fun onSignalAnomaly(cell: CellObservation, prev: Int, curr: Int)
        fun onRapidCellChanges(count: Int, windowMs: Long)
    }

    fun setCallback(cb: CellMonitorCallback) { callback = cb }
    fun updateLocation(loc: Location) { lastLocation = loc }

    fun getCurrentCellInfo(): List<CellInfo>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        return try { telephonyManager.allCellInfo } catch (e: SecurityException) { null }
    }

    fun parseCellInfo(info: CellInfo): CellObservation? = when (info) {
        is CellInfoGsm -> info.cellIdentity.let { id ->
            CellObservation(id.cid, id.lac, id.mcc, id.mnc, "GSM", info.cellSignalStrength.dbm)
        }
        is CellInfoWcdma -> info.cellIdentity.let { id ->
            CellObservation(id.cid, id.lac, id.mcc, id.mnc, "WCDMA", info.cellSignalStrength.dbm)
        }
        is CellInfoLte -> info.cellIdentity.let { id ->
            CellObservation(id.ci, id.tac, id.mcc, id.mnc, "LTE", info.cellSignalStrength.dbm)
        }
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
            (info.cellIdentity as? CellIdentityNr)?.let { id ->
                (info.cellSignalStrength as? CellSignalStrengthNr)?.let { sig ->
                    CellObservation(id.nci.toInt(), id.tac, id.mccString?.toIntOrNull() ?: 0,
                        id.mncString?.toIntOrNull() ?: 0, "NR", sig.dbm)
                }
            }
        } else null
    }

    fun toEntity(obs: CellObservation, loc: Location?) = CellTowerEntity(
        cellId = obs.cellId, lac = obs.lac, mcc = obs.mcc, mnc = obs.mnc,
        networkType = obs.networkType, signalStrengthDbm = obs.signalStrength,
        latitude = loc?.latitude ?: 0.0, longitude = loc?.longitude ?: 0.0
    )

    fun analyzeChange(prev: CellObservation?, curr: CellObservation, isKnown: Boolean, threshold: Int = 20): List<String> {
        val anomalies = mutableListOf<String>()
        if (!isKnown) { anomalies.add("UNKNOWN_TOWER"); callback?.onUnknownTowerDetected(curr) }
        if (prev != null && kotlin.math.abs(curr.signalStrength - prev.signalStrength) > threshold) {
            anomalies.add("SIGNAL_ANOMALY")
            callback?.onSignalAnomaly(curr, prev.signalStrength, curr.signalStrength)
        }
        val recent = _cellHistory.value.count { it.timestamp > System.currentTimeMillis() - 60000 }
        if (recent > 10) { anomalies.add("RAPID_CHANGES"); callback?.onRapidCellChanges(recent, 60000) }
        return anomalies
    }

    fun recordObservation(obs: CellObservation) {
        _cellHistory.value = listOf(obs) + _cellHistory.value.take(999)
    }
}