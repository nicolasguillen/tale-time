package com.nicolasguillen.taletime

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIApplication
import kotlin.experimental.ExperimentalNativeApi

// PascalCase on purpose: Swift calls it as `MainViewControllerKt.MainViewController()`.
@OptIn(ExperimentalNativeApi::class)
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController() =
    ComposeUIViewController { App() }.also {
        // Debug builds keep the screen on, so agents can drive a plugged-in device.
        if (Platform.isDebugBinary) UIApplication.sharedApplication.idleTimerDisabled = true
    }
