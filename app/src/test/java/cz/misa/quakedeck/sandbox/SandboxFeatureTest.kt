package cz.misa.quakedeck.sandbox

import cz.misa.quakedeck.BuildConfig
import cz.misa.quakedeck.time.AppClockController
import cz.misa.quakedeck.time.AppClockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SandboxFeatureTest {
    @Test
    fun enabledReflectsTheSingleBuildSetting() {
        assertEquals(BuildConfig.SANDBOX_ENABLED, SandboxFeature.ENABLED)
    }

    @Test
    fun disabledRequestCanNeverEnableSandbox() {
        assertFalse(SandboxFeature.permitted(false))
    }

    @Test
    fun enabledRequestStillRequiresBuildPermission() {
        assertEquals(SandboxFeature.ENABLED, SandboxFeature.permitted(true))
    }

    @Test
    fun clockCannotEnterSandboxInADisabledBuild() {
        val controller = AppClockController()

        controller.enterSandboxWaiting()
        assertEquals(
            if (SandboxFeature.ENABLED) AppClockMode.SANDBOX_WAITING else AppClockMode.LIVE,
            controller.mode
        )

        controller.startSandboxCountdown(
            firstPacketTimeMillis = 10_000L,
            startDelayMillis = 2_000L,
            wallNowMillis = 1_000L
        )
        assertEquals(
            if (SandboxFeature.ENABLED) AppClockMode.SANDBOX else AppClockMode.LIVE,
            controller.mode
        )

        controller.synchronizeSandboxTime(sourceTimeMillis = 10_000L, wallNowMillis = 1_000L)
        assertEquals(
            if (SandboxFeature.ENABLED) AppClockMode.SANDBOX else AppClockMode.LIVE,
            controller.mode
        )
    }
}
