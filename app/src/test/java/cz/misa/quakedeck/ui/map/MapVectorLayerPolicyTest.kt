package cz.misa.quakedeck.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapVectorLayerPolicyTest {
    @Test
    fun zoomBoundariesSelectExactlyOneVectorLayer() {
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(1f))
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(9.999f))
        assertEquals(MapVectorLayer.JMA_EEW_AREAS, mapVectorLayerForZoom(10f))
        assertEquals(MapVectorLayer.JMA_EEW_AREAS, mapVectorLayerForZoom(31.999f))
        assertEquals(MapVectorLayer.MUNICIPALITIES, mapVectorLayerForZoom(32f))
        assertEquals(MapVectorLayer.MUNICIPALITIES, mapVectorLayerForZoom(256f))
    }

    @Test
    fun invalidZoomFallsBackToCoarseLayer() {
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(Float.NaN))
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(-1f))
    }

    @Test
    fun effectiveZoomSwitchesTiersDuringPinch() {
        assertEquals(
            MapVectorLayer.JMA_EEW_AREAS,
            mapVectorLayerForEffectiveZoom(committedZoom = 8f, gestureScale = 1.25f)
        )
        assertEquals(
            MapVectorLayer.MUNICIPALITIES,
            mapVectorLayerForEffectiveZoom(committedZoom = 16f, gestureScale = 2f)
        )
    }

    @Test
    fun geometryAggregationKeepsHighestValidShindo() {
        val values = linkedMapOf<String, String>()

        values.recordHighestShindo("zone", "2")
        values.recordHighestShindo("zone", "5-")
        values.recordHighestShindo("zone", "4")
        values.recordHighestShindo("zone", "—")

        assertEquals("5-", values["zone"])
        assertFalse("missing" in values)
    }

    @Test
    fun shindoZeroRemainsAReportedValue() {
        val values = linkedMapOf<String, String>()

        values.recordHighestShindo("municipality", "0")

        assertEquals("0", values["municipality"])
    }
}
