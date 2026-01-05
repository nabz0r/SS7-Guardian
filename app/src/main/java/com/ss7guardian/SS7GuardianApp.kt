package com.ss7guardian

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.ss7guardian.data.AppDatabase

/**
 * SS7GuardianApp - Main Application Class
 * =========================================
 * 
 * This is the entry point for the SS7 Guardian application.
 * Initialized before any activity, service, or receiver.
 * 
 * Responsibilities:
 * - Initialize Room database singleton
 * - Create notification channels (required for Android 8+)
 * - Provide global access to database instance
 * 
 * Lifecycle:
 * 1. onCreate() called when app process starts
 * 2. Database initialized lazily on first access
 * 3. Notification channels created immediately
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class SS7GuardianApp : Application() {
    
    /**
     * Room database instance
     * 
     * Provides access to:
     * - cellTowerDao(): Known cell towers with trust scores
     * - securityEventDao(): Security alerts and events
     * 
     * Thread-safe singleton - can be accessed from any thread
     */
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "SS7 Guardian initializing...")
        
        // Store singleton reference for global access
        instance = this
        
        // Initialize Room database
        // Using fallbackToDestructiveMigration for development
        // TODO: Implement proper migrations before production
        database = AppDatabase.getInstance(this)
        
        // Create notification channels for Android 8+
        createNotificationChannels()
        
        Log.d(TAG, "SS7 Guardian initialized successfully")
    }

    /**
     * Create notification channels for different alert types
     * 
     * Channels (required for Android 8.0+):
     * - Service: Low priority, persistent notification for foreground service
     * - Alerts: High priority with vibration for security warnings
     * 
     * Users can customize these in system settings per-channel
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Channel for foreground service notification
            // Low importance = no sound, minimized in shade
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_service_desc)
                setShowBadge(false) // Don't show badge for service
            }
            
            // Channel for security alerts
            // High importance = sound, vibration, heads-up display
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alerts_desc)
                enableVibration(true)
                enableLights(true)
                lightColor = getColor(R.color.status_critical)
                setShowBadge(true)
            }
            
            // Register both channels with the system
            manager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
            
            Log.d(TAG, "Notification channels created")
        }
    }

    companion object {
        private const val TAG = "SS7GuardianApp"
        
        /**
         * Notification channel IDs
         * Must match the IDs used in NotificationCompat.Builder
         */
        const val CHANNEL_SERVICE = "ss7_guardian_service"
        const val CHANNEL_ALERTS = "ss7_guardian_alerts"
        
        /**
         * Global singleton instance
         * 
         * Access pattern: SS7GuardianApp.instance.database
         * 
         * WARNING: Only use after Application.onCreate() has completed
         * Safe to use from: Activities, Services, BroadcastReceivers
         */
        lateinit var instance: SS7GuardianApp
            private set
    }
}