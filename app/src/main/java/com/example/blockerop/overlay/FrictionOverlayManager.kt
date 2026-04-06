package com.example.blockerop.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
    private const val TAG_TIMER_LABEL   = "friction_timer_label"

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
                    Color.parseColor("#05080F"),
                    Color.parseColor("#0A0E1A"),
                    Color.parseColor("#05080F")
                )
            )
            isClickable = true
            isFocusable = true

            addView(buildCard(context), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                val m = dp(context, 24)
                setMargins(m, m, m, m)
            })
        }
    }

    private fun buildCard(context: Context): LinearLayout {
        val accent = Color.parseColor("#EF4444")

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 26), dp(context, 32), dp(context, 26), dp(context, 28))
            background = buildCardBackground(accent)

            // Chip
            addView(buildChip(context, accent))

            // Divider
            addView(View(context).apply {
                setBackgroundColor(adjustAlpha(accent, 0.15f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
                ).apply { topMargin = dp(context, 16); bottomMargin = dp(context, 20) }
            })

            // Heading
            addView(TextView(context).apply {
                text = "Wait a moment."
                textSize = 26f
                setTextColor(Color.parseColor("#F1F5F9"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.BOLD)
                setLineSpacing(0f, 1.3f)
            })

            // Body
            addView(TextView(context).apply {
                text = "You installed BlockerOP because you knew\nfuture-you would try this.\n\nThis is exactly that moment."
                textSize = 15f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setLineSpacing(0f, 1.55f)
                setPadding(0, dp(context, 16), 0, dp(context, 24))
            })

            // Timer label
            addView(TextView(context).apply {
                tag = TAG_TIMER_LABEL
                text = "REFLECT FOR"
                textSize = 9f
                setTextColor(Color.parseColor("#475569"))
                gravity = Gravity.CENTER
                letterSpacing = 0.16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })

            // Large countdown display
            addView(buildCountdownBox(context, accent))

            // Primary: stay on track
            addView(buildStayButton(context))

            // Secondary: revealed only after countdown
            addView(buildProceedLink(context))
        }
    }

    private fun buildCardBackground(accentColor: Int): LayerDrawable {
        val base = GradientDrawable().apply {
            setColor(Color.parseColor("#0D1422"))
            cornerRadius = dp_f(20f)
        }
        val border = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp_f(20f)
            setStroke(2, adjustAlpha(accentColor, 0.30f))
        }
        return LayerDrawable(arrayOf(base, border))
    }

    private fun buildChip(context: Context, accent: Int): TextView {
        return TextView(context).apply {
            text = "🛡️  COMMITMENT GUARD"
            textSize = 11f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.10f
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 7), dp(context, 16), dp(context, 7))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(adjustAlpha(accent, 0.12f))
                setStroke(dp(context, 1), adjustAlpha(accent, 0.40f))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildCountdownBox(context: Context, accent: Int): FrameLayout {
        return FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(adjustAlpha(accent, 0.10f))
                cornerRadius = dp(context, 16).toFloat()
                setStroke(dp(context, 1), adjustAlpha(accent, 0.25f))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 10); bottomMargin = dp(context, 4) }
            setPadding(0, dp(context, 20), 0, dp(context, 20))

            addView(TextView(context).apply {
                tag = TAG_COUNTDOWN
                textSize = 46f
                setTextColor(Color.parseColor("#F1F5F9"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.04f
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
    }

    private fun buildStayButton(context: Context): TextView {
        val green = Color.parseColor("#10B981")
        return TextView(context).apply {
            text = "I'll stay on track"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 17), 0, dp(context, 17))
            background = GradientDrawable().apply {
                setColor(adjustAlpha(green, 0.90f))
                cornerRadius = dp(context, 14).toFloat()
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
            text = "I've made up my mind — let me through"
            textSize = 12f
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 14), 0, 0)
            visibility = View.GONE
            setOnClickListener { hide() }
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private fun startCountdown(layout: FrameLayout) {
        var secondsLeft = COUNTDOWN_SECONDS
        val runnable = object : Runnable {
            override fun run() {
                if (overlayView == null) return
                val countdownTv  = layout.findViewWithTag<TextView>(TAG_COUNTDOWN)
                val proceedTv    = layout.findViewWithTag<TextView>(TAG_PROCEED)
                val timerLabelTv = layout.findViewWithTag<TextView>(TAG_TIMER_LABEL)

                if (secondsLeft > 0) {
                    countdownTv?.text = formatSeconds(secondsLeft)
                    secondsLeft--
                    handler.postDelayed(this, 1_000)
                } else {
                    countdownTv?.text    = "0:00"
                    timerLabelTv?.text   = "TAKE YOUR TIME"
                    proceedTv?.visibility = View.VISIBLE
                }
            }
        }
        timerRunnable = runnable
        handler.post(runnable)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatSeconds(s: Int) = "0:%02d".format(s)

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    private var _density = 0f
    private fun dp_f(value: Float): Float {
        if (_density == 0f) _density = android.content.res.Resources.getSystem().displayMetrics.density
        return value * _density
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (255 * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
