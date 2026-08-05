package cz.misa.quakedeck.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A deliberately coarse, user-selected Japanese alert location.
 *
 * The model stores one representative point plus the official JMA areas that
 * contain it. A future ACCESS_COARSE_LOCATION implementation can create the
 * exact same object, so notification and EEW policy does not care whether the
 * point came from manual search or Android's approximate location.
 */
data class AlertLocation(
    val displayName: String,
    val city: String,
    val prefecture: String,
    val prefectureJa: String,
    val postalCode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val eewAreaNameJa: String? = null,
    val quakeAreaCode: String? = null,
    val quakeAreaNameJa: String? = null,
    val resolutionKind: AlertLocationResolutionKind = AlertLocationResolutionKind.CITY
) {
    companion object {
        val DEFAULT_TOKYO = AlertLocation(
            displayName = "Tokyo",
            city = "Tokyo",
            prefecture = "Tokyo",
            prefectureJa = "東京都",
            postalCode = "100",
            latitude = 35.6762,
            longitude = 139.6503,
            eewAreaNameJa = "東京",
            resolutionKind = AlertLocationResolutionKind.CITY
        )
    }
}

enum class AlertLocationResolutionKind {
    CITY,
    POSTAL_PREFIX,
    POSTAL_CODE
}

/** Text-to-place resolver used by the manual alert-location picker. */
object ManualAlertLocationResolver {
    private const val MAX_RESULTS = 8
    private val SEARCH_NORMALIZATION_PATTERN = Regex("[^\\p{L}\\p{N}]")

    suspend fun search(
        context: Context,
        rawQuery: String,
        language: PlaceNameLanguage = PlaceNameLanguage.ENGLISH
    ): Result<List<AlertLocation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = rawQuery.trim()
                require(query.isNotBlank()) { "Enter a city or Japanese postcode." }
                require(Geocoder.isPresent()) { "No geocoding service is available on this device." }

                val digits = query
                    .filter(Char::isDigit)
                    .map { it.digitToInt().digitToChar() }
                    .joinToString("")
                val kind = when (digits.length) {
                    3 -> AlertLocationResolutionKind.POSTAL_PREFIX
                    7 -> AlertLocationResolutionKind.POSTAL_CODE
                    else -> AlertLocationResolutionKind.CITY
                }
                if (digits.isNotEmpty() && digits.length != 3 && digits.length != 7) {
                    error("Use either the first 3 digits or all 7 digits of a Japanese postcode.")
                }

                val geocoderLocale = if (language == PlaceNameLanguage.JAPANESE) {
                    Locale.JAPANESE
                } else {
                    Locale.ENGLISH
                }
                val geocoder = Geocoder(context.applicationContext, geocoderLocale)
                val addresses = LinkedHashMap<String, Address>()
                var lastLookupFailure: Throwable? = null
                searchQueries(context, query, digits).forEach { candidateQuery ->
                    val candidates = runCatching {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(candidateQuery, MAX_RESULTS).orEmpty()
                    }.onFailure { lastLookupFailure = it }
                        .getOrDefault(emptyList())
                    candidates.asSequence()
                        .filter(::isJapaneseAddress)
                        .forEach { address ->
                            val key = "%.5f".format(Locale.US, address.latitude) + "|" +
                                "%.5f".format(Locale.US, address.longitude)
                            addresses.putIfAbsent(key, address)
                        }
                }
                if (addresses.isEmpty()) {
                    lastLookupFailure?.let { throw it }
                }

