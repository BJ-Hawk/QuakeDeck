package cz.misa.quakedeck.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class DmDssConfirmedReportParserTest {
    @Test
    fun p2pConfirmedReportsFillOnlyMissingDmDssCoverage() {
        val complete = DmDssFeed.CONFIRMED.types
        assertFalse(shouldUseP2pConfirmedReport("DetailScale", complete))
        assertFalse(shouldUseP2pConfirmedReport("UnrecognizedType", complete))
        assertFalse(shouldUseP2pConfirmedReport(null, complete))
        assertFalse(shouldUseP2pConfirmedReport(" DetailScale ", setOf("VXSE53")))
        assertTrue(shouldUseP2pConfirmedReport("Destination", setOf("VXSE53")))
        assertTrue(shouldUseP2pConfirmedReport("DetailScale", emptySet()))
    }

    @Test
    fun mapsEachBulletinWithStableJmaIdentityAndTheOriginalReportTime() {
        val initial = normalized("VXSE51", JSONObject().put("intensity", JSONObject()
            .put("maxInt", "5+")
            .put("regions", JSONArray().put(JSONObject().put("code", "250")
                .put("name", "福島県中通り").put("maxInt", "5+")))))
        val hypocenter = normalized("VXSE52", JSONObject().put("earthquake", earthquake()))
        val detailed = normalized("VXSE53", JSONObject().put("earthquake", earthquake())
            .put("intensity", JSONObject().put("maxInt", "5+").put("stations", JSONArray()
                .put(JSONObject().put("code", "1234567").put("name", "観測点").put("int", "5+"))
                .put(JSONObject().put("code", "1234568").put("name", "未入電").put("int", "!5-")))))

        assertEquals("ScalePrompt", initial.getJSONObject("issue").getString("type"))
        assertEquals(50, initial.getJSONObject("earthquake").getInt("maxScale"))
        assertEquals("250", initial.getJSONArray("points").getJSONObject(0).getString("regionCode"))
        assertEquals("2021/02/13 23:08:00", initial.getJSONObject("earthquake").getString("time"))
        assertEquals("Destination", hypocenter.getJSONObject("issue").getString("type"))
        assertEquals("2021/02/13 23:07:00", hypocenter.getJSONObject("earthquake").getString("time"))
        assertEquals(7.1, hypocenter.getJSONObject("earthquake").getJSONObject("hypocenter")
            .getDouble("magnitude"), 0.0)
        assertEquals("DetailScale", detailed.getJSONObject("issue").getString("type"))
        assertEquals(1, detailed.getJSONArray("points").length())
        assertEquals("1234567", detailed.getJSONArray("points").getJSONObject(0).getString("stationCode"))
        for (bulletin in listOf(initial, hypocenter, detailed)) {
            assertEquals("20210213230800", bulletin.getJSONObject("issue").getString("eventId"))
            assertEquals("2021/02/13 23:09:36", bulletin.getJSONObject("issue").getString("time"))
        }
    }

    @Test
    fun acceptsGzippedBase64Bulletin() {
        val envelope = envelope("VXSE53", JSONObject().put("earthquake", earthquake()))
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(envelope.getString("body").toByteArray(Charsets.UTF_8)) }
        envelope.put("encoding", "base64").put("compression", "gzip")
            .put("body", Base64.getEncoder().encodeToString(compressed.toByteArray()))
        assertEquals("DetailScale", DmDssConfirmedReportParser.parseEnvelope(envelope)
            ?.getJSONObject("issue")?.getString("type"))
    }

    @Test
    fun separatesWithdrawalsAndDrillsFromRegularConfirmedReports() {
        val cancelled = requireNotNull(DmDssConfirmedReportParser.parseEnvelope(
            envelope("VXSE53", JSONObject(), infoType = "取消")))
        assertTrue(cancelled.getBoolean("cancelled"))
        assertEquals("20210213230800", cancelled.getJSONObject("issue").getString("eventId"))
        assertNull(DmDssConfirmedReportParser.parseEnvelope(envelope("VXSE51", JSONObject(), test = true)))
        assertNull(DmDssConfirmedReportParser.parseEnvelope(envelope("VXSE51", JSONObject(), status = "訓練")))
    }

    @Test
    fun bulletinIdentitySurvivesDifferentTransportIdsAndKeepsCorrectionsSeparate() {
        val first = normalized("VXSE53", JSONObject().put("earthquake", earthquake()))
        val other = JSONObject(first.toString()).put("id", "history-wrapper")
        other.getJSONObject("issue").put("time", "2021-02-13T14:09:36Z")
        assertNotNull(confirmedBulletinIdentity(first))
        assertEquals(confirmedBulletinIdentity(first), confirmedBulletinIdentity(other))
        other.getJSONObject("issue").put("correct", "ScaleOnly")
        assertNotEquals(confirmedBulletinIdentity(first), confirmedBulletinIdentity(other))
    }

    private fun normalized(type: String, body: JSONObject): JSONObject =
        requireNotNull(DmDssConfirmedReportParser.parseEnvelope(envelope(type, body)))

    private fun earthquake(): JSONObject = JSONObject()
        .put("originTime", "2021-02-13T23:07:00+09:00")
        .put("magnitude", JSONObject().put("value", "7.1"))
        .put("hypocenter", JSONObject().put("name", "福島県沖")
            .put("depth", JSONObject().put("value", "60"))
            .put("coordinate", JSONObject()
                .put("latitude", JSONObject().put("value", "37.7"))
                .put("longitude", JSONObject().put("value", "141.8"))))

    private fun envelope(
        type: String,
        body: JSONObject,
        infoType: String = "発表",
        status: String = "通常",
        test: Boolean = false
    ): JSONObject = JSONObject()
        .put("type", "data")
        .put("classification", "telegram.earthquake")
        .put("id", "telegram-wrapper")
        .put("format", "json")
        .put("encoding", "utf-8")
        .put("head", JSONObject().put("type", type).put("test", test))
        .put("body", JSONObject()
            .put("_schema", JSONObject().put("type", "earthquake-information"))
            .put("status", status)
            .put("infoType", infoType)
            .put("eventId", "20210213230800")
            .put("pressDateTime", "2021-02-13T14:09:36Z")
            .put("reportDateTime", "2021-02-13T23:09:00+09:00")
            .put("targetDateTime", "2021-02-13T23:08:00+09:00")
            .put("body", body)
            .toString())
}
