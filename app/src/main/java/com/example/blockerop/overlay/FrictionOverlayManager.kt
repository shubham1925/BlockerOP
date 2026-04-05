package com.example.blockerop.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen commitment overlay shown when the user tries to deactivate
 * Device Admin from Settings. Forces a 60-second reflection period before
 * revealing any exit option.
 */
object FrictionOverlayManager {

    private const val COUNTDOWN_SECONDS = 60
    private const val TAG_COUNTDOWN     = "friction_countdown"
    private const val TAG_PROCEED       = "friction_proceed"

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    fun show(context: Context) {
        if (overlayView != null) return
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layout = buildView(appCtx)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )

        try {
            wm.addView(layout, params)
            overlayView = layout
            windowManager = wm
            startCountdown(layout)
        } catch (_: Exception) { }
    }

    fun hide() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        val view = overlayView ?: return
        try { windowManager?.removeView(view) } catch (_: Exception) { }
        overlayView = null
        windowManager = null
    }

    fun isShowing(): Boolean = overlayView != null

    // ── View ──────────────────────────────────────────────────────────────────

    private fun buildView(context: Context): FrameLayout {
        return FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#06080F"),
                    Color.parseColor("#0D1117"),
                    Color.parseColor("#06080F")
                )
            )
            isClickable = true
            isFocusable = true

            addView(buildCard(context), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                val m = dp(context, 28)
                setMargins(m, m, m, m)
            })
        }
    }

    private fun buildCard(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 28), dp(context, 36), dp(context, 28), dp(context, 32))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F1319"))
                cornerRadius = dp(context, 20).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#2E1A1A"))
            }

            // Category chip
            addView(buildChip(context))

            // Main heading
            addView(TextView(context).apply {
                text = "You're About to Remove\nYour Commitment"
                textSize = 22f
                setTextColor(Color.parseColor("#E8E0D0"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.BOLD)
                setLineSpacing(0f, 1.3f)
                setPadding(0, dp(context, 20), 0, dp(context, 12))
            })

            // Body
            addView(TextView(context).apply {
                text = "This is exactly the moment your distracted self was preparing for.\n\nYou installed BlockerOP because you knew future-you would try this."
                textSize = 16f
                setTextColor(Color.parseColor("#9CA3AF"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setLineSpacing(0f, 1.5f)
                setPadding(0, 0, 0, dp(context, 24))
            })

            // Divider
            addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#1A2030"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
                ).apply { bottomMargin = dp(context, 20) }
            })

            // Countdown label
            addView(TextView(context).apply {
                tag = TAG_COUNTDOWN
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
                letterSpacing = 0.10f
            })

            // Primary: stay on track
            addView(buildStayButton(context))

            // Secondary: revealed only after countdown
            addView(buildProceedLink(context))
        }
    }

    private fun buildChip(context: Context): TextView {
        val accent = Color.parseColor("#C0392B")
        return TextView(context).apply {
            text = "🛡️  COMMITMENT GUARD"
            textSize = 10f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(adjustAlpha(accent, 0.10f))
                setStroke(dp(context, 1), adjustAlpha(accent, 0.40f))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildStayButton(context: Context): TextView {
        val green = Color.parseColor("#2E7D32")
        return TextView(context).apply {
            text = "I'll Stay on Track"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16), 0, dp(context, 16))
            background = GradientDrawable().apply {
                setColor(adjustAlpha(green, 0.85f))
                cornerRadius = dp(context, 12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 20) }
            setOnClickListener { hide() }
        }
    }

    private fun buildProceedLink(context: Context): TextView {
        return TextView(context).apply {
            tag = TAG_PROCEED
            text = "I've decided — let me through"
            textSize = 12f
            setTextColor(Color.parseColor("#4B5563"))
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 12), 0, 0)
            visibility = View.GONE   // hidden until countdown finishes
            setOnClickListener { hide() }
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private fun startCountdown(layout: FrameLayout) {
        var secondsLeft = COUNTDOWN_SECONDS
        val runnable = object : Runnable {
            override fun run() {
                if (overlayView == null) return
                val countdownTv = layout.findViewWithTag<TextView>(TAG_COUNTDOWN)
                val proceedTv   = layout.findViewWithTag<TextView>(TAG_PROCEED)

                if (secondsLeft > 0) {
                    countdownTv?.text = "REFLECT FOR  ${formatSeconds(secondsLeft)}"
                    secondsLeft--
                    handler.postDelayed(this, 1_000)
                } else {
                    countdownTv?.text = "Take your time."
                    proceedTv?.visibility = View.VISIBLE
                }
            }
        }
        timerRunnable = runnable
        handler.post(runnable)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatSeconds(s: Int) = "%02d:%02d".format(s / 60, s % 60)

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
        ).toInt()

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
