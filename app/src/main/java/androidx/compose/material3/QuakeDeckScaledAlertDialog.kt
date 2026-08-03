@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity

/**
 * A narrower overload used by QuakeDeck's compact information/help dialogs.
 *
 * Material dialogs are hosted in a separate window. On some Compose/Android
 * combinations that window recreates [LocalDensity] from the system and drops
 * QuakeDeck's in-app Text size multiplier. Capture the caller density before
 * crossing the dialog boundary, then restore it independently inside every
 * content slot.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable () -> Unit
) {
    val appDensity = LocalDensity.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            CompositionLocalProvider(LocalDensity provides appDensity) {
                confirmButton()
            }
        },
        dismissButton = null,
        title = title?.let { titleContent ->
            {
                CompositionLocalProvider(LocalDensity provides appDensity) {
                    titleContent()
                }
            }
        },
        text = text?.let { textContent ->
            {
                CompositionLocalProvider(LocalDensity provides appDensity) {
                    textContent()
                }
            }
        }
    )
}
