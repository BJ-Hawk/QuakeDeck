package cz.misa.quakedeck.notifications

/**
 * Standard notification text is what a paired watch normally renders when it
 * cannot use QuakeDeck's custom phone-only Shindo card.
 */
internal data class NotificationWatchText(
    val title: String,
    val body: String
)

internal fun notificationWatchText(
    intensityTitle: String?,
    intensityQualifier: String?,
    alertTitle: String,
    place: String,
    showIntensity: Boolean = true
): NotificationWatchText {
    val knownIntensity = intensityTitle?.takeIf { showIntensity && it.isNotBlank() }
    val watchTitle = if (knownIntensity != null) {
        listOfNotNull(
            knownIntensity,
            intensityQualifier?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
    } else {
        alertTitle
    }
    val watchBody = if (knownIntensity != null) {
        listOf(alertTitle, place)
    } else {
        listOf(place)
    }.filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")

    return NotificationWatchText(title = watchTitle, body = watchBody)
}
