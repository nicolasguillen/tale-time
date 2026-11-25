package com.nicolasguillen.taletime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nicolasguillen.taletime.designsystem.TaleTheme
import com.nicolasguillen.taletime.di.appModules
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.domain.SettingsRepository
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.i18n.ProvideLanguage
import com.nicolasguillen.taletime.navigation.TaleNavHost
import com.nicolasguillen.taletime.platform.deviceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

private val koinConfig = koinConfiguration { modules(appModules) }

@Composable
fun App() {
    // Story pictures and the SVG placeholders (content/placeholders/) that stand in until pictures are delivered.
    setSingletonImageLoaderFactory { context ->
        ImageLoader
            .Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
    KoinApplication(configuration = koinConfig) {
        // The language from the parents area; the device's own until the settings are read, so nothing flickers.
        val settings = koinInject<SettingsRepository>()
        val language by remember(settings) { settings.settings.map { it.language } }
            .collectAsState(initial = Language.of(deviceLanguage()) ?: Language.DEFAULT)
        // Every time the app opens, when it's online: the shelf from the host, and downloaded stories brought up to date.
        val library = koinInject<StoryLibrary>()
        val appScope = koinInject<CoroutineScope>()
        LifecycleEventEffect(Lifecycle.Event.ON_START) { appScope.launch { library.refresh() } }
        TaleTheme {
            ProvideLanguage(language) {
                TaleNavHost()
            }
        }
    }
}
