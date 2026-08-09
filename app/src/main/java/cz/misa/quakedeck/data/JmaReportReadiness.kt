package cz.misa.quakedeck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * JMA publishes the row in its public earthquake list before it publishes the
 * JSON payload used by the detail viewer. A non-empty `json` field is therefore
 * the authoritative signal that opening the detail page will show a report.
 */
enum class JmaReportReadiness {
    CHECKING,
    PREPARING,
    AVAILABLE
}

/** A pending JMA payload replaces only the final detailed-report status. */
internal fun shouldShowOfficialJmaReportPreparing(
    event: EarthquakeEvent,
    readiness: JmaReportReadiness
): Boolean = event.reportStage == EarthquakeReportStage.DETAILED &&
    readiness == JmaReportReadiness.PREPARING

private const val JMA_QUAKE_LIST_URL = "https://www.jma.go.jp/bosai/quake/data/list.json"
private const val JMA_LIST_CACHE_MILLIS = 25_000L

/** Extracts the 14-digit JMA report ID used by the official detail-page link. */
fun officialJmaReportId(event: EarthquakeEvent): String? =
    event.reportIssuedAt
        .orEmpty()
        .filter(Char::isDigit)
        .takeIf { it.length >= 14 }
        ?.take(14)

/**
 * Returns whether JMA has published a usable detail payload for [reportId].
 * A missing matching row is still "preparing": JMA adds it to this list before
 * the report viewer itself becomes usable.
 */
internal fun jmaReportReadinessFromList(
    reportId: String,
    listPayload: String
): JmaReportReadiness {
    if (!listPayload.trimStart().startsWith("[")) return JmaReportReadiness.CHECKING

    // `ctt` is the JMA detail-page/report ID. Limiting the search
    // to the current object means a missing `json` cannot accidentally borrow
    // the value from the next event. This keeps the parsing deliberately small
    // and makes the policy unit-testable without Android's org.json runtime.
    val reportIdMatch = Regex("\\\"ctt\\\"\\s*:\\s*\\\"${Regex.escape(reportId)}\\\"")
        .find(listPayload)
        ?: return JmaReportReadiness.PREPARING
    val afterReportId = listPayload.substring(reportIdMatch.range.last + 1)
    val nextReport = Regex("\\{\\s*\\\"ctt\\\"").find(afterReportId)?.range?.first
        ?: afterReportId.length
    val matchingReport = afterReportId.substring(0, nextReport)
    val json = Regex("\\\"json\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        .find(matchingReport)
        ?.groupValues
        ?.getOrNull(1)
    return if (json.isNullOrBlank()) {
        JmaReportReadiness.PREPARING
    } else {
        JmaReportReadiness.AVAILABLE
    }
}

/**
 * One process-wide, short-lived cache keeps an open report card from repeatedly
 * downloading JMA's full list while still allowing a newly published report to
 * become available promptly.
 */
object JmaReportReadinessChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private data class CachedList(val payload: String, val fetchedAtMillis: Long)

    @Volatile
    private var cachedList: CachedList? = null

    suspend fun readinessFor(reportId: String): JmaReportReadiness = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedList?.takeIf { now - it.fetchedAtMillis < JMA_LIST_CACHE_MILLIS }
        val payload = cached?.payload ?: fetchList()?.also {
            cachedList = CachedList(it, now)
        } ?: return@withContext JmaReportReadiness.CHECKING

        jmaReportReadinessFromList(reportId, payload)
    }

    private fun fetchList(): String? = runCatching {
        val request = Request.Builder().url(JMA_QUAKE_LIST_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
    }.getOrNull()
}
