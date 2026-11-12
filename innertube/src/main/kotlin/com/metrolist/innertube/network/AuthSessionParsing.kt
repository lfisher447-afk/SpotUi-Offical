package com.metrolist.innertube.network

import com.metrolist.innertube.models.AccountChannel
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.GridRenderer
import com.metrolist.innertube.models.MusicShelfRenderer
import com.metrolist.innertube.models.highResAvatarUrl
import com.metrolist.innertube.models.getContinuation
import com.metrolist.innertube.models.response.AccountMenuResponse
import com.metrolist.innertube.models.response.BrowseResponse
import com.metrolist.innertube.pages.LibraryContinuationPage
import com.metrolist.innertube.pages.LibraryPage

object AuthSessionParsing {
    fun parseAccountInfo(response: AccountMenuResponse): AccountInfo? =
        response.actions
            ?.firstOrNull()
            ?.openPopupAction
            ?.popup
            ?.multiPageMenuRenderer
            ?.header
            ?.activeAccountHeaderRenderer
            ?.toAccountInfo()

    fun parseAccountChannels(response: AccountMenuResponse): List<AccountChannel> {
        val menuRenderer = response.actions
            ?.firstOrNull()
            ?.openPopupAction
            ?.popup
            ?.multiPageMenuRenderer
            ?: return emptyList()

        return menuRenderer.sections.orEmpty()
            .flatMap { it.multiPageMenuSectionRenderer?.items.orEmpty() }
            .mapNotNull { item ->
                val renderer = item.accountItemRenderer ?: return@mapNotNull null
                val name = renderer.accountName?.runs
                    ?.joinToString(separator = "") { it.text }
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                val handle = renderer.accountByline?.runs
                    ?.joinToString(separator = "") { it.text }
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                AccountChannel(
                    name = name,
                    handle = handle,
                    thumbnailUrl = highResAvatarUrl(renderer.accountPhoto?.thumbnails?.lastOrNull()?.url),
                    dataSyncId = null,
                    isSelected = renderer.isSelected == true,
                )
            }
    }

    fun parseLibraryContinuation(response: BrowseResponse): LibraryContinuationPage {
        val contents = response.continuationContents
        val gridContinuation = contents?.gridContinuation
        if (gridContinuation != null) {
            return LibraryContinuationPage(
                items = gridContinuation.items
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull(LibraryPage.Companion::fromMusicTwoRowItemRenderer),
                continuation = gridContinuation.continuations?.getContinuation(),
            )
        }

        val shelfContinuation = contents?.musicShelfContinuation
        val shelfItems = shelfContinuation?.contents.orEmpty()
            .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            .mapNotNull(LibraryPage.Companion::fromMusicResponsiveListItemRenderer)

        return LibraryContinuationPage(
            items = shelfItems,
            continuation = shelfContinuation?.continuations?.getContinuation(),
        )
    }
}
