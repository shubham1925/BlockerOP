package com.example.blockerop.data

/**
 * In-memory store of the most recently fetched news articles.
 * Written from a background thread, read from the main thread —
 * the @Volatile immutable-list swap makes this safe without locks.
 */
data class NewsArticle(val title: String, val url: String)

object NewsCache {

    @Volatile
    private var articles: List<NewsArticle> = emptyList()

    /** Replace the cached articles with a fresh batch. */
    fun update(newArticles: List<NewsArticle>) {
        articles = newArticles.toList()
    }

    /** Returns a random article, or null if no articles are cached yet. */
    fun getRandomArticle(): NewsArticle? = articles.randomOrNull()

    fun hasArticles(): Boolean = articles.isNotEmpty()
}
