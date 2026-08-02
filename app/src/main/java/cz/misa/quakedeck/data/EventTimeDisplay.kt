package cz.misa.quakedeck.data

private val EVENT_ORIGIN_WITH_SECONDS = Regex(
    "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}):00( JST)$"
)

/**
 * Keeps the canonical event timestamp untouched in the data model while hiding
 * an always-zero seconds field from human-facing earthquake occurrence times.
 */
fun displayEventOriginTime(value: String): String {
    val match = EVENT_ORIGIN_WITH_SECONDS.matchEntire(value) ?: return value
    return match.groupValues[1] + match.groupValues[2]
}
