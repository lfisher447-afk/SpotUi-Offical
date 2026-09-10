package com.music.spotui

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.music.spotui.ui.navigation.MainBottomNavigation
import com.music.spotui.ui.navigation.MainSideNavigation
import com.music.spotui.ui.navigation.MainTopNavigation
import com.music.spotui.ui.navigation.MyNavHost
import com.music.spotui.ui.navigation.Routes


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun App() {
    val bottomBarState = rememberSaveable { (mutableStateOf(true)) }
    val bottomBarPlayerState = rememberSaveable { (mutableStateOf(true)) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerViewModel: com.music.spotui.ui.viewmodel.PlayerViewModel = hiltViewModel()
    val playerTitle by playerViewModel.currentSongTitle
    val playerState = playerTitle.orEmpty()
    var lastRoute by remember { mutableStateOf<String?>(null) }
    // Incremented each time the user re-taps the Search bottom-nav icon while
    // already on the search route, so SearchScreen can focus its text field.
    var searchFocusTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(currentRoute, playerState) {
        if (currentRoute != Routes.Player.route) {
            bottomBarState.value = when (currentRoute) {
                Routes.Login.route, Routes.Queue.route -> false
                else -> true
            }
            bottomBarPlayerState.value = when (currentRoute) {
                Routes.Login.route, Routes.Queue.route -> false
                else -> playerState.isNotBlank()
            }
        }
        lastRoute = currentRoute
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiRevision by com.music.spotui.data.preferences.uiSettingsUpdates.collectAsState()
    val navigationMode = remember(uiRevision) { com.music.spotui.data.preferences.getNavigationMode(context) }
    val compactUi = remember(uiRevision) { com.music.spotui.data.preferences.isCompactUiEnabled(context) }
    val cornerRadius = remember(uiRevision) { com.music.spotui.data.preferences.getCornerRadiusDp(context).dp }

    LaunchedEffect(navController) {
        // Process any cold-start deep link intent that launched the app
        com.music.spotui.util.DeepLinkHandler.consumePendingUri()?.let { uri ->
            com.music.spotui.util.DeepLinkHandler.processUri(
                uri,
                context,
                navController,
                playerViewModel,
                scope
            )
        }
        // Process any new deep link intent while the app is active
        com.music.spotui.util.DeepLinkHandler.deepLinkFlow.collect { uri ->
            com.music.spotui.util.DeepLinkHandler.processUri(
                uri,
                context,
                navController,
                playerViewModel,
                scope
            )
        }
    }

    val useBottomNavigation = navigationMode == com.music.spotui.data.preferences.NavigationMode.BOTTOM_BAR
    val useTopNavigation = navigationMode == com.music.spotui.data.preferences.NavigationMode.TOP_BAR
    val useSideNavigation = navigationMode == com.music.spotui.data.preferences.NavigationMode.SIDE_BAR ||
        navigationMode == com.music.spotui.data.preferences.NavigationMode.NAVIGATION_RAIL

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MainTopNavigation(
                navController = navController,
                visible = bottomBarState.value && useTopNavigation,
                compact = compactUi,
                cornerRadius = cornerRadius,
                onSearchReselected = { searchFocusTrigger++ },
            )
        },
        bottomBar = {
            if (useBottomNavigation) {
                MainBottomNavigation(
                    navController = navController,
                    bottomBarState = bottomBarState,
                    bottomBarPlayerState = bottomBarPlayerState,
                    compact = compactUi,
                    cornerRadius = cornerRadius,
                    onSearchReselected = { searchFocusTrigger++ },
                )
            }
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (useSideNavigation) {
                MainSideNavigation(
                    navController = navController,
                    mode = navigationMode,
                    visible = bottomBarState.value,
                    compact = compactUi,
                    cornerRadius = cornerRadius,
                    onSearchReselected = { searchFocusTrigger++ },
                )
            }
            MyNavHost(
                navHostController = navController,
                searchFocusTrigger = searchFocusTrigger,
                modifier = Modifier.weight(1f),
            )
        }
    }
}


