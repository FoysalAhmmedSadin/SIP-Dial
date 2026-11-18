package com.sipdial.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.ui.components.ContactAvatar
import com.sipdial.app.ui.theme.LocalSemanticColors
import com.sipdial.app.util.CallerIdFormatter
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact
import com.sipdial.app.util.RecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class RecentCallGroup(
    val key: String,
    val number: String,
    val hasDigits: Boolean,
    val matchedContact: DeviceContact?,
    val formattedTime: String,
    val records: List<CallRecord>
)

private fun cleanAddress(addr: String): String =
    CallerIdFormatter.extractNumber(addr)

internal fun callNumberKey(addr: String): String {
    val clean = cleanAddress(addr)
    val digits = CallerIdFormatter.normalizeMatchDigits(clean)
    return if (digits.length >= 10) digits.takeLast(10) else digits.ifEmpty { clean }
}

private val TIME_FORMAT = ThreadLocal.withInitial { SimpleDateFormat("h:mm a", Locale.US) }
private val DATE_FORMAT = ThreadLocal.withInitial { SimpleDateFormat("MMM dd", Locale.US) }

private fun currentDayBoundsMs(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return start to cal.timeInMillis
}

private fun formatShortCallTime(ts: Long, todayStartMs: Long, tomorrowStartMs: Long): String {
    val fmt = if (ts in todayStartMs until tomorrowStartMs) TIME_FORMAT.get() else DATE_FORMAT.get()
    return fmt?.format(Date(ts)).orEmpty()
}

