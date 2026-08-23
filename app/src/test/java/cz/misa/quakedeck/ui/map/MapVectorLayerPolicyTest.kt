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

    @Test
    fun detailedIntensityFallsBackThroughParentTiers() {
        assertEquals(TierIntensity("5+", 0), inheritedTierIntensity("5+", "4", "3"))
        assertEquals(TierIntensity("4", 1), inheritedTierIntensity(null, "4", "3"))
        assertEquals(TierIntensity("3", 2), inheritedTierIntensity(null, null, "3"))
        assertEquals(null, inheritedTierIntensity(null, "—", null))
    }

    @Test
    fun municipalityUsesStableCodeParentageForFadedAreaAndPrefectureColours() {
        val parents = mapOf(
            "1320100" to ("351" to "東京都"),
            "1320200" to ("352" to "東京都")
        )

        assertEquals(
            TierIntensity("2", 1),
            municipalityTierIntensity(
                municipalityCode = "1320100",
                directByMunicipalityCode = emptyMap(),
                parentsByMunicipalityCode = parents,
                directByAreaCode = mapOf("351" to "2"),
                directByPrefecture = mapOf("東京都" to "3")
            )
        )
        assertEquals(
            TierIntensity("3", 2),
            municipalityTierIntensity(
                municipalityCode = "1320200",
                directByMunicipalityCode = emptyMap(),
                parentsByMunicipalityCode = parents,
                directByAreaCode = emptyMap(),
                directByPrefecture = mapOf("東京都" to "3")
            )
        )
    }

    @Test
    fun directMunicipalityColourStillWinsOverGeneratedParents() {
        assertEquals(
            TierIntensity("1", 0),
            municipalityTierIntensity(
                municipalityCode = "1320100",
                directByMunicipalityCode = mapOf("1320100" to "1"),
                parentsByMunicipalityCode = mapOf("1320100" to ("351" to "東京都")),
                directByAreaCode = mapOf("351" to "2"),
                directByPrefecture = mapOf("東京都" to "3")
            )
        )
    }

}
