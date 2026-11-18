package com.sipdial.app.ui.screens

import android.content.Context
import android.os.SystemClock
import android.telephony.PhoneNumberUtils
import android.view.View
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.WifiCalling3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.telecom.sim.DialCodes
import com.sipdial.app.ui.components.ContactAvatar
import com.sipdial.app.ui.components.DialPad
import com.sipdial.app.ui.theme.JetBrainsMono
import com.sipdial.app.util.CallingPrefs
import com.sipdial.app.util.DeviceContact
import com.sipdial.app.util.GeneralPrefs
import kotlinx.coroutines.awaitCancellation
import java.util.Locale

private data class PendingCallTarget(
    val displayNumber: String,
    val dialNumber: String,
    val clearDialpadAfter: Boolean
)

private data class PendingConfirmTarget(
    val displayNumber: String,
    val action: () -> Unit
)

internal fun normalizeDialInput(raw: String): String {
    val sb = StringBuilder()
    for (ch in raw) {
        when {
            ch == '+' || ch == '*' || ch == '#' -> sb.append(ch)
            ch in '0'..'9' -> sb.append(ch)
            Character.isDigit(ch) -> {
                val v = Character.digit(ch, 10)
                if (v in 0..9) sb.append(('0' + v))
            }
        }
    }
    return sb.toString()
}

internal fun resolveSuggestionDialNumber(rawNumber: String): String {
    val trimmed = rawNumber.trim()
    if (trimmed.contains("@")) return trimmed
    return normalizeDialInput(trimmed).ifEmpty { trimmed }
}

internal fun sanitizeDialFieldChange(incoming: TextFieldValue): TextFieldValue {
    val raw = incoming.text
    val filtered = normalizeDialInput(raw)
    if (filtered == raw) {
        val start = incoming.selection.start.coerceIn(0, filtered.length)
        val end = incoming.selection.end.coerceIn(0, filtered.length)
        return incoming.copy(
            text = filtered,
            selection = TextRange(start, end)
        )
    }

    fun mappedIndex(rawIdx: Int): Int {
        val clamped = rawIdx.coerceIn(0, raw.length)
        return normalizeDialInput(raw.substring(0, clamped)).length.coerceIn(0, filtered.length)
    }

    val nextStart = mappedIndex(incoming.selection.start)
    val nextEnd = mappedIndex(incoming.selection.end)
    return TextFieldValue(
        text = filtered,
        selection = TextRange(nextStart, nextEnd)
    )
}

internal data class IndexedKeypadContact(
    val contact: DeviceContact,
    val t9Name: String,
    val digitsOnly: String
)

internal fun buildKeypadSuggestionIndex(contacts: List<DeviceContact>): List<IndexedKeypadContact> =
    contacts.map { c ->
        IndexedKeypadContact(
            contact = c,
            t9Name = t9Code(c.name),
            digitsOnly = c.normalizedDigits.filter { it.isDigit() }
        )
    }

internal fun filterKeypadSuggestionsIndexed(
    indexedContacts: List<IndexedKeypadContact>,
    query: String
): List<DeviceContact> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val dialChars = normalizeDialInput(q)
    val digitsOnly = q.filter { it.isDigit() }
    val checkT9 = digitsOnly.length >= 2
    val matches = ArrayList<DeviceContact>(15)
    for (entry in indexedContacts) {
        val c = entry.contact
        if (c.number.contains(q, ignoreCase = true) ||
            (dialChars.isNotEmpty() && c.normalizedDigits.contains(dialChars, ignoreCase = true)) ||
            (digitsOnly.isNotEmpty() && entry.digitsOnly.contains(digitsOnly)) ||
            (checkT9 && entry.t9Name.contains(digitsOnly))
        ) {
            matches.add(c)
            if (matches.size >= 15) break
        }
    }
    return matches
}

internal fun filterKeypadSuggestions(contacts: List<DeviceContact>, query: String): List<DeviceContact> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val dialChars = normalizeDialInput(q)
    val digitsOnly = q.filter { it.isDigit() }
    return contacts.filter { c ->
        val contactDigitsOnly = c.normalizedDigits.filter { it.isDigit() }
        c.number.contains(q, ignoreCase = true) ||
            (dialChars.isNotEmpty() && c.normalizedDigits.contains(dialChars, ignoreCase = true)) ||
            (digitsOnly.isNotEmpty() && contactDigitsOnly.contains(digitsOnly)) ||
            (digitsOnly.length >= 2 && t9Code(c.name).contains(digitsOnly))
    }.take(15)
}

