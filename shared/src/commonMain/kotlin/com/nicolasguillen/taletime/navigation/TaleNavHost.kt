package com.nicolasguillen.taletime.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nicolasguillen.taletime.feature.cover.CoverRoute
import com.nicolasguillen.taletime.feature.parents.ParentsRoute
import com.nicolasguillen.taletime.feature.reader.ReaderRoute
import com.nicolasguillen.taletime.feature.shelf.ShelfRoute

/** Shelf → cover → reader, and the parents area behind the gate (plan.md §3.1). */
@Composable
fun TaleNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()

    fun backToShelf() {
        nav.popBackStack(ShelfRoute, inclusive = false)
    }
    NavHost(
        navController = nav,
        startDestination = ShelfRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) },
    ) {
        composable<ShelfRoute> {
            ShelfRoute(
                onOpenBook = { nav.navigate(CoverRoute(it)) },
                onOpenParents = { nav.navigate(ParentsRoute) },
            )
        }
        composable<CoverRoute> { entry ->
            val route = entry.toRoute<CoverRoute>()
            CoverRoute(
                storyId = route.storyId,
                onRead = { restart -> nav.navigate(ReaderRoute(route.storyId, restart)) },
                onBack = ::backToShelf,
            )
        }
        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            ReaderRoute(storyId = route.storyId, restart = route.restart, onHome = ::backToShelf)
        }
        composable<ParentsRoute> { ParentsRoute(onBack = ::backToShelf) }
    }
}