                val geometry = JmaAreaGeometry.load(context)
                addresses.values.mapNotNull { address ->
                    address.toAlertLocation(
                        geometry = geometry,
                        requestedDigits = digits.takeIf { it.isNotBlank() },
                        kind = kind
                    )
                }.distinctBy { location ->
                    listOf(
                        normalizeSearchText(location.city),
                        normalizeSearchText(location.prefecture),
                        "%.3f".format(Locale.US, location.latitude),
                        "%.3f".format(Locale.US, location.longitude)
                    ).joinToString("|")
                }.take(MAX_RESULTS)
            }
        }

    private fun searchQueries(context: Context, raw: String, digits: String): List<String> {
        if (digits.length == 3) {
            // Japan Post commonly uses the all-zero suffix as the municipality's
            // broad catch-all code. It gives Android's geocoder a full code while
            // retaining the user's intentionally coarse three-digit selection.
            return listOf(
                "${digits}-0000, Japan",
                "$digits, Japan",
                "postal code $digits, Japan"
            )
        }
        if (digits.length == 7) {
            return listOf(
                "${digits.take(3)}-${digits.drop(3)}, Japan",
                "$digits, Japan"
            )
        }

        val canonical = canonicalCityQuery(context, raw)
        return listOf(
            "$canonical, Japan",
            canonical,
            "$raw, Japan",
            raw
        ).distinct()
    }

    /**
     * Resolve common exonyms/romanisation variants and mild misspellings before
     * asking Android's geocoder. Tokyo/Tokio/Tōkyō/Toukyou are therefore one
     * place rather than four unrelated strings.
     */
    private fun canonicalCityQuery(context: Context, raw: String): String {
        val normalized = normalizeSearchText(raw)
        commonAliases[normalized]?.let { return it }

        val aliases = municipalityAliases(context)
        aliases[normalized]?.let { return it }

        val fuzzy = aliases.entries
            .asSequence()
            .map { entry -> entry to levenshtein(normalized, entry.key) }
            .filter { (entry, distance) ->
                val allowed = when {
                    entry.key.length <= 5 -> 1
                    entry.key.length <= 9 -> 2
                    else -> 3
                }
                distance <= allowed
            }
            .minWithOrNull(compareBy<Pair<Map.Entry<String, String>, Int>> { it.second }
                .thenBy { it.first.key.length })
        return fuzzy?.first?.value ?: raw
    }

    @Volatile
    private var cachedMunicipalityAliases: Map<String, String>? = null

    private fun municipalityAliases(context: Context): Map<String, String> {
        cachedMunicipalityAliases?.let { return it }
        return synchronized(this) {
            cachedMunicipalityAliases ?: run {
                val text = context.resources.openRawResource(cz.misa.quakedeck.R.raw.jma_place_names)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val root = JSONObject(text)
                val municipalities = root.optJSONObject("municipality")
                buildMap {
                    if (municipalities != null) {
                        val keys = municipalities.keys()
                        while (keys.hasNext()) {
                            val japanese = keys.next()
                            val english = municipalities.optString(japanese)
                            if (english.isBlank()) continue
                            val city = english.substringBefore(',').trim()
                            putIfAbsent(normalizeSearchText(english), english)
                            putIfAbsent(normalizeSearchText(city), english)
                            putIfAbsent(normalizeSearchText(japanese), japanese)
                        }
                    }
                }
            }.also { cachedMunicipalityAliases = it }
        }
    }

    private fun Address.toAlertLocation(
        geometry: JmaRegionalMapData,
        requestedDigits: String?,
        kind: AlertLocationResolutionKind
    ): AlertLocation? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        val cityName = listOfNotNull(
            locality,
            subAdminArea,
            featureName?.takeUnless(::looksLikePostalCode)
        ).firstOrNull { it.isNotBlank() }
            ?: adminArea
            ?: return null
        val prefectureName = adminArea.orEmpty().ifBlank { cityName }
        val prefectureJapanese = JapanesePrefectureCatalog.toJapanese(prefectureName)
            ?: JapanesePrefectureCatalog.fromCoordinatesFallback(
                geometry = geometry,
                latitude = latitude,
                longitude = longitude
            )
            ?: ""
        val eewArea = geometry.eewAreaAt(latitude, longitude)
        val quakeArea = geometry.quakeAreaAt(latitude, longitude)
        val effectivePostalCode = when {
            kind == AlertLocationResolutionKind.POSTAL_PREFIX -> requestedDigits
            !postalCode.isNullOrBlank() -> postalCode
            else -> requestedDigits
        }
        val display = buildList {
            if (kind == AlertLocationResolutionKind.POSTAL_PREFIX && !effectivePostalCode.isNullOrBlank()) {
                add("〒${effectivePostalCode}")
            }
            add(cityName)
            if (!samePlace(cityName, prefectureName)) add(prefectureName)
        }.joinToString(", ")

        return AlertLocation(
            displayName = display,
            city = cityName,
            prefecture = prefectureName,
            prefectureJa = prefectureJapanese,
            postalCode = effectivePostalCode,
            latitude = latitude,
            longitude = longitude,
            eewAreaNameJa = eewArea?.nameJa,
            quakeAreaCode = quakeArea?.code,
            quakeAreaNameJa = quakeArea?.nameJa,
            resolutionKind = kind
        )
    }

    private fun looksLikePostalCode(value: String): Boolean {
        val compact = value.filterNot(Char::isWhitespace).removePrefix("〒")
        val digits = compact.filter(Char::isDigit)
        return digits.length in setOf(3, 7) && compact.all { it.isDigit() || it == '-' }
    }

    private fun isJapaneseAddress(address: Address): Boolean =
        address.countryCode.equals("JP", ignoreCase = true) ||
            address.countryName?.contains("Japan", ignoreCase = true) == true ||
            address.countryName?.contains("日本") == true ||
            (address.latitude in 20.0..46.5 && address.longitude in 122.0..154.5)

    private fun samePlace(first: String, second: String): Boolean =
        normalizeSearchText(first) == normalizeSearchText(second)

    private val commonAliases = mapOf(
        "tokio" to "Tokyo",
        "tokyo" to "Tokyo",
        "tokyou" to "Tokyo",
        "toukyou" to "Tokyo",
        "とうきょう" to "東京",
        "東京" to "東京",
        "kioto" to "Kyoto",
        "kyoto" to "Kyoto",
        "kyouto" to "Kyoto",
        "きょうと" to "京都",
        "京都" to "京都",
        "osaka" to "Osaka",
        "oosaka" to "Osaka",
        "おおさか" to "大阪",
        "大阪" to "大阪"
    )

    private fun normalizeSearchText(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
            .lowercase(Locale.ROOT)
            .replace("ō", "o")
            .replace("ū", "u")
            .replace("ô", "o")
            .replace("û", "u")
            .replace(SEARCH_NORMALIZATION_PATTERN, "")
            .removeSuffix("city")
            .removeSuffix("shi")
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val substitution = previous[j] + if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    substitution
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}

