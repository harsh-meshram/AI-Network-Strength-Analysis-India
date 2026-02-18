package com.virtualcoverage.signalmap.presentation.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualcoverage.signalmap.data.local.dao.H3AggregateResult
import com.virtualcoverage.signalmap.data.local.entity.SignalMeasurementEntity
import com.virtualcoverage.signalmap.data.repository.SignalRepository
import com.virtualcoverage.signalmap.domain.usecase.PrivacyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val signalRepository: SignalRepository,
    private val privacyManager: PrivacyManager
) : ViewModel() {

    // Selected carrier filter (null = all carriers)
    private val _selectedCarrier = MutableLiveData<String?>(null)
    val selectedCarrier: LiveData<String?> = _selectedCarrier

    // Heatmap data for rendering hexagons
    private val _heatmapData = MutableLiveData<List<H3AggregateResult>>(emptyList())
    val heatmapData: LiveData<List<H3AggregateResult>> = _heatmapData

    // Available carriers from stored data
    private val _carriers = MutableLiveData<List<String>>(emptyList())
    val carriers: LiveData<List<String>> = _carriers

    // Latest signal for each carrier (for status display)
    private val _latestSignals = MutableLiveData<Map<String, SignalMeasurementEntity>>(emptyMap())
    val latestSignals: LiveData<Map<String, SignalMeasurementEntity>> = _latestSignals

    // Live count of records
    val totalCount: StateFlow<Int> = signalRepository.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedCount: StateFlow<Int> = signalRepository.getUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadCarriers()
    }

    fun selectCarrier(carrier: String?) {
        _selectedCarrier.value = carrier
    }

    /**
     * Load heatmap data for the given map bounds
     */
    fun loadHeatmapForBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) {
        viewModelScope.launch {
            val data = signalRepository.getHeatmapData(minLat, maxLat, minLng, maxLng)
            _heatmapData.postValue(data)
        }
    }

    /**
     * Load available carriers from database
     */
    private fun loadCarriers() {
        viewModelScope.launch {
            val carrierList = signalRepository.getAllCarriers()
            _carriers.postValue(carrierList)

            // Load latest signal for each carrier
            val signals = mutableMapOf<String, SignalMeasurementEntity>()
            for (carrier in carrierList) {
                signalRepository.getLatestForCarrier(carrier)?.let {
                    signals[carrier] = it
                }
            }
            _latestSignals.postValue(signals)
        }
    }

    fun refreshData() {
        loadCarriers()
    }

    /**
     * Get H3 hex boundary from the PrivacyManager (for rendering polygons on map)
     */
    fun getH3Boundary(h3Index: String): List<Pair<Double, Double>> {
        return privacyManager.getH3Boundary(h3Index)
    }
}
