package com.music.spotui.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.music.spotui.ui.theme.AppPalette
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.ui.components.MiniPlayer
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class NoRippleInteractionSource : MutableInteractionSource {

    override val interactions: Flow<Interaction> = emptyFlow()

    override suspend fun emit(interaction: Interaction) {}

    override fun tryEmit(interaction: Interaction) = true
}

@Composable
fun MainBottomNavigation(
    navController: NavHostController,
    bottomBarState: MutableState<Boolean>,
    bottomBarPlayerState: MutableState<Boolean>,
    compact: Boolean = false,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    onSearchReselected: () -> Unit = {},
) {
    val context = LocalContext.current
    val navItems = listOf(
        Routes.Home,
        Routes.Search,
        Routes.Library,
        Routes.Downloads,
        Routes.Settings
    )
    AnimatedVisibility(
        visible = bottomBarState.value,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        content = {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black,
                                Color.Black
                            ),
                            startY = 0f
                        )
                    )
            ) {

                Column(
                    modifier = Modifier.navigationBarsPadding()
                ) {

                    AnimatedVisibility(
                        visible = bottomBarPlayerState.value,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        content = {
                            MiniPlayer(navController)
                        }
                    )

                    Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

                    val dockShape = RoundedCornerShape(cornerRadius.coerceAtLeast(22.dp))
                    NavigationBar(
                        modifier = Modifier
                            .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(dockShape)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.10f),
                                shape = dockShape,
                            ),
                        containerColor = Color(0xFF10131D).copy(alpha = 0.96f),
                        tonalElevation = 8.dp,
                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                    ) {
                        val navStack by navController.currentBackStackEntryAsState()
                        val currentRoute = navStack?.destination?.route

                        val rootRoutes = listOf(
                            Routes.Home.route,
                            Routes.Search.route,
                            Routes.Library.route,
                            Routes.Downloads.route,
                            Routes.Settings.route
                        )
                        var currentTab by rememberSaveable { mutableStateOf(Routes.Home.route) }
                        if (currentRoute in rootRoutes && currentRoute != null) {
                            currentTab = currentRoute
                        }

                        navItems.forEach { item ->
                            val isSelected = currentTab == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                icon = {
                                    Icon(
                                        painter = painterResource(id = item.icon),
                                        contentDescription = item.label,
                                        modifier = Modifier.size(if (compact) 20.dp else 23.dp),
                                    )
                                },
                                label = {
                                    if (!compact || isSelected) {
                                        Text(
                                            color = if (isSelected) Color.White else Color(0xFF8C94A8),
                                            text = item.label,
                                            fontSize = if (compact) 9.sp else 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    }
                                },
                                onClick = {
                                    UiSoundEngine.playTabSound(context)
                                    DevConsoleManager.logButtonAction("BottomNav", "switch_tab", item.route)
                                    if (!isSelected) {
                                        navController.navigate(item.route) {
                                            navController.graph.startDestinationRoute?.let { startRoute ->
                                                popUpTo(startRoute) {
                                                    saveState = true
                                                }
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else if (currentRoute != item.route) {
                                        navController.popBackStack(item.route, inclusive = false)
                                    } else if (item.route == Routes.Search.route) {
                                        onSearchReselected()
                                    }
                                },
                                alwaysShowLabel = !compact,
                                interactionSource = NoRippleInteractionSource(),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppPalette,
                                    unselectedIconColor = Color(0xFF8C94A8),
                                    indicatorColor = AppPalette.copy(alpha = 0.20f),
                                )
                            )
                        }
                    }



                }



            }
        }
    )
}
