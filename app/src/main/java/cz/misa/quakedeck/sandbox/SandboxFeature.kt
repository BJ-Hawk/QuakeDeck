package cz.misa.quakedeck.sandbox

import androidx.compose.runtime.Immutable

/**
 * Single compile-time boundary for every user-facing Sandbox feature.
 *
 * Set [ENABLED] to false to remove Sandbox entry points and visual treatment,
 * force persisted test mode back to live, and make every replay callback inert.
 */
object SandboxFeature {
    const val ENABLED: Boolean = true

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
