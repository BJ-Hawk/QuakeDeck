package cz.misa.quakedeck.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.zip.GZIPInputStream

internal data class DmDssEewUpdate(
    val event: EarthquakeEvent,
    val active: Boolean,
    val kind: LiveUpdateKind,
    val status: String,
    val expiresAtMillis: Long?,
    val issuedAtMillis: Long?
)

internal enum class DmDssEewRejection(val diagnostic: String) {
    NOT_DATA("Not a data message"),
    WRONG_CLASSIFICATION("Unexpected classification"),
    WRONG_FORMAT("Unexpected message format"),
    TEST_BULLETIN("Test bulletin ignored"),
    WRONG_TYPE("Unexpected EEW bulletin type"),
    MISSING_BODY("Bulletin body was missing"),
    UNREADABLE_BODY("Bulletin body could not be read"),
    UNSUPPORTED_BODY_ENCODING("Unsupported bulletin body encoding"),
    INVALID_BASE64_BODY("Bulletin body Base64 could not be decoded"),
    UNSUPPORTED_BODY_COMPRESSION("Unsupported bulletin body compression"),
    INVALID_GZIP_BODY("Bulletin body GZIP could not be decompressed"),
    BODY_TOO_LARGE("Decoded bulletin body exceeded the safety limit"),
    WRONG_SCHEMA("Unexpected converted-data schema"),
    NON_STANDARD_STATUS("Non-standard bulletin status"),
    MISSING_EVENT_BODY("Converted EEW body was missing"),
    CANCELLATION_WITHOUT_EVENT("Cancellation had no matching active event"),
    MISSING_EARTHQUAKE("Earthquake details were missing"),
    MISSING_HYPOCENTER("Hypocentre details were missing"),
    MISSING_COORDINATE("Hypocentre coordinates were missing"),
    INVALID_LATITUDE("Hypocentre latitude was invalid"),
    INVALID_LONGITUDE("Hypocentre longitude was invalid")
}

internal sealed interface DmDssEewParseResult {
    data class Accepted(val update: DmDssEewUpdate) : DmDssEewParseResult
    data class Rejected(val reason: DmDssEewRejection) : DmDssEewParseResult
}

internal object DmDssEewParser {
    private const val FORECAST_ACTIVE_MILLIS = 180_000L
    private val jstZone = ZoneId.of("Asia/Tokyo")
    private val jstFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")

    fun parseEnvelope(
        envelope: JSONObject,
        previous: EarthquakeEvent?
    ): DmDssEewUpdate? = (parseEnvelopeResult(envelope, previous) as? DmDssEewParseResult.Accepted)
        ?.update

    fun parseEnvelopeResult(
        envelope: JSONObject,
        previous: EarthquakeEvent?
    ): DmDssEewParseResult {
        if (envelope.optString("type") != "data") return rejected(DmDssEewRejection.NOT_DATA)
        if (envelope.optString("classification") != "eew.forecast") {
            return rejected(DmDssEewRejection.WRONG_CLASSIFICATION)
        }
        if (envelope.optString("format") != "json") {
            return rejected(DmDssEewRejection.WRONG_FORMAT)
        }
        if (envelope.optJSONObject("head")?.optBoolean("test", false) == true) {
            return rejected(DmDssEewRejection.TEST_BULLETIN)
        }

        val type = envelope.optJSONObject("head")?.optString("type").orEmpty()
        if (type !in setOf("VXSE44", "VXSE45")) {
            return rejected(DmDssEewRejection.WRONG_TYPE)
        }
        val bodyText = envelope.optString("body")
        if (bodyText.isBlank()) return rejected(DmDssEewRejection.MISSING_BODY)
        val decodedBody = when (val decoded = decodeBody(envelope, bodyText)) {
            is DmDssBodyDecodeResult.Decoded -> decoded.text
            is DmDssBodyDecodeResult.Rejected -> return rejected(decoded.reason)
        }
        val report = runCatching { JSONObject(decodedBody) }.getOrNull()
            ?: return rejected(DmDssEewRejection.UNREADABLE_BODY)
        if (report.optJSONObject("_schema")?.optString("type") != "eew-information") {
            return rejected(DmDssEewRejection.WRONG_SCHEMA)
        }
        if (report.optString("status") != "通常") {
            return rejected(DmDssEewRejection.NON_STANDARD_STATUS)
        }
        val payload = report.optJSONObject("body")
            ?: return rejected(DmDssEewRejection.MISSING_EVENT_BODY)
        return parseReport(
            report,
            payload,
            type,
            previous,
            "DM-D.S.S EEW forecast",
            envelope.optString("id")
        )
    }

