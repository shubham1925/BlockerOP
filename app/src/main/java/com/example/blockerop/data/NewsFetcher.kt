package com.example.blockerop.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches top headlines from Axios and The Hindu RSS feeds.
 * Must be called from a background thread.
 */
object NewsFetcher {

    private val RSS_SOURCES = listOf(
        "https://api.axios.com/feed/",
        "https://www.thehindu.com/feeder/default.rss"
    )

    private const val MAX_PER_SOURCE   = 6
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS    = 12_000

    /**
     * Returns up to [MAX_PER_SOURCE] articles from each source combined,
     * or an empty list if both fail.
     */
    fun fetchHeadlines(): List<NewsArticle> {
        val combined = mutableListOf<NewsArticle>()
        for (url in RSS_SOURCES) {
            try {
                combined += fetchFrom(url)
            } catch (_: Exception) { }
        }
        return combined
    }

    private fun fetchFrom(rssUrl: String): List<NewsArticle> {
        val conn = (URL(rssUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout    = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "BlockerOP/1.0")
            instanceFollowRedirects = true
        }
        return try {
            conn.inputStream.use { parseRss(it) }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRss(stream: java.io.InputStream): List<NewsArticle> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        val articles     = mutableListOf<NewsArticle>()
        var insideItem   = false
        var captureTitle = false
        var captureLink  = false
        var captureGuid  = false
        var pendingTitle = ""
        var pendingUrl   = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item"  -> { insideItem = true; pendingTitle = ""; pendingUrl = "" }
                    "title" -> if (insideItem) captureTitle = true
                    "link"  -> if (insideItem) captureLink  = true
                    "guid"  -> if (insideItem && pendingUrl.isEmpty()) captureGuid = true
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text?.trim().orEmpty()
                    when {
                        captureTitle -> { pendingTitle = text; captureTitle = false }
                        captureLink  -> {
                            if (text.startsWith("http")) pendingUrl = text
                            captureLink = false
                        }
                        captureGuid  -> {
                            if (text.startsWith("http") && pendingUrl.isEmpty()) pendingUrl = text
                            captureGuid = false
                        }
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "item" -> {
                        if (pendingTitle.isNotEmpty() && pendingUrl.isNotEmpty()) {
                            articles.add(NewsArticle(pendingTitle, pendingUrl))
                            if (articles.size >= MAX_PER_SOURCE) return articles
                        }
                        insideItem = false; captureTitle = false
                        captureLink = false; captureGuid = false
                    }
                    "title" -> captureTitle = false
                    "link"  -> captureLink  = false
                    "guid"  -> captureGuid  = false
                }
            }
            event = parser.next()
        }
        return articles
    }
}
