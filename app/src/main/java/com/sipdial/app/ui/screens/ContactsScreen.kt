package com.sipdial.app.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.sipdial.app.data.models.FavoriteContact
import com.sipdial.app.data.models.SavedContact
import com.sipdial.app.ui.components.ContactAvatar
import com.sipdial.app.ui.theme.LocalSemanticColors
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen(
    contacts: List<DeviceContact>,
    hasContactsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCallContact: (String) -> Unit,
    savedContacts: List<SavedContact>,
    favoriteNumbers: Set<String>,
    onToggleFavorite: (name: String, number: String, photoUri: String?) -> Unit,
    onSaveContact: (SavedContact) -> Unit,
    onDeleteSavedContact: (SavedContact) -> Unit,
    onOpenChat: (String, String) -> Unit,
    detectMessengers: suspend (String) -> Pair<Boolean, Boolean>,
    pendingAdd: AddContactRequest? = null,
    onPendingConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    phoneListState: LazyListState = rememberLazyListState(),
    clientListState: LazyListState = rememberLazyListState(),
    officeListState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val semantic = LocalSemanticColors.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 Phone, 1 Client, 2 Office
    var formDraft by remember { mutableStateOf<SavedContact?>(null) }
    var phoneMenu by remember { mutableStateOf<DeviceContact?>(null) }

    LaunchedEffect(pendingAdd) {
        if (pendingAdd != null) {
            tab = if (pendingAdd.category == SavedContact.CATEGORY_OFFICE) 2 else 1
            formDraft = SavedContact(
                category = pendingAdd.category,
                name = pendingAdd.name,
                number = pendingAdd.number
            )
            onPendingConsumed()
        }
    }

    val category = if (tab == 2) SavedContact.CATEGORY_OFFICE else SavedContact.CATEGORY_CLIENT
    val inCategory = remember(savedContacts, category) { savedContacts.filter { it.category == category } }
    val filteredSaved = remember(inCategory, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) inCategory
        else {
            val digits = q.filter { it.isDigit() || it == '+' }
            inCategory.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.company.contains(q, ignoreCase = true) ||
                    it.number.contains(q, ignoreCase = true) ||
                    (digits.isNotEmpty() && it.number.filter { c -> c.isDigit() || c == '+' }.contains(digits))
            }
        }
    }

    val filteredContacts = remember(contacts, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) contacts
        else {
            val cleanDigits = q.filter { it.isDigit() || it == '+' }
            contacts.filter { contact ->
                contact.name.contains(q, ignoreCase = true) ||
                    contact.number.contains(q, ignoreCase = true) ||
                    (cleanDigits.isNotEmpty() && contact.normalizedDigits.contains(cleanDigits))
            }
        }
    }

    val favKeyByContact = remember(contacts) {
        HashMap<String, String>(maxOf(16, contacts.size * 2)).also { map ->
            contacts.forEach { c ->
                map["${c.id}_${c.number}"] = FavoriteContact.normalize(c.number)
            }
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
                    placeholder = { Text("Search contacts...") },
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
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("contacts_search_input")
                )
                if (tab == 0 && hasContactsPermission) {
                    FilledTonalIconButton(
                        onClick = { ContactsHelper.openInsertContactIntent(context, "") },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("add_phone_contact_fab")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Create phone contact")
                    }
                }
            }

            // Segmented Phone | Client | Office tabs
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    listOf("Phone", "Client", "Office").forEachIndexed { idx, label ->
                        val selected = tab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .clickable { tab = idx }
                                .padding(vertical = 8.dp)
                                .testTag("contacts_tab_$label"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (tab != 0) {
                SavedContactsTab(
                    category = category,
                    contacts = filteredSaved,
                    totalInCategory = inCategory.size,
                    favoriteNumbers = favoriteNumbers,
                    onAdd = { formDraft = SavedContact(category = category, name = "", number = "") },
                    onCall = { c ->
                        val raw = c.number.trim()
                        val target = if (raw.contains("@")) raw else raw.filter { ch -> ch.isDigit() || ch == '+' || ch == '*' || ch == '#' }.ifEmpty { raw }
                        onCallContact(target)
                    },
                    onEdit = { formDraft = it },
                    onDelete = onDeleteSavedContact,
                    onToggleFavorite = { c -> onToggleFavorite(c.name, c.number, null) },
                    onOpenChat = onOpenChat,
                    listState = if (tab == 2) officeListState else clientListState
                )
            } else if (!hasContactsPermission) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Contacts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = "Contacts Permission Needed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Grant Contacts permission to view phone contacts, caller names, and photos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("grant_contacts_permission_button")
                        ) {
                            Text("Allow Contacts Access")
                        }
                    }
                }
            } else if (filteredContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = if (searchQuery.isEmpty()) "No Phone Contacts Found" else "No matching contacts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = phoneListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .imeNestedScroll(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = filteredContacts,
                        key = { _, c -> "${c.id}_${c.number}" },
                        contentType = { _, _ -> "phone_contact_row" }
                    ) { _, contact ->
                        val favKey = favKeyByContact["${contact.id}_${contact.number}"]
                            ?: FavoriteContact.normalize(contact.number)
                        val isFav = favKey in favoriteNumbers
                        PhoneContactRow(
                            contact = contact,
                            isFavorite = isFav,
                            favoriteTint = semantic.warning,
                            onCall = { onCallContact(contact.normalizedDigits) },
                            onToggleFavorite = { onToggleFavorite(contact.name, contact.number, contact.photoUri) },
                            onLongClick = { phoneMenu = contact }
                        )
                    }
                }
            }
        }
    }

    phoneMenu?.let { c ->
        AlertDialog(
            onDismissRequest = { phoneMenu = null },
            title = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "View / Edit in Phone Contacts",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                phoneMenu = null
                                try {
                                    val contactId = c.id.toLongOrNull()
                                    if (contactId != null) {
                                        val contactUri = android.content.ContentUris.withAppendedId(
                                            android.provider.ContactsContract.Contacts.CONTENT_URI,
                                            contactId
                                        )
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            contactUri
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } else {
                                        ContactsHelper.openInsertContactIntent(context, c.number)
                                    }
                                } catch (_: Exception) {
                                    ContactsHelper.openInsertContactIntent(context, c.number)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                    listOf(
                        SavedContact.CATEGORY_CLIENT to "Save to Client list",
                        SavedContact.CATEGORY_OFFICE to "Save to Office list"
                    ).forEach { (cat, label) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    phoneMenu = null
                                    tab = if (cat == SavedContact.CATEGORY_OFFICE) 2 else 1
                                    formDraft = SavedContact(category = cat, name = c.name, number = c.number)
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { phoneMenu = null }) { Text("Cancel") } }
        )
    }

    formDraft?.let { draft ->
        SavedContactFormDialog(
            draft = draft,
            detectMessengers = detectMessengers,
            onSave = { onSaveContact(it); formDraft = null },
            onDismiss = { formDraft = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneContactRow(
    contact: DeviceContact,
    isFavorite: Boolean,
    favoriteTint: androidx.compose.ui.graphics.Color,
    onCall: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onCall, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContactAvatar(
                name = contact.name,
                photoUri = contact.photoUri,
                size = 44.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (isFavorite) favoriteTint else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCall,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Call ${contact.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
