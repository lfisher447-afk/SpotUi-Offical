package com.music.spotui.playback

import com.music.spotui.data.entity.SongsModel

/**
 * Bounded window over a potentially huge logical queue.
 *
 * Every change to the system MediaSession timeline republishes the item list across Binder IPC to
 * SystemUI, Bluetooth AVRCP, and Android Auto. Capping the player-exposed window at [MAX_PLAYER_ITEMS]
 * keeps Binder transactions fast and crash-free even for 70k+ library queues while retaining
 * plenty of upcoming and recently played context around the active track.
 */
object QueueWindow {

    /** Maximum number of tracks exposed concurrently to Media3 / System MediaSession timelines. */
    const val MAX_PLAYER_ITEMS = 500

    /** How many already-played items before [windowStartIndex] we retain inside the window. */
    private const val BACKWARD_CONTEXT = 50

    data class Slice(
        val items: List<SongsModel>,
        val windowStartIndex: Int,
        val activeIndexInWindow: Int,
        val totalLogicalSize: Int,
    )

    fun slice(
        fullQueue: List<SongsModel>,
        currentIndex: Int,
        maxItems: Int = MAX_PLAYER_ITEMS,
    ): Slice {
        if (fullQueue.isEmpty()) {
            return Slice(
                items = emptyList(),
                windowStartIndex = 0,
                activeIndexInWindow = -1,
                totalLogicalSize = 0,
            )
        }
        val cap = maxItems.coerceAtLeast(1)
        val safeCurrent = currentIndex.coerceIn(0, fullQueue.lastIndex)
        if (fullQueue.size <= cap) {
            return Slice(
                items = fullQueue,
                windowStartIndex = 0,
                activeIndexInWindow = safeCurrent,
                totalLogicalSize = fullQueue.size,
            )
        }

        val maxStart = (fullQueue.size - cap).coerceAtLeast(0)
        val desiredBack = minOf(BACKWARD_CONTEXT, cap / 4)
        val start = (safeCurrent - desiredBack).coerceIn(0, maxStart)
        val endExclusive = (start + cap).coerceAtMost(fullQueue.size)

        return Slice(
            items = fullQueue.subList(start, endExclusive),
            windowStartIndex = start,
            activeIndexInWindow = (safeCurrent - start).coerceIn(0, endExclusive - start - 1),
            totalLogicalSize = fullQueue.size,
        )
    }
}
