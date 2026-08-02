package cz.misa.quakedeck.sandbox

import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.LiveUpdateKind
import cz.misa.quakedeck.time.AppClockController
import cz.misa.quakedeck.time.AppClockMode
import cz.misa.quakedeck.time.parseJstMillis

/** Built-in scenarios currently exposed by QuakeDeck's test tools. */
enum class BuiltInSandboxScenario(val firstTimestampJst: String) {
    NOTO_2023_EEW("2023-05-05 14:42:13.900"),
    NOTO_2024_TSUNAMI("2024-01-01 16:10:22.500"),
    NOTO_2024_COMBINED("2024-01-01 16:10:29.200")
}

/**
 * The only bridge allowed to place the shared application clock on historical
 * time. Normal UI never needs to understand replay packet formats.
 */
object SandboxClockBridge {
    fun enterSandbox(controller: AppClockController) {
        if (SandboxFeature.ENABLED) controller.enterSandboxWaiting()
    }

    fun returnToLive(controller: AppClockController) {
        controller.useLiveTime()
    }

    fun startBuiltInScenario(
        controller: AppClockController,
        scenario: BuiltInSandboxScenario,
        startDelayMillis: Long
    ) {
        if (!SandboxFeature.ENABLED) return
        val firstTimestamp = parseJstMillis(scenario.firstTimestampJst) ?: return
        controller.startSandboxCountdown(firstTimestamp, startDelayMillis)
    }

    fun synchronizeFromSnapshot(controller: AppClockController, snapshot: AppSnapshot) {
        if (!SandboxFeature.ENABLED || !snapshot.testingMode) {
            controller.useLiveTime()
            return
        }

        val sourceTime = when (snapshot.liveUpdateKind) {
            LiveUpdateKind.TSUNAMI,
            LiveUpdateKind.TSUNAMI_CANCELLED -> parseJstMillis(snapshot.tsunami?.issueTime)

            LiveUpdateKind.EEW,
            LiveUpdateKind.EEW_ENDED,
            LiveUpdateKind.CANCELLED -> parseJstMillis(
                snapshot.activeEewEvent?.reportIssuedAt
                    ?: snapshot.event.reportIssuedAt
                    ?: snapshot.event.originTime
            )

            LiveUpdateKind.CONFIRMED -> parseJstMillis(
                snapshot.event.reportIssuedAt ?: snapshot.event.originTime
            )

            LiveUpdateKind.EEW_DETECTED,
            LiveUpdateKind.NONE -> null
        }

        if (sourceTime != null) {
            controller.synchronizeSandboxTime(sourceTime)
        } else if (controller.mode == AppClockMode.LIVE) {
            controller.enterSandboxWaiting()
        }
    }
}
