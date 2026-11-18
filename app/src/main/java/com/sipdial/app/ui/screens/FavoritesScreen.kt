package com.sipdial.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sipdial.app.data.models.FavoriteContact
import com.sipdial.app.ui.components.ContactAvatar
import com.sipdial.app.ui.theme.JetBrainsMono
import com.sipdial.app.util.DeviceContact

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    favorites: List<FavoriteContact>,
    contacts: List<DeviceContact>,
    onCallFavorite: (FavoriteContact) -> Unit,
    onLoadOnKeypad: (String) -> Unit,
    onAddFavorite: (name: String, number: String, photoUri: String?, route: String) -> Unit,
    onRemoveFavorite: (FavoriteContact) -> Unit,
    onUpdateRoute: (FavoriteContact, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<FavoriteContact?>(null) }

    val contactByNorm = remember(contacts) {
        contacts.associateBy { FavoriteContact.normalize(it.number) }
    }

    val filtered = remember(favorites, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) favorites
        else favorites.filter {
            it.name.contains(q, ignoreCase = true) || it.number.contains(q, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search favorites...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("favorites_search_input")
                )
                FilledTonalIconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("add_favorite_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add favorite")
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            text = if (favorites.isEmpty()) "No Favorites Yet" else "No Matching Favorites",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (favorites.isEmpty()) {
                                "Star contacts or tap + to add speed-dial favorites for instant SIP or SIM calling."
                            } else {
                                "Try a different name or number."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (favorites.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("favorites_empty_add_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Favorite")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { fav ->
                        val photo = fav.photoUri ?: contactByNorm[fav.normalizedNumber]?.photoUri
                        FavoriteCard(
                            favorite = fav,
                            photoUri = photo,
                            onCall = { onCallFavorite(fav) },
                            onCycleRoute = {
                                val next = when (fav.preferredRoute) {
                                    FavoriteContact.ROUTE_ASK -> FavoriteContact.ROUTE_SIP
                                    FavoriteContact.ROUTE_SIP -> FavoriteContact.ROUTE_SIM
                                    else -> FavoriteContact.ROUTE_ASK
                                }
                                onUpdateRoute(fav, next)
                            },
                            onMore = { actionTarget = fav }
                        )
                    }
                }
            }
        }
    }

    actionTarget?.let { fav ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(fav.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = fav.number,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Preferred calling route",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            FavoriteContact.ROUTE_ASK to "Ask",
                            FavoriteContact.ROUTE_SIP to "SIP",
                            FavoriteContact.ROUTE_SIM to "SIM"
                        ).forEach { (route, label) ->
                            FilterChip(
                                selected = fav.preferredRoute == route,
                                onClick = {
                                    onUpdateRoute(fav, route)
                                    actionTarget = fav.copy(preferredRoute = route)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                actionTarget = null
                                onLoadOnKeypad(fav.number)
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Dialpad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Load on Keypad", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                actionTarget = null
                                onRemoveFavorite(fav)
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("Remove from Favorites", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) { Text("Done") }
            }
        )
    }

    if (showAddDialog) {
        AddFavoriteDialog(
            contacts = contacts,
            existingNumbers = favorites.map { it.normalizedNumber }.toSet(),
            onDismiss = { showAddDialog = false },
            onAdd = { name, number, photo, route ->
                onAddFavorite(name, number, photo, route)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(
    favorite: FavoriteContact,
    photoUri: String?,
    onCall: () -> Unit,
    onCycleRoute: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onCall,
                onLongClick = onMore
            )
            .testTag("favorite_card_${favorite.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContactAvatar(
                name = favorite.name,
                photoUri = photoUri,
                size = 48.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = favorite.number,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onCycleRoute)
                    .testTag("favorite_route_${favorite.id}"),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = when (favorite.preferredRoute) {
                        FavoriteContact.ROUTE_SIP -> "SIP"
                        FavoriteContact.ROUTE_SIM -> "SIM"
                        else -> "Ask"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = onCall,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("favorite_call_${favorite.id}")
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call ${favorite.name}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFavoriteDialog(
    contacts: List<DeviceContact>,
    existingNumbers: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (name: String, number: String, photoUri: String?, route: String) -> Unit
) {
    var mode by remember { mutableIntStateOf(if (contacts.isNotEmpty()) 0 else 1) } // 0 Contacts, 1 Manual
    var query by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var manualNumber by remember { mutableStateOf("") }
    var selectedRoute by remember { mutableStateOf(FavoriteContact.ROUTE_ASK) }

    val availableContacts = remember(contacts, existingNumbers, query) {
        val q = query.trim()
        contacts.filter {
            val norm = FavoriteContact.normalize(it.number)
            norm !in existingNumbers && (q.isEmpty() || it.name.contains(q, true) || it.number.contains(q, true))
        }.take(40)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add to Favorites", style = MaterialTheme.typography.titleLarge)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text("From Contacts") }
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text("Enter Number") }
                    )
                }

                Text(
                    "Preferred call route",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FavoriteContact.ROUTE_ASK to "Ask",
                        FavoriteContact.ROUTE_SIP to "SIP",
                        FavoriteContact.ROUTE_SIM to "SIM"
                    ).forEach { (r, label) ->
                        FilterChip(
                            selected = selectedRoute == r,
                            onClick = { selectedRoute = r },
                            label = { Text(label) }
                        )
                    }
                }

                if (mode == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search contacts...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (availableContacts.isEmpty()) {
                        Text(
                            "No available contacts match.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                        ) {
                            items(availableContacts, key = { "${it.id}_${it.number}" }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onAdd(contact.name, contact.number, contact.photoUri, selectedRoute)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 38.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = contact.number,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Filled.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualNumber,
                        onValueChange = { manualNumber = it },
                        label = { Text("Phone or SIP extension") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (mode == 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = manualNumber.trim().isNotEmpty(),
                            onClick = {
                                val num = manualNumber.trim()
                                val name = manualName.trim().ifBlank { num }
                                onAdd(name, num, null, selectedRoute)
                            }
                        ) { Text("Add") }
                    }
                }
            }
        }
    }
}
