package com.music.spotui.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.music.spotui.data.preferences.NavigationMode
import com.music.spotui.ui.theme.AppPalette

private val appNavItems = listOf(
    Routes.Home,
    Routes.Search,
    Routes.Library,
    Routes.Downloads,
    Routes.Settings,
)

@Composable
fun MainTopNavigation(
    navController: NavHostController,
    visible: Boolean,
    compact: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onSearchReselected: () -> Unit = {},
) {
    if (!visible) return
    val currentRoute = currentRootRoute(navController)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121216))
            .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = if (compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        appNavItems.forEach { item ->
            val selected = currentRoute == item.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) AppPalette.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(cornerRadius))
                    .clickable { navigateToRoot(navController, item, onSearchReselected) }
                    .padding(vertical = if (compact) 5.dp else 8.dp),
            ) {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = item.label,
                    tint = if (selected) AppPalette else Color.LightGray,
                    modifier = Modifier.size(if (compact) 19.dp else 22.dp),
                )
                if (!compact || selected) {
                    Text(item.label, color = if (selected) Color.White else Color.Gray, fontSize = if (compact) 9.sp else 10.sp)
                }
            }
        }
    }
}

@Composable
fun MainSideNavigation(
    navController: NavHostController,
    mode: NavigationMode,
    visible: Boolean,
    compact: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onSearchReselected: () -> Unit = {},
) {
    if (!visible) return
    val currentRoute = currentRootRoute(navController)
    val rail = mode == NavigationMode.NAVIGATION_RAIL
    if (rail) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = Color(0xFF121216),
        ) {
            Spacer(Modifier.size(if (compact) 4.dp else 12.dp))
            appNavItems.forEach { item ->
                NavigationRailItem(
                    selected = currentRoute == item.route,
                    onClick = { navigateToRoot(navController, item, onSearchReselected) },
                    icon = {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = item.label,
                            modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                        )
                    },
                    label = { if (!compact) Text(item.label, fontSize = 10.sp) },
                    alwaysShowLabel = !compact,
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (compact) 72.dp else 116.dp)
                .background(Color(0xFF121216))
                .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (compact) "S" else "Spotui",
                color = Color.White,
                fontSize = if (compact) 20.sp else 22.sp,
                modifier = Modifier.padding(horizontal = if (compact) 0.dp else 8.dp, vertical = 6.dp),
            )
            appNavItems.forEach { item ->
                val selected = currentRoute == item.route
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) AppPalette.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(cornerRadius))
                        .clickable { navigateToRoot(navController, item, onSearchReselected) }
                        .padding(horizontal = if (compact) 0.dp else 10.dp, vertical = if (compact) 10.dp else 11.dp),
                ) {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = item.label,
                        tint = if (selected) AppPalette else Color.LightGray,
                        modifier = Modifier.size(if (compact) 23.dp else 21.dp),
                    )
                    if (!compact) {
                        Spacer(Modifier.width(10.dp))
                        Text(item.label, color = if (selected) Color.White else Color.LightGray, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun currentRootRoute(navController: NavHostController): String {
    val navStack by navController.currentBackStackEntryAsState()
    val currentRoute = navStack?.destination?.route
    var selectedRoute by rememberSaveable { mutableStateOf(Routes.Home.route) }
    if (currentRoute in appNavItems.map { it.route }) selectedRoute = currentRoute.orEmpty()
    return selectedRoute
}

private fun navigateToRoot(
    navController: NavHostController,
    item: Routes,
    onSearchReselected: () -> Unit,
) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    if (currentRoute == item.route) {
        if (item.route == Routes.Search.route) onSearchReselected()
        return
    }
    navController.navigate(item.route) {
        navController.graph.startDestinationRoute?.let { startRoute ->
            popUpTo(startRoute) { saveState = true }
        }
        launchSingleTop = true
        restoreState = true
    }
}
