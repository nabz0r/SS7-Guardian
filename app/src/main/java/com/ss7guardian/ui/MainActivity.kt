package com.ss7guardian.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ss7guardian.R
import com.ss7guardian.SS7GuardianApp
import com.ss7guardian.service.GuardianService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity - Main Dashboard UI
 * ==================================
 * 
 * Primary user interface for SS7 Guardian.
 * Shows current protection status and recent security events.
 * 
 * Features:
 * - Start/Stop monitoring toggle
 * - Current threat level indicator
 * - Recent events list
 * - Permission handling
 * 
 * Layout:
 * - Programmatic layout (no XML) for simplicity
 * - Material 3 styling via theme
 * 
 * Future Improvements:
 * - ViewBinding with XML layouts
 * - RecyclerView for events
 * - Bottom navigation for settings/history
 * - Map view for tower locations
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // UI state
    private var isMonitoring = false
    
    // UI elements
    private lateinit var statusText: TextView
    private lateinit var threatLevelText: TextView
    private lateinit var toggleButton: Button
    private lateinit var eventsText: TextView
    private lateinit var towerCountText: TextView
    
    // Required permissions
    private val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    ).apply {
        // SMS permission for monitoring silent SMS
        add(Manifest.permission.RECEIVE_SMS)
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        if (allGranted) {
            startMonitoring()
        } else {
            Toast.makeText(
                this,
                getString(R.string.permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        observeData()
    }
    
    /**
     * Setup programmatic UI layout
     * TODO: Replace with XML layout and ViewBinding
     */
    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(ContextCompat.getColor(context, R.color.background_light))
        }
        
        // App title
        val title = TextView(this).apply {
            text = "🛡️ SS7 Guardian"
            textSize = 28f
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            gravity = Gravity.CENTER
        }
        
        // Status indicator
        statusText = TextView(this).apply {
            text = getString(R.string.status_stopped)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }
        
        // Threat level display
        threatLevelText = TextView(this).apply {
            text = "🟢 Safe"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.status_safe))
            setPadding(0, 8, 0, 32)
        }
        
        // Tower count
        towerCountText = TextView(this).apply {
            text = "Known towers: 0"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary_light))
        }
        
        // Toggle button
        toggleButton = Button(this).apply {
            text = getString(R.string.btn_start_protection)
            setOnClickListener { toggleProtection() }
            setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
            setTextColor(Color.WHITE)
        }
        
        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply { setMargins(0, 32, 0, 32) }
            setBackgroundColor(ContextCompat.getColor(context, R.color.divider_light))
        }
        
        // Events header
        val eventsHeader = TextView(this).apply {
            text = getString(R.string.recent_events_header)
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary_light))
        }
        
        // Events list
        eventsText = TextView(this).apply {
            text = getString(R.string.no_events)
            textSize = 14f
            setPadding(0, 16, 0, 0)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary_light))
        }
        
        // Build layout
        layout.addView(title)
        layout.addView(statusText)
        layout.addView(threatLevelText)
        layout.addView(towerCountText)
        layout.addView(toggleButton)
        layout.addView(divider)
        layout.addView(eventsHeader)
        layout.addView(eventsText)
        
        // ScrollView wrapper for events
        val scrollView = ScrollView(this).apply {
            addView(layout)
        }
        
        setContentView(scrollView)
    }
    
    /**
     * Observe database for live updates
     */
    private fun observeData() {
        // Observe security events
        lifecycleScope.launch {
            SS7GuardianApp.instance.database.securityEventDao().getAllEvents().collectLatest { events ->
                if (events.isEmpty()) {
                    eventsText.text = getString(R.string.no_events)
                    updateThreatLevel(0)
                } else {
                    // Show last 10 events
                    eventsText.text = events.take(10).joinToString("\n\n") { event ->
                        val icon = when (event.threatLevel) {
                            0 -> "🟢"
                            1 -> "🟡"
                            2 -> "🟠"
                            3 -> "🔴"
                            else -> "⚫"
                        }
                        "$icon ${event.title}\n   ${event.description}"
                    }
                    
                    // Update threat level from recent events
                    val maxThreat = events
                        .filter { !it.dismissed }
                        .maxOfOrNull { it.threatLevel } ?: 0
                    updateThreatLevel(maxThreat)
                }
            }
        }
        
        // Observe tower count
        lifecycleScope.launch {
            SS7GuardianApp.instance.database.cellTowerDao().getAllTowers().collectLatest { towers ->
                towerCountText.text = "Known towers: ${towers.size}"
            }
        }
    }
    
    /**
     * Update threat level display
     */
    private fun updateThreatLevel(level: Int) {
        val (icon, text, color) = when (level) {
            0 -> Triple("🟢", getString(R.string.threat_level_safe), R.color.status_safe)
            1 -> Triple("🟡", getString(R.string.threat_level_low), R.color.status_low)
            2 -> Triple("🟠", getString(R.string.threat_level_medium), R.color.status_medium)
            3 -> Triple("🔴", getString(R.string.threat_level_high), R.color.status_high)
            else -> Triple("⚫", getString(R.string.threat_level_critical), R.color.status_critical)
        }
        
        threatLevelText.text = "$icon $text"
        threatLevelText.setTextColor(ContextCompat.getColor(this, color))
    }
    
    /**
     * Toggle protection on/off
     */
    private fun toggleProtection() {
        if (isMonitoring) {
            stopMonitoring()
        } else {
            checkPermissionsAndStart()
        }
    }
    
    /**
     * Check permissions before starting
     */
    private fun checkPermissionsAndStart() {
        val needed = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needed.isEmpty()) {
            startMonitoring()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
    
    /**
     * Start the guardian service
     */
    private fun startMonitoring() {
        GuardianService.start(this)
        isMonitoring = true
        
        statusText.text = getString(R.string.status_monitoring)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_safe))
        toggleButton.text = getString(R.string.btn_stop_protection)
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.status_high))
    }
    
    /**
     * Stop the guardian service
     */
    private fun stopMonitoring() {
        GuardianService.stop(this)
        isMonitoring = false
        
        statusText.text = getString(R.string.status_stopped)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary_light))
        toggleButton.text = getString(R.string.btn_start_protection)
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
    }
}