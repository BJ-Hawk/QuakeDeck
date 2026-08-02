package cz.misa.quakedeck.ui.sandbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.misa.quakedeck.R
import cz.misa.quakedeck.data.PlaceNameLanguage
import cz.misa.quakedeck.data.UiLocalization
import cz.misa.quakedeck.sandbox.SandboxFeature
import cz.misa.quakedeck.ui.common.responsiveControlSizing

/** Sandbox-only content injected into the otherwise generic status drawer. */
@Composable
fun SandboxDrawerSection(
    language: PlaceNameLanguage,
    onReturnToLive: () -> Unit,
    onSandboxSettings: () -> Unit
) {
    if (!SandboxFeature.ENABLED) return

    val controlSizing = responsiveControlSizing()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "⚠ ${localized(R.string.sandbox_active_title, language)}",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                localized(R.string.sandbox_active_explanation, language),
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
            Spacer(Modifier.height(7.dp))
            // Material buttons reserve a 48 dp layout slot by default even when
            // their requested visual height is smaller. For these compact drawer
            // actions we opt out locally; Android still expands the pointer target
            // at the input layer, while the visible controls stay genuinely lean.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onReturnToLive,
                        modifier = Modifier
                            .weight(1f)
                            .height(controlSizing.actionButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = controlSizing.actionButtonHorizontalPadding,
                            vertical = 0.dp
                        )
                    ) {
                        Text(localized(R.string.return_live_data, language), fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onSandboxSettings,
                        modifier = Modifier
                            .weight(1f)
                            .height(controlSizing.actionButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = controlSizing.actionButtonHorizontalPadding,
                            vertical = 0.dp
                        )
                    ) {
                        Text(localized(R.string.sandbox_settings, language), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun localized(resourceId: Int, language: PlaceNameLanguage): String {
    val context = LocalContext.current
    return UiLocalization.format(context, resourceId, language)
}
