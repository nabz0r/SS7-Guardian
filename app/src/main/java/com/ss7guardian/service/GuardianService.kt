package com.ss7guardian.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ss7guardian.R
import com.ss7guardian.SS7GuardianApp
import com.ss7guardian.data.entity.SecurityEventEntity
import com.ss7guardian.monitor.CellMonitor
import com.ss7guardian.monitor.NetworkMonitor
import com.ss7guardian.monitor.SmsMonitor
import com.ss7guardian.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * GuardianService - Main Monitoring Foreground Service
 * ======================================================
 * 
 * Core service that coordinates all monitoring activities.
 * Runs as a foreground service to ensure continuous protection.
 * 
 * Components:
 * - CellMonitor: Tracks cell tower connections for IMSI catcher detection
 * - NetworkMonitor: Detects forced 2G downgrades
 * - SmsMonitor: Detects silent SMS attacks
 * 
 * Lifecycle:
 * 1. start() called -> Service starts in foreground
 * 2. All monitors activated
 * 3. Cell scanning loop every 10 seconds
 * 4. Anomalies saved to database and notified
 * 5. stop() called or system kills service
 * 
 * Foreground Service:
 * - Required for background location access (Android 10+)
 * - Uses FOREGROUND_SERVICE_TYPE_LOCATION
 * - Persistent notification shows status
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class GuardianService : Service() {
    
    companion object {
        private const val TAG = "GuardianService"
        private const val NOTIFICATION_ID = 1
        private const val CELL_SCAN_INTERVAL_MS = 10_000L
        private const val LOCATION_UPDATE_INTERVAL_MS = 60_000L
        private const val LOCATION_UPDATE_DISTANCE_M = 100f
        
        // Intent actions
        const val ACTION_START = "com.ss7guardian.START"
        const val ACTION_STOP = "com.ss7guardian.STOP"
        
        /**
         * Start the guardian service
         */
        fun start(ctx: Context) {
            val intent = Intent(ctx, GuardianService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
        
        /**
         * Stop the guardian service
         */
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, GuardianService::class.java))
        }
    }
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val executor = Executors.newSingleThreadExecutor()
    
    // Monitors
    private lateinit var cellMonitor: CellMonitor
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var smsMonitor: SmsMonitor
    
    // Location
    private var locationManager: LocationManager? = null
    
    // Active monitoring job
    private var monitoringJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize monitors
        cellMonitor = CellMonitor(this)
        networkMonitor = NetworkMonitor(this)
        smsMonitor = SmsMonitor(this)
        
        // Setup event callbacks
        setupCallbacks()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopSelf()
        }
        
        // Restart if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        scope.cancel()
        Log.d(TAG, "Service destroyed")
    }
    
    /**
     * Start all monitoring activities
     */
    private fun startMonitoring() {
        Log.d(TAG, "Starting monitoring...")
        
        // Start foreground with notification
        startForeground()
        
        // Start individual monitors
        networkMonitor.startMonitoring(executor)
        smsMonitor.startMonitoring()
        startLocationUpdates()
        
        // Start cell scanning loop
        monitoringJob = scope.launch {
            while (isActive) {
                scanCells()
                delay(CELL_SCAN_INTERVAL_MS)
            }
        }
        
        Log.d(TAG, "Monitoring started successfully")
    }
    
    /**
     * Stop all monitoring activities
     */
    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring...")
        
        monitoringJob?.cancel()
        networkMonitor.stopMonitoring()
        smsMonitor.stopMonitoring()
        locationManager?.removeUpdates(locationListener)
    }
    
    /**
     * Start foreground service with persistent notification
     */
    private fun startForeground() {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, SS7GuardianApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(intent)
            .setColor(getColor(R.color.primary))
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Setup callbacks for all monitors
     */
    private fun setupCallbacks() {
        // Cell monitor events
        cellMonitor.setCallback(object : CellMonitor.CellMonitorCallback {
            override fun onCellChanged(old: CellMonitor.CellObservation?, new: CellMonitor.CellObservation) {
                Log.d(TAG, "Cell changed: ${new.getCGI()}")
            }
            
            override fun onUnknownTowerDetected(cell: CellMonitor.CellObservation) {
                scope.launch {
                    saveEvent(
                        SecurityEventEntity.EventType.UNKNOWN_CELL_TOWER,
                        threatLevel = 2,
                        title = getString(R.string.event_unknown_tower),
                        description = "Connected to unknown tower ${cell.getCGI()}"
                    )
                }
            }
            
            override fun onSignalAnomaly(cell: CellMonitor.CellObservation, prev: Int, curr: Int) {
                scope.launch {
                    saveEvent(
                        SecurityEventEntity.EventType.SIGNAL_ANOMALY,
                        threatLevel = 1,
                        title = getString(R.string.event_signal_anomaly),
                        description = "Signal changed from ${prev}dBm to ${curr}dBm"
                    )
                }
            }
            
            override fun onRapidCellChanges(count: Int, windowMs: Long) {
                scope.launch {
                    saveEvent(
                        SecurityEventEntity.EventType.RAPID_CELL_CHANGE,
                        threatLevel = 2,
                        title = getString(R.string.event_rapid_cell_change),
                        description = "$count cell changes in ${windowMs / 1000} seconds"
                    )
                }
            }
        })
        
        // Network monitor events
        networkMonitor.setCallback(object : NetworkMonitor.NetworkMonitorCallback {
            override fun onNetworkTypeChanged(oldType: NetworkMonitor.NetworkType, newType: NetworkMonitor.NetworkType) {
                Log.d(TAG, "Network: ${oldType.displayName} -> ${newType.displayName}")
            }
            
            override fun on2GDowngradeDetected(fromType: NetworkMonitor.NetworkType) {
                scope.launch {
                    saveEvent(
                        SecurityEventEntity.EventType.NETWORK_DOWNGRADE_2G,
                        threatLevel = 3,
                        title = getString(R.string.event_2g_downgrade),
                        description = "Network downgraded from ${fromType.displayName} to 2G"
                    )
                }
            }
        })
        
        // SMS monitor events
        smsMonitor.setCallback(object : SmsMonitor.SmsMonitorCallback {
            override fun onSuspiciousSmsDetected(sms: SmsMonitor.SuspiciousSms) {
                val type = when (sms.type) {
                    SmsMonitor.SmsType.CLASS_0_FLASH -> SecurityEventEntity.EventType.SILENT_SMS_CLASS0
                    SmsMonitor.SmsType.TYPE_0_SILENT -> SecurityEventEntity.EventType.SILENT_SMS_TYPE0
                    SmsMonitor.SmsType.WAP_PUSH -> SecurityEventEntity.EventType.WAP_PUSH
                    else -> SecurityEventEntity.EventType.OTHER
                }
                scope.launch {
                    saveEvent(
                        type,
                        threatLevel = if (sms.type == SmsMonitor.SmsType.TYPE_0_SILENT) 4 else 2,
                        title = "Suspicious SMS: ${sms.type}",
                        description = "From: ${sms.from ?: "Unknown"}"
                    )
                }
            }
        })
    }
    
    /**
     * Save security event to database and show notification if needed
     */
    private suspend fun saveEvent(
        type: SecurityEventEntity.EventType,
        threatLevel: Int,
        title: String,
        description: String
    ) {
        withContext(Dispatchers.IO) {
            val event = SecurityEventEntity(
                eventType = type,
                threatLevel = threatLevel,
                title = title,
                description = description
            )
            SS7GuardianApp.instance.database.securityEventDao().insertEvent(event)
            Log.d(TAG, "Event saved: $title")
            
            // Show alert for high threat levels
            if (threatLevel >= 2) {
                showAlert(title, description, threatLevel)
            }
        }
    }
    
    /**
     * Show alert notification
     */
    private fun showAlert(title: String, description: String, threatLevel: Int) {
        val color = when (threatLevel) {
            0, 1 -> getColor(R.color.status_low)
            2 -> getColor(R.color.status_medium)
            3 -> getColor(R.color.status_high)
            else -> getColor(R.color.status_critical)
        }
        
        val notification = NotificationCompat.Builder(this, SS7GuardianApp.CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(color)
            .build()
        
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
    
    /**
     * Scan current cell towers
     */
    private fun scanCells() {
        cellMonitor.getCurrentCellInfo()
            ?.filter { it.isRegistered }
            ?.forEach { info ->
                cellMonitor.parseCellInfo(info)?.let { obs ->
                    scope.launch {
                        val db = SS7GuardianApp.instance.database
                        val isKnown = db.cellTowerDao()
                            .isTowerKnown(obs.cellId, obs.lac, obs.mcc, obs.mnc) > 0
                        
                        // Analyze for anomalies
                        cellMonitor.analyzeChange(
                            cellMonitor.cellHistory.value.firstOrNull(),
                            obs,
                            isKnown
                        )
                        
                        // Record observation
                        cellMonitor.recordObservation(obs)
                        
                        // Update database
                        if (isKnown) {
                            db.cellTowerDao().updateTowerSeen(
                                obs.cellId, obs.lac, obs.mcc, obs.mnc,
                                System.currentTimeMillis()
                            )
                        } else {
                            locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                                ?.let { loc ->
                                    db.cellTowerDao().insertTower(cellMonitor.toEntity(obs, loc))
                                }
                        }
                    }
                }
            }
    }
    
    /**
     * Location listener for tower positioning
     */
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            cellMonitor.updateLocation(location)
        }
    }
    
    /**
     * Start location updates for tower positioning
     */
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_UPDATE_INTERVAL_MS,
                LOCATION_UPDATE_DISTANCE_M,
                locationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }
}