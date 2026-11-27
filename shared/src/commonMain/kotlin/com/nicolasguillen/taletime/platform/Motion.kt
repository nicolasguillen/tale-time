package com.nicolasguillen.taletime.platform

import androidx.compose.ui.Modifier

/**
 * The device asks for less motion: Android's animator duration scale is off, or iOS's Reduce Motion is on. Then the
 * reader's pages cross-fade instead of turning, and a choice glows without its sparkles (docs/page-turn.md §9).
 */
expect fun reduceMotion(): Boolean

/**
 * Keeps the system's edge gestures (Android's back swipe) out of this element, so a finger dragging a page's corner
 * turns the page. Nothing on iOS.
 */
expect fun Modifier.excludeSystemGestures(): Modifier
