package com.nicolasguillen.taletime

import androidx.compose.ui.window.ComposeUIViewController

// PascalCase on purpose: Swift calls it as `MainViewControllerKt.MainViewController()`.
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController() = ComposeUIViewController { App() }
