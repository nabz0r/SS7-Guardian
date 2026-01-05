package com.ss7guardian.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SecurityEventEntity - Security Event Database Record
 * ======================================================
 * 
 * Represents a detected security anomaly or threat.
 * All suspicious activities are logged here for analysis and notification.
 * 
 * Event Lifecycle:
 * 1. Detection: Monitor detects anomaly
 * 2. Recording: Event saved to database
 * 3. Notification: If threat level >= 2, user is notified
 * 4. Review: User can view and dismiss events
 * 5. Analysis: Historical events used for pattern detection
 * 
 * Threat Level Guidelines:
 * - Level 0 (Safe): Informational, no concern
 * - Level 1 (Low): Minor anomaly, probably benign
 * - Level 2 (Medium): Multiple indicators, worth attention
 * - Level 3 (High): Strong attack indicators
 * - Level 4 (Critical): Active attack likely
 * 
 * Storage Considerations:
 * - Events are never auto-deleted (audit trail)
 * - Consider implementing archival for events older than 1 year
 * - Export feature should be available for user analysis
 * 
 * @property id Auto-generated primary key
 * @property eventType Category of the security event
 * @property threatLevel Severity from 0 (safe) to 4 (critical)
 * @property title Short description for notifications
 * @property description Detailed explanation of the event
 * @property cellId Associated cell tower (if applicable)
 * @property lac Associated location area (if applicable)
 * @property networkType Network type when event occurred
 * @property timestamp When the event was detected
 * @property notified Whether user has been notified
 * @property dismissed Whether user has dismissed this event
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
@Entity(
    tableName = "security_events",
    indices = [
        // Index for querying recent events by time
        Index(value = ["timestamp"]),
        // Index for filtering by event type
        Index(value = ["eventType"]),
        // Index for finding undismissed events
        Index(value = ["dismissed"])
    ]
)
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // ========================================
    // Event Classification
    // ========================================
    
    /**
     * Type of security event
     * Determines icon, color, and handling logic
     */
    val eventType: EventType,
    
    /**
     * Threat severity level (0-4)
     * 
     * 0: Safe - Informational only
     * 1: Low - Minor anomaly
     * 2: Medium - Possible attack indicator
     * 3: High - Strong attack indicator
     * 4: Critical - Active attack likely
     * 
     * Notifications shown for level >= 2
     */
    val threatLevel: Int,
    
    // ========================================
    // Event Description
    // ========================================
    
    /**
     * Short title for notification and list display
     * Should be concise (< 50 chars)
     */
    val title: String,
    
    /**
     * Detailed description of what was detected
     * Include specific values and recommendations
     */
    val description: String,
    
    // ========================================
    // Context Information
    // ========================================
    
    /**
     * Cell ID associated with this event (if applicable)
     * Null for events not related to specific tower
     */
    val cellId: Int? = null,
    
    /**
     * Location Area Code associated with event
     */
    val lac: Int? = null,
    
    /**
     * Network type when event occurred
     * Values: "GSM", "WCDMA", "LTE", "NR", "UNKNOWN"
     */
    val networkType: String? = null,
    
    // ========================================
    // Timestamps and State
    // ========================================
    
    /**
     * When the event was detected
     * Unix timestamp in milliseconds
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * Whether a notification was shown for this event
     * Used to prevent duplicate notifications
     */
    val notified: Boolean = false,
    
    /**
     * Whether user has dismissed this event
     * Dismissed events don't contribute to threat level calculation
     */
    val dismissed: Boolean = false
) {
    /**
     * Security Event Types
     * 
     * Each type corresponds to a specific detection mechanism:
     * 
     * Network Events:
     * - NETWORK_DOWNGRADE_2G: Forced downgrade to insecure 2G
     * 
     * Cell Tower Events:
     * - UNKNOWN_CELL_TOWER: New tower not in trusted database
     * - SIGNAL_ANOMALY: Unusual signal strength changes
     * - RAPID_CELL_CHANGE: Too many handoffs in short time
     * 
     * SMS Events:
     * - SILENT_SMS_CLASS0: Flash SMS (display only, no storage)
     * - SILENT_SMS_TYPE0: Silent ping SMS (no user indication)
     * - WAP_PUSH: WAP push message (possible config attack)
     * 
     * Other:
     * - OTHER: Unclassified security event
     */
    enum class EventType {
        /**
         * Network downgraded from 4G/5G to 2G
         * 
         * Attack vector: Jamming higher frequencies to force
         * connection to 2G, which has weaker encryption (A5/1)
         * that can be cracked in real-time.
         * 
         * Threat: Call/SMS interception possible
         */
        NETWORK_DOWNGRADE_2G,
        
        /**
         * Connected to a cell tower not in trusted database
         * 
         * Attack vector: IMSI catcher (Stingray) impersonates
         * legitimate tower to intercept communications.
         * 
         * Note: Could also be legitimate new tower
         */
        UNKNOWN_CELL_TOWER,
        
        /**
         * Class 0 (Flash) SMS received
         * 
         * Attack vector: Class 0 SMS displays immediately and
         * isn't stored. Used for silent tracking pings.
         * 
         * Legitimate uses: Emergency alerts, carrier messages
         */
        SILENT_SMS_CLASS0,
        
        /**
         * Type 0 (Silent) SMS received
         * 
         * Attack vector: Protocol ID 0x40 indicates message
         * should be processed without user notification.
         * Used for location tracking via network response.
         * 
         * Legitimate uses: Almost none for consumers
         */
        SILENT_SMS_TYPE0,
        
        /**
         * WAP Push message received
         * 
         * Attack vector: Can push malicious configurations
         * to device (OMA-DM provisioning attack).
         * 
         * Legitimate uses: MMS notifications, carrier config
         */
        WAP_PUSH,
        
        /**
         * Unusual signal strength change
         * 
         * Attack vector: IMSI catchers typically have very
         * strong signals to overpower legitimate towers.
         * 
         * Note: Can also indicate physical movement
         */
        SIGNAL_ANOMALY,
        
        /**
         * Too many cell tower changes in short time
         * 
         * Attack vector: IMSI catcher may cause rapid
         * handoffs as device struggles between real and
         * fake towers.
         * 
         * Note: Can also indicate poor coverage area
         */
        RAPID_CELL_CHANGE,
        
        /**
         * Other security event
         * 
         * Catch-all for events that don't fit other categories
         */
        OTHER
    }
    
    /**
     * Get color resource for this threat level
     */
    fun getThreatColor(): Int {
        return when (threatLevel) {
            0 -> android.graphics.Color.parseColor("#4CAF50") // Green
            1 -> android.graphics.Color.parseColor("#FFEB3B") // Yellow
            2 -> android.graphics.Color.parseColor("#FF9800") // Orange
            3 -> android.graphics.Color.parseColor("#F44336") // Red
            else -> android.graphics.Color.parseColor("#B71C1C") // Dark Red
        }
    }
    
    /**
     * Get threat level name
     */
    fun getThreatLevelName(): String {
        return when (threatLevel) {
            0 -> "Safe"
            1 -> "Low"
            2 -> "Medium"
            3 -> "High"
            else -> "Critical"
        }
    }
}