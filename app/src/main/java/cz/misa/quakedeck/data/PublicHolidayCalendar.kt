package cz.misa.quakedeck.data

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.edit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit

/** How QuakeDeck chooses the country whose public holidays reuse the weekend schedule. */
enum class HolidayCountryMode { AUTO, MANUAL }

enum class HolidayCountrySource {
    MANUAL,
    MOBILE_NETWORK,
    SIM,
    PHONE_REGION,
    UNAVAILABLE
}

enum class PublicHolidayCalendarStatus {
    READY,
    DOWNLOADING,
    NOT_REQUESTED,
    UNAVAILABLE
}

data class HolidayCountryResolution(
    val countryCode: String?,
    val source: HolidayCountrySource
)

/**
 * Permission-free, on-device country resolution. No carrier name, SIM identifier,
 * location coordinate or schedule preference is read or transmitted.
 */
object HolidayCountryDetector {
    fun resolve(
        context: Context,
        mode: HolidayCountryMode,
        manualCountryCode: String?
    ): HolidayCountryResolution {
        if (mode == HolidayCountryMode.MANUAL) {
            val manual = normalizeCode(manualCountryCode)
                ?.takeIf(PublicHolidayCalendar::isSupportedCountry)
            return HolidayCountryResolution(
                countryCode = manual,
                source = if (manual != null) HolidayCountrySource.MANUAL
                else HolidayCountrySource.UNAVAILABLE
            )
        }

        val telephony = context.getSystemService(TelephonyManager::class.java)
        val networkCode = runCatching { normalizeCode(telephony?.networkCountryIso) }
            .getOrNull()
            ?.takeIf(PublicHolidayCalendar::isSupportedCountry)
        if (networkCode != null) {
            return HolidayCountryResolution(networkCode, HolidayCountrySource.MOBILE_NETWORK)
        }

        val simCode = runCatching { normalizeCode(telephony?.simCountryIso) }
            .getOrNull()
            ?.takeIf(PublicHolidayCalendar::isSupportedCountry)
        if (simCode != null) {
            return HolidayCountryResolution(simCode, HolidayCountrySource.SIM)
        }

        val localeCode = normalizeCode(Locale.getDefault().country)
            ?.takeIf(PublicHolidayCalendar::isSupportedCountry)
        return HolidayCountryResolution(
            countryCode = localeCode,
            source = if (localeCode != null) HolidayCountrySource.PHONE_REGION
            else HolidayCountrySource.UNAVAILABLE
        )
    }

    internal fun normalizeCode(value: String?): String? = value
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
}

private data class CountryHolidayData(
    val loadedYears: Set<Int> = emptySet(),
    val nationalDates: Set<LocalDate> = emptySet(),
    val subdivisionDates: Map<String, Set<LocalDate>> = emptyMap()
)

/**
 * Downloads the complete yearly public-holiday calendar for only the country selected by the
 * user (or resolved from the phone's current network). The ISO country code therefore appears
 * in the Nager.Date request URL; carrier, SIM, schedule and location details do not.
 *
 * The current and following calendar years are cached locally. Subdivision-scoped public
 * holidays are retained, although only country-wide dates are currently applied because the
 * settings UI does not yet select a state/province/canton.
 */
object PublicHolidayCalendar {
    private const val CACHE_PREFIX = "public_holidays_country_"
    private const val CACHE_SUFFIX = ".json"
    private const val PREFERENCES = "quakedeck_public_holidays"
    private const val UPDATE_INTERVAL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    private const val RETRY_INTERVAL_MILLIS = 60L * 60L * 1000L
    private const val API_BASE_URL = "https://date.nager.at/api/v4/Holidays"
    private const val MAX_RESPONSE_BYTES = 1_000_000

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val refreshLock = Any()
    private val refreshingCountries = mutableSetOf<String>()
    private val changeListeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    private var holidaysByCountry: Map<String, CountryHolidayData> = emptyMap()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val filesDir = context.applicationContext.filesDir

            // Remove superseded bootstrap, all-country and seven-day worldwide cache formats.
            listOf(
                "public_holidays.json",
                "public_holidays_all_countries.json",
                "public_holidays_worldwide.json"
            ).forEach { File(filesDir, it).delete() }

            val loaded = linkedMapOf<String, CountryHolidayData>()
            filesDir.listFiles { file ->
                file.isFile && file.name.startsWith(CACHE_PREFIX) &&
                    file.name.endsWith(CACHE_SUFFIX)
            }?.forEach { cacheFile ->
                val parsed = runCatching {
                    parseCountryCache(cacheFile.readText(Charsets.UTF_8))
                }.getOrNull()
                if (parsed == null) {
                    cacheFile.delete()
                } else {
                    loaded[parsed.first] = parsed.second
                }
            }

