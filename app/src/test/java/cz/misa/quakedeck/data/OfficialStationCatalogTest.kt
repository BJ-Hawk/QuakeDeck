package cz.misa.quakedeck.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialStationCatalogTest {
    private fun resource(name: String): String = File("src/main/res/raw/$name").readText()

    @Test
    fun officialBundlePreservesEveryAuditedFieldAndApprovedName() {
        val stations = StationCatalog.parseBundledStations(resource("jma_intensity_stations.json"))
        val research = JSONObject(File("../outputs/station-name-audit/station_metadata_sources.json").readText())
            .getJSONArray("stations")
        val byCode = stations.associateBy { it.code }
        val names = JSONObject(resource("station_english_names.json")).getJSONObject("names")
        assertEquals(4_360, stations.size)
        assertEquals(stations.size, byCode.size)
        assertEquals(stations.size, research.length())
        assertEquals(byCode.keys, names.keys().asSequence().toSet())
        for (i in 0 until research.length()) {
            val row = research.getJSONObject(i)
            val station = byCode.getValue(row.getString("code"))
            assertEquals(row.getString("nameJa"), station.nameJa)
            assertEquals(row.getString("prefectureJa"), station.prefectureJa)
            assertEquals(row.getDouble("catalogueLatitude"), station.latitude, 0.0)
            assertEquals(row.getDouble("catalogueLongitude"), station.longitude, 0.0)
            assertEquals(row.getString("providerJa"), station.networkJa)
            assertEquals(row.getString("areaCode"), station.areaCode)
            assertEquals(row.getString("areaNameJa"), station.areaNameJa)
            assertEquals(row.getString("municipalityCode"), station.municipalityCode)
            assertTrue(names.getString(station.code).isNotBlank())
        }
        assertEquals(188, stations.map { it.areaCode }.toSet().size)
        assertEquals(1_894, stations.map { it.municipalityCode }.toSet().size)
        assertEquals(670, stations.count { it.provider == SeismicStationProvider.JMA })
        assertEquals(800, stations.count { it.provider == SeismicStationProvider.NIED })
        assertEquals(2_890, stations.count { it.provider == SeismicStationProvider.LOCAL_GOVERNMENT })
    }

    @Test(expected = IllegalArgumentException::class)
    fun legacyBundleCannotBeAcceptedAsOfficialData() {
        val legacy = JSONObject(resource("jma_intensity_stations.json"))
            .put("source", "legacy-third-party-export")
        StationCatalog.parseBundledStations(legacy.toString())
    }
}
