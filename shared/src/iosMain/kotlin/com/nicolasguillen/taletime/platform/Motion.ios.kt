package com.nicolasguillen.taletime.platform

import androidx.compose.ui.Modifier
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

actual fun reduceMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

/** iOS's own edge gestures are deferred in the host if they ever fight the page's corner (docs/page-turn.md §9). */
actual fun Modifier.excludeSystemGestures(): Modifier = this
