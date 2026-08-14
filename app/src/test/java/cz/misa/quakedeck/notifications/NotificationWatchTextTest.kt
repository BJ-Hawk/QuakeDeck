package cz.misa.quakedeck.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationWatchTextTest {
    @Test
    fun knownIntensityComesFirstAndKeepsOnlyAlertAndPlaceInBody() {
        val text = notificationWatchText(
            intensityTitle = "Shindo 5+",
            intensityQualifier = "PRED.",
            alertTitle = "Earthquake Early Warning",
            place = "Off the coast of Chiba Pref."
        )

        assertEquals("Shindo 5+ · PRED.", text.title)
        assertEquals("Earthquake Early Warning · Off the coast of Chiba Pref.", text.body)
    }

    @Test
    fun endedAlertSuppressesStaleIntensityAndFallsBackToStatusAndPlace() {
        val text = notificationWatchText(
            intensityTitle = "Shindo 5+",
            intensityQualifier = "PRED.",
            alertTitle = "Earthquake Early Warning ended",
            place = "Japan",
            showIntensity = false
        )

        assertEquals("Earthquake Early Warning ended", text.title)
        assertEquals("Japan", text.body)
    }
}