    fun parseGdItem(
        item: JSONObject,
        previous: EarthquakeEvent?
    ): DmDssEewParseResult {
        val telegram = item.optJSONArray("telegrams")?.optJSONObject(0)
        val type = telegram?.optJSONObject("head")?.optString("type")
            .orEmpty().ifBlank { "VXSE45" }
        return parseReport(
            report = item,
            payload = item,
            type = type,
            previous = previous,
            statusPrefix = "DM-D.S.S recovered recent EEW",
            fallbackEventId = ""
        )
    }

    private fun parseReport(
        report: JSONObject,
        payload: JSONObject,
        type: String,
        previous: EarthquakeEvent?,
        statusPrefix: String,
        fallbackEventId: String
    ): DmDssEewParseResult {

        val eventId = report.optString("eventId").ifBlank { fallbackEventId }
        val serial = report.optString("serialNo").ifBlank { report.optString("serial") }
            .takeIf(String::isNotBlank)
        val issuedAtRaw = report.optString("reportDateTime").ifBlank {
            report.optString("dateTime")
        }
        val issuedAt = formatJst(issuedAtRaw).takeUnless { it == "—" }
        val issuedAtMillis = parseMillis(issuedAtRaw)
        val cancelled = payload.optBoolean("isCanceled", false) || report.optString("infoType") == "取消"
        if (cancelled) {
            val active = previous?.takeIf { eventId.isBlank() || it.id == eventId }
                ?: return rejected(DmDssEewRejection.CANCELLATION_WITHOUT_EVENT)
            return accepted(
                DmDssEewUpdate(
                    event = active.copy(
                        reportSerial = serial ?: active.reportSerial,
                        reportIssuedAt = issuedAt ?: active.reportIssuedAt,
                        reportCorrection = report.optString("infoType").takeIf(String::isNotBlank),
                        isCancelled = true
                    ),
                    active = false,
                    kind = LiveUpdateKind.EEW_ENDED,
                    status = "$statusPrefix cancelled",
                    expiresAtMillis = null,
                    issuedAtMillis = issuedAtMillis
                )
            )
        }

        val earthquake = payload.optJSONObject("earthquake")
            ?: return rejected(DmDssEewRejection.MISSING_EARTHQUAKE)
        val hypocenter = earthquake.optJSONObject("hypocenter")
            ?: return rejected(DmDssEewRejection.MISSING_HYPOCENTER)
        val coordinate = hypocenter.optJSONObject("coordinate")
            ?: return rejected(DmDssEewRejection.MISSING_COORDINATE)
        val latitude = coordinate.optJSONObject("latitude")?.optString("value")?.toDoubleOrNull()
            ?: return rejected(DmDssEewRejection.INVALID_LATITUDE)
        val longitude = coordinate.optJSONObject("longitude")?.optString("value")?.toDoubleOrNull()
            ?: return rejected(DmDssEewRejection.INVALID_LONGITUDE)
        val intensity = payload.optJSONObject("intensity")
        val bulletinWarning = payload.optBoolean("isWarning", false)
        val points = parseRegions(intensity?.optJSONArray("regions"), bulletinWarning)
        val forecastMax = intensity?.optJSONObject("forecastMaxInt")
        val maximum = displayIntensity(
            forecastMax?.optString("from").orEmpty(),
            forecastMax?.optString("to").orEmpty()
        ).first.takeUnless { it == "—" }
            ?: points.maxByOrNull { intensityRank(it.intensity) }?.intensity
            ?: "—"
        val originTimeRaw = earthquake.optString("originTime")
            .ifBlank { earthquake.optString("arrivalTime") }
        val expiry = forecastExpiryMillis(originTimeRaw, issuedAtRaw)
        val finalLabel = if (payload.optBoolean("isLastInfo", false)) " · final bulletin" else ""
        val event = EarthquakeEvent(
            id = eventId.ifBlank { "dmdss-$issuedAtRaw" },
            place = hypocenter.optString("name").ifBlank { "EEW" },
            originTime = formatJst(originTimeRaw),
            magnitude = earthquake.optJSONObject("magnitude")
                ?.optString("value")?.toDoubleOrNull() ?: 0.0,
            depthKm = hypocenter.optJSONObject("depth")
                ?.optString("value")?.toIntOrNull() ?: 0,
            maxIntensity = maximum,
            latitude = latitude,
            longitude = longitude,
            points = points,
            kind = EarthquakeEventKind.EEW,
            eewAlertLevel = if (payload.optBoolean("isWarning", false)) {
                EewAlertLevel.WARNING
            } else {
                EewAlertLevel.FORECAST
            },
            reportSerial = serial,
            reportIssuedAt = issuedAt,
            reportType = type,
            reportCorrection = report.optString("infoType").takeIf { it !in setOf("", "発表") },
            eewHypocenterCondition = earthquake.optString("condition")
                .takeIf(String::isNotBlank),
            eewMagnitudeUnit = earthquake.optJSONObject("magnitude")
                ?.optString("unit")?.takeIf(String::isNotBlank),
            eewSourceAccuracy = parseSourceAccuracy(hypocenter.optJSONObject("accuracy"))
        )
        return accepted(
            DmDssEewUpdate(
                event = event,
                active = expiry == null || expiry > System.currentTimeMillis(),
                kind = LiveUpdateKind.EEW,
                status = "$statusPrefix$finalLabel",
                expiresAtMillis = expiry,
                issuedAtMillis = issuedAtMillis
            )
        )
    }