object JapanesePrefectureCatalog {
    private val englishToJapanese = mapOf(
        "hokkaido" to "北海道", "aomori" to "青森県", "iwate" to "岩手県",
        "miyagi" to "宮城県", "akita" to "秋田県", "yamagata" to "山形県",
        "fukushima" to "福島県", "ibaraki" to "茨城県", "tochigi" to "栃木県",
        "gunma" to "群馬県", "saitama" to "埼玉県", "chiba" to "千葉県",
        "tokyo" to "東京都", "kanagawa" to "神奈川県", "niigata" to "新潟県",
        "toyama" to "富山県", "ishikawa" to "石川県", "fukui" to "福井県",
        "yamanashi" to "山梨県", "nagano" to "長野県", "gifu" to "岐阜県",
        "shizuoka" to "静岡県", "aichi" to "愛知県", "mie" to "三重県",
        "shiga" to "滋賀県", "kyoto" to "京都府", "osaka" to "大阪府",
        "hyogo" to "兵庫県", "nara" to "奈良県", "wakayama" to "和歌山県",
        "tottori" to "鳥取県", "shimane" to "島根県", "okayama" to "岡山県",
        "hiroshima" to "広島県", "yamaguchi" to "山口県", "tokushima" to "徳島県",
        "kagawa" to "香川県", "ehime" to "愛媛県", "kochi" to "高知県",
        "fukuoka" to "福岡県", "saga" to "佐賀県", "nagasaki" to "長崎県",
        "kumamoto" to "熊本県", "oita" to "大分県", "miyazaki" to "宮崎県",
        "kagoshima" to "鹿児島県", "okinawa" to "沖縄県"
    )

    fun toJapanese(value: String): String? {
        if (value.any { it in '\u3400'..'\u9fff' }) {
            return value.takeIf { it.endsWith("都") || it.endsWith("道") || it.endsWith("府") || it.endsWith("県") }
        }
        val normalized = value.lowercase(Locale.ROOT)
            .replace(" prefecture", "")
            .replace(" metropolis", "")
            .replace("-ken", "")
            .replace("-fu", "")
            .replace("-to", "")
            .trim()
        return englishToJapanese[normalized]
    }

    fun fromCoordinatesFallback(
        geometry: JmaRegionalMapData,
        latitude: Double,
        longitude: Double
    ): String? {
        val eewName = geometry.eewAreaAt(latitude, longitude)?.nameJa ?: return null
        return when {
            eewName.startsWith("北海道") -> "北海道"
            eewName == "東京" || eewName == "伊豆諸島" || eewName == "小笠原" -> "東京都"
            eewName == "大阪" -> "大阪府"
            eewName == "京都" -> "京都府"
            else -> eewName
                .removeSuffix("地方")
                .takeIf { it.isNotBlank() }
                ?.let { base ->
                    when (base) {
                        "鹿児島", "奄美(群島)" -> "鹿児島県"
                        "沖縄本島", "大東島", "宮古島", "八重山" -> "沖縄県"
                        else -> "${base}県"
                    }
                }
        }
    }
}
