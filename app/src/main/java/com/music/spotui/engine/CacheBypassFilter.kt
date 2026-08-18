package com.music.spotui.engine

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.CacheControl
import java.util.concurrent.TimeUnit

/**
 * Version 1.5.1 - CacheBypassFilter
 * Forces fresh network checks for playlist updates, ignoring stale local metadata caches.
 */
class CacheBypassFilter : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
            .header("Cache-Control", "no-cache")
            .build()
        return chain.proceed(request)
    }
}
