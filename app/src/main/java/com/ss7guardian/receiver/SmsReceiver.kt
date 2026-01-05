package com.ss7guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.ss7guardian.SS7GuardianApp
import com.ss7guardian.data.entity.SecurityEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SmsReceiver - Static broadcast receiver for SMS detection
 * ============================================================
 * 
 * This receiver is registered in the manifest to detect SMS messages
 * even when the app is not running. It complements the dynamic
 * SmsMonitor registered in GuardianService.
 * 
 * Detection capabilities:
 * - Class 0 (Flash) SMS: Display immediately without storage
 * - Type 0 (Silent) SMS: No indication to user, used for pinging
 * - WAP Push: Can contain malicious configuration updates
 * - Binary SMS: Non-text messages, potentially OTA commands
 * 
 * Why both static and dynamic receivers?
 * - Static (this): Works even when app is killed
 * - Dynamic (SmsMonitor): More control, can be enabled/disabled
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class SmsReceiver : BroadcastReceiver() {
    
    // Coroutine scope for database operations
    // Uses SupervisorJob so one failure doesn't cancel others
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "SmsReceiver"
        
        // Protocol identifier for silent SMS (Type 0)
        // Reference: 3GPP TS 23.040 Section 9.2.3.9
        private const val PID_TYPE_0_SILENT = 0x40
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            // Standard SMS received
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                handleSmsReceived(context, intent)
            }
            
            // WAP Push message (MMS notification, config push, etc.)
            Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION -> {
                handleWapPush(context, intent)
            }
        }
    }
    
    /**
     * Process incoming SMS messages
     * 
     * Analyzes each SMS PDU for suspicious characteristics:
     * - Message class (Class 0 = Flash SMS)
     * - Protocol identifier (PID 0x40 = Silent/Type 0)
     * - Content type (null body with data = binary SMS)
     */
    private fun handleSmsReceived(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        messages?.forEach { sms ->
            val messageClass = getMessageClass(sms)
            val protocolId = sms.protocolIdentifier
            val hasBody = !sms.messageBody.isNullOrEmpty()
            val hasUserData = sms.userData != null
            val sender = sms.originatingAddress ?: "Unknown"
            
            Log.d(TAG, "SMS from $sender - Class: $messageClass, PID: $protocolId, " +
                      "HasBody: $hasBody, HasData: $hasUserData")
            
            // Check for Class 0 (Flash) SMS
            // These display immediately and are often used for tracking
            if (messageClass == 0) {
                Log.w(TAG, "⚠️ CLASS 0 (FLASH) SMS DETECTED from $sender")
                recordSecurityEvent(
                    type = SecurityEventEntity.EventType.SILENT_SMS_CLASS0,
                    threatLevel = 3,
                    title = "Class 0 Flash SMS Detected",
                    description = "Received Class 0 (Flash) SMS from $sender. " +
                                 "These messages are often used for location tracking."
                )
            }
            
            // Check for Type 0 (Silent) SMS
            // PID 0x40 means the message should be processed silently
            if (protocolId == PID_TYPE_0_SILENT) {
                Log.w(TAG, "⚠️ TYPE 0 (SILENT) SMS DETECTED from $sender")
                recordSecurityEvent(
                    type = SecurityEventEntity.EventType.SILENT_SMS_TYPE0,
                    threatLevel = 4, // Higher threat - definitely suspicious
                    title = "Silent SMS (Type 0) Detected",
                    description = "Received Type 0 silent SMS from $sender. " +
                                 "This is a strong indicator of network-based tracking."
                )
            }
            
            // Check for Binary SMS (no text body but has data)
            // Could be OTA updates, SIM toolkit commands, etc.
            if (!hasBody && hasUserData) {
                Log.w(TAG, "⚠️ BINARY SMS DETECTED from $sender")
                recordSecurityEvent(
                    type = SecurityEventEntity.EventType.OTHER,
                    threatLevel = 2,
                    title = "Binary SMS Detected",
                    description = "Received binary (non-text) SMS from $sender. " +
                                 "Could be OTA update or SIM toolkit command."
                )
            }
        }
    }
    
    /**
     * Process WAP Push messages
     * 
     * WAP Push can contain:
     * - MMS notifications
     * - Service loading (SL) - potentially malicious
     * - Service indication (SI) - notification with URL
     * - Provisioning - OMA-DM device configuration
     */
    private fun handleWapPush(context: Context, intent: Intent) {
        val contentType = intent.type
        val sender = intent.getStringExtra("address") ?: "Unknown"
        
        Log.w(TAG, "⚠️ WAP PUSH DETECTED from $sender, type: $contentType")
        
        recordSecurityEvent(
            type = SecurityEventEntity.EventType.WAP_PUSH,
            threatLevel = 2,
            title = "WAP Push Message Received",
            description = "Received WAP Push from $sender. " +
                         "Content type: ${contentType ?: "unknown"}. " +
                         "Could be legitimate MMS or malicious configuration push."
        )
    }
    
    /**
     * Get message class from SmsMessage using reflection
     * 
     * Message classes (3GPP TS 23.038):
     * - Class 0: Flash message, display immediately
     * - Class 1: ME-specific, store in device
     * - Class 2: SIM-specific, store in SIM
     * - Class 3: TE-specific, forward to terminal equipment
     * 
     * @return Message class (0-3) or -1 if unknown
     */
    private fun getMessageClass(sms: android.telephony.SmsMessage): Int {
        return try {
            // Use reflection since getMessageClass() returns enum
            val messageClassMethod = android.telephony.SmsMessage::class.java
                .getMethod("getMessageClass")
            
            when (messageClassMethod.invoke(sms).toString()) {
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
    }
    
    /**
     * Record a security event to the database
     * 
     * Uses coroutine to avoid blocking the broadcast receiver.
     * Database operations are performed on IO dispatcher.
     */
    private fun recordSecurityEvent(
        type: SecurityEventEntity.EventType,
        threatLevel: Int,
        title: String,
        description: String
    ) {
        scope.launch {
            try {
                val event = SecurityEventEntity(
                    eventType = type,
                    threatLevel = threatLevel,
                    title = title,
                    description = description
                )
                
                SS7GuardianApp.instance.database.securityEventDao().insertEvent(event)
                Log.d(TAG, "Security event recorded: $title")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record security event", e)
            }
        }
    }
}