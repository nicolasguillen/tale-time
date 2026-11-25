package com.nicolasguillen.taletime.platform

import okio.Path
import org.koin.core.module.Module

/**
 * Facts about the running build, bound in [platformModule].
 *
 * @property debug a debug build: a debuggable APK on Android, a debug Kotlin framework (Xcode's Debug configuration)
 *   on iOS.
 * @property version the version parents see, with the build number: "1.0 (6)".
 */
class BuildInfo(
    val debug: Boolean,
    val version: String,
)

/**
 * Where downloaded stories are kept (data/HostedStoryLibrary.kt): private to the app, never emptied by the system
 * (not a cache folder), and left out of device backups, since they download again.
 */
class LibraryDirectory(
    val path: Path,
)

/**
 * The language the device is set to, as a language code ("es", "es-MX", "en"). Tale Time speaks it until a grown-up
 * picks another in the parents area (domain/Language).
 */
expect fun deviceLanguage(): String

/**
 * Koin bindings that need the platform: where the settings file, the database and the downloaded stories
 * ([LibraryDirectory]) live, the HTTP client for downloads, the [BuildInfo], and a [NarrationPlayer] for each screen
 * that reads aloud. Everything else here is a process-wide singleton (DataStore and Room both require one instance per
 * file).
 */
expect val platformModule: Module
