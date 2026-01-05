package com.ss7guardian.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ss7guardian.SS7GuardianApp
import com.ss7guardian.service.GuardianService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var isMonitoring = false
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var eventsText: TextView

    private val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) startMonitoring()
        else Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        observeEvents()
    }

    private fun setupUI() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "\uD83D\uDEE1\uFE0F SS7 Guardian"
            textSize = 28f
            setTextColor(0xFF1565C0.toInt())
        }

        statusText = TextView(this).apply {
            text = "Status: Stopped"
            textSize = 18f
            setPadding(0, 32, 0, 32)
        }

        toggleButton = Button(this).apply {
            text = "Start Protection"
            setOnClickListener { toggleProtection() }
        }

        eventsText = TextView(this).apply {
            text = "Recent Events:\n"
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(toggleButton)
        layout.addView(eventsText)
        setContentView(layout)
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            SS7GuardianApp.instance.database.securityEventDao().getAllEvents().collectLatest { events ->
                eventsText.text = "Recent Events:\n" + events.take(5).joinToString("\n") {
                    "[${it.threatLevel}] ${it.title}"
                }
            }
        }
    }

    private fun toggleProtection() {
        if (isMonitoring) stopMonitoring() else checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) startMonitoring()
        else permLauncher.launch(needed.toTypedArray())
    }

    private fun startMonitoring() {
        GuardianService.start(this)
        isMonitoring = true
        statusText.text = "Status: \uD83D\uDFE2 Monitoring"
        toggleButton.text = "Stop Protection"
    }

    private fun stopMonitoring() {
        GuardianService.stop(this)
        isMonitoring = false
        statusText.text = "Status: Stopped"
        toggleButton.text = "Start Protection"
    }
}