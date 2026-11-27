package com.nicolasguillen.taletime.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import com.nicolasguillen.taletime.data.local.DATABASE_FILE_NAME
import com.nicolasguillen.taletime.data.local.SETTINGS_FILE_NAME
import com.nicolasguillen.taletime.data.local.TaleTimeDatabase
import com.nicolasguillen.taletime.data.local.buildTaleTimeDatabase
import com.nicolasguillen.taletime.data.local.createPreferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.preferredLanguages
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

actual val platformModule =
    module {
        single<DataStore<Preferences>> { createPreferencesDataStore("${appSupportDirectory()}/$SETTINGS_FILE_NAME") }
        single<TaleTimeDatabase> {
            Room.databaseBuilder<TaleTimeDatabase>(name = "${appSupportDirectory()}/$DATABASE_FILE_NAME").buildTaleTimeDatabase()
        }
        single { LibraryDirectory(libraryDirectory().toPath()) }
        single {
            HttpClient(Darwin) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 20_000
                }
            }
        }
        // A new one for each screen that reads aloud: it releases its own.
        factory<NarrationPlayer> { IosNarrationPlayer() }
        single { BuildInfo(debug = isDebugBinary(), version = appVersion()) }
        // An agent's switches come as launch arguments (XCUIApplication.launchArguments, `devicectl … --`), only in a
        // debug build.
        single { if (isDebugBinary()) agentFlags(NSProcessInfo.processInfo.arguments.map { it.toString() }) else AgentFlags() }
    }

/** An agent's switches in the app's launch arguments: `-skipReading -autoTurn -openStory <id> -openEnding <id>`. */
private fun agentFlags(arguments: List<String>) =
    AgentFlags(
        skipReading = "-skipReading" in arguments,
        openStory = arguments.valueOf("-openStory"),
        autoTurn = "-autoTurn" in arguments,
        openEnding = arguments.valueOf("-openEnding"),
    )

/** The argument after [name], if [name] is there. */
private fun List<String>.valueOf(name: String): String? = indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

/** Xcode's Debug configuration links the debug framework (`embedAndSignAppleFrameworkForXcode` follows it). */
@OptIn(ExperimentalNativeApi::class)
private fun isDebugBinary(): Boolean = Platform.isDebugBinary

/** "1.0 (6)": CFBundleShortVersionString (MARKETING_VERSION) and CFBundleVersion (the build number). */
private fun appVersion(): String {
    val info = NSBundle.mainBundle.infoDictionary
    return "${info?.get("CFBundleShortVersionString")} (${info?.get("CFBundleVersion")})"
}

/** App-private files that aren't user documents go in Application Support (created on first use). */
@OptIn(ExperimentalForeignApi::class)
private fun appSupportDirectory(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(url?.path) { "No Application Support directory" }
}

/**
 * Application Support/library, for the downloaded stories: kept until the app deletes it (unlike Caches), and left out of
 * iCloud and computer backups, since the stories download again.
 */
@OptIn(ExperimentalForeignApi::class)
private fun libraryDirectory(): String {
    val path = "${appSupportDirectory()}/library"
    NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    NSURL.fileURLWithPath(path).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return path
}
