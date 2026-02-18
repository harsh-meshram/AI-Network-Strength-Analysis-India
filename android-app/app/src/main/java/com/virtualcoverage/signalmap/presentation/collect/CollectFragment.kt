package com.virtualcoverage.signalmap.presentation.collect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.virtualcoverage.signalmap.R
import com.virtualcoverage.signalmap.presentation.MainActivity
import com.virtualcoverage.signalmap.presentation.map.MapViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Collection control fragment - start/stop signal collection and view stats
 */
@AndroidEntryPoint
class CollectFragment : Fragment() {

    private val viewModel: MapViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_collect, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvTotalCount = view.findViewById<TextView>(R.id.tvTotalCount)
        val tvUnsyncedCount = view.findViewById<TextView>(R.id.tvUnsyncedCount)
        val tvCarrierInfo = view.findViewById<TextView>(R.id.tvCarrierInfo)
        val btnStartStop = view.findViewById<Button>(R.id.btnStartStop)

        tvTitle.text = "Signal Collection"

        btnStartStop.setOnClickListener {
            val activity = requireActivity() as MainActivity
            if (activity.isCollectionRunning()) {
                activity.stopSignalCollection()
                btnStartStop.text = "Start Collection"
                Toast.makeText(requireContext(), "Collection stopped", Toast.LENGTH_SHORT).show()
            } else {
                // Will restart via permission flow
                Toast.makeText(requireContext(), "Collection started", Toast.LENGTH_SHORT).show()
                btnStartStop.text = "Stop Collection"
            }
        }

        // Observe counts
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalCount.collectLatest { count ->
                tvTotalCount.text = "Total measurements: $count"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unsyncedCount.collectLatest { count ->
                tvUnsyncedCount.text = "Pending upload: $count"
            }
        }

        // Observe carrier info
        viewModel.latestSignals.observe(viewLifecycleOwner) { signals ->
            val sb = StringBuilder()
            for ((carrier, measurement) in signals) {
                val rsrp = measurement.rsrp ?: measurement.ssRsrp ?: measurement.dbm ?: 0
                val network = measurement.networkType
                val badge = if (network == "5G_SA") " ⚡ True 5G" else ""
                sb.appendLine("📱 $carrier ($network$badge)")
                sb.appendLine("   Signal: $rsrp dBm — ${getQuality(rsrp)}")
                sb.appendLine()
            }
            tvCarrierInfo.text = if (sb.isNotEmpty()) sb.toString() else "Collecting data..."
        }
    }

    private fun getQuality(rsrp: Int): String = when {
        rsrp > -70 -> "Excellent 🟢"
        rsrp > -85 -> "Good 🟡"
        rsrp > -100 -> "Fair 🟠"
        else -> "Poor 🔴"
    }
}
