package com.ss7guardian.util

import android.telephony.TelephonyManager

/**
 * NetworkUtils - Network Type Utilities
 * =======================================
 * 
 * Utilities for working with cellular network types.
 * Provides human-readable names and security assessments.
 * 
 * Network Security Levels:
 * - 5G (NR): Most secure, strong encryption
 * - 4G (LTE): Secure, 128-bit encryption
 * - 3G (UMTS): Moderately secure
 * - 2G (GSM): INSECURE - A5/1 encryption can be cracked
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
object NetworkUtils {
    
    // ========================================
    // Network Generation Constants
    // ========================================
    
    const val GENERATION_UNKNOWN = 0
    const val GENERATION_2G = 2
    const val GENERATION_3G = 3
    const val GENERATION_4G = 4
    const val GENERATION_5G = 5
    
    // ========================================
    // Network Type Mapping
    // ========================================
    
    /**
     * Get network generation from TelephonyManager network type
     * 
     * @param networkType Value from TelephonyManager.getNetworkType()
     * @return Generation number (2, 3, 4, 5) or 0 for unknown
     */
    fun getNetworkGeneration(networkType: Int): Int {
        return when (networkType) {
            // 2G - INSECURE
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> GENERATION_2G
            
            // 3G
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> GENERATION_3G
            
            // 4G
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> GENERATION_4G
            
            // 5G
            TelephonyManager.NETWORK_TYPE_NR -> GENERATION_5G
            
            // Unknown
            else -> GENERATION_UNKNOWN
        }
    }
    
    /**
     * Get human-readable name for network type
     * 
     * @param networkType Value from TelephonyManager.getNetworkType()
     * @return Display name like "4G LTE" or "2G GSM"
     */
    fun getNetworkTypeName(networkType: Int): String {
        return when (networkType) {
            // 2G
            TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_CDMA -> "2G CDMA"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "2G 1xRTT"
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G iDEN"
            
            // 3G
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G EVDO"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G EVDO-A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G EVDO-B"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "3G eHRPD"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G TD-SCDMA"
            
            // 4G
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "4G Wi-Fi"
            
            // 5G
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            
            // Unknown
            else -> "Unknown"
        }
    }
    
    /**
     * Get short generation label (2G, 3G, 4G, 5G)
     * 
     * @param networkType Value from TelephonyManager.getNetworkType()
     * @return Short label like "4G" or "2G"
     */
    fun getGenerationLabel(networkType: Int): String {
        return when (getNetworkGeneration(networkType)) {
            GENERATION_2G -> "2G"
            GENERATION_3G -> "3G"
            GENERATION_4G -> "4G"
            GENERATION_5G -> "5G"
            else -> "--"
        }
    }
    
    // ========================================
    // Security Assessment
    // ========================================
    
    /**
     * Check if network type is secure
     * 2G is considered insecure due to weak A5/1 encryption
     * 
     * @param networkType Value from TelephonyManager.getNetworkType()
     * @return true if network is 3G or higher
     */
    fun isSecure(networkType: Int): Boolean {
        return getNetworkGeneration(networkType) >= GENERATION_3G
    }
    
    /**
     * Check if network type is 2G (insecure)
     * 
     * @param networkType Value from TelephonyManager.getNetworkType()
     * @return true if network is 2G
     */
    fun is2G(networkType: Int): Boolean {
        return getNetworkGeneration(networkType) == GENERATION_2G
    }
    
    /**
     * Check if transition from old to new type is a downgrade
     * 
     * @param oldType Previous network type
     * @param newType Current network type
     * @return true if new generation is lower than old
     */
    fun isDowngrade(oldType: Int, newType: Int): Boolean {
        val oldGen = getNetworkGeneration(oldType)
        val newGen = getNetworkGeneration(newType)
        return oldGen > newGen && newGen > 0
    }
    
    /**
     * Check if transition is a dangerous 2G downgrade
     * This is a common attack vector
     * 
     * @param oldType Previous network type
     * @param newType Current network type
     * @return true if downgraded to 2G from higher generation
     */
    fun is2GDowngrade(oldType: Int, newType: Int): Boolean {
        return isDowngrade(oldType, newType) && is2G(newType)
    }
    
    // ========================================
    // Signal Strength Assessment
    // ========================================
    
    /**
     * Signal strength quality levels
     */
    enum class SignalQuality {
        EXCELLENT,  // > -65 dBm
        GOOD,       // -65 to -75 dBm
        FAIR,       // -75 to -85 dBm
        POOR,       // -85 to -95 dBm
        VERY_POOR,  // < -95 dBm
        UNKNOWN     // Invalid reading
    }
    
    /**
     * Assess signal strength quality
     * 
     * Note: Unusually strong signals (> -50 dBm) may indicate IMSI catcher
     * 
     * @param dbm Signal strength in dBm
     * @return SignalQuality enum value
     */
    fun getSignalQuality(dbm: Int): SignalQuality {
        return when {
            dbm == Int.MAX_VALUE || dbm == Int.MIN_VALUE -> SignalQuality.UNKNOWN
            dbm > -65 -> SignalQuality.EXCELLENT
            dbm > -75 -> SignalQuality.GOOD
            dbm > -85 -> SignalQuality.FAIR
            dbm > -95 -> SignalQuality.POOR
            else -> SignalQuality.VERY_POOR
        }
    }
    
    /**
     * Check if signal is suspiciously strong
     * IMSI catchers often have very strong signals to overpower real towers
     * 
     * @param dbm Signal strength in dBm
     * @return true if signal is unusually strong (> -50 dBm)
     */
    fun isSuspiciouslyStrong(dbm: Int): Boolean {
        return dbm > -50 && dbm != Int.MAX_VALUE
    }
    
    /**
     * Get color resource for signal quality
     * For UI display purposes
     * 
     * @param dbm Signal strength in dBm
     * @return Color resource ID
     */
    fun getSignalColor(dbm: Int): Int {
        return when (getSignalQuality(dbm)) {
            SignalQuality.EXCELLENT -> android.graphics.Color.parseColor("#4CAF50") // Green
            SignalQuality.GOOD -> android.graphics.Color.parseColor("#8BC34A")      // Light Green
            SignalQuality.FAIR -> android.graphics.Color.parseColor("#FFEB3B")      // Yellow
            SignalQuality.POOR -> android.graphics.Color.parseColor("#FF9800")      // Orange
            SignalQuality.VERY_POOR -> android.graphics.Color.parseColor("#F44336") // Red
            SignalQuality.UNKNOWN -> android.graphics.Color.parseColor("#9E9E9E")   // Gray
        }
    }
}