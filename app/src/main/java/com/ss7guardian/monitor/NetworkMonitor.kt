package com.ss7guardian.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

class NetworkMonitor(private val context: Context) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val _currentNetworkType = MutableStateFlow(NetworkType.UNKNOWN)
    val currentNetworkType: StateFlow<NetworkType> = _currentNetworkType.asStateFlow()
    private val _networkHistory = MutableStateFlow<List<NetworkChange>>(emptyList())
    val networkHistory: StateFlow<List<NetworkChange>> = _networkHistory.asStateFlow()
    private var callback: NetworkMonitorCallback? = null
    private var telephonyCallback: Any? = null

    enum class NetworkType(val generation: Int, val displayName: String) {
        UNKNOWN(0, "Unknown"), GSM(2, "2G GSM"), GPRS(2, "2G GPRS"), EDGE(2, "2G EDGE"),
        CDMA(2, "2G CDMA"), UMTS(3, "3G UMTS"), HSDPA(3, "3G HSDPA"), HSPA(3, "3G HSPA"),
        HSPA_PLUS(3, "3G HSPA+"), LTE(4, "4G LTE"), LTE_CA(4, "4G LTE-CA"),
        NR(5, "5G NR"), NR_NSA(5, "5G NSA")
    }

    data class NetworkChange(
        val fromType: NetworkType, val toType: NetworkType,
        val timestamp: Long = System.currentTimeMillis(),
        val isDowngrade: Boolean = fromType.generation > toType.generation
    )

    interface NetworkMonitorCallback {
        fun onNetworkTypeChanged(oldType: NetworkType, newType: NetworkType)
        fun on2GDowngradeDetected(fromType: NetworkType)
    }

    fun setCallback(cb: NetworkMonitorCallback) { callback = cb }

    fun startMonitoring(executor: Executor) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) startModernMonitoring(executor)
        _currentNetworkType.value = getCurrentNetworkType()
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null)
            telephonyManager.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startModernMonitoring(executor: Executor) {
        val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                handleNetworkChange(mapDisplayInfoToNetworkType(info))
            }
        }
        telephonyCallback = cb
        try { telephonyManager.registerTelephonyCallback(executor, cb) }
        catch (e: SecurityException) { Log.e(TAG, "Error", e) }
    }

    private fun handleNetworkChange(newType: NetworkType) {
        val oldType = _currentNetworkType.value
        if (oldType == newType) return
        Log.d(TAG, "Network: ${oldType.displayName} -> ${newType.displayName}")
        val change = NetworkChange(oldType, newType)
        _networkHistory.value = listOf(change) + _networkHistory.value.take(99)
        _currentNetworkType.value = newType
        callback?.onNetworkTypeChanged(oldType, newType)
        if (change.isDowngrade && newType.generation == 2) {
            Log.w(TAG, "2G DOWNGRADE DETECTED")
            callback?.on2GDowngradeDetected(oldType)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun mapDisplayInfoToNetworkType(info: TelephonyDisplayInfo) = when (info.overrideNetworkType) {
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> NetworkType.NR_NSA
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> NetworkType.NR
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> NetworkType.LTE_CA
        else -> mapLegacyNetworkType(info.networkType)
    }

    private fun mapLegacyNetworkType(type: Int) = when (type) {
        TelephonyManager.NETWORK_TYPE_GSM -> NetworkType.GSM
        TelephonyManager.NETWORK_TYPE_GPRS -> NetworkType.GPRS
        TelephonyManager.NETWORK_TYPE_EDGE -> NetworkType.EDGE
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> NetworkType.CDMA
        TelephonyManager.NETWORK_TYPE_UMTS -> NetworkType.UMTS
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> NetworkType.HSDPA
        TelephonyManager.NETWORK_TYPE_HSPA -> NetworkType.HSPA
        TelephonyManager.NETWORK_TYPE_HSPAP -> NetworkType.HSPA_PLUS
        TelephonyManager.NETWORK_TYPE_LTE -> NetworkType.LTE
        TelephonyManager.NETWORK_TYPE_NR -> NetworkType.NR
        else -> NetworkType.UNKNOWN
    }

    @Suppress("DEPRECATION")
    private fun getCurrentNetworkType(): NetworkType {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return NetworkType.UNKNOWN
        return try { mapLegacyNetworkType(telephonyManager.networkType) }
        catch (e: SecurityException) { NetworkType.UNKNOWN }
    }

    companion object { private const val TAG = "NetworkMonitor" }
}