package cz.misa.quakedeck.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.misa.quakedeck.R
import cz.misa.quakedeck.data.HistoricalEventSummary
import cz.misa.quakedeck.data.PlaceNameLanguage
import cz.misa.quakedeck.data.PlaceNameTranslator
import cz.misa.quakedeck.data.UiLocalization
import cz.misa.quakedeck.data.displayEventOriginTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class HistoricalSort {
    DATE_NEWEST,
    DATE_OLDEST,
    INTENSITY_STRONGEST,
    INTENSITY_WEAKEST
}

private val EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
private val EXACT_INTENSITIES = listOf("—", "1", "2", "3", "4", "5-", "5+", "6-", "6+", "7")

@Composable
fun HistoricalReportsBrowser(
    language: PlaceNameLanguage,
    loading: Boolean,
    events: List<HistoricalEventSummary>,
    error: String?,
    onRetry: () -> Unit,
    onSelectEvent: (HistoricalEventSummary) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                HistoricalHeader(language = language, onDismiss = onDismiss)
                HistoricalBrowserBody(
                    language = language,
                    loading = loading,
                    events = events,
                    error = error,
                    onRetry = onRetry,
                    onSelectEvent = onSelectEvent
                )
            }
        }
    }
}

@Composable
private fun HistoricalHeader(
    language: PlaceNameLanguage,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Text(historyText(R.string.back, language))
        }
        Text(
            text = historyText(R.string.past_reports, language),
            modifier = Modifier.align(Alignment.Center),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HistoricalBrowserBody(
    language: PlaceNameLanguage,
    loading: Boolean,
    events: List<HistoricalEventSummary>,
    error: String?,
    onRetry: () -> Unit,
    onSelectEvent: (HistoricalEventSummary) -> Unit
) {
    var sortName by rememberSaveable { mutableStateOf(HistoricalSort.DATE_NEWEST.name) }
    var fromDateText by rememberSaveable { mutableStateOf("") }
    var toDateText by rememberSaveable { mutableStateOf("") }
    var selectedIntensityValues by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedIntensities = selectedIntensityValues.toSet()
    val sort = runCatching { HistoricalSort.valueOf(sortName) }
        .getOrDefault(HistoricalSort.DATE_NEWEST)
    val fromDate = parseDate(fromDateText)
    val toDate = parseDate(toDateText)
    val fromDateInvalid = fromDateText.isNotBlank() && fromDate == null
    val toDateInvalid = toDateText.isNotBlank() && toDate == null

    val visibleEvents = remember(
        events,
        sort,
        fromDateText,
        toDateText,
        selectedIntensities
    ) {
        events.asSequence()
            .filter { summary ->
                val date = eventDate(summary)
                (fromDate == null || date?.isBefore(fromDate) == false) &&
                    (toDate == null || date?.isAfter(toDate) == false)
            }
            .filter { summary ->
                selectedIntensities.isEmpty() ||
                    normalizedIntensity(summary.event.maxIntensity) in selectedIntensities
            }
            .sortedWith(sortComparator(sort))
            .toList()
    }

    if (loading && events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(historyText(R.string.loading_archived_reports, language))
            }
        }
        return
    }

    if (error != null && events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) {
                    Text(historyText(R.string.retry, language))
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val showTopButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                top = 12.dp,
                end = 14.dp,
                bottom = 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "filters") {
                FilterPanel(
                    language = language,
                    sort = sort,
                    onSortChanged = { sortName = it.name },
                    fromDateText = fromDateText,
                    onFromDateChanged = { fromDateText = it },
                    fromDateInvalid = fromDateInvalid,
                    toDateText = toDateText,
                    onToDateChanged = { toDateText = it },
                    toDateInvalid = toDateInvalid,
                    selectedIntensities = selectedIntensities,
                    onIntensityToggle = { intensity ->
                        selectedIntensityValues = if (intensity in selectedIntensities) {
                            selectedIntensityValues - intensity
                        } else {
                            selectedIntensityValues + intensity
                        }
                    },
                    onClearIntensity = { selectedIntensityValues = emptyList() },
                    resultCount = visibleEvents.size,
                    totalCount = events.size
                )
            }

            if (loading) {
                item(key = "loading-progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            error?.let { message ->
                item(key = "warning") {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }

            if (visibleEvents.isEmpty()) {
                item(key = "empty") {
                    Text(
                        historyText(R.string.no_archived_events_match, language),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                items(
                    items = visibleEvents,
                    key = { it.eventKey }
                ) { summary ->
                    HistoricalEventRow(
                        summary = summary,
                        language = language,
                        onClick = { onSelectEvent(summary) }
                    )
                }
            }
        }

        if (showTopButton) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 8.dp)
                    .clickable { scrollScope.launch { listState.animateScrollToItem(0) } },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Text(
                    "↑ " + historyText(R.string.top, language),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterPanel(
    language: PlaceNameLanguage,
    sort: HistoricalSort,
    onSortChanged: (HistoricalSort) -> Unit,
    fromDateText: String,
    onFromDateChanged: (String) -> Unit,
    fromDateInvalid: Boolean,
    toDateText: String,
    onToDateChanged: (String) -> Unit,
    toDateInvalid: Boolean,
    selectedIntensities: Set<String>,
    onIntensityToggle: (String) -> Unit,
    onClearIntensity: () -> Unit,
    resultCount: Int,
    totalCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(historyText(R.string.sort, language), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SortChip(HistoricalSort.DATE_NEWEST, sort, language, onSortChanged)
                SortChip(HistoricalSort.DATE_OLDEST, sort, language, onSortChanged)
                SortChip(HistoricalSort.INTENSITY_STRONGEST, sort, language, onSortChanged)
                SortChip(HistoricalSort.INTENSITY_WEAKEST, sort, language, onSortChanged)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fromDateText,
                    onValueChange = { onFromDateChanged(it.take(10)) },
                    modifier = Modifier.weight(1f),
                    label = { Text(historyText(R.string.from_date, language), fontSize = 11.sp) },
                    placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                    singleLine = true,
                    isError = fromDateInvalid
                )
                OutlinedTextField(
                    value = toDateText,
                    onValueChange = { onToDateChanged(it.take(10)) },
                    modifier = Modifier.weight(1f),
                    label = { Text(historyText(R.string.to_date, language), fontSize = 11.sp) },
                    placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                    singleLine = true,
                    isError = toDateInvalid
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(historyText(R.string.maximum_intensity, language), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                TextButton(onClick = onClearIntensity, enabled = selectedIntensities.isNotEmpty()) {
                    Text(historyText(R.string.all, language), fontSize = 11.sp)
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                EXACT_INTENSITIES.chunked(3).forEach { rowValues ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        rowValues.forEach { intensity ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onIntensityToggle(intensity) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = intensity in selectedIntensities,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    if (intensity == "—") {
                                        historyText(R.string.unknown, language)
                                    } else {
                                        intensity
                                    },
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat(3 - rowValues.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(
                UiLocalization.format(LocalContext.current, R.string.events_result_count, language, resultCount, totalCount),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SortChip(
    value: HistoricalSort,
    selected: HistoricalSort,
    language: PlaceNameLanguage,
    onSelected: (HistoricalSort) -> Unit
) {
    val label = when (value) {
        HistoricalSort.DATE_NEWEST -> R.string.date_newest
        HistoricalSort.DATE_OLDEST -> R.string.date_oldest
        HistoricalSort.INTENSITY_STRONGEST -> R.string.intensity_strongest
        HistoricalSort.INTENSITY_WEAKEST -> R.string.intensity_weakest
    }
    FilterChip(
        selected = value == selected,
        onClick = { onSelected(value) },
        label = { Text(historyText(label, language), fontSize = 11.sp) }
    )
}

@Composable
private fun HistoricalEventRow(
    summary: HistoricalEventSummary,
    language: PlaceNameLanguage,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val event = summary.event
    val place = PlaceNameTranslator.epicenter(
        context,
        event.place,
        language,
        untranslatedFallback = if (event.hasHypocenter) null else "Hypocenter under assessment"
    )
    val reportsLabel = historyText(R.string.reports, language)
    val correctedLabel = historyText(R.string.corrected, language)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    displayEventOriginTime(event.originTime),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Text(
                    place,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append("M")
                        append(
                            if (event.magnitude > 0.0) {
                                String.format(java.util.Locale.US, "%.1f", event.magnitude)
                            } else {
                                "—"
                            }
                        )
                        append(" · ")
                        append(summary.reportCount)
                        append(' ')
                        append(reportsLabel)
                        if (summary.hasCorrection) {
                            append(" · ")
                            append(correctedLabel)
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = intensityColor(event.maxIntensity)
            ) {
                Text(
                    normalizedIntensity(event.maxIntensity),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
    }
}

private fun parseDate(value: String): LocalDate? =
    value.trim().takeIf { it.isNotEmpty() }?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

private fun eventDate(summary: HistoricalEventSummary): LocalDate? =
    runCatching { LocalDateTime.parse(summary.event.originTime, EVENT_TIME_FORMAT).toLocalDate() }.getOrNull()

private fun eventTime(summary: HistoricalEventSummary): LocalDateTime =
    runCatching { LocalDateTime.parse(summary.event.originTime, EVENT_TIME_FORMAT) }
        .getOrDefault(LocalDateTime.MIN)

private fun sortComparator(sort: HistoricalSort): Comparator<HistoricalEventSummary> = when (sort) {
    HistoricalSort.DATE_NEWEST -> compareByDescending<HistoricalEventSummary> { eventTime(it) }
    HistoricalSort.DATE_OLDEST -> compareBy<HistoricalEventSummary> { eventTime(it) }
    HistoricalSort.INTENSITY_STRONGEST -> compareByDescending<HistoricalEventSummary> {
        intensityRank(it.event.maxIntensity)
    }.thenByDescending(::eventTime)
    HistoricalSort.INTENSITY_WEAKEST -> compareBy<HistoricalEventSummary> {
        intensityRank(it.event.maxIntensity)
    }.thenByDescending(::eventTime)
}

private fun normalizedIntensity(value: String): String = when (value.trim()) {
    "5弱" -> "5-"
    "5強" -> "5+"
    "6弱" -> "6-"
    "6強" -> "6+"
    "", "-1", "0" -> "—"
    else -> value.trim()
}

private fun intensityRank(value: String): Int = when (normalizedIntensity(value)) {
    "1" -> 1
    "2" -> 2
    "3" -> 3
    "4" -> 4
    "5-" -> 5
    "5+" -> 6
    "6-" -> 7
    "6+" -> 8
    "7" -> 9
    else -> 0
}

private fun intensityColor(value: String): Color = when (normalizedIntensity(value)) {
    "1" -> Color(0xFFE7F3FF)
    "2" -> Color(0xFFB7E1FF)
    "3" -> Color(0xFF78D5E8)
    "4" -> Color(0xFFFFE66D)
    "5-" -> Color(0xFFFFB347)
    "5+" -> Color(0xFFFF8C42)
    "6-" -> Color(0xFFFF5A5F)
    "6+" -> Color(0xFFD9364E)
    "7" -> Color(0xFFB24C9A)
    else -> Color(0xFFD0D0D0)
}

@Composable
private fun historyText(resourceId: Int, language: PlaceNameLanguage): String =
    UiLocalization.format(LocalContext.current, resourceId, language)
