package cz.misa.quakedeck.data

import android.content.Context
import cz.misa.quakedeck.R
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object PlaceNameTranslator {
    private data class Dictionaries(
        val epicenter: Map<String, String>,
        val municipality: Map<String, String>,
        val prefecture: Map<String, String>
    )

    @Volatile
    private var dictionaries: Dictionaries? = null

    // Station labels can be requested every frame at deep zoom. Some unmatched
    // names need a longest-prefix municipality lookup, which is intentionally
    // more expensive than a direct map lookup. Cache final display strings so
    // that cost is paid once per raw JMA place name rather than once per frame.
    private val observationCache = ConcurrentHashMap<String, String>()

    // These are official station titles rather than municipalities, so they
    // cannot be inferred from the JMA municipality-name dictionary.
    private val stationNameAliases = mapOf(
        "福岡空港" to "Fukuoka Airport",
        "鹿児島空港" to "Kagoshima Airport"
    )

    fun shouldUseEnglish(setting: PlaceNameLanguage): Boolean = when (setting) {
        PlaceNameLanguage.ENGLISH,
        PlaceNameLanguage.CZECH -> true
        PlaceNameLanguage.JAPANESE -> false
        PlaceNameLanguage.AUTO -> Locale.getDefault().language.lowercase(Locale.ROOT) != "ja"
    }

    fun epicenter(
        context: Context,
        japanese: String,
        setting: PlaceNameLanguage,
        untranslatedFallback: String? = null
    ): String {
        if (japanese == "Distant earthquake") return UiLocalization.format(context, R.string.distant_earthquake, setting)
        if (japanese == "Unknown hypocenter") return UiLocalization.format(context, R.string.unknown_hypocenter, setting)
        if (!shouldUseEnglish(setting) || japanese.isBlank()) return japanese

        val epicenters = getDictionaries(context).epicenter
        val translated = epicenters[japanese]
            ?: epicenters.keys
                .asSequence()
                .filter { japanese.startsWith(it) }
                .maxByOrNull { it.length }
                ?.let(epicenters::get)
        if (translated != null) return sentenceCaseEpicenterName(translated)

        // Never machine-translate JMA place names. If an unexpected name is not
        // present in the official dictionary, avoid leaking a Japanese-only label
        // into a non-Japanese UI.
        val fallback = if (containsJapanese(japanese)) {
            when (untranslatedFallback) {
                "Distant earthquake" -> UiLocalization.format(context, R.string.distant_earthquake, setting)
                else -> UiLocalization.format(context, R.string.unknown_hypocenter, setting)
            }
        } else japanese
        return sentenceCaseEpicenterName(fallback)
    }

    fun observation(context: Context, rawName: String, setting: PlaceNameLanguage): String {
        if (!shouldUseEnglish(setting) || rawName.isBlank()) return rawName
        return observationCache.getOrPut(rawName) {
            val dict = getDictionaries(context)
            val parts = rawName.split(" · ")
            parts.mapNotNull { part ->
                dict.prefecture[part]
                    ?: stationNameAliases[part]
                    ?: dict.municipality[part]
                    ?: translateLongestMunicipalityPrefix(part, dict.municipality)
                    ?: part.takeUnless(::containsJapanese)
            }.distinct().joinToString(" · ")
        }
    }

    fun prefecture(context: Context, japanese: String, setting: PlaceNameLanguage): String {
        if (!shouldUseEnglish(setting) || japanese.isBlank()) return japanese
        return getDictionaries(context).prefecture[japanese]
            ?: observation(context, japanese, setting)
    }

    /**
     * Translate a JMA initial-intensity reporting area. P2PQuake's ScalePrompt
     * payloads occasionally omit the final \"地方\" from the official JMA area
     * name (for example 熊本県熊本), so try that canonical form as well.
     */
    fun intensityReportingArea(
        context: Context,
        japanese: String,
        setting: PlaceNameLanguage,
        areaCode: String? = null
    ): String {
        if (!shouldUseEnglish(setting) || japanese.isBlank()) return japanese
        val dictionaries = getDictionaries(context)
        val translated = areaCode
            ?.takeIf { it.isNotBlank() }
            ?.let { StationCatalog.reportingAreaEnglishName(context, it, japanese) }
            ?: dictionaries.epicenter[japanese]
            ?: dictionaries.epicenter["${japanese}地方"]
            ?: observation(context, japanese, setting)
        if (translated.isNotBlank()) return translated
        if (!containsJapanese(japanese)) return japanese
        return areaCode
            ?.takeIf { it.isNotBlank() }
            ?.let { "JMA reporting area $it" }
            ?: "JMA reporting area"
    }

    private fun translateLongestMunicipalityPrefix(
        value: String,
        municipality: Map<String, String>
    ): String? {
        val match = municipality.keys
            .asSequence()
            .filter { value.startsWith(it) }
            .maxByOrNull { it.length }
            ?: return null

        // The JMA multilingual dictionary is authoritative. Do not append an
        // untranslated Japanese neighbourhood/station suffix in English mode.
        return municipality[match]
    }

    private fun containsJapanese(value: String): Boolean = value.any { ch ->
        ch in '\u3040'..'\u30ff' || ch in '\u3400'..'\u9fff'
    }

    private fun getDictionaries(context: Context): Dictionaries {
        dictionaries?.let { return it }
        return synchronized(this) {
            dictionaries ?: load(context.applicationContext).also { dictionaries = it }
        }
    }

    private fun load(context: Context): Dictionaries {
        val text = context.resources.openRawResource(R.raw.jma_place_names)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(text)
        return Dictionaries(
            epicenter = root.optJSONObject("epicenter").toMap(),
            municipality = root.optJSONObject("municipality").toMap(),
            prefecture = root.optJSONObject("prefecture").toMap()
        )
    }

    private fun JSONObject?.toMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val result = HashMap<String, String>(length())
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = optString(key, key)
        }
        return result
    }
}

/** JMA's English dictionary contains one lower-case sentence-style place name. */
internal fun sentenceCaseEpicenterName(value: String): String =
    value.replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(Locale.ROOT) else first.toString()
    }
