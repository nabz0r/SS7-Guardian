package com.ss7guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ss7guardian.service.GuardianService

/**
 * BootReceiver - Auto-start on Device Boot
 * ==========================================
 * 
 * Automatically starts the Guardian service when the device boots.
 * This ensures continuous protection without user intervention.
 * 
 * Triggered by:
 * - ACTION_BOOT_COMPLETED: Normal boot complete
 * - ACTION_LOCKED_BOOT_COMPLETED: Direct boot (Android 7+, if directBootAware=true)
 * 
 * Requirements:
 * - RECEIVE_BOOT_COMPLETED permission in manifest
 * - android:exported="true" in manifest (to receive system broadcasts)
 * 
 * Considerations:
 * - User should be able to disable auto-start in settings (TODO)
 * - Service may be delayed if device is in Doze mode
 * - On Android 8+, use startForegroundService()
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d(TAG, "Device booted, starting Guardian service")
                
                // TODO: Check user preference for auto-start
                // val prefs = context.getSharedPreferences("ss7_prefs", Context.MODE_PRIVATE)
                // if (!prefs.getBoolean("auto_start", true)) return
                
                try {
                    GuardianService.start(context)
                    Log.d(TAG, "Guardian service start requested")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start Guardian service", e)
                }
            }
        }
    }
}