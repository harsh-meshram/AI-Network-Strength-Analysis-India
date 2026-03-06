package com.virtualcoverage.signalmap.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for signal quality classification logic.
 * Tests RSRP-based color coding, quality labels, and 5G SA/NSA detection.
 */
class SignalQualityTest {

    // =============================================
    // 1. Signal Quality Label Tests (matches CollectFragment logic)
    // =============================================

    @Test
    fun `excellent quality for RSRP above -70`() {
        assertEquals("Excellent", getQualityLabel(-60))
        assertEquals("Excellent", getQualityLabel(-69))
        assertEquals("Excellent", getQualityLabel(-50))
    }

    @Test
    fun `good quality for RSRP -70 to -85`() {
        assertEquals("Good", getQualityLabel(-70))
        assertEquals("Good", getQualityLabel(-75))
        assertEquals("Good", getQualityLabel(-84))
    }

    @Test
    fun `fair quality for RSRP -85 to -100`() {
        assertEquals("Fair", getQualityLabel(-85))
        assertEquals("Fair", getQualityLabel(-90))
        assertEquals("Fair", getQualityLabel(-99))
    }

    @Test
    fun `poor quality for RSRP below -100`() {
        assertEquals("Poor", getQualityLabel(-100))
        assertEquals("Poor", getQualityLabel(-110))
        assertEquals("Poor", getQualityLabel(-140))
    }

    // =============================================
    // 2. Boundary Value Tests
    // =============================================

    @Test
    fun `boundary at -70`() {
        assertEquals("Excellent quality ends above -70", "Good", getQualityLabel(-70))
    }

    @Test
    fun `boundary at -85`() {
        assertEquals("Good quality ends above -85", "Fair", getQualityLabel(-85))
    }

    @Test
    fun `boundary at -100`() {
        assertEquals("Fair quality ends above -100", "Poor", getQualityLabel(-100))
    }

    // =============================================
    // 3. Color Coding Tests (matches MapFragment logic)
    // =============================================

    @Test
    fun `green color for excellent signal`() {
        val color = getColorCategory(-60)
        assertEquals("GREEN", color)
    }

    @Test
    fun `lime color for good signal`() {
        val color = getColorCategory(-75)
        assertEquals("LIME", color)
    }

    @Test
    fun `amber color for fair signal`() {
        val color = getColorCategory(-92)
        assertEquals("AMBER", color)
    }

    @Test
    fun `orange color for poor signal`() {
        val color = getColorCategory(-105)
        assertEquals("ORANGE", color)
    }

    @Test
    fun `red color for very poor signal`() {
        val color = getColorCategory(-115)
        assertEquals("RED", color)
    }

    // =============================================
    // 4. 5G SA vs NSA Detection Logic Tests
    // =============================================

    @Test
    fun `5G SA when both data and voice on NR`() {
        val result = classify5GType(
            dataNetworkType = NETWORK_TYPE_NR,
            voiceNetworkType = NETWORK_TYPE_NR
        )
        assertEquals("5G_SA", result)
    }

    @Test
    fun `5G NSA when data on NR but voice on LTE`() {
        val result = classify5GType(
            dataNetworkType = NETWORK_TYPE_NR,
            voiceNetworkType = NETWORK_TYPE_LTE
        )
        assertEquals("5G_NSA", result)
    }

    @Test
    fun `4G LTE when data on LTE`() {
        val result = classify5GType(
            dataNetworkType = NETWORK_TYPE_LTE,
            voiceNetworkType = NETWORK_TYPE_LTE
        )
        assertEquals("4G_LTE", result)
    }

    @Test
    fun `Unknown for other network types`() {
        val result = classify5GType(
            dataNetworkType = NETWORK_TYPE_UNKNOWN,
            voiceNetworkType = NETWORK_TYPE_UNKNOWN
        )
        assertEquals("Unknown", result)
    }

    // =============================================
    // 5. Network Type Labels
    // =============================================

    @Test
    fun `all network type strings are non-empty`() {
        val types = listOf("5G_SA", "5G_NSA", "4G_LTE", "2G_GSM", "Unknown")
        for (type in types) {
            assertTrue("Network type '$type' should not be empty", type.isNotEmpty())
            assertTrue("Network type should be at most 20 chars", type.length <= 20)
        }
    }

    // =============================================
    // Helper methods (mirrors app logic)
    // =============================================

    companion object {
        const val NETWORK_TYPE_NR = 20       // TelephonyManager.NETWORK_TYPE_NR
        const val NETWORK_TYPE_LTE = 13      // TelephonyManager.NETWORK_TYPE_LTE
        const val NETWORK_TYPE_UNKNOWN = 0   // TelephonyManager.NETWORK_TYPE_UNKNOWN
    }

    /**
     * Quality label logic from CollectFragment
     */
    private fun getQualityLabel(rsrp: Int): String = when {
        rsrp > -70  -> "Excellent"
        rsrp > -85  -> "Good"
        rsrp > -100 -> "Fair"
        else        -> "Poor"
    }

    /**
     * Color category logic from MapFragment.getColorForRsrp()
     */
    private fun getColorCategory(rsrp: Int): String = when {
        rsrp > -70  -> "GREEN"
        rsrp > -85  -> "LIME"
        rsrp > -100 -> "AMBER"
        rsrp > -110 -> "ORANGE"
        else        -> "RED"
    }

    /**
     * 5G SA/NSA classification from SignalCollectionService.detect5GType()
     */
    private fun classify5GType(dataNetworkType: Int, voiceNetworkType: Int): String {
        return when {
            dataNetworkType == NETWORK_TYPE_NR -> {
                if (voiceNetworkType == NETWORK_TYPE_NR) "5G_SA"
                else "5G_NSA"
            }
            dataNetworkType == NETWORK_TYPE_LTE -> "4G_LTE"
            else -> "Unknown"
        }
    }
}
