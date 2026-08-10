@file:Suppress("SimplifyBooleanWithConstants")

package cz.misa.quakedeck.sandbox

import androidx.compose.runtime.Immutable
import cz.misa.quakedeck.BuildConfig

/**
 * Single compile-time boundary for every Sandbox capability.
 *
 * Change `sandboxEnabled` in app/build.gradle.kts to false to remove Sandbox
 * entry points and visual treatment, force persisted test mode back to live,
 * and make every provider, replay, and injection entry point inert.
 */
object SandboxFeature {
    const val ENABLED: Boolean = BuildConfig.SANDBOX_ENABLED

    fun permitted(requested: Boolean): Boolean = ENABLED && requested
}

@Immutable
data class SandboxUiState(
    val available: Boolean,
    val active: Boolean
)

fun sandboxUiState(testingMode: Boolean): SandboxUiState = SandboxUiState(
    available = SandboxFeature.ENABLED,
    active = SandboxFeature.permitted(testingMode)
)
