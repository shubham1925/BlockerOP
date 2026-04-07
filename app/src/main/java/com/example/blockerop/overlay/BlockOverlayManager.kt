package com.example.blockerop.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.data.NewsCache
import com.example.blockerop.scheduler.BlockSchedule

object BlockOverlayManager {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    fun show(context: Context) {
        if (overlayView != null) return
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layout = buildOverlayView(appCtx)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        val prefs = BlockerPreferences(appCtx)
        try {
            wm.addView(layout, params)
            overlayView = layout
            windowManager = wm
            startCountdown(layout, prefs.allowStartMinutes)
        } catch (_: Exception) { }
    }

    fun hide() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        val view = overlayView ?: return
        try { windowManager?.removeView(view) } catch (_: Exception) { }
        overlayView = null
        windowManager = null
    }

    fun isShowing(): Boolean = overlayView != null

    // ── Content ───────────────────────────────────────────────────────────────

    private enum class Category(val label: String, val emoji: String, val color: Int) {
        BREAKING_NEWS("BREAKING NEWS", "🚨", Color.parseColor("#EF4444")),
    }

    private data class ContentItem(
        val category: Category,
        val text: String,
        val url: String? = null
    )

    private val CONTENT = listOf(
        ContentItem(Category.BREAKING_NEWS, "Local person opens app they were trying to quit. Nation unsurprised."),
        ContentItem(Category.BREAKING_NEWS, "Scientists confirm scrolling Instagram for 2 hours does not, in fact, bring happiness."),
        ContentItem(Category.BREAKING_NEWS, "Study finds average human checks phone 96 times a day. You just tried to make it 97."),
        ContentItem(Category.BREAKING_NEWS, "Breaking: Infinite scroll confirmed to have been intentionally designed to be addictive. Experts baffled nobody noticed sooner."),
        ContentItem(Category.BREAKING_NEWS, "Man discovers extra hour in his day after deleting social media. Shocked to learn it was there the whole time."),
        ContentItem(Category.BREAKING_NEWS, "App you were just trying to open has no new information since 4 minutes ago. Developing story."),
    )

    // ── View building ─────────────────────────────────────────────────────────

    private fun buildOverlayView(context: Context): FrameLayout {
        val item = if (NewsCache.hasArticles()) {
            val article = NewsCache.getRandomArticle()!!
            ContentItem(Category.BREAKING_NEWS, article.title, article.url)
        } else {
            CONTENT.random()
        }
        val prefs = BlockerPreferences(context)
        val windowText = "${BlockSchedule.formatMinutes(prefs.allowStartMinutes)} – ${BlockSchedule.formatMinutes(prefs.allowEndMinutes)}"

        return FrameLayout(context).apply {
            // Deep dark radial gradient background
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

            addView(buildCard(context, item, windowText), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                val h = dp(context, 24)
                setMargins(h, h, h, h)
            })
        }
    }

    private fun buildCard(context: Context, item: ContentItem, windowText: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 26), dp(context, 32), dp(context, 26), dp(context, 28))

            // Card background with subtle glow border
            background = buildCardBackground(item.category.color)

            // Category chip
            addView(buildCategoryChip(context, item.category))

            // Divider line
            addView(View(context).apply {
                setBackgroundColor(adjustAlpha(item.category.color, 0.15f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
                ).apply { topMargin = dp(context, 16); bottomMargin = dp(context, 20) }
            })

            // Main content text
            addView(TextView(context).apply {
                text = item.text
                textSize = 18f
                setTextColor(Color.parseColor("#E2E8F0"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.NORMAL)
                setLineSpacing(0f, 1.55f)
            })

            // "Read full story" link
            if (item.url != null) {
                addView(buildReadMoreLink(context, item.url, item.category.color))
            }

            // Spacer
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(context, 24))
            })

            // Access window row
            addView(buildInfoRow(context, "ACCESS WINDOW", windowText))

            // Countdown
            addView(TextView(context).apply {
                tag = TAG_COUNTDOWN
                textSize = 28f
                setTextColor(Color.parseColor("#F1F5F9"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.05f
                setPadding(0, dp(context, 6), 0, dp(context, 20))
            })

            // OK button
            addView(buildOkButton(context, item.category.color))
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
            setStroke(2, adjustAlpha(accentColor, 0.25f))
        }
        return LayerDrawable(arrayOf(base, border))
    }

    private fun buildCategoryChip(context: Context, category: Category): TextView {
        return TextView(context).apply {
            text = "${category.emoji}  ${category.label}"
            textSize = 11f
            setTextColor(category.color)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.10f
            gravity = Gravity.CENTER
            val hPad = dp(context, 16)
            val vPad = dp(context, 7)
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(adjustAlpha(category.color, 0.12f))
                setStroke(dp(context, 1), adjustAlpha(category.color, 0.40f))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildReadMoreLink(context: Context, url: String, accentColor: Int): TextView {
        return TextView(context).apply {
            text = "Read full story  →"
            textSize = 13f
            setTextColor(adjustAlpha(accentColor, 0.9f))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 16)
            }
            setOnClickListener {
                hide()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { context.applicationContext.startActivity(intent) } catch (_: Exception) { }
            }
        }
    }

    private fun buildInfoRow(context: Context, label: String, value: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(context).apply {
                text = label
                textSize = 9f
                setTextColor(Color.parseColor("#475569"))
                gravity = Gravity.CENTER
                letterSpacing = 0.16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(context).apply {
                text = value
                textSize = 15f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(context, 2), 0, 0)
            })
        }
    }

    private fun buildOkButton(context: Context, accentColor: Int): TextView {
        return TextView(context).apply {
            text = "Got it"
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            val vPad = dp(context, 16)
            setPadding(0, vPad, 0, vPad)
            background = GradientDrawable().apply {
                setColor(adjustAlpha(accentColor, 0.18f))
                cornerRadius = dp(context, 14).toFloat()
                setStroke(dp(context, 1), adjustAlpha(accentColor, 0.50f))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 20) }
            setOnClickListener { hide() }
        }
    }

    private fun startCountdown(layout: FrameLayout, startMinutes: Int) {
        val runnable = object : Runnable {
            override fun run() {
                if (overlayView == null) return
                val seconds = BlockSchedule.secondsUntilUnblocked(startMinutes)
                val tv = layout.findViewWithTag<TextView>(TAG_COUNTDOWN)
                tv?.text = BlockSchedule.formatCountdown(seconds)
                handler.postDelayed(this, 1000)
            }
        }
        countdownRunnable = runnable
        handler.post(runnable)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    // density-independent float for corner radius (uses application context display metrics workaround)
    private var _density = 0f
    private fun dp_f(value: Float): Float {
        if (_density == 0f) _density = android.content.res.Resources.getSystem().displayMetrics.density
        return value * _density
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (255 * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private const val TAG_COUNTDOWN = "countdown"
}