private fun t9Code(name: String): String {
    val sb = StringBuilder(name.length)
    for (ch in name.lowercase()) {
        when (ch) {
            'a', 'b', 'c' -> sb.append('2')
            'd', 'e', 'f' -> sb.append('3')
            'g', 'h', 'i' -> sb.append('4')
            'j', 'k', 'l' -> sb.append('5')
            'm', 'n', 'o' -> sb.append('6')
            'p', 'q', 'r', 's' -> sb.append('7')
            't', 'u', 'v' -> sb.append('8')
            'w', 'x', 'y', 'z' -> sb.append('9')
            in '0'..'9' -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun hideSystemSoftInput(context: Context, view: View) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val token = view.windowToken
    if (imm != null && token != null) {
        imm.hideSoftInputFromWindow(token, 0)
    }
}

private fun isEmergencyNumber(n: String): Boolean =
    n.filter { it.isDigit() } in setOf("112", "999", "911", "000", "110", "119")

internal fun insertAtSelection(current: TextFieldValue, inserted: String): TextFieldValue {
    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    val nextText = text.substring(0, start) + inserted + text.substring(end)
    val nextCursor = (start + inserted.length).coerceIn(0, nextText.length)
    return TextFieldValue(text = nextText, selection = TextRange(nextCursor))
}

internal fun backspaceAtSelection(current: TextFieldValue): TextFieldValue {
    val text = current.text
    if (text.isEmpty()) return current
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    return if (start != end) {
        val nextText = text.removeRange(start, end)
        TextFieldValue(text = nextText, selection = TextRange(start.coerceIn(0, nextText.length)))
    } else if (start > 0) {
        val nextText = text.removeRange(start - 1, start)
        TextFieldValue(text = nextText, selection = TextRange((start - 1).coerceIn(0, nextText.length)))
    } else {
        current
    }
}

private class PhoneFormatVisualTransformation(
    private val enabled: Boolean,
    private val countryIso: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!enabled || raw.isEmpty() || raw.any { it == '*' || it == '#' || it == '@' || it.isLetter() } || raw.length > 18) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val formatted = try {
            PhoneNumberUtils.formatNumber(raw, countryIso)
        } catch (_: Throwable) {
            null
        }
        if (formatted.isNullOrBlank() || formatted == raw) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val rawIndices = IntArray(formatted.length + 1)
        val formattedIndices = IntArray(raw.length + 1)
        var rIdx = 0
        for (fIdx in formatted.indices) {
            rawIndices[fIdx] = rIdx
            if (rIdx < raw.length && formatted[fIdx] == raw[rIdx]) {
                formattedIndices[rIdx] = fIdx
                rIdx++
            }
        }
        rawIndices[formatted.length] = rIdx
        while (rIdx <= raw.length) {
            formattedIndices[rIdx] = formatted.length
            rIdx++
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return formattedIndices[offset.coerceIn(0, raw.length)].coerceIn(0, formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                return rawIndices[offset.coerceIn(0, formatted.length)].coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun KeypadScreen(
    registrationState: SipRegistrationState,
    currentAccount: SipAccountData?,
    contacts: List<DeviceContact>,
    favoriteNumbers: Set<String>,
    prefillNumber: String?,
    onPrefillConsumed: () -> Unit,
    onSipCall: (String) -> Unit,
    onToggleFavorite: (name: String, number: String, photoUri: String?) -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    dialInput: TextFieldValue? = null,
    onDialInputChange: ((TextFieldValue) -> Unit)? = null,
    dialableSipAccounts: List<SipAccountData> = emptyList(),
    onSipCallWithAccount: ((String, String?) -> Unit)? = null,
    isTabActive: Boolean = true
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val outerKeyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val generalPrefs by GeneralPrefs.state.collectAsStateWithLifecycle()

    var internalInput by remember { mutableStateOf(TextFieldValue("")) }
    val activeInput = dialInput ?: internalInput
    val phoneNumber = activeInput.text

    fun updateInput(next: TextFieldValue) {
        if (onDialInputChange != null) onDialInputChange(next)
        else internalInput = next
    }

    LaunchedEffect(isTabActive) {
        if (isTabActive) {
            outerKeyboardController?.hide()
            hideSystemSoftInput(context, hostView)
        }
    }

    val noOpKeyboardController = remember(outerKeyboardController, context, hostView) {
        object : SoftwareKeyboardController {
            override fun show() {
                outerKeyboardController?.hide()
                hideSystemSoftInput(context, hostView)
            }

            override fun hide() {
                outerKeyboardController?.hide()
                hideSystemSoftInput(context, hostView)
            }
        }
    }
    val suppressPlatformImeInterceptor = remember {
        PlatformTextInputInterceptor { _, _ -> awaitCancellation() }
    }

    var dialCodeMsg by remember { mutableStateOf<String?>(null) }
    var pendingChooserTarget by remember { mutableStateOf<PendingCallTarget?>(null) }
    var pendingConfirmTarget by remember { mutableStateOf<PendingConfirmTarget?>(null) }
    var showSipLineMenu by remember { mutableStateOf(false) }
    var selectedDialSipAccountId by remember(currentAccount?.id) { mutableStateOf(currentAccount?.id) }
    var lastCallTapAtMs by remember { mutableLongStateOf(-1000L) }

    val sims = remember { TelecomHelper.getAvailableSimAccounts(context) }
    val isRegistered = registrationState == SipRegistrationState.REGISTERED
    val sipLineOptions = remember(dialableSipAccounts, currentAccount) {
        if (dialableSipAccounts.isNotEmpty()) dialableSipAccounts
        else listOfNotNull(currentAccount)
    }
    val chosenSipAccount = remember(sipLineOptions, selectedDialSipAccountId, currentAccount) {
        sipLineOptions.firstOrNull { it.id == selectedDialSipAccountId }
            ?: currentAccount
            ?: sipLineOptions.firstOrNull()
    }

    val countryIso = remember { Locale.getDefault().country.ifBlank { "US" } }
    val visualTransformation = remember(generalPrefs.numberFormattingEnabled, countryIso) {
        PhoneFormatVisualTransformation(
            enabled = generalPrefs.numberFormattingEnabled,
            countryIso = countryIso
        )
    }

    LaunchedEffect(prefillNumber) {
        if (!prefillNumber.isNullOrBlank()) {
            updateInput(TextFieldValue(prefillNumber, TextRange(prefillNumber.length)))
            onPrefillConsumed()
        }
    }

    LaunchedEffect(phoneNumber) {
        if (DialCodes.isAutoCode(phoneNumber)) {
            val code = phoneNumber
            updateInput(TextFieldValue(""))
            DialCodes.handle(context, code, sims.firstOrNull()?.handle) { dialCodeMsg = it }
        }
    }

    val suggestionIndex = remember(contacts) {
        buildKeypadSuggestionIndex(contacts)
    }
    val matchingContacts = remember(suggestionIndex, phoneNumber) {
        filterKeypadSuggestionsIndexed(suggestionIndex, phoneNumber)
    }

    fun executeSimCall(target: String, slotIndex: Int, clearDialpad: Boolean) {
        val clean = target.trim()
        if (clean.isEmpty()) return
        val handle = sims.getOrNull(slotIndex)?.handle
        if (!DialCodes.handle(context, clean, handle) { dialCodeMsg = it }) {
            TelecomHelper.placeSimCallWithHandle(context, clean, handle)
        }
        if (clearDialpad) {
            updateInput(TextFieldValue(""))
        }
    }

    fun executeSipCall(target: String, accountId: String? = chosenSipAccount?.id, clearDialpad: Boolean) {
        val clean = target.trim()
        if (clean.isEmpty()) return
        val canDialSip = isRegistered || sipLineOptions.isNotEmpty()
        if (canDialSip) {
            if (onSipCallWithAccount != null) {
                onSipCallWithAccount(clean, accountId)
            } else {
                onSipCall(clean)
            }
            if (clearDialpad) {
                updateInput(TextFieldValue(""))
            }
        } else {
            onOpenAccounts()
        }
    }

    fun triggerCall(displayNumber: String, action: () -> Unit) {
        if (CallingPrefs.isCallConfirmationEnabled(context)) {
            pendingConfirmTarget = PendingConfirmTarget(displayNumber, action)
        } else {
            action()
        }
    }

    fun initiateCallForTarget(
        displayNumber: String,
        dialNumber: String,
        clearDialpad: Boolean
    ) {
        val cleanDial = dialNumber.trim()
        val cleanDisplay = displayNumber.trim().ifEmpty { cleanDial }
        if (cleanDial.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        if (lastCallTapAtMs >= 0L && now - lastCallTapAtMs < 550L) return
        lastCallTapAtMs = now

        if (DialCodes.isCode(cleanDial) || isEmergencyNumber(cleanDial)) {
            executeSimCall(cleanDial, slotIndex = 0, clearDialpad = clearDialpad)
            return
        }

        when (TelecomHelper.getNativeCallRoutingMode(context)) {
            TelecomHelper.MODE_ALWAYS_SIP -> {
                if (sipLineOptions.size > 1 && chosenSipAccount == null) {
                    pendingChooserTarget = PendingCallTarget(cleanDisplay, cleanDial, clearDialpad)
                } else {
                    triggerCall(cleanDisplay) {
                        executeSipCall(cleanDial, chosenSipAccount?.id, clearDialpad)
                    }
                }
            }
            TelecomHelper.MODE_SIM_ONLY -> {
                if (sims.size > 1) {
                    pendingChooserTarget = PendingCallTarget(cleanDisplay, cleanDial, clearDialpad)
                } else {
                    triggerCall(cleanDisplay) {
                        executeSimCall(cleanDial, slotIndex = 0, clearDialpad = clearDialpad)
                    }
                }
            }
            else -> {
                pendingChooserTarget = PendingCallTarget(cleanDisplay, cleanDial, clearDialpad)
            }
        }
    }

    fun handleDialpadPrimaryCallTap() {
        val target = phoneNumber.trim()
        if (target.isEmpty()) return
        initiateCallForTarget(
            displayNumber = target,
            dialNumber = target,
            clearDialpad = true
        )
    }

    dialCodeMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { dialCodeMsg = null },
            title = { Text("Dial Code Info") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { dialCodeMsg = null }) { Text("OK") } }
        )
    }

    pendingConfirmTarget?.let { confirm ->
        AlertDialog(
            onDismissRequest = { pendingConfirmTarget = null },
            title = { Text("Confirm Call") },
            text = { Text("Place call to ${confirm.displayNumber}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirmTarget = null
                        confirm.action()
                    }
                ) { Text("Call") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmTarget = null }) { Text("Cancel") }
            }
        )
    }

    pendingChooserTarget?.let { target ->
        Dialog(onDismissRequest = { pendingChooserTarget = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Choose Calling Line", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = target.displayNumber,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sipLineOptions.isEmpty()) {
                        Button(
                            onClick = {
                                pendingChooserTarget = null
                                onOpenAccounts()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("chooser_sip_button")
                        ) {
                            Icon(Icons.Filled.WifiCalling3, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SIP (Configure Account)")
                        }
                    } else {
                        sipLineOptions.forEachIndexed { idx, acc ->
                            val accLabel = acc.name.ifBlank { acc.username }.ifBlank { acc.domain }
                            Button(
                                onClick = {
                                    pendingChooserTarget = null
                                    selectedDialSipAccountId = acc.id
                                    triggerCall(target.displayNumber) {
                                        executeSipCall(target.dialNumber, acc.id, target.clearDialpadAfter)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag(if (idx == 0) "chooser_sip_button" else "chooser_sip_${acc.id}")
                            ) {
                                Icon(Icons.Filled.WifiCalling3, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SIP ($accLabel)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (sims.isEmpty()) {
                        Button(
                            onClick = {
                                pendingChooserTarget = null
                                triggerCall(target.displayNumber) {
                                    executeSimCall(target.dialNumber, 0, target.clearDialpadAfter)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("chooser_sim_default_button")
                        ) {
                            Icon(Icons.Filled.SimCard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SIM Card")
                        }
                    } else {
                        sims.forEachIndexed { idx, sim ->
                            Button(
                                onClick = {
                                    pendingChooserTarget = null
                                    triggerCall(target.displayNumber) {
                                        executeSimCall(target.dialNumber, idx, target.clearDialpadAfter)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("chooser_sim_${idx + 1}_button")
                            ) {
                                Icon(Icons.Filled.SimCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(sim.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { pendingChooserTarget = null }) { Text("Cancel") }
                    }
                }
            }
        }
    }

    val dynamicFontSize = when {
        phoneNumber.length > 28 -> 17.sp
        phoneNumber.length > 22 -> 19.sp
        phoneNumber.length > 16 -> 22.sp
        phoneNumber.length > 12 -> 26.sp
        else -> 32.sp
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Upper contact suggestions or account banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            if (matchingContacts.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = matchingContacts,
                        key = { "${it.id}_${it.number}" },
                        contentType = { "dialpad_suggestion" }
                    ) { contact ->
                        val suggestionDialNumber = remember(contact.number) {
                            resolveSuggestionDialNumber(contact.number)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val digits = resolveSuggestionDialNumber(contact.number)
                                    updateInput(TextFieldValue(digits, TextRange(digits.length)))
                                }
                                .testTag("keypad_suggestion_row_${contact.id}_${contact.number}"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 38.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.number,
                                        fontFamily = JetBrainsMono,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        // Independent calling destination: call this suggestion's actual number directly
                                        // without reading or depending on dialpad input state.
                                        initiateCallForTarget(
                                            displayNumber = contact.number,
                                            dialNumber = suggestionDialNumber,
                                            clearDialpad = false
                                        )
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("keypad_suggestion_call_${contact.id}_${contact.number}")
                                ) {
                                    Icon(
                                        Icons.Filled.Call,
                                        contentDescription = "Call ${contact.name} (${contact.number})",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (currentAccount == null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenAccounts),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.WifiCalling3,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "Set up a SIP Account",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Or dial any number now to call via SIM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Google Phone-style Dialed Number Sheet & Keypad
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Outgoing SIP Account selector pill when multiple SIP accounts exist
                if (sipLineOptions.size > 1) {
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showSipLineMenu = true }
                                .testTag("keypad_sip_account_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WifiCalling3,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Calling via ${chosenSipAccount?.name?.ifBlank { chosenSipAccount.username } ?: "SIP"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Filled.ExpandMore,
                                    contentDescription = "Choose outgoing SIP account",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showSipLineMenu,
                            onDismissRequest = { showSipLineMenu = false }
                        ) {
                            sipLineOptions.forEach { acc ->
                                val label = acc.name.ifBlank { acc.username }.ifBlank { acc.domain }
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(label, fontWeight = if (acc.id == chosenSipAccount?.id) FontWeight.Bold else FontWeight.Normal)
                                            Text(
                                                "${acc.username}@${acc.domain}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedDialSipAccountId = acc.id
                                        showSipLineMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Clean full-width native Android editable number field (IME suppressed; long-press Copy/Paste/Cut/Select All intact)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InterceptPlatformTextInput(interceptor = suppressPlatformImeInterceptor) {
                        CompositionLocalProvider(LocalSoftwareKeyboardController provides noOpKeyboardController) {
                            BasicTextField(
                                value = activeInput,
                                onValueChange = { incoming ->
                                    updateInput(sanitizeDialFieldChange(incoming))
                                },
                                singleLine = true,
                                visualTransformation = visualTransformation,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontSize = dynamicFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (phoneNumber.isEmpty()) {
                                            Text(
                                                text = "Enter number",
                                                fontFamily = JetBrainsMono,
                                                fontSize = 26.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                maxLines = 1
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            outerKeyboardController?.hide()
                                            hideSystemSoftInput(context, hostView)
                                        }
                                    }
                                    .testTag("keypad_number_display")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                DialPad(
                    onDigitPressed = { digit ->
                        updateInput(insertAtSelection(activeInput, digit))
                    },
                    onLongPressZero = {
                        updateInput(insertAtSelection(activeInput, "+"))
                    },
                    flat = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (phoneNumber.isNotEmpty() && isRegistered) {
                            IconButton(
                                onClick = {
                                    val target = phoneNumber.trim()
                                    if (target.isNotEmpty()) {
                                        triggerCall(target) {
                                            executeSimCall(target, slotIndex = 0, clearDialpad = true)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("keypad_quick_sim_button")
                            ) {
                                Icon(
                                    Icons.Filled.SimCard,
                                    contentDescription = "Call via SIM",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { handleDialpadPrimaryCallTap() },
                        enabled = phoneNumber.isNotEmpty(),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .testTag("keypad_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Call",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = phoneNumber.isNotEmpty(),
                                onClick = {
                                    if (generalPrefs.hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    updateInput(backspaceAtSelection(activeInput))
                                },
                                onLongClick = {
                                    if (generalPrefs.hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    updateInput(TextFieldValue(""))
                                }
                            )
                            .testTag("keypad_backspace_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = if (phoneNumber.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}
