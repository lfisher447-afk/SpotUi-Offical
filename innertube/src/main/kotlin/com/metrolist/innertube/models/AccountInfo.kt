package com.metrolist.innertube.models

data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
) {
    fun withHighResAvatar(sizePx: Int = 512): AccountInfo =
        copy(thumbnailUrl = highResAvatarUrl(thumbnailUrl, sizePx))
}

data class AccountChannel(
    val name: String,
    val handle: String?,
    val thumbnailUrl: String?,
    val dataSyncId: String?,
    val isSelected: Boolean = false,
)

fun highResAvatarUrl(url: String?, sizePx: Int = 512): String? {
    val raw = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return raw
        .replace(Regex("=s\\d+-c"), "=s$sizePx-c")
        .replace(Regex("s\\d+-c-k"), "s$sizePx-c-k")
}
