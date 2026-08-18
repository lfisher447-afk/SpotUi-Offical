package com.music.spotui.ui.components

import com.music.spotui.data.entity.SongsModel

/**
 * Version 1.5.1 - QueueReorderHandler
 * Touch listener handling drag-and-drop queue item repositioning without triggering API sync rate limits.
 */
object QueueReorderHandler {
    fun moveItem(queue: MutableList<SongsModel>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
    }
}
