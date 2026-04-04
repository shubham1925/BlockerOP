package com.example.blockerop.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        BREAKING_NEWS("BREAKING NEWS", "🚨", Color.parseColor("#C0392B")),
        FUN_FACT     ("FUN FACT",      "💡", Color.parseColor("#2471A3")),
        JOKE         ("JOKE",          "😂", Color.parseColor("#B7950B")),
    }

    private data class ContentItem(
        val category: Category,
        val text: String,
        val url: String? = null
    )

    private val CONTENT = listOf(
        // Breaking news
        ContentItem(Category.BREAKING_NEWS,
            "Local person opens app they were trying to quit. Nation unsurprised."),
        ContentItem(Category.BREAKING_NEWS,
            "Scientists confirm scrolling Instagram for 2 hours does not, in fact, bring happiness."),
        ContentItem(Category.BREAKING_NEWS,
            "Study finds average human checks phone 96 times a day. You just tried to make it 97."),
        ContentItem(Category.BREAKING_NEWS,
            "Breaking: Infinite scroll feature confirmed to have been intentionally designed to be addictive. Experts baffled nobody noticed sooner."),
        ContentItem(Category.BREAKING_NEWS,
            "Man discovers extra hour in his day after deleting social media. Shocked to learn it was there the whole time."),
        ContentItem(Category.BREAKING_NEWS,
            "App you were just trying to open has no new information since 4 minutes ago. Developing story."),

        // Fun facts
        ContentItem(Category.FUN_FACT,
            "Instagram was bought by Facebook for \$1 billion in 2012. Your attention was the actual product they acquired."),
        ContentItem(Category.FUN_FACT,
            "The average person spends 2 hours and 27 minutes on social media every day. That's 37 full days per year."),
        ContentItem(Category.FUN_FACT,
            "A goldfish has a 9-second attention span. Thanks to social media, humans now average 8 seconds. The goldfish is winning."),
        ContentItem(Category.FUN_FACT,
            "The \"pull to refresh\" gesture was designed to mimic a slot machine lever. Your brain gets the same dopamine hit."),
        ContentItem(Category.FUN_FACT,
            "Reading a physical book for 6 minutes reduces stress levels by 68%, more than walking or listening to music."),
        ContentItem(Category.FUN_FACT,
            "The notification red badge color was specifically chosen because red triggers urgency in the human brain."),

        // Jokes
        ContentItem(Category.JOKE,
            "Why did the influencer stare at the orange juice carton?\n\nIt said \"concentrate.\""),
        ContentItem(Category.JOKE,
            "I told my therapist I was addicted to social media.\n\nShe said I should talk about it.\n\nSo I posted a thread."),
        ContentItem(Category.JOKE,
            "My phone asked me to rate my experience.\n\nI gave it 3 stars.\n\nIt immediately suggested 4 similar apps."),
        ContentItem(Category.JOKE,
            "Why don't scientists trust atoms?\n\nBecause they make up everything.\n\nMuch like your explore page."),
        ContentItem(Category.JOKE,
            "I tried a digital detox.\n\nDay 1: peaceful.\nDay 2: productive.\nDay 3: I named a spider and taught him tricks."),
        ContentItem(Category.JOKE,
            "My attention span is so short that—\n\nAnyway, here's a fun cat video.\n\nWait, you can't see it. You're blocked."),
    )

    // ── View building ─────────────────────────────────────────────────────────

    private fun buildOverlayView(context: Context): FrameLayout {
        val item = if (NewsCache.hasArticles() && Math.random() < 0.70) {
            val article = NewsCache.getRandomArticle()!!
            ContentItem(Category.BREAKING_NEWS, article.title, article.url)
        } else {
            CONTENT.random()
        }
        val prefs = BlockerPreferences(context)
        val windowText = "${BlockSchedule.formatMinutes(prefs.allowStartMinutes)} – ${BlockSchedule.formatMinutes(prefs.allowEndMinutes)}"

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

            addView(buildCard(context, item, windowText), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                val h = dp(context, 28)
                setMargins(h, h, h, h)
            })
        }
    }

    private fun buildCard(context: Context, item: ContentItem, windowText: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 28), dp(context, 36), dp(context, 28), dp(context, 32))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F1319"))
                cornerRadius = dp(context, 20).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#1E2533"))
            }

            // Category chip
            addView(buildCategoryChip(context, item.category))

            // Main content text
            addView(TextView(context).apply {
                text = item.text
                textSize = 19f
                setTextColor(Color.parseColor("#E8E0D0"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.NORMAL)
                setLineSpacing(0f, 1.45f)
                setPadding(0, dp(context, 24), 0, dp(context, 24))
            })

            // "Read full story" link — only for real news with a URL
            if (item.url != null) {
                addView(buildReadMoreLink(context, item.url, item.category.color))
            }

            // Divider
            addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#1A2030"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
                ).apply {
                    topMargin = if (item.url != null) dp(context, 16) else 0
                    bottomMargin = dp(context, 20)
                }
            })

            // Access window label
            addView(TextView(context).apply {
                text = "ACCESS WINDOW  $windowText"
                textSize = 11f
                setTextColor(Color.parseColor("#4A5568"))
                gravity = Gravity.CENTER
                letterSpacing = 0.12f
                setPadding(0, 0, 0, dp(context, 4))
            })

            // Countdown
            addView(TextView(context).apply {
                tag = TAG_COUNTDOWN
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
            })

            // OK button
            addView(buildOkButton(context, item.category.color))
        }
    }

    private fun buildCategoryChip(context: Context, category: Category): TextView {
        return TextView(context).apply {
            text = "${category.emoji}  ${category.label}"
            textSize = 10f
            setTextColor(category.color)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            val hPad = dp(context, 14)
            val vPad = dp(context, 6)
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(adjustAlpha(category.color, 0.10f))
                setStroke(dp(context, 1), adjustAlpha(category.color, 0.35f))
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
            setTextColor(adjustAlpha(accentColor, 0.85f))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 4)
            }
            setOnClickListener {
                hide()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.applicationContext.startActivity(intent) } catch (_: Exception) { }
            }
        }
    }

    private fun buildOkButton(context: Context, accentColor: Int): TextView {
        return TextView(context).apply {
            text = "OK, GOT IT"
            textSize = 13f
            setTextColor(Color.parseColor("#C9D1D9"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            val vPad = dp(context, 15)
            setPadding(0, vPad, 0, vPad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B22"))
                cornerRadius = dp(context, 12).toFloat()
                setStroke(dp(context, 1), adjustAlpha(accentColor, 0.45f))
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
                tv?.text = "AVAILABLE IN  ${BlockSchedule.formatCountdown(seconds)}"
                handler.postDelayed(this, 1000)
            }
        }
        countdownRunnable = runnable
        handler.post(runnable)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
        ).toInt()

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private const val TAG_COUNTDOWN = "countdown"
}
