package com.music.spotui.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharedWork<K, V>(private val scope: CoroutineScope) {
    private val mutexMap = java.util.concurrent.ConcurrentHashMap<K, Mutex>()
    private val cacheMap = java.util.concurrent.ConcurrentHashMap<K, V>()

    suspend fun await(key: K, block: suspend () -> V): V {
        val mutex = mutexMap.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            cacheMap[key] ?: block().also { cacheMap[key] = it }
        }
    }
}
