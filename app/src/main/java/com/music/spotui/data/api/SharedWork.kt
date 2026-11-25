package com.music.spotui.data.api

object SharedWork {
    suspend fun <T> run(key: String, block: suspend () -> T): T {
        return block()
    }
}
