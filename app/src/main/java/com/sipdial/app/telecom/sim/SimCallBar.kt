package com.sipdial.app.telecom.sim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.telecom.Call
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.sipdial.app.R
import com.sipdial.app.ui.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object SimCallBar {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var attached = false
    private var bar: View? = null
    private var barKey: String? = null
    private var timerJob: Job? = null
    private var statusView: TextView? = null
    private lateinit var appCtx: Context

    fun attach(context: Context) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        appCtx = app
        scope.launch {
            combine(SimCallManager.calls, SimCallManager.tick, SimCallManager.activityVisible) { _, _, vis -> vis }
                .collect { vis ->
                    try {
                        render(app, vis)
                    } catch (e: Exception) {
                        android.util.Log.w("SimCallBar", "render failed", e)
                    }
                }
        }
    }

    private fun currentCall(): Call? =
        SimCallManager.calls.value.firstOrNull { it.compatState() == Call.STATE_RINGING }
            ?: SimCallManager.calls.value.firstOrNull()

    private fun render(app: Context, activityVisible: Boolean) {
        val call = currentCall()
        val kind = when (call?.compatState()) {
            Call.STATE_RINGING -> "in"
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> "out"
            Call.STATE_ACTIVE, Call.STATE_HOLDING -> "active"
            else -> null
        }
        val canOverlay = Settings.canDrawOverlays(app)
        if (call == null || kind == null || activityVisible || !canOverlay) {
            dismiss()
            return
        }
        val title = callTitle(call)
        val key = "$kind|$title"
        if (bar == null || barKey != key) {
            dismiss()
            show(app, kind, title)
            barKey = key
        }
        updateStatus()
    }

    private fun updateStatus() {
        val call = currentCall() ?: return
        val accent = ThemePreferences.state.value.accentColor.color.toArgb()
        val (text, color) = when (call.compatState()) {
            Call.STATE_RINGING -> "Incoming call via ${simSlotLabel(appCtx, call)}" to accent
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL ->
                "Calling via ${simSlotLabel(appCtx, call)}…" to accent
            Call.STATE_HOLDING -> "On hold" to Color.parseColor("#FBBF24")
            Call.STATE_ACTIVE -> {
                val start = call.details?.connectTimeMillis ?: 0L
                val sec = if (start > 0) ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0) else 0L
                "Connected • %02d:%02d".format(sec / 60, sec % 60) to Color.parseColor("#34C759")
            }
            else -> "" to Color.WHITE
        }
        statusView?.text = text
        statusView?.setTextColor(color)
    }

    private fun show(app: Context, kind: String, title: String) {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (kind == "in") {
            showIncomingCard(app, wm, title)
            return
        }
        val accent = ThemePreferences.state.value.accentColor.color.toArgb()
        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111622"))
                cornerRadius = dp(app, 14).toFloat()
                setStroke(dp(app, 1), accent)
            }
            setPadding(dp(app, 16), dp(app, 8), dp(app, 8), dp(app, 8))
            elevation = dp(app, 6).toFloat()
        }
        val textCol = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        val nameView = TextView(app).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val status = TextView(app).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
        }
        statusView = status
        textCol.addView(nameView)
        textCol.addView(status)
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val spkActive = SimCallManager.audioState.value?.route == android.telecom.CallAudioState.ROUTE_SPEAKER
        lateinit var spkBtn: View
        spkBtn = circleButton(app, R.drawable.ic_volume_up, if (spkActive) "#2563EB" else "#334155") {
            val nowSpeaker = SimCallManager.audioState.value?.route != android.telecom.CallAudioState.ROUTE_SPEAKER
            SimCallManager.setSpeaker(nowSpeaker)
            (spkBtn.background as? GradientDrawable)?.setColor(
                Color.parseColor(if (nowSpeaker) "#2563EB" else "#334155")
            )
        }
        row.addView(spkBtn)
        row.addView(circleButton(app, R.drawable.ic_call_end, "#DC2626") {
            currentCall()?.let { SimCallManager.disconnect(it) }
        })
        row.setOnClickListener { openCallScreen(app) }

        val root = FrameLayout(app).apply {
            setPadding(dp(app, 10), 0, dp(app, 10), 0)
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        wm.addView(root, params(app))
        bar = root

        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                updateStatus()
                delay(1000)
            }
        }
    }

    private fun showIncomingCard(app: Context, wm: WindowManager, title: String) {
        val accent = ThemePreferences.state.value.accentColor.color.toArgb()
        val card = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111622"))
                cornerRadius = dp(app, 20).toFloat()
                setStroke(dp(app, 1), accent)
            }
            setPadding(dp(app, 18), dp(app, 14), dp(app, 18), dp(app, 14))
            elevation = dp(app, 12).toFloat()
        }
        card.addView(TextView(app).apply {
            text = "Incoming call via " + currentCall()?.let { simSlotLabel(app, it) }.orEmpty().ifBlank { "SIM" }
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(app).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(app, 2), 0, dp(app, 12))
        })
        val actions = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(
            pillButton(app, "Decline", "#DC2626") { currentCall()?.let { SimCallManager.reject(it) } },
            LinearLayout.LayoutParams(0, dp(app, 40), 1f)
        )
        actions.addView(
            pillButton(app, "Answer", "#16A34A") {
                currentCall()?.let { SimCallManager.answer(it) }
                openCallScreen(app)
            },
            LinearLayout.LayoutParams(0, dp(app, 40), 1f).apply { leftMargin = dp(app, 10) }
        )
        card.addView(
            actions,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        card.setOnClickListener { openCallScreen(app) }

        val root = FrameLayout(app).apply {
            setPadding(dp(app, 12), 0, dp(app, 12), 0)
            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        wm.addView(root, params(app))
        bar = root
    }

    private fun pillButton(app: Context, label: String, colorHex: String, onClick: () -> Unit): View {
        return TextView(app).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(app, 20).toFloat()
                setColor(Color.parseColor(colorHex))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun openCallScreen(app: Context) {
        try {
            app.startActivity(
                Intent(app, SimCallActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun circleButton(app: Context, icon: Int, colorHex: String, onClick: () -> Unit): View {
        val size = dp(app, 40)
        return FrameLayout(app).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(app, 10).toFloat()
                setColor(Color.parseColor(colorHex))
            }
            addView(
                ImageView(app).apply {
                    setImageResource(icon)
                    setColorFilter(Color.WHITE)
                },
                FrameLayout.LayoutParams(dp(app, 22), dp(app, 22), Gravity.CENTER)
            )
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = dp(app, 8) }
        }
    }

    private fun params(app: Context): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        @Suppress("DEPRECATION")
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val id = app.resources.getIdentifier("status_bar_height", "dimen", "android")
            y = (if (id > 0) app.resources.getDimensionPixelSize(id) else dp(app, 24)) + dp(app, 6)
        }
    }

    fun dismiss() {
        timerJob?.cancel()
        timerJob = null
        statusView = null
        barKey = null
        val v = bar ?: return
        bar = null
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
