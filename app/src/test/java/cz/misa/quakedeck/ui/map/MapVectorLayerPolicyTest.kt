package cz.misa.quakedeck.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapVectorLayerPolicyTest {
    @Test
    fun zoomBoundariesSelectExactlyOneVectorLayer() {
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(1f))
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(6.499f))
        assertEquals(MapVectorLayer.JMA_QUAKE_AREAS, mapVectorLayerForZoom(6.5f))
        assertEquals(MapVectorLayer.JMA_QUAKE_AREAS, mapVectorLayerForZoom(20.999f))
        assertEquals(MapVectorLayer.MUNICIPALITIES, mapVectorLayerForZoom(21f))
        assertEquals(MapVectorLayer.MUNICIPALITIES, mapVectorLayerForZoom(128f))
    }

    @Test
    fun sourceIsolationHookUsesItsConfiguredLayerAtEveryZoom() {
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(1f, true))
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(128f, true))
    }

    @Test
    fun invalidZoomFallsBackToCoarseLayer() {
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(Float.NaN))
        assertEquals(MapVectorLayer.N03_PREFECTURES, mapVectorLayerForZoom(-1f))
    }

    @Test
    fun effectiveZoomSwitchesTiersDuringPinch() {
        assertEquals(
            MapVectorLayer.JMA_QUAKE_AREAS,
            mapVectorLayerForEffectiveZoom(committedZoom = 5.2f, gestureScale = 1.25f)
        )
        assertEquals(
            MapVectorLayer.MUNICIPALITIES,
            mapVectorLayerForEffectiveZoom(committedZoom = 10.5f, gestureScale = 2f)
        )
    }

    @Test
    fun displayZoomIsNormalizedToTheFormerOnePointFiveView() {
        assertEquals(1f, displayZoomForCameraZoom(1.5f), 0.0001f)
        assertEquals(1.5f, cameraZoomForDisplayZoom(1f), 0.0001f)
        assertEquals(192f, cameraZoomForDisplayZoom(128f), 0.0001f)
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
