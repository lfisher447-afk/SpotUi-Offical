// File: app/src/main/java/com/music/spotui/util/ImageCacheHelper.kt
package com.music.spotui.util

import android.content.Context
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy

/** Creates consistent disk-cached artwork requests for the Compose presentation layer. */
object ImageCacheHelper {
    /** Returns a disk-cached Glide request for [url], or a request with no model when it is absent. */
    fun artworkRequest(context: Context, url: String?): RequestBuilder<Drawable> =
        Glide.with(context.applicationContext)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
}
