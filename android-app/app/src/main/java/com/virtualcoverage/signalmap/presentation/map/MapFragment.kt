package com.virtualcoverage.signalmap.presentation.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.virtualcoverage.signalmap.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

/**
 * Map Fragment displaying signal strength heatmap using osmdroid.
 * Renders H3 hexagons colored by average RSRP values.
 */
@AndroidEntryPoint
class MapFragment : Fragment() {

    private val viewModel: MapViewModel by viewModels()
    private lateinit var mapView: MapView
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvRecordCount: TextView
    private lateinit var fabRefresh: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize osmdroid configuration
        Configuration.getInstance().userAgentValue = requireContext().packageName

        // Bind views
        mapView = view.findViewById(R.id.mapView)
        chipGroup = view.findViewById(R.id.chipGroupCarriers)
        tvRecordCount = view.findViewById(R.id.tvRecordCount)
        fabRefresh = view.findViewById(R.id.fabRefresh)

        setupMap()
        setupCarrierChips()
        setupObservers()

        fabRefresh.setOnClickListener {
            viewModel.refreshData()
            loadHeatmapForVisibleArea()
            Toast.makeText(requireContext(), "Refreshing map data...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMap() {
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            // Default center: India
            controller.setCenter(GeoPoint(20.5937, 78.9629))
        }

        // Load heatmap when map moves
        mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
            loadHeatmapForVisibleArea()
        }
    }

    private fun setupCarrierChips() {
        // Add "All" chip
        val allChip = Chip(requireContext()).apply {
            text = "All Carriers"
            isCheckable = true
            isChecked = true
            setOnClickListener {
                viewModel.selectCarrier(null)
                loadHeatmapForVisibleArea()
            }
        }
        chipGroup.addView(allChip)

        // Observe available carriers and add chips dynamically
        viewModel.carriers.observe(viewLifecycleOwner) { carriers ->
            // Remove all except "All" chip
            while (chipGroup.childCount > 1) {
                chipGroup.removeViewAt(1)
            }
            for (carrier in carriers) {
                val chip = Chip(requireContext()).apply {
                    text = carrier
                    isCheckable = true
                    setOnClickListener {
                        viewModel.selectCarrier(carrier)
                        loadHeatmapForVisibleArea()
                    }
                }
                chipGroup.addView(chip)
            }
        }
    }

    private fun setupObservers() {
        // Observe heatmap data and render hexagons
        viewModel.heatmapData.observe(viewLifecycleOwner) { heatmapData ->
            renderHexagons(heatmapData)
        }

        // Observe record count
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalCount.collectLatest { count ->
                tvRecordCount.text = "$count measurements collected"
            }
        }

        // Observe latest signals for status display
        viewModel.latestSignals.observe(viewLifecycleOwner) { signals ->
            // Update carrier chips with signal info
            for ((carrier, measurement) in signals) {
                val rsrp = measurement.rsrp ?: measurement.ssRsrp ?: measurement.dbm
                val networkType = measurement.networkType
                val badge = if (networkType == "5G_SA") " ⚡5G" else ""
                // Update chip text if it exists
                for (i in 0 until chipGroup.childCount) {
                    val chip = chipGroup.getChildAt(i) as? Chip
                    if (chip?.text?.startsWith(carrier) == true) {
                        chip.text = "$carrier ($rsrp dBm$badge)"
                    }
                }
            }
        }
    }

    /**
     * Load heatmap data for the currently visible map area
     */
    private fun loadHeatmapForVisibleArea() {
        val bounds = mapView.boundingBox
        viewModel.loadHeatmapForBounds(
            bounds.latSouth, bounds.latNorth,
            bounds.lonWest, bounds.lonEast
        )
    }

    /**
     * Render H3 hexagons on the map as colored polygons
     */
    private fun renderHexagons(data: List<com.virtualcoverage.signalmap.data.local.dao.H3AggregateResult>) {
        // Clear existing hexagon overlays (keep base map tiles)
        val overlaysToRemove = mapView.overlays.filterIsInstance<Polygon>()
        mapView.overlays.removeAll(overlaysToRemove.toSet())

        for (result in data) {
            val boundary = viewModel.getH3Boundary(result.h3IndexRes9)
            if (boundary.isEmpty()) continue

            val polygon = Polygon(mapView).apply {
                // Set hexagon vertices
                points = boundary.map { GeoPoint(it.first, it.second) }

                // Color based on average RSRP
                fillPaint.color = getColorForRsrp(result.avgRsrp.toInt())
                outlinePaint.color = Color.argb(100, 255, 255, 255)
                outlinePaint.strokeWidth = 1.5f

                // Tooltip on tap
                title = buildString {
                    append("Signal Strength\n")
                    append("Avg RSRP: ${result.avgRsrp.toInt()} dBm\n")
                    append("Samples: ${result.count}\n")
                    append(getSignalQuality(result.avgRsrp.toInt()))
                }
            }
            mapView.overlays.add(polygon)
        }
        mapView.invalidate()
    }

    /**
     * Color coding for RSRP values
     * Green = Excellent, Yellow = Good, Orange = Fair, Red = Poor
     */
    private fun getColorForRsrp(rsrp: Int): Int {
        return when {
            rsrp > -70 ->  Color.argb(160, 34, 197, 94)    // Green - Excellent
            rsrp > -85 ->  Color.argb(160, 132, 204, 22)   // Lime - Good
            rsrp > -100 -> Color.argb(160, 245, 158, 11)   // Amber - Fair
            rsrp > -110 -> Color.argb(160, 249, 115, 22)   // Orange - Poor
            else ->         Color.argb(160, 220, 38, 38)    // Red - Very Poor
        }
    }

    /**
     * Human-readable signal quality label
     */
    private fun getSignalQuality(rsrp: Int): String {
        return when {
            rsrp > -70 ->  "📶 Excellent"
            rsrp > -85 ->  "📶 Good"
            rsrp > -100 -> "📶 Fair"
            rsrp > -110 -> "📶 Poor"
            else ->         "📶 Very Poor"
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        loadHeatmapForVisibleArea()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
