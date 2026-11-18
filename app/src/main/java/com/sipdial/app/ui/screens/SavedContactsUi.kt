package com.sipdial.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sipdial.app.data.models.FavoriteContact
import com.sipdial.app.data.models.SavedContact
import com.sipdial.app.ui.components.ContactAvatar
import com.sipdial.app.ui.components.bringIntoViewOnFocusWithIme
import com.sipdial.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay

data class AddContactRequest(
    val category: String,
    val number: String,
    val name: String
)

private val WhatsAppBrandGreen = Color(0xFF16A34A)
private val TelegramBrandBlue = Color(0xFF2AABEE)

fun categoryLabel(category: String): String =
    if (category == SavedContact.CATEGORY_OFFICE) "Office" else "Client"

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SavedContactsTab(
    category: String,
    contacts: List<SavedContact>,
    totalInCategory: Int,
    favoriteNumbers: Set<String>,
    onAdd: () -> Unit,
    onCall: (SavedContact) -> Unit,
    onEdit: (SavedContact) -> Unit,
    onDelete: (SavedContact) -> Unit,
    onToggleFavorite: (SavedContact) -> Unit,
    onOpenChat: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val semantic = LocalSemanticColors.current
    val label = categoryLabel(category)
    var menuFor by remember { mutableStateOf<SavedContact?>(null) }
    var detailsFor by remember { mutableStateOf<SavedContact?>(null) }
    var deleteFor by remember { mutableStateOf<SavedContact?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalInCategory $label contact" + if (totalInCategory == 1) "" else "s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("add_saved_contact_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add $label")
            }
        }

        if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
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
                        text = if (totalInCategory == 0) "No $label contacts yet" else "No matching contacts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (totalInCategory == 0) {
                        Text(
                            text = "Tap Add $label, or add from Recents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .imeNestedScroll(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(contacts, key = { _, c -> c.id }, contentType = { _, _ -> "saved_contact_row" }) { _, c ->
                    val isFav = FavoriteContact.normalize(c.number) in favoriteNumbers
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { detailsFor = c },
                                onLongClick = { menuFor = c }
                            ),
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
                            ContactAvatar(name = c.name, size = 44.dp)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = c.number,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (c.hasWhatsapp) MiniMessengerBadge("WA", WhatsAppBrandGreen)
                                    if (c.hasTelegram) MiniMessengerBadge("TG", TelegramBrandBlue)
                                }
                            }

                            IconButton(
                                onClick = { onToggleFavorite(c) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (isFav) "Remove favorite" else "Add favorite",
                                    tint = if (isFav) semantic.warning else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { onCall(c) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Call ${c.name}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { c ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    MenuActionRow(Icons.Filled.Visibility, "View details", MaterialTheme.colorScheme.onSurface) {
                        menuFor = null
                        detailsFor = c
                    }
                    MenuActionRow(Icons.Filled.Edit, "Edit", MaterialTheme.colorScheme.onSurface) {
                        menuFor = null
                        onEdit(c)
                    }
                    MenuActionRow(Icons.Filled.Delete, "Delete", MaterialTheme.colorScheme.error) {
                        menuFor = null
                        deleteFor = c
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text("Cancel") } }
        )
    }

    deleteFor?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete contact?") },
            text = { Text("${c.name} will be removed from your $label list. Phone contacts are unaffected.") },
            confirmButton = {
                TextButton(onClick = { deleteFor = null; onDelete(c) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } }
        )
    }

    detailsFor?.let { c ->
        SavedContactDetailsDialog(
            contact = c,
            onDismiss = { detailsFor = null },
            onCall = { detailsFor = null; onCall(c) },
            onEdit = { detailsFor = null; onEdit(c) },
            onOpenChat = onOpenChat
        )
    }
}

