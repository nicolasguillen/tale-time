package com.nicolasguillen.taletime

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nicolasguillen.taletime.platform.takeAgentFlags

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always light (warm paper), so the system bar icons are always dark, even in the system's dark
        // theme. The navigation bar gets a paper-colored scrim where Android draws one (3-button navigation).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(PAPER_SCRIM, PAPER_SCRIM),
        )
        super.onCreate(savedInstanceState)
        // Debug builds keep the screen on, so agents can drive a plugged-in device.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // An agent testing a debug build may ask for switches at launch (platform/AgentFlags).
        takeAgentFlags(intent)
        setContent { App() }
    }
}

/** TaleColors.Paper at 90%. */
private const val PAPER_SCRIM = 0xE6F3EAD8.toInt()
