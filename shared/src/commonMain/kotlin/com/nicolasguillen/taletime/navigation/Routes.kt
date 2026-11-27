package com.nicolasguillen.taletime.navigation

import kotlinx.serialization.Serializable

// Type-safe routes. Args are primitives: the `story` package stays free of navigation types.

@Serializable
data object ShelfRoute

@Serializable
data class CoverRoute(
    val storyId: String,
)

/**
 * @property restart start from the first page, even if there's a saved play-through.
 * @property at start on this page instead (an agent's switch, platform/AgentFlags).
 */
@Serializable
data class ReaderRoute(
    val storyId: String,
    val restart: Boolean,
    val at: String? = null,
)

/** The parents area, opened only through the parental gate. */
@Serializable
data object ParentsRoute
