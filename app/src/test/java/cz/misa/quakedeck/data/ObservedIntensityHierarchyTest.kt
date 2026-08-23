package cz.misa.quakedeck.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedIntensityHierarchyTest {
    @Test
    fun buildsCodeKeyedFourLevelHierarchyAndAggregatesMaximums() {
        val strong = IntensityPoint(
            name = "北海道 · 石狩市花川",
            intensity = "5+",
            prefecture = "北海道",
            stationName = "石狩市花川"
        )
        val weak = IntensityPoint(
            name = "北海道 · 石狩市聚富",
            intensity = "3",
            prefecture = "北海道",
            stationName = "石狩市聚富"
        )

        val hierarchy = buildObservedIntensityHierarchy(listOf(weak, strong)) { point ->
            when (point.stationName) {
                "石狩市花川" -> ObservedStationIdentity(
                    stationCode = "0123500",
                    areaCode = "100",
                    areaNameJa = "石狩地方北部",
                    municipalityCode = "0123500",
                    municipalityNameJa = "石狩市",
                    municipalityNameEn = "Ishikari City"
                )
                else -> ObservedStationIdentity(
                    stationCode = "0123501",
                    areaCode = "100",
                    areaNameJa = "石狩地方北部",
                    municipalityCode = "0123500",
                    municipalityNameJa = "石狩市",
                    municipalityNameEn = "Ishikari City"
                )
            }
        }

        assertEquals(1, hierarchy.size)
        assertEquals("5+", hierarchy.single().maximumIntensity)
        assertEquals("100", hierarchy.single().areas.single().code)
        assertEquals("5+", hierarchy.single().areas.single().maximumIntensity)
        val municipality = hierarchy.single().areas.single().municipalities.single()
        assertEquals("0123500", municipality.code)
        assertEquals(listOf(strong, weak), municipality.points)
        assertEquals("5+", municipality.maximumIntensity)
    }

    @Test
    fun preservesAreaOnlyReportsWithoutInventingMunicipalities() {
        val area = IntensityPoint(
            name = "東京都 · 東京地方",
            intensity = "4",
            prefecture = "東京都",
            stationName = "東京地方",
            isArea = true
        )

        val group = buildObservedIntensityHierarchy(listOf(area)) { null }
            .single()
            .areas
            .single()

        assertEquals("東京地方", group.nameJa)
        assertEquals(listOf(area), group.directPoints)
        assertEquals(emptyList<ObservedHierarchyMunicipality>(), group.municipalities)
    }

    @Test
    fun conservesTokyoStationRowsAndTheirActualMaximumsAcrossEveryTier() {
        val source = buildList {
            add("三宅村神着" to Triple("3", "357", "三宅島"))
            add("三宅村坪田" to Triple("3", "357", "三宅島"))
            add("御蔵島村西川" to Triple("3", "357", "三宅島"))
            add("三宅村阿古" to Triple("2", "357", "三宅島"))
            add("東京千代田区大手町" to Triple("2", "350", "東京都２３区"))
            add("神津島村金長" to Triple("2", "354", "神津島"))
            repeat(87) { index ->
                add("東京試験観測点$index" to Triple("1", "351", "東京都多摩東部"))
            }
        }
        val points = source.map { (name, tier) ->
            IntensityPoint(
                name = "東京都 · $name",
                intensity = tier.first,
                prefecture = "東京都",
                stationName = name
            )
        }
        val identityByName = source.mapIndexed { index, (name, tier) ->
            name to ObservedStationIdentity(
                stationCode = (13_000_00 + index).toString().padStart(7, '0'),
                areaCode = tier.second,
                areaNameJa = tier.third,
                municipalityCode = "13${index.toString().padStart(5, '0')}",
                municipalityNameJa = name
            )
        }.toMap()

        val tokyo = buildObservedIntensityHierarchy(points) { point ->
            identityByName[point.stationName]
        }.single()

        assertEquals(93, tokyo.allPoints.size)
        assertEquals(points.toSet(), tokyo.allPoints.toSet())
        assertEquals("3", tokyo.maximumIntensity)
        assertEquals("3", tokyo.areas.single { it.code == "357" }.maximumIntensity)
        assertEquals("2", tokyo.areas.single { it.code == "350" }.maximumIntensity)
        assertEquals("2", tokyo.areas.single { it.code == "354" }.maximumIntensity)
        assertEquals("1", tokyo.areas.single { it.code == "351" }.maximumIntensity)
    }

    @Test
    fun keepsBothKumamotoAreasAndAllSixteenReportingStations() {
        data class SourceStation(
            val name: String,
            val intensity: String,
            val areaCode: String,
            val areaName: String,
            val municipalityCode: String
        )

        val source = buildList {
            repeat(6) { index ->
                add(SourceStation("八代市観測点$index", if (index < 3) "2" else "1", "741", "熊本県熊本", "4320200"))
            }
            repeat(5) { index ->
                add(SourceStation("宇城市観測点$index", if (index == 0) "2" else "1", "741", "熊本県熊本", "4321300"))
            }
            add(SourceStation("熊本南区城南町", "1", "741", "熊本県熊本", "4310400"))
            add(SourceStation("甲佐町豊内", "1", "741", "熊本県熊本", "4344400"))
            add(SourceStation("氷川町島地", "1", "741", "熊本県熊本", "4346800"))
            add(SourceStation("氷川町宮原", "1", "741", "熊本県熊本", "4346800"))
            add(SourceStation("上天草市松島町", "1", "743", "熊本県天草・芦北", "4321200"))
        }
        val points = source.map { station ->
            IntensityPoint(
                name = "熊本県 · ${station.name}",
                intensity = station.intensity,
                prefecture = "熊本県",
                stationName = station.name
            )
        }
        val identities = source.mapIndexed { index, station ->
            station.name to ObservedStationIdentity(
                stationCode = (43_000_00 + index).toString(),
                areaCode = station.areaCode,
                areaNameJa = station.areaName,
                municipalityCode = station.municipalityCode,
                municipalityNameJa = station.name.substringBefore("観測点")
            )
        }.toMap()

        val kumamoto = buildObservedIntensityHierarchy(points) { point ->
            identities[point.stationName]
        }.single()

        assertEquals(16, kumamoto.allPoints.size)
        assertEquals(2, kumamoto.areas.size)
        assertEquals("2", kumamoto.maximumIntensity)
        assertEquals(15, kumamoto.areas.single { it.code == "741" }.allPoints.size)
        assertEquals("2", kumamoto.areas.single { it.code == "741" }.maximumIntensity)
        assertEquals(1, kumamoto.areas.single { it.code == "743" }.allPoints.size)
        assertEquals("1", kumamoto.areas.single { it.code == "743" }.maximumIntensity)
        assertEquals(6, kumamoto.areas.sumOf { it.municipalities.size })
    }
}
