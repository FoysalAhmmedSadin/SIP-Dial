package com.sipdial.app.telecom.sim

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.ui.components.bringIntoViewOnFocusWithIme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallForwardingDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sims = remember { TelecomHelper.getAvailableSimAccounts(ctx) }

    var simIdx by remember { mutableIntStateOf(0) }
    var simType by remember { mutableIntStateOf(0) }
    var simNumber by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    var sipType by remember {
        mutableIntStateOf(CallForward.mode(ctx).let { if (it == CallForward.OFF) CallForward.ALL else it })
    }
    var sipOn by remember { mutableStateOf(CallForward.mode(ctx) != CallForward.OFF) }
    var sipNumber by remember { mutableStateOf(CallForward.number(ctx)) }

    val codes = listOf("21", "67", "61", "62")
    val typeNames = listOf("All calls", "When busy", "No answer", "Unreachable")

    fun clean(n: String) = n.filter { it.isDigit() || it == '+' }

    fun sendSim(code: String) {
        val handle = sims.getOrNull(simIdx)?.handle
        DialCodes.handle(ctx, code, handle) { message = it }
    }

    fun persistSip(type: Int = sipType, number: String = sipNumber) {
        CallForward.save(ctx, if (sipOn) type else CallForward.OFF, number, CallForward.seconds(ctx))
    }

    fun saveAndClose() {
        CallForward.save(ctx, if (sipOn) sipType else CallForward.OFF, sipNumber, CallForward.seconds(ctx))
        onDismiss()
    }

    Dialog(
        onDismissRequest = { saveAndClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { saveAndClose() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("call_forwarding_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        "Call Forwarding",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { saveAndClose() },
                        modifier = Modifier.testTag("call_forwarding_save")
                    ) { Text("Save") }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("SIM Call Forwarding", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Uses standard carrier MMI codes. Availability and call charges depend on your mobile operator.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sims.size > 1) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sims.forEachIndexed { i, _ ->
                                FilterChip(
                                    selected = simIdx == i,
                                    onClick = { simIdx = i },
                                    label = { Text("SIM ${i + 1}") }
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        typeNames.forEachIndexed { i, name ->
                            FilterChip(
                                selected = simType == i,
                                onClick = { simType = i },
                                label = { Text(name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = simNumber,
                        onValueChange = { simNumber = clean(it) },
                        label = { Text("Forward to number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocusWithIme()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val n = clean(simNumber)
                                if (n.isBlank()) {
                                    Toast.makeText(ctx, "Enter a number", Toast.LENGTH_SHORT).show()
                                } else {
                                    val c = codes[simType]
                                    sendSim(if (simType == 2) "**$c*$n**20#" else "**$c*$n#")
                                }
                            }
                        ) { Text("Turn on", maxLines = 1, softWrap = false) }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { sendSim("##${codes[simType]}#") }
                        ) { Text("Turn off", maxLines = 1, softWrap = false) }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { sendSim("*#${codes[simType]}#") }
                        ) { Text("Status", maxLines = 1, softWrap = false) }
                    }
                    message?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))

                    Text("SIP Call Forwarding", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Redirects incoming SIP calls using 302 Redirect while the app is registered and running.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = sipType == CallForward.ALL,
                            onClick = { sipType = CallForward.ALL; persistSip(type = sipType) },
                            label = { Text("All calls") }
                        )
                        FilterChip(
                            selected = sipType == CallForward.BUSY,
                            onClick = { sipType = CallForward.BUSY; persistSip(type = sipType) },
                            label = { Text("When busy") }
                        )
                        FilterChip(
                            selected = sipType == CallForward.NO_ANSWER,
                            onClick = { sipType = CallForward.NO_ANSWER; persistSip(type = sipType) },
                            label = { Text("No answer (20 s)") }
                        )
                    }
                    OutlinedTextField(
                        value = sipNumber,
                        onValueChange = { sipNumber = clean(it); persistSip(number = sipNumber) },
                        label = { Text("Forward to SIP / number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocusWithIme()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (clean(sipNumber).isBlank()) {
                                    Toast.makeText(ctx, "Enter a number", Toast.LENGTH_SHORT).show()
                                } else {
                                    sipOn = true
                                    CallForward.save(ctx, sipType, sipNumber, CallForward.seconds(ctx))
                                    Toast.makeText(ctx, "SIP forwarding enabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) { Text("Turn on", maxLines = 1, softWrap = false) }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                sipOn = false
                                CallForward.save(ctx, CallForward.OFF, sipNumber, CallForward.seconds(ctx))
                                Toast.makeText(ctx, "SIP forwarding disabled", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Turn off", maxLines = 1, softWrap = false) }
                    }
                    Text(
                        if (sipOn) "SIP forwarding is ON" else "SIP forwarding is OFF",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sipOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
    }
}
