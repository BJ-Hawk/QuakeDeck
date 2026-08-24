package cz.misa.quakedeck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmDssEewParserTest {
    @Test
    fun parsesForecastEnvelopeAndUsesOverallMaximumBelowRegionalThreshold() {
        val update = DmDssEewParser.parseEnvelope(forecastEnvelope(), previous = null)

        assertNotNull(update)
        update!!
        assertTrue(update.active)
        assertEquals(LiveUpdateKind.EEW, update.kind)
        assertEquals("20260823123456", update.event.id)
        assertEquals("5-", update.event.maxIntensity)
        assertEquals("5+", update.event.points.single().intensity)
        assertEquals("4", update.event.points.single().intensityFrom)
        assertEquals("VXSE45", update.event.reportType)
        assertEquals(EewAlertLevel.FORECAST, update.event.eewAlertLevel)
        assertEquals("3", update.event.reportSerial)
        assertEquals(35.6, update.event.latitude, 0.0001)
        assertEquals(140.1, update.event.longitude, 0.0001)
    }

    @Test
    fun cancellationKeepsKnownEventIdentityAndEndsForecast() {
        val active = DmDssEewParser.parseEnvelope(forecastEnvelope(), previous = null)!!.event
        val cancelled = DmDssEewParser.parseEnvelope(cancellationEnvelope(), active)

        assertNotNull(cancelled)
        cancelled!!
        assertFalse(cancelled.active)
        assertEquals(LiveUpdateKind.EEW_ENDED, cancelled.kind)
        assertEquals(active.id, cancelled.event.id)
        assertTrue(cancelled.event.isCancelled)
        assertEquals("4", cancelled.event.reportSerial)
    }

    @Test
    fun rejectsTestBulletinsAndOtherClassifications() {
        val test = forecastEnvelope().apply {
            getJSONObject("head").put("test", true)
        }
        val warning = forecastEnvelope().put("classification", "eew.warning")

        assertEquals(null, DmDssEewParser.parseEnvelope(test, null))
        assertEquals(null, DmDssEewParser.parseEnvelope(warning, null))
    }

    @Test
    fun pkceChallengeMatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            DmDssOAuthClient.pkceChallenge(verifier)
        )
    }

    @Test
    fun recoveryDatetimeRefinementMatchesDocumentedSecondPrecision() {
        assertEquals(
            "2021-05-01T00:00:00~",
            DmDssOAuthClient.gdEewDatetimeRefinement(1_619_827_200_987L)
        )
    }

    @Test
    fun requiredScopesIncludeRemoteCleanupAndWarningFlagIsPreserved() {
        assertEquals(
            setOf("contract.list", "socket.start", "socket.close", "eew.get.forecast", "gd.eew"),
            DmDssOAuthClient.REQUIRED_SCOPES
        )
        val warning = forecastEnvelope()
        val report = JSONObject(warning.getString("body"))
        report.getJSONObject("body").put("isWarning", true)
        warning.put("body", report.toString())

        assertEquals(
            EewAlertLevel.WARNING,
            DmDssEewParser.parseEnvelope(warning, null)?.event?.eewAlertLevel
        )
        assertTrue(EewAlertLevel.WARNING.notificationEnabled(true, false))
        assertFalse(EewAlertLevel.WARNING.notificationEnabled(false, true))
        assertTrue(EewAlertLevel.FORECAST.notificationEnabled(false, true))
        assertFalse(EewAlertLevel.FORECAST.notificationEnabled(true, false))
        val forecastPolicy = EewAlertLevel.FORECAST.notificationPolicy(
            warningEnabled = false,
            forecastEnabled = true
        )
        assertTrue(forecastPolicy.enabled)
        assertTrue(forecastPolicy.urgent)
        assertTrue(forecastPolicy.allowsLocalAttention)
        assertEquals(
            MinimumLocalEewAttentionIntensity.SHINDO_3,
            EewAlertLevel.FORECAST.officialMinimumAttentionIntensity()
        )
        assertEquals(
            MinimumLocalEewAttentionIntensity.SHINDO_5_LOWER,
            EewAlertLevel.WARNING.officialMinimumAttentionIntensity()
        )
        assertFalse(
            EewAlertLevel.FORECAST.officialMinimumAttentionIntensity().isReachedBy("2")
        )
        assertTrue(
            EewAlertLevel.FORECAST.officialMinimumAttentionIntensity().isReachedBy("3")
        )
        assertFalse(
            EewAlertLevel.WARNING.officialMinimumAttentionIntensity().isReachedBy("4")
        )
        assertTrue(
            EewAlertLevel.WARNING.officialMinimumAttentionIntensity().isReachedBy("5-")
        )
        assertEquals(
            listOf(
                MinimumLocalEewAttentionIntensity.SHINDO_0,
                MinimumLocalEewAttentionIntensity.SHINDO_1,
                MinimumLocalEewAttentionIntensity.SHINDO_2,
                MinimumLocalEewAttentionIntensity.SHINDO_3,
                MinimumLocalEewAttentionIntensity.SHINDO_4
            ),
            EewAlertLevel.FORECAST.allowedAttentionIntensities()
        )
        assertEquals(
            listOf(
                MinimumLocalEewAttentionIntensity.SHINDO_5_LOWER,
                MinimumLocalEewAttentionIntensity.SHINDO_5_UPPER,
                MinimumLocalEewAttentionIntensity.SHINDO_6_LOWER,
                MinimumLocalEewAttentionIntensity.SHINDO_6_UPPER,
                MinimumLocalEewAttentionIntensity.SHINDO_7
            ),
            EewAlertLevel.WARNING.allowedAttentionIntensities()
        )
        val forecastEvent = DmDssEewParser.parseEnvelope(forecastEnvelope(), null)!!.event
        val warningEnvelope = forecastEnvelope()
        val warningReport = JSONObject(warningEnvelope.getString("body"))
        warningReport.getJSONObject("body").put("isWarning", true)
        warningEnvelope.put("body", warningReport.toString())
        val warningEvent = DmDssEewParser.parseEnvelope(
            warningEnvelope,
            previous = forecastEvent
        )!!.event
        assertEquals(forecastEvent.id, warningEvent.id)
        assertEquals(EewAlertLevel.WARNING, warningEvent.eewAlertLevel)
        assertFalse(
            forecastEvent.eewAttentionIdentity() == warningEvent.eewAttentionIdentity()
        )
        assertFalse(
            forecastEvent.eewNotificationIdentity() == warningEvent.eewNotificationIdentity()
        )
    }

    @Test
    fun forecastDeliverySupportsShindoZeroAndEveryBelowThresholdMode() {
        assertEquals(
            ForecastNotificationDelivery.FULL,
            forecastNotificationDelivery(
                predictedIntensity = "0",
                minimumFullIntensity = MinimumLocalEewAttentionIntensity.SHINDO_0,
                belowThresholdMode = ForecastBelowThresholdMode.OFF
            )
        )
        assertEquals(
            ForecastNotificationDelivery.OFF,
            forecastNotificationDelivery(
                predictedIntensity = "2",
                minimumFullIntensity = MinimumLocalEewAttentionIntensity.SHINDO_3,
                belowThresholdMode = ForecastBelowThresholdMode.OFF
            )
        )
        assertEquals(
            ForecastNotificationDelivery.SILENT,
            forecastNotificationDelivery(
                predictedIntensity = "2",
                minimumFullIntensity = MinimumLocalEewAttentionIntensity.SHINDO_3,
                belowThresholdMode = ForecastBelowThresholdMode.SILENT
            )
        )
        assertEquals(
            ForecastNotificationDelivery.REGULAR,
            forecastNotificationDelivery(
                predictedIntensity = "2",
                minimumFullIntensity = MinimumLocalEewAttentionIntensity.SHINDO_3,
                belowThresholdMode = ForecastBelowThresholdMode.REGULAR
            )
        )
        assertEquals(
            ForecastNotificationDelivery.FULL,
            forecastNotificationDelivery(
                predictedIntensity = "3",
                minimumFullIntensity = MinimumLocalEewAttentionIntensity.SHINDO_3,
                belowThresholdMode = ForecastBelowThresholdMode.OFF
            )
        )
    }

    @Test
    fun contractSummaryRequiresAnActiveForecastClassification() {
        val warningOnly = DmDssContractSummary(
            listOf(DmDssContractEntitlement("eew.warning", "EEW warning", 1))
        )
        val forecast = DmDssContractSummary(
            listOf(DmDssContractEntitlement("eew.forecast", "EEW forecast", 1))
        )

        assertFalse(warningOnly.eewForecastAvailable)
        assertTrue(forecast.eewForecastAvailable)
    }

    @Test
    fun parserReportsWhyAProductionEnvelopeWasRejected() {
        val broken = forecastEnvelope().put("body", "not-json")

        assertEquals(
            DmDssEewRejection.UNREADABLE_BODY,
            (DmDssEewParser.parseEnvelopeResult(broken, null) as DmDssEewParseResult.Rejected)
                .reason
        )
    }

    @Test
    fun acceptsShindoThreeForecastWithoutRegionalEntries() {
        val envelope = forecastEnvelope()
        val report = JSONObject(envelope.getString("body"))
        report.getJSONObject("body").getJSONObject("intensity")
            .put("forecastMaxInt", JSONObject().put("from", "3").put("to", "3"))
            .remove("regions")
        envelope.put("body", report.toString())

        val update = DmDssEewParser.parseEnvelope(envelope, null)

        assertNotNull(update)
        assertEquals("3", update!!.event.maxIntensity)
        assertTrue(update.event.points.isEmpty())
        assertEquals(EewAlertLevel.FORECAST, update.event.eewAlertLevel)
    }

    @Test
    fun parsesGdPostEventItemAndRecoveryPolicyPreventsStaleDuplicates() {
        val report = JSONObject(forecastEnvelope().getString("body"))
        val item = report.getJSONObject("body")
            .put("eventId", report.getString("eventId"))
            .put("serial", report.getString("serialNo"))
            .put("dateTime", report.getString("reportDateTime"))
            .put("isLastInfo", true)
        val parsed = DmDssEewParser.parseGdItem(item, null) as DmDssEewParseResult.Accepted
        val now = System.currentTimeMillis()
        val recent = parsed.update.copy(active = true, issuedAtMillis = now - 30_000L)

        assertEquals("20260823123456", recent.event.id)
        assertEquals("5-", recent.event.maxIntensity)
        assertTrue(
            DmDssRecoveryPolicy.shouldDeliver(
                recent,
                DmDssDiagnosticsSnapshot(),
                now
            )
        )
        val alreadyAccepted = DmDssDiagnosticsSnapshot(
            lastAcceptedEventId = recent.event.id,
            lastAcceptedAlertLevel = EewAlertLevel.FORECAST.name
        )
        assertFalse(DmDssRecoveryPolicy.shouldDeliver(recent, alreadyAccepted, now))
        assertTrue(
            DmDssRecoveryPolicy.shouldDeliver(
                recent.copy(event = recent.event.copy(eewAlertLevel = EewAlertLevel.WARNING)),
                alreadyAccepted,
                now
            )
        )
        assertFalse(
            DmDssRecoveryPolicy.shouldDeliver(
                recent.copy(issuedAtMillis = now - 3 * 60_000L - 1L),
                DmDssDiagnosticsSnapshot(),
                now
            )
        )
        assertFalse(
            DmDssRecoveryPolicy.shouldDeliver(
                recent,
                DmDssDiagnosticsSnapshot(),
                now,
                currentActiveEventId = "another-active-event"
            )
        )
    }

    private fun forecastEnvelope(): JSONObject = JSONObject()
        .put("type", "data")
        .put("classification", "eew.forecast")
        .put("id", "telegram-hash")
        .put("format", "json")
        .put("head", JSONObject().put("type", "VXSE45").put("test", false))
        .put(
            "body",
            JSONObject()
                .put("_schema", JSONObject().put("type", "eew-information").put("version", "1.0.0"))
                .put("status", "通常")
                .put("infoType", "発表")
                .put("eventId", "20260823123456")
                .put("serialNo", "3")
                .put("reportDateTime", "2099-08-23T12:35:05+09:00")
                .put(
                    "body",
                    JSONObject()
                        .put("isLastInfo", false)
                        .put("isCanceled", false)
                        .put(
                            "earthquake",
                            JSONObject()
                                .put("originTime", "2099-08-23T12:34:56+09:00")
                                .put(
                                    "hypocenter",
                                    JSONObject()
                                        .put("name", "千葉県北西部")
                                        .put(
                                            "coordinate",
                                            JSONObject()
                                                .put("latitude", JSONObject().put("value", "35.6"))
                                                .put("longitude", JSONObject().put("value", "140.1"))
                                        )
                                        .put("depth", JSONObject().put("value", "70"))
                                )
                                .put("magnitude", JSONObject().put("value", "5.3"))
                        )
                        .put(
                            "intensity",
                            JSONObject()
                                .put("forecastMaxInt", JSONObject().put("from", "4").put("to", "5-"))
                                .put(
                                    "regions",
                                    org.json.JSONArray().put(
                                        JSONObject()
                                            .put("code", "350")
                                            .put("name", "千葉県北西部")
                                            .put("forecastMaxInt", JSONObject().put("from", "4").put("to", "5+"))
                                            .put("arrivalTime", "2099-08-23T12:35:25+09:00")
                                    )
                                )
                        )
                )
                .toString()
        )

    private fun cancellationEnvelope(): JSONObject = JSONObject()
        .put("type", "data")
        .put("classification", "eew.forecast")
        .put("format", "json")
        .put("head", JSONObject().put("type", "VXSE45").put("test", false))
        .put(
            "body",
            JSONObject()
                .put("_schema", JSONObject().put("type", "eew-information").put("version", "1.0.0"))
                .put("status", "通常")
                .put("infoType", "取消")
                .put("eventId", "20260823123456")
                .put("serialNo", "4")
                .put("reportDateTime", "2099-08-23T12:35:20+09:00")
                .put("body", JSONObject().put("isLastInfo", true).put("isCanceled", true))
                .toString()
        )
}
