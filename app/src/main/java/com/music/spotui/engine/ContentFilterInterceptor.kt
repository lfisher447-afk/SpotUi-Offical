package com.music.spotui.engine

import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject

/**
 * Version 1.5.1 - ContentFilterInterceptor
 * OkHttp network interceptor parsing Spotify JSON responses to strip out podcasts, audiobooks, and sponsored shelves.
 */
class ContentFilterInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        if (response.isSuccessful && response.body != null && request.url.host.contains("spotify.com")) {
            val bodyString = response.body!!.string()
            try {
                val json = JSONObject(bodyString)
                // Filter logic
                if (json.has("items")) {
                    val items = json.getJSONArray("items")
                    val newItems = org.json.JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val type = item.optString("type", "")
                        if (type != "podcast" && type != "audiobook" && !item.optBoolean("is_sponsored", false)) {
                            newItems.put(item)
                        }
                    }
                    json.put("items", newItems)
                }
                
                val newBody = okhttp3.ResponseBody.create(response.body!!.contentType(), json.toString())
                return response.newBuilder().body(newBody).build()
            } catch (e: Exception) {
                // If it's not JSON or parsing fails, just return original
                val newBody = okhttp3.ResponseBody.create(response.body!!.contentType(), bodyString)
                return response.newBuilder().body(newBody).build()
            }
        }
        return response
    }
}
