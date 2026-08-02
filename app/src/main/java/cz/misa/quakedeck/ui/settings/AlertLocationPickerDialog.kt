package cz.misa.quakedeck.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.misa.quakedeck.R
import cz.misa.quakedeck.data.AlertLocation
import cz.misa.quakedeck.data.AlertLocationResolutionKind
import cz.misa.quakedeck.data.ManualAlertLocationResolver
import cz.misa.quakedeck.data.PlaceNameLanguage
import cz.misa.quakedeck.data.UiLocalization
import kotlinx.coroutines.launch

@Composable
internal fun AlertLocationPickerDialog(
    language: PlaceNameLanguage,
    currentLocation: AlertLocation,
    onLocationSelected: (AlertLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<AlertLocation>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }

    fun text(resourceId: Int, vararg args: Any): String =
        UiLocalization.format(context, resourceId, language, *args)

    fun runSearch() {
        if (query.isBlank() || searching) return
        searching = true
        attempted = true
        failed = false
        scope.launch {
            ManualAlertLocationResolver.search(context, query, language)
                .onSuccess { resolved ->
                    results = resolved
                    failed = false
                }
                .onFailure {
                    results = emptyList()
                    failed = true
                }
            searching = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text(R.string.notification_alert_location_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = text(R.string.notification_alert_location_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = text(R.string.notification_current_alert_location),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                        Text(
                            text = currentLocation.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text(R.string.notification_city_or_postcode)) },
                    placeholder = { Text(text(R.string.notification_city_or_postcode_example)) },
                    supportingText = {
                        Text(
                            text = text(R.string.notification_postcode_hint),
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    },
                    singleLine = true
                )
                OutlinedButton(
                    onClick = ::runSearch,
                    enabled = query.isNotBlank() && !searching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text(R.string.search))
                }

                when {
                    failed -> Text(
                        text = text(R.string.notification_location_search_failed),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                    attempted && !searching && results.isEmpty() -> Text(
                        text = text(R.string.notification_location_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                    results.isNotEmpty() -> {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 270.dp)
                        ) {
                            items(
                                items = results,
                                key = { result ->
                                    "${result.latitude}:${result.longitude}:${result.displayName}"
                                }
                            ) { result ->
                                LocationResultRow(
                                    location = result,
                                    kindLabel = when (result.resolutionKind) {
                                        AlertLocationResolutionKind.CITY ->
                                            text(R.string.notification_location_city_result)
                                        AlertLocationResolutionKind.POSTAL_PREFIX ->
                                            text(R.string.notification_location_postal_area_result)
                                        AlertLocationResolutionKind.POSTAL_CODE ->
                                            text(R.string.notification_location_postcode_result)
                                    },
                                    jmaAreaLabel = result.eewAreaNameJa?.let {
                                        text(R.string.notification_location_jma_area, it)
                                    },
                                    onClick = { onLocationSelected(result) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LocationResultRow(
    location: AlertLocation,
    kindLabel: String,
    jmaAreaLabel: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = location.displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(kindLabel, jmaAreaLabel).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
