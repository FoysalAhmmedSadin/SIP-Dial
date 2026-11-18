package com.sipdial.app.telecom

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.util.Log
import androidx.annotation.RequiresApi
import com.sipdial.app.data.AccountRepository
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.sip.SipRegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.Q)
class SipCallRedirectionService : CallRedirectionService() {

    companion object {
        private const val TAG = "SipCallRedirection"
        private const val INTERACTIVE_WINDOW_MS = 4100L

        @Volatile
        var bypassNextNumber: String? = null

        @Volatile
        var bypassUntilMs: Long = 0L

        fun normalizeDigits(input: String): String {
            return input.filter { it.isDigit() || it == '+' }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean
    ) {
        val number = Uri.decode(handle.schemeSpecificPart ?: "").trim()
        val norm = normalizeDigits(number)
        val ourHandle = TelecomHelper.getPhoneAccountHandle(applicationContext)

        val isBypassed = norm.isNotEmpty() &&
            norm == normalizeDigits(bypassNextNumber ?: "") &&
            System.currentTimeMillis() < bypassUntilMs

        if (initialPhoneAccount == ourHandle || isBypassed) {
            bypassNextNumber = null
            bypassUntilMs = 0L
            placeCallUnmodified()
            return
        }

        val mode = TelecomHelper.getNativeCallRoutingMode(applicationContext)
        if (mode == TelecomHelper.MODE_SIM_ONLY || number.isEmpty()) {
            placeCallUnmodified()
            return
        }

        val sipEngine = SipEngine.getInstance(applicationContext)

        when (mode) {
            TelecomHelper.MODE_ASK_EVERY_TIME -> {
                if (NativeDialerOverlay.canShowOverlay(applicationContext)) {
                    val callHandled = AtomicBoolean(false)
                    val timeoutRunnable = Runnable {
                        if (callHandled.compareAndSet(false, true)) {
                            try { cancelCall() } catch (_: Exception) {}
                        }
                    }
                    mainHandler.postDelayed(timeoutRunnable, INTERACTIVE_WINDOW_MS)

                    NativeDialerOverlay.showAccountChooser(
                        context = applicationContext,
                        number = number,
                        onSimSelected = { selectedSimHandle ->
                            mainHandler.removeCallbacks(timeoutRunnable)
                            if (callHandled.compareAndSet(false, true)) {
                                try {
                                    if (selectedSimHandle != null && selectedSimHandle != initialPhoneAccount) {
                                        redirectCall(handle, selectedSimHandle, false)
                                    } else {
                                        placeCallUnmodified()
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Native redirect failed, placing SIM call directly", e)
                                    bypassNextNumber = number
                                    bypassUntilMs = System.currentTimeMillis() + 8000L
                                    TelecomHelper.placeSimCallWithHandle(
                                        applicationContext,
                                        number,
                                        selectedSimHandle ?: initialPhoneAccount
                                    )
                                }
                            } else {
                                bypassNextNumber = number
                                bypassUntilMs = System.currentTimeMillis() + 8000L
                                TelecomHelper.placeSimCallWithHandle(
                                    applicationContext,
                                    number,
                                    selectedSimHandle ?: initialPhoneAccount
                                )
                            }
                        },
                        onSipSelected = {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            if (callHandled.compareAndSet(false, true)) {
                                try { cancelCall() } catch (_: Exception) {}
                            }
                            startSipCallEnsuringRegistration(sipEngine, number)
                        },
                        onCancel = {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            if (callHandled.compareAndSet(false, true)) {
                                try { cancelCall() } catch (_: Exception) {}
                            }
                        },
                        lockedHandle = initialPhoneAccount
                    )
                } else {
                    cancelCall()
                    val choiceIntent = Intent(applicationContext, CallChoiceActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        putExtra("phone_number", number)
                    }
                    try {
                        startActivity(choiceIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback startActivity failed", e)
                    }
                }
            }
            TelecomHelper.MODE_ALWAYS_SIP -> {
                cancelCall()
                if (NativeDialerOverlay.canShowOverlay(applicationContext)) {
                    NativeDialerOverlay.openAppCallScreen(applicationContext)
                }
                startSipCallEnsuringRegistration(sipEngine, number)
            }
            else -> placeCallUnmodified()
        }
    }

    private fun startSipCallEnsuringRegistration(sipEngine: SipEngine, number: String) {
        serviceScope.launch {
            if (sipEngine.registrationState.value != SipRegistrationState.REGISTERED) {
                val repo = AccountRepository(applicationContext)
                val savedAccount = repo.getAccount().first()
                if (savedAccount != null) {
                    sipEngine.registerAllSaved()
                    var waited = 0
                    while (sipEngine.registrationState.value != SipRegistrationState.REGISTERED && waited < 40) {
                        delay(100)
                        waited++
                    }
                }
            }
            sipEngine.makeCall(number)
        }
    }
}
