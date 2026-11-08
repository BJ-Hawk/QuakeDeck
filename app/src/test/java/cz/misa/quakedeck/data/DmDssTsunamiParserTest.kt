package cz.misa.quakedeck.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DmDssTsunamiParserTest {
    @Test
    fun readsWarningGradesArrivalAndQualitativeOrOpenEndedHeights() {
        val major = area("岩手県", "53")
            .put("firstHeight", JSONObject().put("condition", "津波到達中と推測"))
            .put("maxHeight", JSONObject().put("height", JSONObject().put("condition", "巨大")))
        val warning = area("宮城県", "51")
            .put("firstHeight", JSONObject().put("arrivalTime", "2025-07-02T19:45:00+09:00"))
            .put("maxHeight", JSONObject().put("height",
                JSONObject().put("value", "10").put("over", true)))
        val result = bulletin(envelope(major, warning, area("高知県", "62")))
        val areas = result.getJSONArray("areas")

        assertFalse(result.getBoolean("cancelled"))
        assertEquals("2025-07-02T10:30:01Z", result.getJSONObject("issue").getString("time"))
        assertEquals("MajorWarning", areas.getJSONObject(0).getString("grade"))
        assertEquals("津波到達中と推測",
            areas.getJSONObject(0).getJSONObject("firstHeight").getString("condition"))
        assertEquals("巨大", areas.getJSONObject(0).getJSONObject("maxHeight").getString("description"))
        assertEquals("Warning", areas.getJSONObject(1).getString("grade"))
        assertEquals(">10 m", areas.getJSONObject(1).getJSONObject("maxHeight").getString("description"))
        assertEquals("Watch", areas.getJSONObject(2).getString("grade"))
    }

    @Test
    fun aRegionalReleaseOnlyClearsTheAlertWhenNoWarningsOrAdvisoriesRemain() {
        val lift = area("岩手県", "50")
        val coastalFluctuation = area("宮城県", "72")
        assertFalse(bulletin(envelope(lift, area("高知県", "62"))).getBoolean("cancelled"))
        assertTrue(bulletin(envelope(lift, coastalFluctuation)).getBoolean("cancelled"))
        assertTrue(bulletin(envelope(coastalFluctuation)).getBoolean("cancelled"))
        assertFalse(bulletin(envelope(area("岩手県", "71"))).getBoolean("cancelled"))
    }

    @Test
    fun ignoresDrillsAndDoesNotTreatWithdrawalOfObservationAsAnAllClear() {
        val warning = area("岩手県", "52")
        assertEquals(DmDssTsunamiPacket.Ignored,
            DmDssTsunamiParser.parseEnvelope(envelope(warning, test = true)))
        assertEquals(DmDssTsunamiPacket.Ignored,
            DmDssTsunamiParser.parseEnvelope(envelope(warning, status = "訓練")))
        assertEquals(DmDssTsunamiPacket.Ignored,
            DmDssTsunamiParser.parseEnvelope(envelope(type = "VTSE51", infoType = "取消")))
        assertEquals(DmDssTsunamiPacket.Unreadable,
            DmDssTsunamiParser.parseEnvelope(envelope(area("岩手県", "99"))))
    }

    @Test
    fun sourceSwitchRecognizesSameWarningButKeepsEscalationsAndLiftsDistinct() {
        val direct = TsunamiReport(
            id = "dm-dss:report",
            issueTime = "2025-07-02 19:30:01 JST",
            issueType = "VTSE41",
            cancelled = false,
            areas = listOf(TsunamiArea("岩手県", TsunamiGrade.WARNING),
                TsunamiArea("宮城県", TsunamiGrade.NONE))
        )
        val fallback = direct.copy(id = "p2p-wrapper", issueTime = "2025-07-02 19:30:00 JST",
            areas = listOf(TsunamiArea("岩手県", TsunamiGrade.WARNING)))
        assertTrue(sameTsunamiNotice(direct, fallback))
        assertFalse(sameTsunamiNotice(direct, fallback.copy(cancelled = true)))
        assertFalse(sameTsunamiNotice(direct,
            fallback.copy(areas = listOf(TsunamiArea("岩手県", TsunamiGrade.MAJOR_WARNING)))))
    }

    private fun bulletin(envelope: JSONObject): JSONObject =
        (DmDssTsunamiParser.parseEnvelope(envelope) as DmDssTsunamiPacket.Bulletin).packet

    private fun area(name: String, code: String): JSONObject =
        JSONObject().put("name", name).put("kind", JSONObject().put("code", code))

    private fun envelope(
        vararg areas: JSONObject,
        type: String = "VTSE41",
        status: String = "通常",
        infoType: String = "発表",
        test: Boolean = false
    ): JSONObject {
        val forecasts = JSONArray(areas.toList())
        val body = JSONObject().put("tsunami", JSONObject().put("forecasts", forecasts))
        val report = JSONObject()
            .put("_schema", JSONObject().put("type", "tsunami-information"))
            .put("status", status)
            .put("infoType", infoType)
            .put("pressDateTime", "2025-07-02T10:30:01Z")
            .put("body", body)
        return JSONObject()
            .put("type", "data")
            .put("id", "tsunami-bulletin")
            .put("classification", "telegram.earthquake")
            .put("format", "json")
            .put("encoding", "utf-8")
            .put("head", JSONObject().put("type", type).put("test", test))
            .put("body", report.toString())
    }
}
