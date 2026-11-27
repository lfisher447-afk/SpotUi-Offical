package com.harmber2.suadat.paxsenix.models

import kotlinx.serialization.Serializable

@Serializable
data class PaxsenixStats(
    val tracksCount: Long = 0,
    val lyricsCount: Long = 0,
)
