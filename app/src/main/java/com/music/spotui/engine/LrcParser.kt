package com.music.spotui.engine

/**
 * Version 1.5.1 - LrcParser
 * High-precision timestamp parser processing .lrc lyric strings into millisecond-accurate display streams.
 */
data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {
    fun parseLrc(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val lines = lrcContent.lines()
        val parsedLines = mutableListOf<LyricLine>()
        
        val regex = Regex("""\[(\d+):(\d+\.\d+)\](.*)""")
        
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val (minutes, seconds, text) = match.destructured
                val timeMs = (minutes.toLong() * 60 * 1000) + (seconds.toDouble() * 1000).toLong()
                parsedLines.add(LyricLine(timeMs, text.trim()))
            }
        }
        
        return parsedLines.sortedBy { it.timeMs }
    }
}
