package cz.misa.quakedeck.ui.status

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.misa.quakedeck.BuildConfig
import cz.misa.quakedeck.R
import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.ConnectionState
import cz.misa.quakedeck.data.DataSourceMode
import cz.misa.quakedeck.data.PlaceNameLanguage
import cz.misa.quakedeck.data.UiLocalization
import cz.misa.quakedeck.sandbox.SandboxUiState
import cz.misa.quakedeck.time.AppClockController
import cz.misa.quakedeck.time.AppClockMode
import cz.misa.quakedeck.time.LiveClockSyncStatus
import cz.misa.quakedeck.ui.sandbox.SandboxDrawerSection
import cz.misa.quakedeck.ui.common.responsiveControlSizing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val JST = ZoneId.of("Asia/Tokyo")
private val FULL_CLOCK_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
private val COMPACT_CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss 'JST'")

/**
 * Owns QuakeDeck's fixed top strip and its pull-down status drawer.
 * The collapsed bar participates in layout; the expanded drawer overlays the
 * app content so the map is never resized while the user opens it.
 */
@Composable
fun ExpandableStatusChrome(
    snapshot: AppSnapshot,
    rawSnapshot: AppSnapshot,
    requestedMode: DataSourceMode,
    language: PlaceNameLanguage,
    sandbox: SandboxUiState,
    historicalMode: Boolean,
    liveWarningActive: Boolean,
    clockController: AppClockController,
    lastProviderUpdateMillis: Long,
    onReconnect: () -> Unit,
    onSourceMenu: () -> Unit,
    onSettings: () -> Unit,
    onSandboxSettings: () -> Unit,
    onReturnToLive: () -> Unit,
    onReturnFromHistory: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val density = LocalDensity.current
        // The chrome follows the actual app/system font scale instead of reserving
        // the same tall strip at 80% and 130%. It is deliberately a little
        // slimmer than the original 40/44 dp bar at the normal 100% scale.
        val responsiveFontScale = density.fontScale.coerceIn(0.75f, 1.60f)
        val baseBarHeight = if (isLandscape) 36f else 40f
        val barHeight = (
            baseBarHeight + (responsiveFontScale - 1f) * 20f
        ).coerceIn(
            minimumValue = if (isLandscape) 31f else 35f,
            maximumValue = if (isLandscape) 48f else 52f
        ).dp
        // Let the drawer wrap its actual content and use every available pixel only
        // when that content needs it. Drag distance remains bounded so opening a tall
        // drawer never requires dragging across the whole screen.
        val maxDrawerHeight = (maxHeight - barHeight).coerceAtLeast(1.dp)
        val drawerDragDistance = maxDrawerHeight.coerceAtMost(
            if (isLandscape) 300.dp else 440.dp
        )
        val drawerDragDistancePx = with(density) {
            drawerDragDistance.toPx()
        }.coerceAtLeast(1f)

        var settledOpen by rememberSaveable { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var draggedFraction by remember { mutableFloatStateOf(0f) }
        var measuredDrawerHeightPx by remember { mutableFloatStateOf(1f) }
        val drawerFraction by animateFloatAsState(
            targetValue = if (dragging) draggedFraction else if (settledOpen) 1f else 0f,
            animationSpec = if (dragging) tween(0) else tween(190),
            label = "statusDrawerFraction"
        )
        val currentDrawerFraction by rememberUpdatedState(drawerFraction)

        var wallNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                val now = System.currentTimeMillis()
                wallNowMillis = now
                val displayedNow = clockController.displayTimeMillis(now)
                    ?: clockController.liveTimeMillis(now)
                delay((1_000L - Math.floorMod(displayedNow, 1_000L)).coerceAtLeast(50L).milliseconds)
            }
        }

        val liveNowMillis = clockController.liveTimeMillis(wallNowMillis)
        // Manual archive browsing is not a replay: keep the independently
        // synchronized real JST clock even if the user entered the browser from
        // Sandbox settings.
        val displayMillis = if (historicalMode) {
            liveNowMillis
        } else {
            clockController.displayTimeMillis(wallNowMillis)
        }
        val compactClock = displayMillis?.let(::formatCompactJst) ?: "--:--:-- JST"
        val fullClock = displayMillis?.let(::formatFullJst) ?: "---- -- -- --:--:-- JST"
        val drawerClock = displayMillis?.let(::formatFullJst) ?: localized(
            R.string.waiting_test_timestamp,
            language
        )
        val actualClock = formatFullJst(liveNowMillis)
        val toggleDrawer = {
            val open = if (dragging) {
                currentDrawerFraction < 0.5f
            } else {
                !settledOpen
            }
            dragging = false
            draggedFraction = if (open) 1f else 0f
            settledOpen = open
        }

        Column(Modifier.fillMaxSize()) {
            StatusBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .pointerInput(drawerDragDistancePx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                draggedFraction = currentDrawerFraction
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                draggedFraction = (
                                    draggedFraction + dragAmount / drawerDragDistancePx
                                ).coerceIn(0f, 1f)
                            },
                            onDragCancel = {
                                settledOpen = draggedFraction >= 0.35f
                                dragging = false
                            },
                            onDragEnd = {
                                settledOpen = draggedFraction >= 0.35f
                                dragging = false
                            }
                        )
                    },
                isLandscape = isLandscape,
                compactClock = compactClock,
                fullClock = fullClock,
                snapshot = snapshot,
                sandbox = sandbox,
                historicalMode = historicalMode,
                liveWarningActive = liveWarningActive,
                language = language,
                drawerFraction = drawerFraction,
                onToggleDrawer = toggleDrawer,
                onLiveWarningClick = onReturnFromHistory,
                onSettings = onSettings
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                content()

                if (drawerFraction > 0.001f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.26f * drawerFraction))
                            .clickable {
                                settledOpen = false
                                dragging = false
                            }
                    )

                    StatusDrawer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxDrawerHeight)
                            .align(Alignment.TopCenter)
                            .onSizeChanged {
                                measuredDrawerHeightPx = it.height.toFloat().coerceAtLeast(1f)
                            }
                            .graphicsLayer {
                                translationY = -measuredDrawerHeightPx * (1f - drawerFraction)
                            },
                        fraction = drawerFraction,
                        displayClock = drawerClock,
                        actualClock = actualClock,
                        rawSnapshot = rawSnapshot,
                        requestedMode = requestedMode,
                        language = language,
                        sandbox = sandbox,
                        historicalMode = historicalMode,
                        liveWarningActive = liveWarningActive,
                        clockMode = clockController.mode,
                        clockController = clockController,
                        wallNowMillis = wallNowMillis,
                        lastProviderUpdateMillis = lastProviderUpdateMillis,
                        onReconnect = onReconnect,
                        onSourceMenu = {
                            settledOpen = false
                            onSourceMenu()
                        },
                        onSettings = {
                            settledOpen = false
                            onSettings()
                        },
                        onSandboxSettings = {
                            settledOpen = false
                            onSandboxSettings()
                        },
                        onReturnToLive = {
                            settledOpen = false
                            onReturnToLive()
                        },
                        onReturnFromHistory = {
                            settledOpen = false
                            onReturnFromHistory()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    modifier: Modifier,
    isLandscape: Boolean,
    compactClock: String,
    fullClock: String,
    snapshot: AppSnapshot,
    sandbox: SandboxUiState,
    historicalMode: Boolean,
    liveWarningActive: Boolean,
    language: PlaceNameLanguage,
    drawerFraction: Float,
    onToggleDrawer: () -> Unit,
    onLiveWarningClick: () -> Unit,
    onSettings: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(0.01f)
    val normalBackground = MaterialTheme.colorScheme.surfaceVariant
    val normalForeground = MaterialTheme.colorScheme.onSurface
    val background = when {
        historicalMode -> Color(0xFF355C8A)
        sandbox.active -> Color(0xFFF59E0B)
        else -> normalBackground
    }
    val foreground = when {
        historicalMode -> Color.White
        sandbox.active -> Color(0xFF271400)
        else -> normalForeground
    }
    val mutedForeground = foreground.copy(alpha = 0.70f)
    val statusLabel = collapsedStatusLabel(snapshot, sandbox, historicalMode)
    val statusColor = when {
        liveWarningActive && historicalMode -> Color(0xFFFF625A)
        historicalMode -> Color(0xFFB9DCFF)
        sandbox.active -> Color(0xFF271400)
        else -> connectionColor(snapshot.connectionState)
    }

    val settingsLabel = localized(R.string.settings, language)
    val drawerActionLabel = localized(
        if (drawerFraction >= 0.5f) {
            R.string.collapse_status_drawer
        } else {
            R.string.expand_status_drawer
        },
        language
    )

    Surface(modifier = modifier, color = background) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 9.dp, end = 2.dp)
        ) {
            // The clock is positioned against the full bar, not between two
            // unequal weighted side groups. It therefore remains exactly at
            // the physical horizontal centre at every text size.
            val longStatus = statusLabel == "SANDBOX" || statusLabel == "CONNECTING" || statusLabel == "HISTORY"
            val compactLongStatus = longStatus && !isLandscape && (
                fontScale > 1.0f || maxWidth < 380.dp
            )
            val statusFontSize = when {
                statusLabel == "LIVE" -> 12.sp
                compactLongStatus -> 9.sp
                longStatus -> 11.sp
                !isLandscape && fontScale > 1.10f -> 10.sp
                else -> 11.sp
            }

            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.align(Alignment.CenterStart),
                color = mutedForeground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )

            Text(
                text = if (isLandscape) fullClock else compactClock,
                modifier = Modifier.align(Alignment.Center),
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier.then(
                        if (historicalMode && liveWarningActive) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = localizedText(R.string.return_to_live, language),
                                onClick = onLiveWarningClick
                            )
                        } else {
                            Modifier
                        }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(if (compactLongStatus) 3.dp else 5.dp))
                    Text(
                        statusLabel,
                        color = foreground,
                        fontSize = statusFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                CompactStatusAction(
                    onClick = onToggleDrawer,
                    onClickLabel = drawerActionLabel,
                    width = if (compactLongStatus) 27.dp else 30.dp
                ) {
                    Chevron(
                        fraction = drawerFraction,
                        color = foreground,
                        modifier = Modifier.size(15.dp)
                    )
                }
                CompactStatusAction(
                    onClick = onSettings,
                    onClickLabel = settingsLabel,
                    width = if (compactLongStatus) 30.dp else 32.dp
                ) {
                    Text("⚙", color = foreground, fontSize = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun CompactStatusAction(
    onClick: () -> Unit,
    onClickLabel: String,
    width: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun Chevron(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = 180f * fraction.coerceIn(0f, 1f)
        }
    ) {
        val stroke = 1.8f.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.34f),
            end = Offset(size.width * 0.50f, size.height * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.66f),
            end = Offset(size.width * 0.82f, size.height * 0.34f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun StatusDrawer(
    modifier: Modifier,
    fraction: Float,
    displayClock: String,
    actualClock: String,
    rawSnapshot: AppSnapshot,
    requestedMode: DataSourceMode,
    language: PlaceNameLanguage,
    sandbox: SandboxUiState,
    historicalMode: Boolean,
    liveWarningActive: Boolean,
    clockMode: AppClockMode,
    clockController: AppClockController,
    wallNowMillis: Long,
    lastProviderUpdateMillis: Long,
    onReconnect: () -> Unit,
    onSourceMenu: () -> Unit,
    onSettings: () -> Unit,
    onSandboxSettings: () -> Unit,
    onReturnToLive: () -> Unit,
    onReturnFromHistory: () -> Unit
) {
    val controlSizing = responsiveControlSizing()
    var showTimeDetails by rememberSaveable { mutableStateOf(false) }
    val openTimeDetails = { showTimeDetails = true }

    if (showTimeDetails) {
        ClockDetailsDialog(
            displayClock = displayClock,
            actualClock = actualClock,
            clockMode = clockMode,
            clockController = clockController,
            wallNowMillis = wallNowMillis,
            language = language,
            onDismiss = { showTimeDetails = false }
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomEnd = 18.dp,
            bottomStart = 18.dp
        ),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(fraction)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Keep the app identity and clock on shared, explicit row heights so
            // their baselines cannot drift. The entire right-hand clock block is
            // one large touch target, including date/time, sync status and the
            // Sandbox Actual time line.
            val headerDensity = LocalDensity.current
            val titleRowHeight = with(headerDensity) { 19.sp.toDp() }
            val subtitleRowHeight = with(headerDensity) { 12.sp.toDp() }
            val actualTimeRowHeight = with(headerDensity) { 12.sp.toDp() }
            val showActualTime = sandbox.active && clockMode != AppClockMode.LIVE

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(0.42f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(titleRowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "QuakeDeck",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            lineHeight = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(subtitleRowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = localized(
                                R.string.version_format,
                                language,
                                BuildConfig.VERSION_NAME
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                    if (showActualTime) {
                        Spacer(Modifier.height(actualTimeRowHeight))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = localized(R.string.show_time_details, language),
                            onClick = openTimeDetails
                        ),
                    horizontalAlignment = Alignment.End
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(titleRowHeight),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = displayClock,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(subtitleRowHeight),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        ClockSyncInlineNote(
                            modifier = Modifier.fillMaxWidth(),
                            clockController = clockController,
                            language = language
                        )
                    }
                    if (showActualTime) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(actualTimeRowHeight),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = localized(
                                    R.string.actual_time_format,
                                    language,
                                    actualClock
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (sandbox.available && sandbox.active) {
                Spacer(Modifier.height(9.dp))
                SandboxDrawerSection(
                    language = language,
                    onReturnToLive = onReturnToLive,
                    onSandboxSettings = onSandboxSettings
                )
            }

            if (historicalMode) {
                Spacer(Modifier.height(9.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            localizedText(R.string.historical_browsing, language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        )
                        Text(
                            localizedText(
                                if (liveWarningActive) R.string.live_warning_during_history else R.string.live_packets_background,
                                language
                            ),
                            modifier = Modifier.padding(top = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                        Spacer(Modifier.height(7.dp))
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Button(
                                onClick = onReturnFromHistory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(controlSizing.actionButtonHeight),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(
                                    horizontal = controlSizing.actionButtonHorizontalPadding,
                                    vertical = 0.dp
                                )
                            ) {
                                Text(localizedText(R.string.return_to_live, language))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(11.dp))
            HorizontalDivider()
            Spacer(Modifier.height(7.dp))
            DrawerSectionLabel(localized(R.string.connections, language))
            Spacer(Modifier.height(6.dp))

            ServiceStatusRow(
                title = "P2PQuake",
                state = connectionLabel(rawSnapshot.connectionState, language),
                stateColor = connectionColor(rawSnapshot.connectionState),
                detail = localizedStatus(rawSnapshot.statusText, language),
                trailing = formatLastUpdate(
                    wallNowMillis = wallNowMillis,
                    lastProviderUpdateMillis = lastProviderUpdateMillis,
                    language = language
                )
            )

            Spacer(Modifier.height(8.dp))
            ServiceStatusRow(
                title = "DM-D.S.S",
                state = if (requestedMode == DataSourceMode.DMDSS) {
                    localized(R.string.not_configured, language)
                } else {
                    localized(R.string.data_source_not_selected, language)
                },
                stateColor = if (requestedMode == DataSourceMode.DMDSS) {
                    Color(0xFFFFA94D)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                detail = if (requestedMode == DataSourceMode.DMDSS) {
                    localized(R.string.free_fallback_in_use, language)
                } else {
                    localized(R.string.dmdss_not_selected_detail, language)
                }
            )

            Spacer(Modifier.height(11.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
            InfoRow(
                label = localized(R.string.requested_mode, language),
                value = if (requestedMode == DataSourceMode.FREE) "FREE" else "DM-D.S.S",
                onClick = onSourceMenu
            )
            Spacer(Modifier.height(2.dp))
            InfoRow(
                label = localized(R.string.actual_provider, language),
                value = actualProviderLabel(rawSnapshot, sandbox, language)
            )

            Spacer(Modifier.height(8.dp))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReconnect,
                        modifier = Modifier
                            .weight(1f)
                            .height(controlSizing.actionButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = controlSizing.actionButtonHorizontalPadding,
                            vertical = 0.dp
                        )
                    ) {
                        Text(localized(R.string.reconnect, language))
                    }
                    Button(
                        onClick = onSettings,
                        modifier = Modifier
                            .weight(1f)
                            .height(controlSizing.actionButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = controlSizing.actionButtonHorizontalPadding,
                            vertical = 0.dp
                        )
                    ) {
                        Text("⚙ ${localized(R.string.settings, language)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockSyncInlineNote(
    modifier: Modifier = Modifier,
    clockController: AppClockController,
    language: PlaceNameLanguage
) {
    val status = clockController.liveSyncStatus
    val hasNetworkTime = clockController.hasNetworkTime
    val rawSource = clockController.liveSyncServer
    val directNict = rawSource == "ntp.nict.jp"

    val indicatorColor = when {
        status == LiveClockSyncStatus.SYNCHRONIZED && directNict -> Color(0xFF42C77A)
        hasNetworkTime -> Color(0xFFFFA94D)
        else -> MaterialTheme.colorScheme.error
    }

    val sourceLabel = when {
        directNict -> "NICT NTP"
        rawSource == "Android network time" -> localized(R.string.clock_source_android_short, language)
        rawSource != null -> rawSource
        else -> localized(R.string.clock_source_device_short, language)
    }

    val note = when (status) {
        LiveClockSyncStatus.NOT_STARTED ->
            localized(R.string.clock_inline_waiting, language)

        LiveClockSyncStatus.SYNCHRONIZING -> if (hasNetworkTime) {
            "$sourceLabel · ${localized(R.string.clock_status_resyncing, language)}"
        } else {
            "NICT NTP · ${localized(R.string.clock_status_syncing, language)}"
        }

        LiveClockSyncStatus.SYNCHRONIZED -> sourceLabel

        LiveClockSyncStatus.FAILED -> if (hasNetworkTime) {
            "$sourceLabel · ${localized(R.string.clock_status_cached_short, language)}"
        } else {
            localized(R.string.clock_inline_unavailable, language)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(indicatorColor, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ClockDetailsDialog(
    displayClock: String,
    actualClock: String,
    clockMode: AppClockMode,
    clockController: AppClockController,
    wallNowMillis: Long,
    language: PlaceNameLanguage,
    onDismiss: () -> Unit
) {
    val status = clockController.liveSyncStatus
    val rawSource = clockController.liveSyncServer
    val directNict = rawSource == "ntp.nict.jp"
    val hasNetworkTime = clockController.hasNetworkTime
    val roundTrip = clockController.liveSyncRoundTripMillis
    val offset = clockController.liveOffsetFromDeviceMillis(wallNowMillis)
    val syncAge = clockController.liveLastSyncElapsedRealtimeMillis?.let { anchor ->
        (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0L)
    }

    val sourceLabel = when {
        directNict -> localized(R.string.clock_source_nict, language)
        rawSource == "Android network time" -> localized(R.string.clock_source_android, language)
        rawSource != null -> rawSource
        else -> localized(R.string.clock_source_device, language)
    }
    val statusLabel = when (status) {
        LiveClockSyncStatus.NOT_STARTED -> localized(R.string.clock_status_waiting, language)
        LiveClockSyncStatus.SYNCHRONIZING -> localized(
            if (hasNetworkTime) R.string.clock_status_resyncing else R.string.clock_status_syncing,
            language
        )
        LiveClockSyncStatus.SYNCHRONIZED -> localized(R.string.clock_status_synchronized, language)
        LiveClockSyncStatus.FAILED -> localized(
            if (hasNetworkTime) R.string.clock_status_cached else R.string.clock_status_unavailable,
            language
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                localized(R.string.time_sync_details, language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ClockDetailRow(
                    label = localized(R.string.displayed_jst, language),
                    value = displayClock
                )
                if (clockMode != AppClockMode.LIVE) {
                    ClockDetailRow(
                        label = localized(R.string.actual_jst, language),
                        value = actualClock
                    )
                }
                HorizontalDivider()
                ClockDetailRow(
                    label = localized(R.string.time_source, language),
                    value = sourceLabel
                )
                ClockDetailRow(
                    label = localized(R.string.sync_status, language),
                    value = statusLabel
                )
                ClockDetailRow(
                    label = localized(R.string.round_trip_time, language),
                    value = if (directNict && roundTrip != null) {
                        localized(R.string.clock_rtt_value, language, roundTrip)
                    } else {
                        localized(R.string.clock_rtt_not_available, language)
                    }
                )
                ClockDetailRow(
                    label = localized(R.string.device_clock, language),
                    value = offset?.let { formatDeviceDifference(it, language) }
                        ?: localized(R.string.clock_device_no_reference, language)
                )
                ClockDetailRow(
                    label = localized(R.string.last_synchronized, language),
                    value = syncAge?.let { age ->
                        localized(R.string.clock_last_sync_value, language, formatSyncAge(age))
                    } ?: localized(R.string.clock_not_yet_synchronized, language)
                )
                HorizontalDivider()
                Text(
                    text = if (directNict) {
                        localized(R.string.clock_sampling_note, language)
                    } else {
                        localized(R.string.clock_fallback_note, language)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
                Text(
                    text = localized(R.string.clock_monotonic_note, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
                Text(
                    text = localized(R.string.clock_resync_note, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
                clockController.liveSyncError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        text = localized(R.string.clock_last_error, language, error),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(localized(R.string.close, language))
            }
        }
    )
}

@Composable
private fun ClockDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.Top) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 12.sp
        )
        Text(
            text = value,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun formatDeviceDifference(
    offsetMillis: Long,
    language: PlaceNameLanguage
): String {
    val magnitude = formatOffsetMagnitude(kotlin.math.abs(offsetMillis))
    return when {
        offsetMillis > 0L -> localized(R.string.clock_device_behind, language, magnitude)
        offsetMillis < 0L -> localized(R.string.clock_device_ahead, language, magnitude)
        else -> localized(R.string.clock_device_matches, language)
    }
}

private fun formatOffsetMagnitude(magnitudeMillis: Long): String = when {
    magnitudeMillis < 1_000L -> "$magnitudeMillis ms"
    magnitudeMillis < 60_000L ->
        "${"%.1f".format(java.util.Locale.ROOT, magnitudeMillis / 1_000.0)} s"
    else -> {
        val minutes = magnitudeMillis / 60_000L
        val seconds = (magnitudeMillis % 60_000L) / 1_000L
        "${minutes}m ${seconds}s"
    }
}

private fun formatSyncAge(ageMillis: Long): String {
    val seconds = ageMillis / 1_000L
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3_600L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m"
    }
}

@Composable
private fun ServiceStatusRow(
    title: String,
    state: String,
    stateColor: Color,
    detail: String,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(9.dp)
                .background(stateColor, CircleShape)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = state,
                    color = stateColor,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = detail,
                modifier = Modifier.padding(top = 1.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp
        )
        Text(
            if (onClick != null) "$value  ›" else value,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

private fun collapsedStatusLabel(
    snapshot: AppSnapshot,
    sandbox: SandboxUiState,
    historicalMode: Boolean
): String = when {
    historicalMode -> "HISTORY"
    sandbox.active -> "SANDBOX"
    snapshot.connectionState == ConnectionState.CONNECTING -> "CONNECTING"
    snapshot.connectionState == ConnectionState.DISCONNECTED -> "OFFLINE"
    snapshot.sourceMode == DataSourceMode.DMDSS -> "DM-D.S.S"
    else -> "LIVE"
}

private fun connectionColor(state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED -> Color(0xFF55D67A)
    ConnectionState.CONNECTING -> Color(0xFFFFC857)
    ConnectionState.FREE_FALLBACK -> Color(0xFFFFA94D)
    ConnectionState.DISCONNECTED -> Color(0xFFFF625A)
}

@Composable
private fun connectionLabel(state: ConnectionState, language: PlaceNameLanguage): String = when (state) {
    ConnectionState.CONNECTED -> localized(R.string.connected, language)
    ConnectionState.CONNECTING -> localized(R.string.connecting, language)
    ConnectionState.FREE_FALLBACK -> localized(R.string.using_free_fallback, language)
    ConnectionState.DISCONNECTED -> localized(R.string.disconnected, language)
}

@Composable
private fun actualProviderLabel(
    rawSnapshot: AppSnapshot,
    sandbox: SandboxUiState,
    language: PlaceNameLanguage
): String = when {
    sandbox.active && rawSnapshot.statusText.contains("built-in", ignoreCase = true) ->
        localized(R.string.built_in_replay, language)
    sandbox.active -> localized(R.string.p2pquake_sandbox, language)
    else -> localized(R.string.p2pquake_live, language)
}

@Composable
private fun formatLastUpdate(
    wallNowMillis: Long,
    lastProviderUpdateMillis: Long,
    language: PlaceNameLanguage
): String? {
    if (lastProviderUpdateMillis <= 0L) return null
    val seconds = ((wallNowMillis - lastProviderUpdateMillis).coerceAtLeast(0L) / 1_000L)
        .coerceAtMost(9_999L)
    return localized(R.string.last_update_seconds, language, seconds)
}

@Composable
private fun localizedText(resourceId: Int, language: PlaceNameLanguage): String =
    UiLocalization.format(LocalContext.current, resourceId, language)

@Composable
private fun localizedStatus(value: String, language: PlaceNameLanguage): String =
    UiLocalization.status(LocalContext.current, value, language)

@Composable
private fun localized(resourceId: Int, language: PlaceNameLanguage, vararg args: Any): String {
    val context = LocalContext.current
    return UiLocalization.format(context, resourceId, language, *args)
}

private fun formatCompactJst(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(JST).format(COMPACT_CLOCK_FORMAT)

private fun formatFullJst(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(JST).format(FULL_CLOCK_FORMAT)