@Composable
private fun MiniMessengerBadge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SavedContactDetailsDialog(
    contact: SavedContact,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(contact.name, fontWeight = FontWeight.Bold)
                Text(
                    categoryLabel(contact.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SavedDetailField("Number", contact.number)
                SavedDetailField("Company", contact.company)
                SavedDetailField("Email", contact.email)
                SavedDetailField("Address", contact.address)
                SavedDetailField("Birthday", contact.birthday)
                SavedDetailField("Notes", contact.notes)
                if (contact.hasWhatsapp || contact.hasTelegram) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (contact.hasWhatsapp) {
                            OutlinedButton(onClick = { onOpenChat("whatsapp", contact.number) }) {
                                Text("WhatsApp", color = WhatsAppBrandGreen)
                            }
                        }
                        if (contact.hasTelegram) {
                            OutlinedButton(onClick = { onOpenChat("telegram", contact.number) }) {
                                Text("Telegram", color = TelegramBrandBlue)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCall) { Text("Call") }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SavedDetailField(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SavedContactFormDialog(
    draft: SavedContact,
    detectMessengers: suspend (String) -> Pair<Boolean, Boolean>,
    onSave: (SavedContact) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    var name by remember(draft) { mutableStateOf(draft.name) }
    var number by remember(draft) { mutableStateOf(draft.number) }
    var company by remember(draft) { mutableStateOf(draft.company) }
    var email by remember(draft) { mutableStateOf(draft.email) }
    var address by remember(draft) { mutableStateOf(draft.address) }
    var birthday by remember(draft) { mutableStateOf(draft.birthday) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var wa by remember(draft) { mutableStateOf(draft.hasWhatsapp) }
    var tg by remember(draft) { mutableStateOf(draft.hasTelegram) }
    var autoNote by remember(draft) { mutableStateOf<String?>(null) }

    LaunchedEffect(number) {
        if (number.filter { it.isDigit() }.length >= 7) {
            delay(400)
            val (w, t) = detectMessengers(number)
            var found = false
            if (w && !wa) { wa = true; found = true }
            if (t && !tg) { tg = true; found = true }
            if (found) autoNote = "Detected from phone contacts"
        }
    }

    val isNew = draft.id == 0L
    val canSave = name.isNotBlank() && number.filter { it.isDigit() }.length >= 3

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }
    val numberFocus = remember { FocusRequester() }
    val companyFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    val birthdayFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = (if (isNew) "Add " else "Edit ") + categoryLabel(draft.category) + " contact",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SavedFormField(
                            value = name,
                            onChange = { name = it },
                            label = "Name *",
                            imeAction = ImeAction.Next,
                            onNext = { numberFocus.requestFocus() },
                            modifier = Modifier
                                .focusRequester(nameFocus)
                                .testTag("contact_form_name")
                        )
                        SavedFormField(
                            value = number,
                            onChange = { number = it },
                            label = "Number *",
                            keyboard = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                            onNext = { companyFocus.requestFocus() },
                            modifier = Modifier
                                .focusRequester(numberFocus)
                                .testTag("contact_form_number")
                        )
                        SavedFormField(
                            value = company,
                            onChange = { company = it },
                            label = "Company",
                            imeAction = ImeAction.Next,
                            onNext = { emailFocus.requestFocus() },
                            modifier = Modifier.focusRequester(companyFocus)
                        )
                        SavedFormField(
                            value = email,
                            onChange = { email = it },
                            label = "Email",
                            keyboard = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                            onNext = { addressFocus.requestFocus() },
                            modifier = Modifier.focusRequester(emailFocus)
                        )
                        SavedFormField(
                            value = address,
                            onChange = { address = it },
                            label = "Address",
                            imeAction = ImeAction.Next,
                            onNext = { birthdayFocus.requestFocus() },
                            modifier = Modifier.focusRequester(addressFocus)
                        )
                        SavedFormField(
                            value = birthday,
                            onChange = { birthday = it },
                            label = "Birthday (DD/MM/YYYY)",
                            keyboard = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                            onNext = { notesFocus.requestFocus() },
                            modifier = Modifier.focusRequester(birthdayFocus)
                        )
                        SavedFormField(
                            value = notes,
                            onChange = { notes = it },
                            label = "Notes",
                            singleLine = false,
                            imeAction = ImeAction.Default,
                            modifier = Modifier.focusRequester(notesFocus)
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Has WhatsApp", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = wa, onCheckedChange = { wa = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Has Telegram", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = tg, onCheckedChange = { tg = it })
                        }
                        autoNote?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = semantic.success)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onDismiss()
                            }
                        ) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            enabled = canSave,
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onSave(
                                    draft.copy(
                                        name = name.trim(),
                                        number = number.trim(),
                                        company = company.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        birthday = birthday.trim(),
                                        notes = notes.trim(),
                                        hasWhatsapp = wa,
                                        hasTelegram = tg
                                    )
                                )
                            },
                            modifier = Modifier.testTag("contact_form_save_button")
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedFormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewOnFocusWithIme()
    )
}
