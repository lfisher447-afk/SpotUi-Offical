package com.metrolist.music.utils.potoken

data class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
) {
    val playerPot: String get() = playerRequestPoToken
    val streamingPot: String get() = streamingDataPoToken
}
