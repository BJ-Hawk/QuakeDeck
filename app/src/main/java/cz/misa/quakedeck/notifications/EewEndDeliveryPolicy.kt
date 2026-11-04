package cz.misa.quakedeck.notifications

import cz.misa.quakedeck.data.EarthquakeEvent
import cz.misa.quakedeck.data.notificationEnabled
import cz.misa.quakedeck.data.AppSnapshot

internal fun eewNotificationEvent(snapshot: AppSnapshot): EarthquakeEvent =
    snapshot.eewUpdateEvent ?: snapshot.activeEewEvent ?: snapshot.event

/** EEW termination uses EEW switches/tracking, independently of confirmed earthquake alerts. */
internal fun eewEndDeliveryEnabled(event: EarthquakeEvent, updatesEnabled: Boolean,
    warningEnabled: Boolean, forecastEnabled: Boolean, locationFiltering: Boolean,
    previouslyInScope: Boolean): Boolean = updatesEnabled && event.eewAlertLevel.notificationEnabled(
        warningEnabled, forecastEnabled
    ) && (!locationFiltering || previouslyInScope)
