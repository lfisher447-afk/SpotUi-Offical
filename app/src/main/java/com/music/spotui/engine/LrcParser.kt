package com.music.spotui.engine

/**
 * Version 1.5.1 - LrcParser
 * High-precision timestamp parser processing .lrc lyric strings into millisecond-accurate display streams.
 * Supports multi-timestamp tags, 2-digit and 3-digit millisecond fractions, and colon formats.
 */
data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {
    private val tagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")

    fun parseLrc(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val lines = lrcContent.lines()
        val parsedLines = mutableListOf<LyricLine>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Find all timestamp tags in this line
            val matches = tagRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                // The text is whatever comes after the last timestamp tag
                val lastMatch = matches.last()
                val text = trimmed.substring(lastMatch.range.last + 1).trim()

                for (match in matches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3]
                    val millis = when {
                        fractionStr.isEmpty() -> 0L
                        fractionStr.length == 1 -> fractionStr.toLong() * 100L
                        fractionStr.length == 2 -> fractionStr.toLong() * 10L
                        else -> fractionStr.take(3).toLongOrNull() ?: 0L
                    }

                    val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                    parsedLines.add(LyricLine(timeMs, text))
                }
            }
        }

        return parsedLines.sortedBy { it.timeMs }
    }
}
