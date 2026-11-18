package cz.misa.quakedeck.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedBulletinIdentityTest {
    @Test
    fun seaOfJapanOfficialAndRecoveredPublicReportMatchAcrossTheOriginRevision() {
        assertTrue(sameConfirmedBulletinAcrossOrigins(official, publicReport))
        assertTrue(sameConfirmedBulletinAcrossOrigins(publicReport, official))
        assertTrue(sameConfirmedBulletinAcrossOrigins(
            official.copy(reportIssuedAt = "2026/11/18 18:40:58"),
            publicReport.copy(reportCorrection = " none ")
        ))
    }

    @Test
    fun differingKnownJmaIdentitiesCannotBeJoinedThroughTheSameBulletinFields() {
        val otherKnown = publicReport.copy(jmaReportEventId = "20261118183759")
        assertFalse(sameConfirmedBulletinAcrossOrigins(official, otherKnown))
        assertFalse(sameConfirmedBulletinAcrossOrigins(otherKnown, official))
    }

    @Test
    fun aDifferentIssueProductOrCorrectionRemainsASeparateBulletin() {
        val otherBulletins = listOf(
            publicReport.copy(reportIssuedAt = "2026-11-18 18:40:59 JST"),
            publicReport.copy(reportType = "Destination"),
            publicReport.copy(reportType = null),
            publicReport.copy(reportCorrection = "ScaleOnly"),
            publicReport.copy(reportIssuedAt = null)
        )
        otherBulletins.forEach { assertFalse(sameConfirmedBulletinAcrossOrigins(official, it)) }
        assertTrue(sameConfirmedBulletinAcrossOrigins(
            official.copy(reportCorrection = "ScaleAndDestination"),
            publicReport.copy(reportCorrection = " scaleanddestination ")
        ))
    }

    @Test
    fun originRevisionIsBoundedToNinetySeconds() {
        assertTrue(sameConfirmedBulletinAcrossOrigins(
            official,
            publicReport.copy(originTime = "2026-11-18 18:37:30 JST")
        ))
        assertFalse(sameConfirmedBulletinAcrossOrigins(
            official,
            publicReport.copy(originTime = "2026-11-18 18:37:31 JST")
        ))
        assertFalse(sameConfirmedBulletinAcrossOrigins(
            official,
            publicReport.copy(originTime = "—")
        ))
    }

    @Test
    fun hypocenterMagnitudeAndDepthMustCorroborateTheSharedBulletin() {
        val mismatches = listOf(
            publicReport.copy(hasHypocenter = false),
            publicReport.copy(latitude = Double.NaN),
            publicReport.copy(latitude = -200.0),
            publicReport.copy(latitude = 38.86),
            publicReport.copy(longitude = Double.NaN),
            publicReport.copy(longitude = 138.1),
            publicReport.copy(magnitude = 0.0),
            publicReport.copy(magnitude = 5.11),
            publicReport.copy(depthKm = -1),
            publicReport.copy(depthKm = 12)
        )
        mismatches.forEach { assertFalse(sameConfirmedBulletinAcrossOrigins(official, it)) }
        assertTrue(sameConfirmedBulletinAcrossOrigins(
            official,
            publicReport.copy(latitude = 38.84, longitude = 137.94, magnitude = 5.05, depthKm = 11)
        ))
    }

    @Test
    fun forecastsExercisesAndWithdrawnReportsAreExcluded() {
        val ineligible = listOf(
            publicReport.copy(kind = EarthquakeEventKind.EEW),
            publicReport.copy(isDrill = true),
            publicReport.copy(isCancelled = true)
        )
        ineligible.forEach {
            assertFalse(sameConfirmedBulletinAcrossOrigins(official, it))
            assertFalse(sameConfirmedBulletinAcrossOrigins(it, official))
        }
    }

    private val official = EarthquakeEvent(
        id = "quake:jma:20261118183714",
        place = "日本海中部",
        originTime = "2026-11-18 18:36:00 JST",
        magnitude = 5.0,
        depthKm = 10,
        maxIntensity = "3",
        latitude = 38.8,
        longitude = 137.9,
        points = emptyList(),
        reportIssuedAt = "2026-11-18 18:40:58 JST",
        reportStage = EarthquakeReportStage.DETAILED,
        reportType = "DetailScale",
        jmaReportEventId = "20261118183714"
    )

    private val publicReport = official.copy(
        id = "quake:2026/11/18 18:37:00",
        originTime = "2026-11-18 18:37:00 JST",
        jmaReportEventId = null
    )
}
