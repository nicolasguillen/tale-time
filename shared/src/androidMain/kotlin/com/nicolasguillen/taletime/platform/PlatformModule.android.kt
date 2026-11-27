package com.nicolasguillen.taletime.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import com.nicolasguillen.taletime.data.local.DATABASE_FILE_NAME
import com.nicolasguillen.taletime.data.local.SETTINGS_FILE_NAME
import com.nicolasguillen.taletime.data.local.TaleTimeDatabase
import com.nicolasguillen.taletime.data.local.buildTaleTimeDatabase
import com.nicolasguillen.taletime.data.local.createPreferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule =
    module {
        single<DataStore<Preferences>> {
            createPreferencesDataStore(androidContext().filesDir.resolve(SETTINGS_FILE_NAME).absolutePath)
        }
        single<TaleTimeDatabase> {
            val context = androidContext()
            Room
                .databaseBuilder<TaleTimeDatabase>(context, context.getDatabasePath(DATABASE_FILE_NAME).absolutePath)
                .buildTaleTimeDatabase()
        }
        // In filesDir, not cacheDir, which the system may empty. androidApp's backup rules leave it out of backups.
        single { LibraryDirectory(androidContext().filesDir.resolve("library").toOkioPath()) }
        single {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 20_000
                }
            }
        }
        // A new one for each screen that reads aloud: it releases its own.
        factory<NarrationPlayer> { AndroidNarrationPlayer(androidContext()) }
        single {
            val context = androidContext()
            BuildInfo(
                debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                version = appVersion(context),
            )
        }
        // An agent's switches come with the intent that launched the app (MainActivity), only in a debug build.
        single { if (get<BuildInfo>().debug) launchedWith else AgentFlags() }
    }

/** The switches in the intent that launched the app: see [takeAgentFlags]. */
private var launchedWith = AgentFlags()

/**
 * Takes an agent's switches ([AgentFlags]) from the intent that launched the app, before anything reads them. Launch
 * with `-S`, so the app starts afresh with them: `adb shell am start -S -n com.nicolasguillen.taletime/.MainActivity
 * --ez skipReading true --ez autoTurn true --es openStory fox-and-the-lantern`, or `--es openStory fox-and-the-lantern
 * --es openEnding e2-sky-parade` to try an ending.
 */
fun takeAgentFlags(intent: Intent) {
    launchedWith =
        AgentFlags(
            skipReading = intent.getBooleanExtra("skipReading", false),
            openStory = intent.getStringExtra("openStory"),
            autoTurn = intent.getBooleanExtra("autoTurn", false),
            openEnding = intent.getStringExtra("openEnding"),
        )
}

/** "1.0 (6)": versionName and versionCode. */
private fun appVersion(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    return "${info.versionName} ($code)"
}
