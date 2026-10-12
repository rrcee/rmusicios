package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.ui.screen.MiniPlayer
import com.maxrave.simpmusic.ui.theme.LocalIsDarkTheme
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlin.reflect.KClass

private const val TAG = "LiquidGlassAppBottomNavigationBar"
private val BarEdgePadding = 16.dp
private val SearchGap = 12.dp
private val SearchSize = 56.dp

@Composable
actual fun LiquidGlassAppBottomNavigationBar(
    startDestination: Any,
    navController: NavController,
    backdrop: PlatformBackdrop,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean,
    showAnalyticsTab: Boolean,
    showMixForYouTab: Boolean,
    onOpenNowPlaying: () -> Unit,
    liquidGlass: Boolean,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val searchFabInteraction = rememberGlassInteraction()
    val luminance = rememberGlassLuminance(layer, enabled = liquidGlass)

    val nowPlayingData by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val isShowMiniPlayer by remember {
        derivedStateOf {
            val item = nowPlayingData?.mediaItem
            item != null && item != GenericMediaItem.EMPTY
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val bottomNavScreens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
            BottomNavScreen.Library,
            BottomNavScreen.Search,
        )

    val barTabs =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
            BottomNavScreen.Library,
        )

    var selectedIndex by rememberSaveable {
        mutableIntStateOf(
            when (startDestination) {
                is HomeDestination -> BottomNavScreen.Home.ordinal
                is SearchDestination -> BottomNavScreen.Search.ordinal
                is LibraryDestination -> BottomNavScreen.Library.ordinal
                is AnalyticsDestination -> BottomNavScreen.Analytics.ordinal
                is MixForYouDestination -> BottomNavScreen.MixForYou.ordinal
                else -> BottomNavScreen.Home.ordinal
            },
        )
    }

    LaunchedEffect(showAnalyticsTab, showMixForYouTab) {
        if ((!showAnalyticsTab && selectedIndex == BottomNavScreen.Analytics.ordinal) ||
            (!showMixForYouTab && selectedIndex == BottomNavScreen.MixForYou.ordinal)
        ) {
            selectedIndex = BottomNavScreen.Home.ordinal
        }
    }

    var isExpanded by rememberSaveable {
        mutableStateOf(true)
    }

    var isInSearchDestination by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(currentBackStackEntry) {
        currentBackStackEntry?.destination?.let { current ->
            Logger.d(TAG, "LiquidGlassAppBottomNavigationBar: current route: ${current.route}")
            isInSearchDestination = current.hasRoute(SearchDestination::class)
        }
    }

    LaunchedEffect(isInSearchDestination) {
        isExpanded = !isInSearchDestination
    }

    val miniPlayerPadding = if (liquidGlass) 12.dp else 16.dp
    val miniPlayerGap = if (liquidGlass) 10.dp else 8.dp

    LaunchedEffect(isScrolledToTop) {
        if (!isInSearchDestination) {
            isExpanded = isScrolledToTop
        }
    }

    fun selectTab(index: Int) {
        val screen = bottomNavScreens.find { it.ordinal == index } ?: return
        if (selectedIndex == index) {
            if (currentBackStackEntry?.destination?.hierarchy?.any {
                    it.hasRoute(screen.destination::class)
                } == true
            ) {
                reloadDestinationIfNeeded(screen.destination::class)
            } else {
                navController.navigate(screen.destination)
            }
        } else {
            selectedIndex = index
            navController.navigate(screen.destination) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val collapsedCircle: @Composable () -> Unit = {
        val selectedScreen = bottomNavScreens.find { it.ordinal == selectedIndex } ?: BottomNavScreen.Home
        Box(
            modifier = Modifier.fillMaxSize().clickable(enabled = !isExpanded) { isExpanded = true },
            contentAlignment = Alignment.Center,
        ) {
            if (liquidGlass) {
                selectedScreen.icon()
            } else {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    selectedScreen.icon()
                }
            }
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    WindowInsets.navigationBars.asPaddingValues(),
                ).padding(
                    bottom = 8.dp,
                ),
    ) {
        val capsuleBudget = maxWidth - BarEdgePadding * 2 - SearchGap - SearchSize

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = isShowMiniPlayer,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
            ) {
                MiniPlayer(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = miniPlayerPadding)
                        .height(56.dp),
                    backdrop = backdrop,
                    isVisible = isShowMiniPlayer,
                    onClick = onOpenNowPlaying,
                    onClose = {
                        viewModel.stopPlayer()
                        viewModel.isServiceRunning = false
                    },
                )
            }

            if (isShowMiniPlayer) {
                Spacer(modifier = Modifier.height(miniPlayerGap))
            }

            if (liquidGlass) {
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(SearchGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidGlassTabBar(
                        tabs = barTabs,
                        selectedTab = barTabs.indexOfFirst { it.ordinal == selectedIndex },
                        backdrop = backdrop,
                        layer = layer,
                        luminance = luminance,
                        availableWidth = capsuleBudget,
                        onTabSelected = { position -> selectTab(barTabs[position].ordinal) },
                        collapsedContent = collapsedCircle,
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(SearchSize)
                                .drawInteractiveGlass(
                                    LocalIsDarkTheme.current,
                                    backdrop,
                                    null,
                                    { luminance.value },
                                    CircleShape,
                                    searchFabInteraction,
                                ).clickable { selectTab(BottomNavScreen.Search.ordinal) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BottomNavScreen.Search.icon()
                    }
                }
            } else {
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(SearchGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlatTabBar(
                        tabs = barTabs,
                        selectedIndex = selectedIndex,
                        availableWidth = capsuleBudget,
                        onTabSelected = { screen -> selectTab(screen.ordinal) },
                        collapsedContent = collapsedCircle,
                    )
                    FlatSearchButton(
                        selected = selectedIndex == BottomNavScreen.Search.ordinal,
                        onClick = { selectTab(BottomNavScreen.Search.ordinal) },
                    )
                }
            }
        }
    }
}
