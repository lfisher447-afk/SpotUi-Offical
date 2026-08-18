package com.music.spotui.engine

import com.music.spotui.data.entity.SongsModel
import java.util.Random

/**
 * Version 1.5.1 - TrueShuffleEngine
 * Replaces Spotify's biased shuffle algorithm with a strict Fisher-Yates 
 * randomization pipeline to ensure fair and unweighted track distribution.
 */
object TrueShuffleEngine {
    private val random = Random()

    /**
     * Shuffles a queue using the Fisher-Yates algorithm, preserving the 
     * currently playing track at the start (index 0).
     * 
     * @param queue The list of songs to shuffle.
     * @param currentIndex The index of the song currently playing.
     * @return A newly shuffled list with the current song at the top.
     */
    fun applyTrueShuffle(queue: List<SongsModel>, currentIndex: Int): List<SongsModel> {
        if (queue.isEmpty() || currentIndex !in queue.indices) return queue

        val currentSong = queue[currentIndex]
        val remainingSongs = queue.filterIndexed { index, _ -> index != currentIndex }.toMutableList()

        // Fisher-Yates Shuffle on the remaining tracks
        for (i in remainingSongs.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = remainingSongs[i]
            remainingSongs[i] = remainingSongs[j]
            remainingSongs[j] = temp
        }

        // Return the current song followed by the truly randomized remaining tracks
        val shuffledQueue = mutableListOf<SongsModel>()
        shuffledQueue.add(currentSong)
        shuffledQueue.addAll(remainingSongs)
        
        return shuffledQueue
    }
}
