package com.music.spotui.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.music.spotui.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MiniPlayerXmlAndLogosTest {

    @Test
    fun inflatesMiniPlayerXmlWithPinnedTabsAndAllRequiredIdsAndCustomLogos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(R.style.Theme_Spotui_MiniPlayerOverlay)

        val root = LayoutInflater.from(context).inflate(R.layout.layout_mini_player, null, false)
        assertNotNull(root)

        val requiredIds = listOf(
            R.id.mini_player_outer,
            R.id.mini_player_root,
            R.id.tv_mini_dsp_pill,
            R.id.layout_studio_drawer,
            R.id.layout_studio_content_column,
            R.id.layout_floater_pinned_tabs,
            R.id.pinned_tab_player,
            R.id.pinned_tab_queue,
            R.id.pinned_tab_eq,
            R.id.pinned_tab_3d,
            R.id.pinned_tab_tools,
            R.id.pinned_tab_translate,
            R.id.scroll_studio_drawer,
            R.id.panel_floater_translate,
            R.id.tv_translate_engine_badge,
            R.id.badge_internal_capture_mode,
            R.id.tv_translate_click_through_status,
            R.id.toggle_translate_bubble,
            R.id.seek_translate_width,
            R.id.seek_translate_size,
            R.id.tv_translation_preview,
        )
        for (id in requiredIds) {
            assertNotNull("Missing view ID $id in layout_mini_player.xml", root.findViewById<View>(id))
        }

        assertNotNull(ContextCompat.getDrawable(context, R.drawable.spotui_logo_main))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.spotui_logo_santa_cookie))
    }
}
