package cz.misa.quakedeck.data

internal data class PendingEewExpiry(
    val event: EarthquakeEvent,
    val deadlineMillis: Long
)

/** Keeps each incident's expiry independent of which warning is visible. */
internal class PendingEewExpiries {
    private val pending = linkedMapOf<String, PendingEewExpiry>()

    val nextDeadlineMillis: Long?
        get() = pending.values.minOfOrNull { it.deadlineMillis }

    val hasRealWarning: Boolean
        get() = pending.values.any { !it.event.isDrill }

    fun incident(eventId: String): PendingEewExpiry? = pending[eventId]

    fun newestFirst(): List<PendingEewExpiry> = pending.values.toList().asReversed()

    fun schedule(event: EarthquakeEvent, deadlineMillis: Long) {
        pending.remove(event.id)
        pending[event.id] = PendingEewExpiry(event, deadlineMillis)
    }

    fun remove(eventId: String): EarthquakeEvent? = pending.remove(eventId)?.event

    fun clear() = pending.clear()

    fun removeDrills() {
        pending.values.removeAll { it.event.isDrill }
    }

    fun extractDue(nowMillis: Long): List<PendingEewExpiry> {
        val due = pending.values.filter { it.deadlineMillis <= nowMillis }.sortedBy { it.deadlineMillis }
        due.forEach { pending.remove(it.event.id) }
        return due
    }
}
