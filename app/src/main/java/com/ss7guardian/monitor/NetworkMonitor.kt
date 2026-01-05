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

/**
 * NetworkMonitor - Network Type and Downgrade Detection
 * =======================================================
 * 
 * Monitors the cellular network type and detects forced downgrades
 * from 4G/5G to 2G, which is a common attack vector.
 * 
 * Why 2G Downgrades Are Dangerous:
 * - 2G (GSM) uses A5/1 encryption which can be cracked in real-time
 * - IMSI catchers often jam 4G/5G to force 2G connections
 * - Once on 2G, calls and SMS can be intercepted
 * 
 * Detection Method:
 * - Uses TelephonyCallback (Android 12+) or PhoneStateListener (legacy)
 * - Monitors TelephonyDisplayInfo for network override types
 * - Detects transitions from higher to lower network generations
 * 
 * Network Generations:
 * - 5G: NR, NR_NSA (most secure, SA mode)
 * - 4G: LTE, LTE_CA (secure)
 * - 3G: UMTS, HSPA, HSPA+ (moderately secure)
 * - 2G: GSM, GPRS, EDGE (INSECURE - can be intercepted)
 * 
 * @param context Application context for system services
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class NetworkMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        private const val MAX_HISTORY_SIZE = 100
    }
    
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    // Observable state
    private val _currentNetworkType = MutableStateFlow(NetworkType.UNKNOWN)
    val currentNetworkType: StateFlow<NetworkType> = _currentNetworkType.asStateFlow()
    
    private val _networkHistory = MutableStateFlow<List<NetworkChange>>(emptyList())
    val networkHistory: StateFlow<List<NetworkChange>> = _networkHistory.asStateFlow()
    
    private var callback: NetworkMonitorCallback? = null
    private var telephonyCallback: Any? = null
    
    /**
     * Network types with generation and display names
     */
    enum class NetworkType(val generation: Int, val displayName: String) {
        UNKNOWN(0, "Unknown"),
        // 2G - INSECURE
        GSM(2, "2G GSM"),
        GPRS(2, "2G GPRS"),
        EDGE(2, "2G EDGE"),
        CDMA(2, "2G CDMA"),
        // 3G
        UMTS(3, "3G UMTS"),
        HSDPA(3, "3G HSDPA"),
        HSPA(3, "3G HSPA"),
        HSPA_PLUS(3, "3G HSPA+"),
        // 4G
        LTE(4, "4G LTE"),
        LTE_CA(4, "4G LTE-CA"),
        // 5G
        NR(5, "5G NR"),
        NR_NSA(5, "5G NSA");
        
        fun isSecure(): Boolean = generation >= 3
        fun is2G(): Boolean = generation == 2
    }
    
    /**
     * Network change event record
     */
    data class NetworkChange(
        val fromType: NetworkType,
        val toType: NetworkType,
        val timestamp: Long = System.currentTimeMillis(),
        val isDowngrade: Boolean = fromType.generation > toType.generation
    ) {
        fun is2GDowngrade(): Boolean = isDowngrade && toType.generation == 2
    }
    
    /**
     * Callback for network events
     */
    interface NetworkMonitorCallback {
        fun onNetworkTypeChanged(oldType: NetworkType, newType: NetworkType)
        fun on2GDowngradeDetected(fromType: NetworkType)
    }
    
    fun setCallback(cb: NetworkMonitorCallback) { callback = cb }
    
    /**
     * Start monitoring network changes
     */
    fun startMonitoring(executor: Executor) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing READ_PHONE_STATE permission")
            return
        }
        
        // Use modern API on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startModernMonitoring(executor)
        }
        
        // Get initial state
        _currentNetworkType.value = getCurrentNetworkType()
        Log.d(TAG, "Monitoring started, current: ${_currentNetworkType.value.displayName}")
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
            try {
                telephonyManager.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callback", e)
            }
        }
        Log.d(TAG, "Monitoring stopped")
    }
    
    /**
     * Modern monitoring using TelephonyCallback (Android 12+)
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun startModernMonitoring(executor: Executor) {
        val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                handleNetworkChange(mapDisplayInfoToNetworkType(info))
            }
        }
        telephonyCallback = cb
        try {
            telephonyManager.registerTelephonyCallback(executor, cb)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception registering callback", e)
        }
    }
    
    /**
     * Handle network type change
     */
    private fun handleNetworkChange(newType: NetworkType) {
        val oldType = _currentNetworkType.value
        if (oldType == newType) return
        
        Log.d(TAG, "Network change: ${oldType.displayName} -> ${newType.displayName}")
        
        val change = NetworkChange(oldType, newType)
        _networkHistory.value = listOf(change) + _networkHistory.value.take(MAX_HISTORY_SIZE - 1)
        _currentNetworkType.value = newType
        
        callback?.onNetworkTypeChanged(oldType, newType)
        
        // Alert on 2G downgrade
        if (change.is2GDowngrade()) {
            Log.w(TAG, "⚠️ 2G DOWNGRADE DETECTED from ${oldType.displayName}")
            callback?.on2GDowngradeDetected(oldType)
        }
    }
    
    /**
     * Map TelephonyDisplayInfo to our NetworkType enum (Android 11+)
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun mapDisplayInfoToNetworkType(info: TelephonyDisplayInfo): NetworkType {
        return when (info.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> NetworkType.NR_NSA
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> NetworkType.NR
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> NetworkType.LTE_CA
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> NetworkType.LTE_CA
            else -> mapLegacyNetworkType(info.networkType)
        }
    }
    
    /**
     * Map legacy network type constants
     */
    private fun mapLegacyNetworkType(type: Int): NetworkType = when (type) {
        // 2G
        TelephonyManager.NETWORK_TYPE_GSM -> NetworkType.GSM
        TelephonyManager.NETWORK_TYPE_GPRS -> NetworkType.GPRS
        TelephonyManager.NETWORK_TYPE_EDGE -> NetworkType.EDGE
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> NetworkType.CDMA
        // 3G
        TelephonyManager.NETWORK_TYPE_UMTS -> NetworkType.UMTS
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> NetworkType.HSDPA
        TelephonyManager.NETWORK_TYPE_HSPA -> NetworkType.HSPA
        TelephonyManager.NETWORK_TYPE_HSPAP -> NetworkType.HSPA_PLUS
        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A -> NetworkType.UMTS
        // 4G
        TelephonyManager.NETWORK_TYPE_LTE -> NetworkType.LTE
        // 5G
        TelephonyManager.NETWORK_TYPE_NR -> NetworkType.NR
        // Unknown
        else -> NetworkType.UNKNOWN
    }
    
    /**
     * Get current network type (snapshot)
     */
    @Suppress("DEPRECATION")
    private fun getCurrentNetworkType(): NetworkType {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return NetworkType.UNKNOWN
        return try {
            mapLegacyNetworkType(telephonyManager.networkType)
        } catch (e: SecurityException) {
            NetworkType.UNKNOWN
        }
    }
}