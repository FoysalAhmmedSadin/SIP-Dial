package com.sipdial.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.sipdial.app.ui.theme.ThemePreferences
import com.sipdial.app.util.AppLocalePrefs
import com.sipdial.app.util.AudioQualityPrefs
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.LogLevel
import org.linphone.core.PayloadType

class SipDialApplication : Application() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocalePrefs.wrapContext(newBase))
    }

    companion object {
        lateinit var instance: SipDialApplication
            private set
        lateinit var core: Core
            private set

        @Volatile
        private var coreInitialized: Boolean = false

        fun coreOrNull(): Core? = if (coreInitialized) core else null

        const val SIP_USER_AGENT_NAME = "SIP Dial"
        const val SIP_USER_AGENT_VERSION = "3.0.0"

        @Volatile
        var isMainActivityVisible: Boolean = false

        @Volatile
        var isIncomingCallActivityVisible: Boolean = false

        fun applyUserAgent(targetCore: Core) {
            try {
                targetCore.setUserAgent(SIP_USER_AGENT_NAME, SIP_USER_AGENT_VERSION)
            } catch (e: Exception) {
                android.util.Log.w("SipDialApplication", "Failed to set SIP User-Agent: ${e.message}")
            }
        }

        fun configureVoiceAudio(core: Core) {
            try {
                val hd = AudioQualityPrefs.isHdCodecsEnabled(instance)
                core.isAdaptiveRateControlEnabled = hd
                core.audioDscp = 46
                core.sipDscp = 24
                core.uploadPtime = 20
                core.downloadPtime = 20
                core.audioJittcomp = 60
                core.isAudioAdaptiveJittcompEnabled = true
                core.isEchoLimiterEnabled = false
                core.isEchoCancellationEnabled = !core.hasBuiltinEchoCanceller()
                core.micGainDb = AudioQualityPrefs.getMicBoostDb(instance).toFloat()
                core.playbackGainDb = 0.0f

                core.config.setInt("rtp", "RTP_no_xmit_on_audio_mute", 0)
                core.config.setInt("sound", "echolimiter", 0)

                val allPayloads = core.audioPayloadTypes.toList()
                val custom = AudioQualityPrefs.getCodecConfig(instance)
                val descriptors = allPayloads.map { pt ->
                    AudioQualityPrefs.AudioCodecDescriptor(
                        mimeType = pt.mimeType,
                        clockRate = pt.clockRate,
                        isUsable = pt.isUsable
                    )
                }
                val planned = AudioQualityPrefs.planAudioCodecs(
                    available = descriptors,
                    hdEnabled = hd,
                    customConfig = custom
                )

                val remaining = allPayloads.toMutableList()
                val ordered = ArrayList<PayloadType>(allPayloads.size)
                planned.forEach { item ->
                    val idx = remaining.indexOfFirst {
                        AudioQualityPrefs.codecKey(it.mimeType, it.clockRate) == item.key
                    }
                    if (idx >= 0) {
                        val pt = remaining.removeAt(idx)
                        pt.enable(item.enabled && pt.isUsable)
                        ordered.add(pt)
                    }
                }
                remaining.forEach { pt ->
                    pt.enable(false)
                    ordered.add(pt)
                }

                val firstEnabledKey = planned.firstOrNull { it.enabled }?.key.orEmpty()
                core.isAdaptiveRateControlEnabled = hd || firstEnabledKey.startsWith("OPUS")
                core.setAudioPayloadTypes(ordered.toTypedArray())
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLocalePrefs.init(this)
        ThemePreferences.init(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) isMainActivityVisible = true
            }
            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) isMainActivityVisible = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                if (activity is MainActivity) isMainActivityVisible = true
            }
            override fun onActivityStopped(activity: Activity) {
                if (activity is MainActivity) isMainActivityVisible = false
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        createNotificationChannels()
        initializeLinphone()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        try {
            notificationManager.deleteNotificationChannel("sip_call_channel")
            notificationManager.deleteNotificationChannel("sip_call_channel_v2")
            notificationManager.deleteNotificationChannel("sip_service_channel")
        } catch (_: Exception) {
        }

        val localized = AppLocalePrefs.localizedContext(this)
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE_ID,
            localized.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = localized.getString(R.string.notification_channel_desc)
            setShowBadge(false)
            setSound(null, null)
        }

        val callChannel = NotificationChannel(
            CHANNEL_CALL_ID,
            localized.getString(R.string.notification_call_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = localized.getString(R.string.notification_call_channel_desc)
            setShowBadge(true)
            setSound(null, null)
            enableVibration(false)
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(callChannel)
    }

    private fun initializeLinphone() {
        try {
            try {
                Factory.instance().loggingService.setLogLevel(LogLevel.Message)
            } catch (_: Exception) {
            }
            Factory.instance().enableLogCollection(LogCollectionState.Disabled)

            core = Factory.instance().createCore(null, null, this)
            coreInitialized = true
            applyUserAgent(core)
            try {
                core.config.setInt("sip", "keepalive_period", 15000)
                core.config.setInt("sip", "use_rport", 1)
                core.config.setInt("sip", "contact_autorewrite", 1)
                core.config.setInt("sip", "guess_hostname", 1)
                core.config.setInt("sip", "inc_timeout", 35)
            } catch (_: Exception) {
            }

            core.isKeepAliveEnabled = true
            core.guessHostname = true
            core.isPushNotificationEnabled = false
            core.isAutoSendRingingEnabled = true
            core.isNativeRingingEnabled = false
            core.ring = null
            core.ringback = null
            core.isVibrationOnIncomingCallEnabled = false
            core.ringDuringIncomingEarlyMedia = false
            core.isAutoIterateEnabled = true
            core.autoIterateForegroundSchedule = 20
            core.autoIterateBackgroundSchedule = 50
            core.isVideoCaptureEnabled = false
            core.isVideoDisplayEnabled = false
            core.isVideoPreviewEnabled = false
            core.isQrcodeVideoPreviewEnabled = false

            configureVoiceAudio(core)
            core.start()
        } catch (t: Throwable) {
            android.util.Log.e("SipDialApplication", "Linphone init failed (JVM test or unsupported ABI)", t)
        }
    }

    override fun onTerminate() {
        try {
            if (coreInitialized) core.stop()
        } catch (_: Exception) {
        }
        super.onTerminate()
    }
}

const val CHANNEL_SERVICE_ID = "sip_service_min"
const val CHANNEL_CALL_ID = "sip_call_channel_silent_v3"