            holidaysByCountry = loaded
            initialized = true
        }
    }

    /**
     * Refreshes the selected country's current and following year in a background thread.
     * A valid cached year is reused for seven days, and a failed refresh never replaces it.
     */
    fun refreshIfDue(
        context: Context,
        countryCode: String?,
        force: Boolean = false,
        referenceDate: LocalDate = LocalDate.now()
    ) {
        initialize(context)
        val code = HolidayCountryDetector.normalizeCode(countryCode) ?: return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val requestedYears = linkedSetOf(
            referenceDate.minusDays(1).year,
            referenceDate.year,
            referenceDate.plusYears(1).year
        )
        val currentData = holidaysByCountry[code]

        val dueYears = requestedYears.filter { year ->
            val alreadyLoaded = year in (currentData?.loadedYears ?: emptySet())
            val lastSuccess = prefs.getLong(successKey(code, year), 0L)
            val lastAttempt = prefs.getLong(attemptKey(code, year), 0L)
            when {
                force -> true
                !alreadyLoaded && now - lastAttempt >= RETRY_INTERVAL_MILLIS -> true
                alreadyLoaded && now - lastSuccess >= UPDATE_INTERVAL_MILLIS &&
                    now - lastAttempt >= RETRY_INTERVAL_MILLIS -> true
                else -> false
            }
        }
        if (dueYears.isEmpty()) return

        synchronized(refreshLock) {
            if (!refreshingCountries.add(code)) return
        }
        notifyChangeListeners()

        for (year in dueYears) {
            prefs.edit { putLong(attemptKey(code, year), now) }
        }

        Thread({
            try {
                var updated = holidaysByCountry[code] ?: CountryHolidayData()
                var changed = false

                for (year in dueYears) {
                    val downloaded = downloadYear(code, year) ?: continue
                    updated = replaceYear(updated, year, downloaded)
                    prefs.edit {
                        putLong(successKey(code, year), System.currentTimeMillis())
                    }
                    changed = true
                }

                if (changed) {
                    synchronized(this) {
                        holidaysByCountry = holidaysByCountry + (code to updated)
                        writeCountryCacheAtomically(appContext, code, updated)
                    }
                }
            } finally {
                synchronized(refreshLock) {
                    refreshingCountries.remove(code)
                }
                notifyChangeListeners()
            }
        }, "QuakeDeck-Holiday-$code").apply {
            isDaemon = true
            start()
        }
    }

    fun isPublicHoliday(
        date: LocalDate,
        countryCode: String?,
        subdivisionCode: String? = null
    ): Boolean {
        if (!initialized) return false
        val code = HolidayCountryDetector.normalizeCode(countryCode) ?: return false
        val country = holidaysByCountry[code] ?: return false
        if (date.year !in country.loadedYears) return false
        if (date in country.nationalDates) return true

        val subdivision = subdivisionCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?: return false
        return date in (country.subdivisionDates[subdivision] ?: emptySet())
    }

    fun hasCalendarFor(countryCode: String?, date: LocalDate = LocalDate.now()): Boolean {
        val code = HolidayCountryDetector.normalizeCode(countryCode) ?: return false
        return date.year in (holidaysByCountry[code]?.loadedYears ?: emptySet())
    }

    fun isRefreshing(countryCode: String?): Boolean {
        val code = HolidayCountryDetector.normalizeCode(countryCode) ?: return false
        return synchronized(refreshLock) { code in refreshingCountries }
    }

    fun status(
        context: Context,
        countryCode: String?,
        date: LocalDate = LocalDate.now()
    ): PublicHolidayCalendarStatus {
        initialize(context)
        val code = HolidayCountryDetector.normalizeCode(countryCode)
            ?: return PublicHolidayCalendarStatus.UNAVAILABLE
        if (hasCalendarFor(code, date)) return PublicHolidayCalendarStatus.READY
        if (isRefreshing(code)) return PublicHolidayCalendarStatus.DOWNLOADING

        val prefs = context.applicationContext.getSharedPreferences(
            PREFERENCES,
            Context.MODE_PRIVATE
        )
        val lastAttempt = prefs.getLong(attemptKey(code, date.year), 0L)
        return if (lastAttempt == 0L) {
            PublicHolidayCalendarStatus.NOT_REQUESTED
        } else {
            PublicHolidayCalendarStatus.UNAVAILABLE
        }
    }

    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners -= listener
    }

    private fun notifyChangeListeners() {
        changeListeners.forEach { listener -> runCatching { listener() } }
    }

    /** Countries currently listed by Nager.Date as having public-holiday coverage. */
    private val nagerSupportedCountryCodes: Set<String> = setOf(
        "AX", "AL", "AD", "AT", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK",
        "EE", "FO", "FI", "FR", "DE", "GI", "GR", "GG", "HU", "IS", "IE", "IM",
        "IT", "JE", "LV", "LI", "LT", "LU", "MT", "MD", "MC", "ME", "NL", "MK",
        "NO", "PL", "PT", "RO", "RU", "SM", "RS", "SK", "SI", "ES", "SJ", "SE",
        "CH", "UA", "GB", "VA", "AI", "AG", "AR", "AW", "BS", "BB", "BZ", "BM",
        "BO", "BR", "VG", "CA", "BQ", "KY", "CL", "CO", "CR", "CU", "CW", "DM",
        "DO", "EC", "SV", "FK", "GF", "GL", "GD", "GP", "GT", "GY", "HT", "HN",
        "JM", "MQ", "MX", "MS", "NI", "PA", "PY", "PE", "PR", "BL", "KN", "LC",
        "MF", "PM", "VC", "SX", "SR", "TT", "TC", "US", "UM", "VI", "UY", "VE",
        "AQ", "BV", "TF", "HM", "GS", "DZ", "AO", "BJ", "BW", "BF", "BI", "CM",
        "CV", "CF", "TD", "KM", "CG", "DJ", "CD", "EG", "GQ", "ER", "SZ", "ET",
        "GA", "GM", "GH", "GN", "GW", "CI", "KE", "LS", "LR", "LY", "MG", "MW",
        "ML", "MR", "MA", "MZ", "NA", "NE", "NG", "RW", "SH", "ST", "SN", "SC",
        "SL", "SO", "ZA", "SS", "SD", "TZ", "TG", "TN", "UG", "ZM", "ZW", "AU",
        "CX", "CC", "KI", "MH", "NR", "NC", "NZ", "NU", "NF", "PW", "PG", "PN",
        "WS", "SB", "TK", "TO", "TV", "VU", "WF", "AM", "BH", "BD", "KH", "CN",
        "GE", "HK", "ID", "IQ", "JP", "KZ", "MN", "PH", "SG", "KR", "SY", "TR",
        "VN", "YE",
    )

    fun supportedCountryCodes(): Set<String> = nagerSupportedCountryCodes

    fun isSupportedCountry(countryCode: String?): Boolean =
        countryCode?.trim()?.uppercase(Locale.ROOT) in nagerSupportedCountryCodes

    private fun downloadYear(countryCode: String, year: Int): CountryHolidayData? {
        val url = "$API_BASE_URL/$countryCode/$year"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "QuakeDeck/0.9.65 (Android; yearly holiday calendar)")
            .header("Accept", "application/json")
            .build()

        val raw = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string().takeIf { it.length in 2..MAX_RESPONSE_BYTES }
            }
        }.getOrNull() ?: return null

        return runCatching { parseProviderYear(raw, countryCode, year) }.getOrNull()
    }

    private fun parseProviderYear(
        raw: String,
        expectedCountryCode: String,
        expectedYear: Int
    ): CountryHolidayData {
        val array = JSONArray(raw)
        val national = linkedSetOf<LocalDate>()
        val regional = linkedMapOf<String, MutableSet<LocalDate>>()

        for (index in 0 until array.length()) {
            val holiday = array.optJSONObject(index) ?: continue
            val holidayTypes = holiday.optJSONArray("holidayTypes")
                ?: holiday.optJSONArray("types")
            if (holidayTypes != null && (0 until holidayTypes.length()).none {
                    holidayTypes.optString(it).equals("Public", ignoreCase = true)
                }
            ) continue

            val responseCountry = HolidayCountryDetector.normalizeCode(
                holiday.optString("countryCode")
            ) ?: expectedCountryCode
            if (responseCountry != expectedCountryCode) continue

            val date = runCatching { LocalDate.parse(holiday.getString("date")) }.getOrNull()
                ?.takeIf { it.year == expectedYear }
                ?: continue

            val subdivisions = holiday.optJSONArray("subdivisionCodes")
                ?: holiday.optJSONArray("counties")
            val isNational = when {
                holiday.has("nationalHoliday") -> holiday.optBoolean("nationalHoliday", false)
                holiday.has("global") -> holiday.optBoolean("global", false)
                else -> subdivisions == null || subdivisions.length() == 0
            }

            if (isNational) {
                national += date
            } else if (subdivisions != null) {
                for (subdivisionIndex in 0 until subdivisions.length()) {
                    val subdivision = subdivisions.optString(subdivisionIndex)
                        .trim()
                        .uppercase(Locale.ROOT)
                        .takeIf { it.isNotEmpty() }
                        ?: continue
                    regional.getOrPut(subdivision) { linkedSetOf() } += date
                }
            }
        }

        return CountryHolidayData(
            loadedYears = setOf(expectedYear),
            nationalDates = national,
            subdivisionDates = regional.mapValues { it.value.toSet() }
        )
    }

    private fun replaceYear(
        existing: CountryHolidayData,
        year: Int,
        downloaded: CountryHolidayData
    ): CountryHolidayData {
        val national = existing.nationalDates
            .filterTo(linkedSetOf()) { it.year != year }
            .apply { addAll(downloaded.nationalDates) }

        val subdivisions = linkedMapOf<String, Set<LocalDate>>()
        val subdivisionCodes = existing.subdivisionDates.keys + downloaded.subdivisionDates.keys
        for (subdivision in subdivisionCodes) {
            val dates = existing.subdivisionDates[subdivision]
                .orEmpty()
                .filterTo(linkedSetOf()) { it.year != year }
                .apply { addAll(downloaded.subdivisionDates[subdivision].orEmpty()) }
            if (dates.isNotEmpty()) subdivisions[subdivision] = dates
        }

        return CountryHolidayData(
            loadedYears = existing.loadedYears + year,
            nationalDates = national,
            subdivisionDates = subdivisions
        )
    }

    private fun encodeCountryCache(
        countryCode: String,
        data: CountryHolidayData
    ): String {
        val subdivisions = JSONObject()
        for ((subdivision, dates) in data.subdivisionDates.toSortedMap()) {
            subdivisions.put(subdivision, JSONArray(dates.sorted().map(LocalDate::toString)))
        }
        return JSONObject()
            .put("schema", 3)
            .put("provider", "Nager.Date yearly API v4")
            .put("generatedAt", Instant.now().toString())
            .put("countryCode", countryCode)
            .put("years", JSONArray(data.loadedYears.sorted()))
            .put("national", JSONArray(data.nationalDates.sorted().map(LocalDate::toString)))
            .put("subdivisions", subdivisions)
            .toString()
    }

    private fun parseCountryCache(raw: String): Pair<String, CountryHolidayData> {
        val root = JSONObject(raw)
        require(root.optInt("schema", -1) == 3) { "Unsupported holiday schema" }
        val countryCode = HolidayCountryDetector.normalizeCode(root.optString("countryCode"))
            ?: error("Missing holiday country")
        val years = parseYears(root.optJSONArray("years"))
        require(years.isNotEmpty()) { "Holiday cache contains no years" }
        val national = parseDates(root.optJSONArray("national"))
        val regional = linkedMapOf<String, Set<LocalDate>>()
        val subdivisions = root.optJSONObject("subdivisions")
        if (subdivisions != null) {
            val keys = subdivisions.keys()
            while (keys.hasNext()) {
                val subdivision = keys.next().trim().uppercase(Locale.ROOT)
                val dates = parseDates(subdivisions.optJSONArray(subdivision))
                if (subdivision.isNotEmpty() && dates.isNotEmpty()) {
                    regional[subdivision] = dates
                }
            }
        }
        return countryCode to CountryHolidayData(years, national, regional)
    }

    private fun parseYears(array: JSONArray?): Set<Int> = buildSet {
        if (array == null) return@buildSet
        for (index in 0 until array.length()) {
            array.optInt(index, -1)
                .takeIf { it in 1970..2200 }
                ?.let(::add)
        }
    }

    private fun parseDates(array: JSONArray?): Set<LocalDate> = buildSet {
        if (array == null) return@buildSet
        for (index in 0 until array.length()) {
            runCatching { LocalDate.parse(array.getString(index)) }
                .getOrNull()
                ?.let(::add)
        }
    }

    private fun writeCountryCacheAtomically(
        context: Context,
        countryCode: String,
        data: CountryHolidayData
    ) {
        val fileName = "$CACHE_PREFIX${countryCode.lowercase(Locale.ROOT)}$CACHE_SUFFIX"
        val target = File(context.filesDir, fileName)
        val temporary = File(context.filesDir, "$fileName.tmp")
        val raw = encodeCountryCache(countryCode, data)
        runCatching {
            temporary.writeText(raw, Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                target.writeText(raw, Charsets.UTF_8)
                temporary.delete()
            }
        }
    }

    private fun successKey(countryCode: String, year: Int): String =
        "success_${countryCode}_$year"

    private fun attemptKey(countryCode: String, year: Int): String =
        "attempt_${countryCode}_$year"
}
