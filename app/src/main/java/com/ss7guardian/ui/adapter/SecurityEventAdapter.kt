package com.ss7guardian.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ss7guardian.R
import com.ss7guardian.data.entity.SecurityEventEntity
import com.ss7guardian.util.DateUtils

/**
 * SecurityEventAdapter - RecyclerView Adapter for Security Events
 * =================================================================
 * 
 * Displays security events in a RecyclerView list.
 * Uses ListAdapter with DiffUtil for efficient updates.
 * 
 * Features:
 * - Color-coded threat level indicators
 * - Relative timestamps ("2 minutes ago")
 * - Dismiss button for individual events
 * - Click listener for event details
 * 
 * Usage:
 * ```kotlin
 * val adapter = SecurityEventAdapter(
 *     onEventClick = { event -> showDetails(event) },
 *     onDismissClick = { event -> dismissEvent(event) }
 * )
 * recyclerView.adapter = adapter
 * adapter.submitList(events)
 * ```
 * 
 * @param onEventClick Callback when event item is clicked
 * @param onDismissClick Callback when dismiss button is clicked
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
class SecurityEventAdapter(
    private val onEventClick: ((SecurityEventEntity) -> Unit)? = null,
    private val onDismissClick: ((SecurityEventEntity) -> Unit)? = null
) : ListAdapter<SecurityEventEntity, SecurityEventAdapter.EventViewHolder>(
    SecurityEventDiffCallback()
) {
    
    /**
     * ViewHolder for security event items
     * Caches view references for performance
     */
    class EventViewHolder(
        itemView: View,
        private val onEventClick: ((SecurityEventEntity) -> Unit)?,
        private val onDismissClick: ((SecurityEventEntity) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        
        // View references
        private val threatIndicator: View = itemView.findViewById(R.id.viewThreatIndicator)
        private val titleText: TextView = itemView.findViewById(R.id.tvEventTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.tvEventDescription)
        private val timeText: TextView = itemView.findViewById(R.id.tvEventTime)
        private val dismissButton: ImageButton = itemView.findViewById(R.id.btnDismiss)
        
        // Currently bound event
        private var currentEvent: SecurityEventEntity? = null
        
        init {
            // Set click listeners once in init
            itemView.setOnClickListener {
                currentEvent?.let { event -> onEventClick?.invoke(event) }
            }
            
            dismissButton.setOnClickListener {
                currentEvent?.let { event -> onDismissClick?.invoke(event) }
            }
        }
        
        /**
         * Bind event data to views
         * 
         * @param event SecurityEventEntity to display
         */
        fun bind(event: SecurityEventEntity) {
            currentEvent = event
            
            // Set text content
            titleText.text = event.title
            descriptionText.text = event.description
            timeText.text = DateUtils.formatRelative(event.timestamp)
            
            // Set threat level indicator color
            val indicatorColor = getThreatColor(event.threatLevel)
            threatIndicator.backgroundTintList = 
                android.content.res.ColorStateList.valueOf(indicatorColor)
            
            // Show/hide dismiss button based on dismissed state
            dismissButton.visibility = if (event.dismissed) View.GONE else View.VISIBLE
            
            // Dim dismissed events
            itemView.alpha = if (event.dismissed) 0.5f else 1.0f
        }
        
        /**
         * Get color for threat level
         */
        private fun getThreatColor(level: Int): Int {
            val context = itemView.context
            return when (level) {
                0 -> ContextCompat.getColor(context, R.color.status_safe)
                1 -> ContextCompat.getColor(context, R.color.status_low)
                2 -> ContextCompat.getColor(context, R.color.status_medium)
                3 -> ContextCompat.getColor(context, R.color.status_high)
                else -> ContextCompat.getColor(context, R.color.status_critical)
            }
        }
    }
    
    // ========================================
    // Adapter Overrides
    // ========================================
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_security_event, parent, false)
        return EventViewHolder(view, onEventClick, onDismissClick)
    }
    
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    // ========================================
    // DiffUtil Callback
    // ========================================
    
    /**
     * DiffUtil callback for efficient list updates
     * Compares events by ID and content
     */
    class SecurityEventDiffCallback : DiffUtil.ItemCallback<SecurityEventEntity>() {
        
        /**
         * Check if two items represent the same event
         * Uses database ID for identity
         */
        override fun areItemsTheSame(
            oldItem: SecurityEventEntity,
            newItem: SecurityEventEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        /**
         * Check if event content has changed
         * Used to determine if view needs rebinding
         */
        override fun areContentsTheSame(
            oldItem: SecurityEventEntity,
            newItem: SecurityEventEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}