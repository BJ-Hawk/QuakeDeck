package cz.misa.quakedeck.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChibaFeltReportRepairTest {
    @Test
    fun confirmedJmaIncidentRecoversTheOriginalAggregateFromTheExport() {
        val repaired = chibaReport.repairChibaFeltReports()
        val restored = requireNotNull(repaired.p2pCrowdSignal)

        assertEquals(originalClusterStart, restored.startedAt)
        assertEquals("2026/11/12 22:29:08.520", restored.updatedAt)
        assertEquals(103, restored.reportCount)
        assertEquals(0.98052, restored.confidence, 0.000001)
        assertEquals(19, restored.areas.size)
        assertEquals(103, restored.areas.sumOf { it.reportCount })
        assertEquals(
            P2pCrowdAreaSignal("241", reportCount = 24, confidence = 1.0, displayGrade = "A"),
            restored.areas.single { it.areaCode == "241" }
        )
        assertEquals(chibaReport, repaired.copy(p2pCrowdSignal = competingCluster))
        assertEquals(repaired, repaired.repairChibaFeltReports())

        val withoutIssueTime = chibaReport.copy(reportIssuedAt = null)
        assertEquals(restored, withoutIssueTime.repairChibaFeltReports().p2pCrowdSignal)
    }

    @Test
    fun publicDetailedBulletinWithoutJmaIdMustMatchEveryFallbackField() {
        val publicReport = chibaReport.copy(id = "quake:2026/11/12 22:27:00", jmaReportEventId = null)
        assertEquals(103, publicReport.repairChibaFeltReports().p2pCrowdSignal?.reportCount)

        val mismatches = listOf(
            publicReport.copy(originTime = "2026-11-12 22:27:01 JST"),
            publicReport.copy(latitude = 35.8),
            publicReport.copy(longitude = 140.8),
            publicReport.copy(reportType = "Destination"),
            publicReport.copy(reportIssuedAt = "2026-11-12 22:30:21 JST"),
            publicReport.copy(jmaReportEventId = "20261112222727")
        )
        mismatches.forEach { assertEquals(it, it.repairChibaFeltReports(attachIfMissing = true)) }
    }

    @Test
    fun sameJmaIdDoesNotRepairAnEewDrillOrWithdrawnBulletin() {
        val ineligibleReports = listOf(
            chibaReport.copy(kind = EarthquakeEventKind.EEW),
            chibaReport.copy(isDrill = true),
            chibaReport.copy(isCancelled = true)
        )
        ineligibleReports.forEach { assertEquals(it, it.repairChibaFeltReports(attachIfMissing = true)) }
    }

    @Test
    fun repairPreservesNewerOriginalAggregateAndUnrelatedClusters() {
        val newerOriginal = competingCluster.copy(
            startedAt = originalClusterStart,
            updatedAt = "2026/11/12 22:31:00.000",
            reportCount = 105
        )
        val unrelated = competingCluster.copy(startedAt = "2026/11/12 22:29:50.174")
        listOf(newerOriginal, unrelated).forEach { signal ->
            val report = chibaReport.copy(p2pCrowdSignal = signal)
            assertEquals(report, report.repairChibaFeltReports(attachIfMissing = true))
        }
    }

    @Test
    fun repairAttachesMissingAggregateOnlyWhenRequested() {
        val report = chibaReport.copy(p2pCrowdSignal = null)
        assertEquals(report, report.repairChibaFeltReports())
        assertEquals(103, report.repairChibaFeltReports(attachIfMissing = true).p2pCrowdSignal?.reportCount)
    }

    private val originalClusterStart = "2026/11/12 22:27:35.786"

    private val competingCluster = P2pCrowdSignal(
        startedAt = "2026/11/12 22:29:50.173",
        updatedAt = "2026/11/12 22:30:28.560",
        reportCount = 4,
        confidence = 0.0,
        areas = listOf(
            P2pCrowdAreaSignal("241", reportCount = 3, confidence = 0.006432119575679608, displayGrade = "E"),
            P2pCrowdAreaSignal("231", reportCount = 1, confidence = 0.0005299017360367575, displayGrade = "E")
        )
    )

    private val chibaReport = EarthquakeEvent(
        id = "quake:jma:20261112222726",
        place = "千葉県北東部",
        originTime = "2026-11-12 22:27:00 JST",
        magnitude = 3.9,
        depthKm = 50,
        maxIntensity = "2",
        latitude = 35.7,
        longitude = 140.7,
        points = emptyList(),
        reportIssuedAt = "2026-11-12 22:30:20 JST",
        reportStage = EarthquakeReportStage.DETAILED,
        reportType = "DetailScale",
        p2pCrowdSignal = competingCluster,
        jmaReportEventId = "20261112222726"
    )
}