    private fun accepted(update: DmDssEewUpdate) = DmDssEewParseResult.Accepted(update)

    private fun rejected(reason: DmDssEewRejection) = DmDssEewParseResult.Rejected(reason)

    private fun decodeBody(envelope: JSONObject, bodyText: String): DmDssBodyDecodeResult {
        if (bodyText.length > MAX_ENCODED_BODY_CHARS) {
            return DmDssBodyDecodeResult.Rejected(DmDssEewRejection.BODY_TOO_LARGE)
        }
        val encodedBytes = when (envelope.optString("encoding").lowercase()) {
            "" -> bodyText.toByteArray(StandardCharsets.UTF_8)
            "base64" -> runCatching { Base64.getDecoder().decode(bodyText) }.getOrElse {
                return DmDssBodyDecodeResult.Rejected(DmDssEewRejection.INVALID_BASE64_BODY)
            }
            else -> return DmDssBodyDecodeResult.Rejected(
                DmDssEewRejection.UNSUPPORTED_BODY_ENCODING
            )
        }
        val decodedBytes = when (envelope.optString("compression").lowercase()) {
            "" -> encodedBytes
            "gzip" -> decodeBoundedGzip(encodedBytes).getOrElse { failure ->
                return DmDssBodyDecodeResult.Rejected(
                    if (failure is DmDssBodyTooLargeException) {
                        DmDssEewRejection.BODY_TOO_LARGE
                    } else {
                        DmDssEewRejection.INVALID_GZIP_BODY
                    }
                )
            }
            else -> return DmDssBodyDecodeResult.Rejected(
                DmDssEewRejection.UNSUPPORTED_BODY_COMPRESSION
            )
        }
        if (decodedBytes.size > MAX_DECODED_BODY_BYTES) {
            return DmDssBodyDecodeResult.Rejected(DmDssEewRejection.BODY_TOO_LARGE)
        }
        return DmDssBodyDecodeResult.Decoded(
            decodedBytes.toString(StandardCharsets.UTF_8)
        )
    }

