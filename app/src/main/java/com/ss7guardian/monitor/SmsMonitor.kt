package com.ss7guardian.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SmsMonitor - Dynamic SMS Attack Detection
 * ===========================================
 * 
 * Monitors incoming SMS for suspicious characteristics that may
 * indicate SS7 attacks or tracking attempts.
 * 
 * Suspicious SMS Types:
 * 
 * 1. Class 0 (Flash) SMS:
 *    - Display immediately without storage
 *    - Used for carrier alerts, but also for tracking
 *    - Attacker sends Class 0, network confirms delivery = location ping
 * 
 * 2. Type 0 (Silent) SMS:
 *    - Protocol ID 0x40 in PDU
 *    - No indication to user whatsoever
 *    - Used by law enforcement and attackers for location tracking
 *    - Network generates delivery report = location confirmation
 * 
 * 3. Binary SMS:
 *    - No text body, contains raw data
 *    - Used for OTA updates, SIM Toolkit commands
 *    - Can be abused for remote SIM attacks
 * 
 * 4. WAP Push:
 *    - Push configuration to device
 *    - Can be used for OMA-DM attacks (malicious provisioning)
 *    - May change APN, proxy, or other settings
 * 
 * This is a dynamic receiver registered at runtime.
 * See also: SmsReceiver (static, works when app killed)
 * 
 * @param context Application context for registering receiver
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class SmsMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "SmsMonitor"
        private const val MAX_HISTORY_SIZE = 100
        
        // Protocol ID for silent SMS (3GPP TS 23.040)
        private const val PID_TYPE_0_SILENT = 0x40
    }
    
    // Observable state
    private val _events = MutableStateFlow<List<SuspiciousSms>>(emptyList())
    val suspiciousSmsEvents: StateFlow<List<SuspiciousSms>> = _events.asStateFlow()
    
    private var callback: SmsMonitorCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var registered = false
    
    /**
     * Suspicious SMS event record
     */
    data class SuspiciousSms(
        val type: SmsType,
        val from: String?,
        val body: String?,
        val messageClass: Int,
        val protocolId: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Types of suspicious SMS
     */
    enum class SmsType {
        CLASS_0_FLASH,   // Flash message (display only)
        BINARY,          // Binary data (no text)
        WAP_PUSH,        // WAP push message
        TYPE_0_SILENT,   // Silent ping SMS
        OTHER            // Other suspicious type
    }
    
    /**
     * Callback for SMS events
     */
    interface SmsMonitorCallback {
        fun onSuspiciousSmsDetected(sms: SuspiciousSms)
    }
    
    fun setCallback(cb: SmsMonitorCallback) { callback = cb }
    
    /**
     * Start monitoring incoming SMS
     */
    fun startMonitoring() {
        if (registered) return
        
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSms(intent)
                    Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION -> handleWap(intent)
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            addAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        registered = true
        Log.d(TAG, "SMS monitoring started")
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        if (!registered) return
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
        receiver = null
        registered = false
        Log.d(TAG, "SMS monitoring stopped")
    }
    
    /**
     * Handle incoming SMS
     */
    private fun handleSms(intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages?.forEach { analyzeSms(it) }
    }
    
    /**
     * Analyze SMS for suspicious characteristics
     */
    private fun analyzeSms(sms: SmsMessage) {
        val messageClass = getMessageClass(sms)
        val protocolId = sms.protocolIdentifier
        val sender = sms.originatingAddress
        val body = sms.messageBody
        val userData = sms.userData
        
        Log.d(TAG, "SMS from $sender - Class: $messageClass, PID: $protocolId")
        
        // Check for Class 0 (Flash) SMS
        if (messageClass == 0) {
            Log.w(TAG, "⚠️ CLASS 0 SMS from $sender")
            record(SuspiciousSms(SmsType.CLASS_0_FLASH, sender, body, messageClass, protocolId))
        }
        
        // Check for Type 0 (Silent) SMS
        if (protocolId == PID_TYPE_0_SILENT) {
            Log.w(TAG, "⚠️ TYPE 0 SILENT SMS from $sender")
            record(SuspiciousSms(SmsType.TYPE_0_SILENT, sender, null, messageClass, protocolId))
        }
        
        // Check for Binary SMS (data but no text)
        if (body == null && userData != null) {
            Log.w(TAG, "⚠️ BINARY SMS from $sender")
            record(SuspiciousSms(SmsType.BINARY, sender, null, messageClass, protocolId))
        }
    }
    
    /**
     * Handle WAP Push message
     */
    private fun handleWap(intent: Intent) {
        val sender = intent.getStringExtra("address")
        val contentType = intent.type
        Log.w(TAG, "⚠️ WAP PUSH from $sender, type: $contentType")
        record(SuspiciousSms(SmsType.WAP_PUSH, sender, "Content-Type: $contentType", -1, -1))
    }
    
    /**
     * Get message class using reflection
     */
    private fun getMessageClass(sms: SmsMessage): Int = try {
        val method = SmsMessage::class.java.getMethod("getMessageClass")
        when (method.invoke(sms).toString()) {
            "CLASS_0" -> 0
            "CLASS_1" -> 1
            "CLASS_2" -> 2
            "CLASS_3" -> 3
            else -> -1
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error getting message class", e)
        -1
    }
    
    /**
     * Record suspicious SMS in history and notify callback
     */
    private fun record(sms: SuspiciousSms) {
        _events.value = listOf(sms) + _events.value.take(MAX_HISTORY_SIZE - 1)
        callback?.onSuspiciousSmsDetected(sms)
    }
}