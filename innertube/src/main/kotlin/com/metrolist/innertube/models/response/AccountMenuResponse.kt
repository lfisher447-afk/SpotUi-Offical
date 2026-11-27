package com.metrolist.innertube.models.response

import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.Runs
import com.metrolist.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuResponse(
    val actions: List<Action>? = null,
) {
    @Serializable
    data class Action(
        val openPopupAction: OpenPopupAction? = null,
    ) {
        @Serializable
        data class OpenPopupAction(
            val popup: Popup? = null,
        ) {
            @Serializable
            data class Popup(
                val multiPageMenuRenderer: MultiPageMenuRenderer? = null,
            ) {
                @Serializable
                data class MultiPageMenuRenderer(
                    val header: Header? = null,
                    val sections: List<MultiPageMenuSection>? = null,
                ) {
                    @Serializable
                    data class Header(
                        val activeAccountHeaderRenderer: ActiveAccountHeaderRenderer? = null,
                    ) {
                        @Serializable
                        data class ActiveAccountHeaderRenderer(
                            val accountName: Runs? = null,
                            val email: Runs? = null,
                            val channelHandle: Runs? = null,
                            val accountPhoto: Thumbnails? = null,
                        ) {
                            fun toAccountInfo(): AccountInfo? {
                                val resolvedName = accountName?.runs
                                    ?.joinToString(separator = "") { it.text }
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?: return null
                                return AccountInfo(
                                    name = resolvedName,
                                    email = email?.runs?.joinToString(separator = "") { it.text }?.trim()?.takeIf(String::isNotEmpty),
                                    channelHandle = channelHandle?.runs?.joinToString(separator = "") { it.text }?.trim()?.takeIf(String::isNotEmpty),
                                    thumbnailUrl = accountPhoto?.thumbnails?.lastOrNull()?.url,
                                ).withHighResAvatar()
                            }
                        }
                    }

                    @Serializable
                    data class MultiPageMenuSection(
                        val multiPageMenuSectionRenderer: MultiPageMenuSectionRenderer? = null,
                    )

                    @Serializable
                    data class MultiPageMenuSectionRenderer(
                        val items: List<AccountSectionItem>? = null,
                    )

                    @Serializable
                    data class AccountSectionItem(
                        val accountItemRenderer: AccountItemRenderer? = null,
                    )

                    @Serializable
                    data class AccountItemRenderer(
                        val accountName: Runs? = null,
                        val accountByline: Runs? = null,
                        val accountPhoto: Thumbnails? = null,
                        val isSelected: Boolean? = null,
                    )
                }
            }
        }
    }
}