    private fun decodeBoundedGzip(bytes: ByteArray): Result<ByteArray> = runCatching {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(GZIP_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_DECODED_BODY_BYTES) {
                    throw DmDssBodyTooLargeException()
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun parseRegions(
        regions: org.json.JSONArray?,
        bulletinWarning: Boolean
    ): List<IntensityPoint> = buildList {
        if (regions == null) return@buildList
        for (index in 0 until regions.length()) {
            val region = regions.optJSONObject(index) ?: continue
            val name = region.optString("name")
            if (name.isBlank()) continue
            val forecast = region.optJSONObject("forecastMaxInt")
            val (display, upperOpenEnded) = displayIntensity(
                forecast?.optString("from").orEmpty(),
                forecast?.optString("to").orEmpty()
            )
            add(
                IntensityPoint(
                    name = name,
                    intensity = display,
                    intensityFrom = forecast?.optString("from")?.takeIf {
                        it.isNotBlank() && it != display
                    },
                    intensityUpperOpenEnded = upperOpenEnded,
                    arrivalTime = formatJst(region.optString("arrivalTime"))
                        .takeUnless { it == "—" },
                    stationName = name,
                    isArea = true,
                    regionCode = region.optString("code").takeIf(String::isNotBlank),
                    isPlum = region.optBoolean("isPlum", false),
                    isWarning = region.optBoolean("isWarning", bulletinWarning)
                )
            )
        }
    }.sortedByDescending { intensityRank(it.intensity) }

    private fun parseSourceAccuracy(accuracy: JSONObject?): EewSourceAccuracy? {
        if (accuracy == null) return null
        val epicenters = buildList {
            val values = accuracy.optJSONArray("epicenters")
            if (values != null) {
                for (index in 0 until values.length()) {
                    values.optInt(index, Int.MIN_VALUE)
                        .takeUnless { it == Int.MIN_VALUE }
                        ?.let(::add)
                }
            }
        }
        return EewSourceAccuracy(
            epicenterRanks = epicenters,
            depthRank = accuracy.optIntOrNull("depth"),
            magnitudeCalculationRank = accuracy.optIntOrNull("magnitudeCalculation"),
            magnitudeStationCountRank = accuracy.optIntOrNull("numberOfMagnitudeCalculation")
        ).takeIf {
            it.epicenterRanks.isNotEmpty() || it.depthRank != null ||
                it.magnitudeCalculationRank != null || it.magnitudeStationCountRank != null
        }
    }

    private fun displayIntensity(from: String, to: String): Pair<String, Boolean> = when {
        to == "over" -> from.ifBlank { "—" } to true
        to.isNotBlank() && to != "不明" -> to to false
        from.isNotBlank() && from != "不明" -> from to false
        else -> "—" to false
    }

    private fun forecastExpiryMillis(originTime: String, reportTime: String): Long? =
        (parseMillis(originTime) ?: parseMillis(reportTime))?.plus(FORECAST_ACTIVE_MILLIS)

    fun forecastExpiryMillis(
        event: EarthquakeEvent,
        receivedAtMillis: Long = System.currentTimeMillis()
    ): Long = (parseMillis(event.originTime) ?: parseMillis(event.reportIssuedAt) ?: receivedAtMillis)
        .plus(FORECAST_ACTIVE_MILLIS)

    private fun parseMillis(value: String?): Long? {
        if (value.isNullOrBlank() || value == "—") return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDateTime.parse(value, jstFormatter).atZone(jstZone).toInstant().toEpochMilli()
            }.getOrNull()
    }

    private fun formatJst(value: String): String {
        if (value.isBlank()) return "—"
        return runCatching {
            OffsetDateTime.parse(value).atZoneSameInstant(jstZone).format(jstFormatter)
        }.getOrDefault(value)
    }

    private fun intensityRank(value: String): Int = when (value) {
        "7" -> 9
        "6+" -> 8
        "6-" -> 7
        "5+" -> 6
        "5-" -> 5
        "4" -> 4
        "3" -> 3
        "2" -> 2
        "1" -> 1
        "0" -> 0
        else -> -1
    }

    private const val MAX_ENCODED_BODY_CHARS = 512 * 1024
    private const val MAX_DECODED_BODY_BYTES = 1024 * 1024
    private const val GZIP_BUFFER_BYTES = 8 * 1024
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private sealed interface DmDssBodyDecodeResult {
    data class Decoded(val text: String) : DmDssBodyDecodeResult
    data class Rejected(val reason: DmDssEewRejection) : DmDssBodyDecodeResult
}

private class DmDssBodyTooLargeException : RuntimeException()

internal fun isDuplicateDmDssRevision(
    candidate: EarthquakeEvent,
    current: EarthquakeEvent
): Boolean {
    if (candidate.id != current.id) return false
    if (candidate.reportSerial.isNullOrBlank() || candidate.reportSerial != current.reportSerial) {
        return false
    }
    return candidate.copy(reportType = null) == current.copy(reportType = null)
}
