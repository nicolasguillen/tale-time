package com.nicolasguillen.taletime.platform

import android.animation.ValueAnimator
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier

/** "Remove animations" in the accessibility settings sets the animator duration scale to 0. */
actual fun reduceMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()

actual fun Modifier.excludeSystemGestures(): Modifier = systemGestureExclusion()
