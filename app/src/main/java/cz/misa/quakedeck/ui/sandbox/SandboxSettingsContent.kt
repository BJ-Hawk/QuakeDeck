package cz.misa.quakedeck.ui.sandbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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

/** The single main-settings entry point for all Sandbox controls. */
@Composable
fun SandboxSettingsEntry(
    language: PlaceNameLanguage,
    active: Boolean,
    onClick: () -> Unit
) {
    if (!SandboxFeature.ENABLED) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(localized(R.string.testing_sandbox, language))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            localized(R.string.testing_sandbox, language),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp,
                            lineHeight = 15.sp
                        )
                        if (active) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    localized(R.string.active, language),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        localized(R.string.sandbox_summary, language),
                        modifier = Modifier.padding(top = 1.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    )
                }
                Text("›", color = MaterialTheme.colorScheme.secondary, fontSize = 22.sp, lineHeight = 22.sp)
            }
        }
    }
}

/** Dedicated Sandbox page. No normal setting needs to know its internal controls. */
@Composable
fun SandboxSettingsPage(
    language: PlaceNameLanguage,
    active: Boolean,
    onActiveChanged: (Boolean) -> Unit,
    onEewReplay: () -> Unit,
    onTsunamiReplay: () -> Unit,
    onCombinedReplay: () -> Unit,
    onInjectEarthquakeReport: () -> Unit,
    onInjectEewWarning: () -> Unit,
    onInjectTsunamiWarning: () -> Unit
) {
    if (!SandboxFeature.ENABLED) return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                )
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Text("⚠", fontSize = 20.sp)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            localized(R.string.historical_simulated_data, language),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 15.sp
                        )
                        Text(
                            localized(R.string.nothing_current_event, language),
                            modifier = Modifier.padding(top = 1.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }

        if (active) {
            item {
                val controlSizing = responsiveControlSizing()
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Button(
                        onClick = { onActiveChanged(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(controlSizing.actionButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = controlSizing.actionButtonHorizontalPadding,
                            vertical = 0.dp
                        )
                    ) {
                        Text(localized(R.string.return_live_data, language), fontSize = 10.sp)
                    }
                }
            }
        }

        item { SectionLabel(localized(R.string.official_test_feed, language)) }
        item {
            SandboxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            localized(R.string.p2pquake_testing_mode, language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 15.sp
                        )
                        Text(
                            localized(R.string.sandbox_explanation, language),
                            modifier = Modifier.padding(top = 1.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                    }
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Switch(checked = active, onCheckedChange = onActiveChanged)
                    }
                }
            }
        }

        item { SectionLabel(localized(R.string.live_pipeline_tests, language)) }
        item {
            SandboxCard {
                ReplayRow(
                    title = localized(R.string.inject_test_report, language),
                    description = localized(R.string.inject_test_report_explanation, language),
                    runLabel = localized(R.string.inject, language),
                    onRun = onInjectEarthquakeReport
                )
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                ReplayRow(
                    title = localized(R.string.inject_test_eew, language),
                    description = localized(R.string.inject_test_eew_explanation, language),
                    runLabel = localized(R.string.inject, language),
                    onRun = onInjectEewWarning
                )
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                ReplayRow(
                    title = localized(R.string.inject_test_tsunami, language),
                    description = localized(R.string.inject_test_tsunami_explanation, language),
                    runLabel = localized(R.string.inject, language),
                    onRun = onInjectTsunamiWarning
                )
            }
        }

        item { SectionLabel(localized(R.string.built_in_scenarios, language)) }
        item {
            SandboxCard {
                ReplayRow(
                    title = localized(R.string.replay_noto_eew, language),
                    description = localized(R.string.replay_noto_eew_explanation, language),
                    runLabel = localized(R.string.run, language),
                    onRun = onEewReplay
                )
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                ReplayRow(
                    title = localized(R.string.replay_noto_tsunami, language),
                    description = localized(R.string.replay_noto_tsunami_explanation, language),
                    runLabel = localized(R.string.run, language),
                    onRun = onTsunamiReplay
                )
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                ReplayRow(
                    title = localized(R.string.replay_noto_combined, language),
                    description = localized(R.string.replay_noto_combined_explanation, language),
                    runLabel = localized(R.string.run, language),
                    onRun = onCombinedReplay
                )
            }
        }
    }
}

@Composable
private fun ReplayRow(
    title: String,
    description: String,
    runLabel: String,
    onRun: () -> Unit
) {
    val controlSizing = responsiveControlSizing()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp)
            Text(
                text = description,
                modifier = Modifier.padding(top = 1.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Button(
                onClick = onRun,
                modifier = Modifier.height(controlSizing.actionButtonHeight),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(
                    horizontal = controlSizing.actionButtonHorizontalPadding,
                    vertical = 0.dp
                )
            ) {
                Text(runLabel, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SandboxCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 3.dp, top = 2.dp)
    )
}

@Composable
private fun localized(resourceId: Int, language: PlaceNameLanguage): String {
    val context = LocalContext.current
    return UiLocalization.format(context, resourceId, language)
}