private fun buildRecentCallGroups(
    filteredRecords: List<CallRecord>,
    phoneIndex: ContactsHelper.PhoneIndex
): List<RecentCallGroup> {
    if (filteredRecords.isEmpty()) return emptyList()
    val (todayStartMs, tomorrowStartMs) = currentDayBoundsMs()
    val map = LinkedHashMap<String, MutableList<CallRecord>>()
    for (rec in filteredRecords) {
        map.getOrPut(callNumberKey(rec.remoteAddress)) { mutableListOf() }.add(rec)
    }
    return map.map { (k, recs) ->
        val latest = recs.first()
        val number = cleanAddress(latest.remoteAddress)
        val hasDigits = k.isNotEmpty() && k.any { it.isDigit() }
        val contact = phoneIndex.find(number)
        RecentCallGroup(
            key = k,
            number = number,
            hasDigits = hasDigits,
            matchedContact = contact,
            formattedTime = formatShortCallTime(latest.timestamp, todayStartMs, tomorrowStartMs),
            records = recs
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun RecentsScreen(
    recentCalls: List<CallRecord>,
    contacts: List<DeviceContact>,
    blockedNumbers: Set<String>,
    onCall: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onDeleteCalls: (List<CallRecord>) -> Unit,
    onClearHistory: () -> Unit,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contactsIndex: ContactsHelper.PhoneIndex? = null,
    isTabActive: Boolean = true
) {
    val context = LocalContext.current
    val semantic = LocalSemanticColors.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var sourceTab by rememberSaveable { mutableIntStateOf(0) } // 0 All, 1 SIP, 2 SIM
    var filterTab by rememberSaveable { mutableIntStateOf(0) } // 0 All, 1 Outgoing, 2 Received, 3 Missed
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val selectionMode = selectedKeys.isNotEmpty()
    BackHandler(enabled = isTabActive && selectionMode) { selectedKeys = emptySet() }

    val effectivePhoneIndex = remember(contactsIndex, contacts) {
        contactsIndex ?: ContactsHelper.buildPhoneIndex(contacts)
    }

    val filteredRecords = remember(recentCalls, sourceTab, filterTab) {
        recentCalls
            .filter {
                when (sourceTab) {
                    1 -> !it.isSimCall()
                    2 -> it.isSimCall()
                    else -> true
                }
            }
            .filter {
                when (filterTab) {
                    1 -> !it.isIncoming
                    2 -> it.isIncoming && it.isAnswered
                    3 -> it.isIncoming && !it.isAnswered
                    else -> true
                }
            }
            .take(350)
    }

    val recordingAvailableKeys by produceState(initialValue = emptySet<String>(), filteredRecords) {
        value = withContext(Dispatchers.IO) {
            val result = HashSet<String>()
            filteredRecords.forEach { rec ->
                if (rec.recordingPath.isNotEmpty() && RecordingStorage.exists(context, rec.recordingPath)) {
                    result.add(callNumberKey(rec.remoteAddress))
                }
            }
            result
        }
    }

    val smallBatchGroups = remember(filteredRecords, effectivePhoneIndex) {
        if (filteredRecords.size <= 40) {
            buildRecentCallGroups(filteredRecords, effectivePhoneIndex)
        } else {
            null
        }
    }
    val asyncGroups by produceState(
        initialValue = emptyList<RecentCallGroup>(),
        filteredRecords,
        effectivePhoneIndex
    ) {
        if (filteredRecords.size > 40) {
            value = withContext(Dispatchers.Default) {
                buildRecentCallGroups(filteredRecords, effectivePhoneIndex)
            }
        }
    }
    val groupedCalls = smallBatchGroups ?: asyncGroups

    val groups = remember(groupedCalls, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            groupedCalls
        } else {
            val qDigits = q.filter { it.isDigit() }
            groupedCalls.filter { g ->
                val contactName = g.matchedContact?.name ?: g.records.first().displayName
                val numberDigits = g.number.filter { it.isDigit() }
                val accountMatch = g.records.any { rec ->
                    rec.resolvedAccountName().contains(q, ignoreCase = true) ||
                        rec.accountName.contains(q, ignoreCase = true) ||
                        rec.accountUser.contains(q, ignoreCase = true) ||
                        rec.accountId.contains(q, ignoreCase = true) ||
                        rec.remoteAddress.contains(q, ignoreCase = true)
                }
                g.number.contains(q, ignoreCase = true) ||
                    (qDigits.isNotEmpty() && numberDigits.contains(qDigits)) ||
                    contactName.contains(q, ignoreCase = true) ||
                    accountMatch
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedKeys = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                    }
                    Text(
                        text = "${selectedKeys.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val chosen = groups.filter { it.key in selectedKeys }
                            chosen.forEach { onBlockNumber(it.number) }
                            selectedKeys = emptySet()
                        },
                        modifier = Modifier.testTag("recents_block_selected")
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = "Block selected")
                    }
                    IconButton(
                        onClick = {
                            val toDelete = groups.filter { it.key in selectedKeys }.flatMap { it.records }
                            onDeleteCalls(toDelete)
                            selectedKeys = emptySet()
                        },
                        modifier = Modifier.testTag("recents_delete_selected")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search contacts, numbers, SIP accounts...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("recents_search_input")
            )
        }

        // Source chips: All / SIP / SIM
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "SIP", "SIM").forEachIndexed { i, label ->
                FilterChip(
                    selected = sourceTab == i,
                    onClick = { sourceTab = i },
                    label = { Text(label) },
                    modifier = Modifier.testTag("recents_source_$label")
                )
            }
        }

        // Direction segmented pills: All | Outgoing | Received | Missed
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("All", "Outgoing", "Received", "Missed").forEachIndexed { idx, label ->
                    val selected = filterTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .clickable { filterTab = idx }
                            .padding(vertical = 8.dp)
                            .testTag("recents_filter_$label"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = if (recentCalls.isEmpty()) "No Recent Calls" else "No Calls Match Filter",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (recentCalls.isEmpty()) {
                            "SIP and SIM call history will appear here automatically."
                        } else {
                            "Try changing the source or call type filter above."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .imeNestedScroll(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = groups,
                    key = { it.key.ifEmpty { it.number } },
                    contentType = { "recent_call_group" }
                ) { group ->
                    val latest = group.records.first()
                    val matchedContact = group.matchedContact
                    val title = matchedContact?.name ?: latest.displayName.ifBlank { group.number }
                    val isMissed = latest.isIncoming && !latest.isAnswered
                    val isBlocked = group.hasDigits && group.key in blockedNumbers
                    val hasRecording = group.key in recordingAvailableKeys
                    val isSelected = group.key in selectedKeys

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selectedKeys = if (isSelected) selectedKeys - group.key else selectedKeys + group.key
                                    } else {
                                        onOpenDetails(group.number)
                                    }
                                },
                                onLongClick = {
                                    selectedKeys = if (isSelected) selectedKeys - group.key else selectedKeys + group.key
                                }
                            )
                            .testTag("recent_item_${group.key}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            } else {
                                ContactAvatar(
                                    name = title,
                                    photoUri = matchedContact?.photoUri,
                                    size = 44.dp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isMissed) semantic.danger else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (group.records.size > 1) {
                                        Text(
                                            text = " (${group.records.size})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isMissed -> Icons.AutoMirrored.Filled.CallMissed
                                            latest.isIncoming -> Icons.AutoMirrored.Filled.CallReceived
                                            else -> Icons.AutoMirrored.Filled.CallMade
                                        },
                                        contentDescription = null,
                                        tint = if (isMissed) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    val accountDisplay = latest.resolvedAccountName()
                                    val routeSummary = when {
                                        latest.isSimCall() -> if (isMissed) "Missed • SIM" else "SIM"
                                        isMissed -> "Missed • SIP • $accountDisplay"
                                        latest.isIncoming -> "↙ SIP • $accountDisplay"
                                        else -> "↗ SIP • $accountDisplay"
                                    }
                                    Text(
                                        text = buildString {
                                            append(routeSummary)
                                            append(" • ").append(group.number)
                                            if (isBlocked) append(" • Blocked")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isBlocked) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (hasRecording) {
                                        Icon(
                                            imageVector = Icons.Filled.GraphicEq,
                                            contentDescription = "Has call recording",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = group.formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (latest.isAnswered && latest.duration > 0) {
                                    Text(
                                        text = "${latest.duration}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onCall(group.number) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("recent_call_${group.key}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Call $title",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
