package com.example.data.search

import java.util.regex.Pattern

object SearchSnippetHelper {

    /**
     * Extracts a 2-line (~160 chars) contextual snippet around the matching keyword,
     * highlighting the keyword with `<mark>keyword</mark>`.
     */
    fun createSnippet(fullText: String, query: String, contextRadius: Int = 80): String {
        if (fullText.isBlank() || query.isBlank()) {
            return fullText.take(120).trim()
        }

        val cleanQuery = query.trim()
        val lowerText = fullText.lowercase()
        val lowerQuery = cleanQuery.lowercase()

        val matchIndex = lowerText.indexOf(lowerQuery)
        if (matchIndex == -1) {
            val words = cleanQuery.split(Regex("\\s+")).filter { it.length > 2 }
            for (word in words) {
                val idx = lowerText.indexOf(word.lowercase())
                if (idx != -1) {
                    return formatWindow(fullText, idx, word.length, contextRadius, cleanQuery)
                }
            }
            return fullText.take(120).replace("\n", " ").trim() + if (fullText.length > 120) "…" else ""
        }

        return formatWindow(fullText, matchIndex, cleanQuery.length, contextRadius, cleanQuery)
    }

    private fun formatWindow(
        text: String,
        matchIndex: Int,
        matchLength: Int,
        radius: Int,
        queryToHighlight: String
    ): String {
        val start = (matchIndex - radius).coerceAtLeast(0)
        val end = (matchIndex + matchLength + radius).coerceAtMost(text.length)

        var snippet = text.substring(start, end).replace("\r", " ").replace("\n", " ")

        // Trim to word boundary at start
        if (start > 0) {
            val firstSpace = snippet.indexOf(' ')
            if (firstSpace in 1..20) {
                snippet = "…" + snippet.substring(firstSpace + 1)
            } else {
                snippet = "…" + snippet
            }
        }

        // Trim to word boundary at end
        if (end < text.length) {
            val lastSpace = snippet.lastIndexOf(' ')
            if (lastSpace in (snippet.length - 20) until snippet.length) {
                snippet = snippet.substring(0, lastSpace) + "…"
            } else {
                snippet = snippet + "…"
            }
        }

        return highlightQueryTerms(snippet, queryToHighlight)
    }

    /**
     * Replaces occurrences of query terms in the snippet with `<mark>term</mark>`.
     */
    fun highlightQueryTerms(snippet: String, query: String): String {
        if (query.isBlank()) return snippet
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return snippet

        var result = snippet
        for (token in tokens) {
            val escaped = Pattern.quote(token)
            val regex = Regex("(?i)($escaped)")
            result = regex.replace(result) { match -> "<mark>${match.value}</mark>" }
        }
        return result
    }
}
