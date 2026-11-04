package cz.misa.quakedeck.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HistoricalCatalogPayloadTest {
    @Test fun confirmedProjectionRetainsListInputsAndLeavesOriginalStationsUntouched() {
        val original = JSONObject("""{
            "code":551,"id":"report-1","time":"2026/10/16 12:40:00",
            "issue":{"type":"DetailScale","correct":"ScaleOnly","time":"2026/10/16 12:39:00"},
            "earthquake":{"time":"2026/10/16 12:38:00","maxScale":70,
                "hypocenter":{"name":"熊本県熊本地方","latitude":32.8,"longitude":130.7,"depth":10,"magnitude":7.2}},
            "points":[{"pref":"熊本県","addr":"熊本市","scale":70}]
        }""")
        val before = original.toString()
        val compact = HistoricalCatalogPayload.fromReport(original)
        listOf("code", "id", "time", "issue", "earthquake").forEach {
            assertEquals(it, original.get(it).toString(), compact.get(it).toString())
        }
        assertFalse(compact.has("points"))
        assertEquals(before, original.toString())
        assertEquals(1, original.getJSONArray("points").length())
    }

    @Test fun largeStationPayloadIsAbsentFromPersistedCatalogue() {
        val original = JSONObject().put("code", 551).put("earthquake", JSONObject()
            .put("time", "2026/10/16 12:38:00").put("maxScale", 70))
            .put("points", JSONArray().apply {
                repeat(4_360) { put(JSONObject().put("addr", "station-$it").put("scale", 70)) }
            })
        val compact = HistoricalCatalogPayload.fromReport(original).toString()
        assertTrue(compact.length * 100 < original.toString().length)
        assertFalse(compact.contains("station-"))
        assertEquals(4_360, original.getJSONArray("points").length())
    }

    @Test fun sourceNeutralForecastRetainsMaximumShindoLevelAndSerialWithoutRegions() {
        val raw = EewArchiveFrame.encode(eew("forecast").copy(
            eewAlertLevel = EewAlertLevel.FORECAST, maxIntensity = "6+",
            points = listOf(IntensityPoint("area", "3", isArea = true))
        ))
        val full = requireNotNull(P2pEewParser.parse(raw))
        val compact = HistoricalCatalogPayload.fromReport(raw)
        assertEquals(full.copy(points = emptyList()), P2pEewParser.parse(compact))
        assertFalse(compact.has("areas"))
        assertEquals(1, raw.getJSONArray("areas").length())
    }

    @Test fun legacyPublicWarningKeepsShindoDerivedFromOpenEndedAreas() {
        val raw = EewArchiveFrame.encode(eew("public"))
        raw.remove("quakedeckMaxIntensity")
        raw.put("areas", JSONArray().put(JSONObject().put("name", "area")
            .put("scaleFrom", 55).put("scaleTo", 99)))
        val full = requireNotNull(P2pEewParser.parse(raw))
        assertEquals("6-", full.maxIntensity)
        assertEquals(full.copy(points = emptyList()),
            P2pEewParser.parse(HistoricalCatalogPayload.fromReport(raw)))
    }

    @Test fun bareCancellationStillUsesPreviousRevisionAndRetainsReportCount() {
        val first = record(EewArchiveFrame.encode(eew("cancelled")))
        val cancel = record(JSONObject().put("code", 556).put("id", "cancel")
            .put("cancelled", true).put("time", "2026/10/16 12:39:00")
            .put("issue", JSONObject().put("eventId", "cancelled").put("serial", "0")
                .put("time", "2026/10/16 12:39:00")))
        val compact = listOf(first, cancel).map {
            it.copy(rawJson = HistoricalCatalogPayload.fromReport(it.rawJson))
        }
        val fullSummary = EewHistory.summaries(listOf(first, cancel), emptyList()).single()
        val summary = EewHistory.summaries(compact, emptyList()).single()
        assertEquals(fullSummary, summary)
        assertTrue(summary.event.isCancelled)
        assertEquals("0", summary.event.reportSerial)
        assertEquals(2, summary.reportCount)
        assertTrue(summary.hasCorrection)
    }

    @Test fun minuteIndexMatchesOriginalAssociationAcrossLargeArchive() {
        val start = LocalDateTime.of(2026, 10, 1, 0, 0)
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
        val confirmed = (0 until 10_000).map { index ->
            eew("confirmed-$index").copy(kind = EarthquakeEventKind.CONFIRMED,
                originTime = start.plusMinutes(index.toLong()).format(format))
        }
        val records = (0 until 40).flatMap { index ->
            val matching = eew("eew-$index").copy(
                originTime = start.plusMinutes(index * 200L).plusSeconds(22).format(format),
                // Half remain standalone because the hypocentre gate fails.
                latitude = if (index % 2 == 0) 32.8 else 10.0)
            listOf(record(EewArchiveFrame.encode(matching)), record(EewArchiveFrame.encode(
                matching.copy(reportSerial = "2", reportIssuedAt = "2026-10-16 12:39:00 JST"))))
        }
        val reference = EewHistory.decoded(records).groupBy { it.second.id }
            .filterValues { revisions -> confirmed.none { report ->
                revisions.any { EewHistory.matchesConfirmed(report, it.second) }
            } }.keys.map { EewHistory.KEY_PREFIX + it }.toSet()
        val actual = EewHistory.summaries(records, confirmed)
        assertEquals(20, actual.size)
        assertEquals(reference, actual.map { it.eventKey }.toSet())
        assertTrue(actual.all { it.reportCount == 2 })
    }

    @Test fun invalidOriginsDoNotMatchEachOtherInMinuteIndex() {
        val bad = eew("invalid").copy(originTime = "unknown")
        val records = listOf(record(EewArchiveFrame.encode(bad)))
        assertEquals(1, EewHistory.summaries(records, listOf(bad.copy(
            kind = EarthquakeEventKind.CONFIRMED))).size)
    }

    private fun eew(id: String) = EarthquakeEvent(
        id = id, place = "熊本県熊本地方", originTime = "2026-10-16 12:38:22 JST",
        magnitude = 6.0, depthKm = 10, maxIntensity = "5-", latitude = 32.8, longitude = 130.7,
        points = emptyList(), kind = EarthquakeEventKind.EEW,
        reportSerial = "1", reportIssuedAt = "2026-10-16 12:38:30 JST"
    )

    private fun record(raw: JSONObject) = ArchivedReportRecord(
        archiveKey = raw.optString("id"), code = 556, sourceTime = raw.optString("time"),
        receivedAt = 1L, issueType = "EEW", eventKey = "event:" +
            raw.getJSONObject("issue").getString("eventId"), rawJson = raw
    )
}
