package com.nicolasguillen.taletime.data.local

import androidx.room3.Room
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.domain.Settings
import com.nicolasguillen.taletime.domain.StoryProgress
import com.nicolasguillen.taletime.domain.TextSize
import com.nicolasguillen.taletime.story.Step
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The real Room database (bundled SQLite) and DataStore, end to end. iOS only: on Android, Room needs a Context,
 * which host tests don't have.
 */
class LocalStorageTest {
    private val id = Random.nextLong().toULong()
    private val dbPath = "${NSTemporaryDirectory()}taletime-test-$id.db"
    private val settingsPath = "${NSTemporaryDirectory()}taletime-test-$id.preferences_pb"
    private val database = Room.databaseBuilder<TaleTimeDatabase>(name = dbPath).buildTaleTimeDatabase()
    private val progress = RoomProgressRepository(database.progress())

    @OptIn(ExperimentalForeignApi::class)
    @AfterTest
    fun tearDown() {
        database.close()
        NSFileManager.defaultManager.removeItemAtPath(dbPath, null)
        NSFileManager.defaultManager.removeItemAtPath(settingsPath, null)
    }

    @Test
    fun savesAPathVisitedPagesAndEndings() =
        runTest {
            assertEquals(emptyMap(), progress.all().first())
            progress.save("fox", listOf(Step("p1"), Step("f1", 1), Step("p2")))
            progress.save("fox", listOf(Step("p1"), Step("f1", 0), Step("e1")))
            progress.markEndingFound("fox", "e1")
            progress.markEndingFound("fox", "e1")
            assertEquals(
                StoryProgress(
                    path = listOf(Step("p1"), Step("f1", 0), Step("e1")),
                    visited = setOf("p1", "f1", "p2", "e1"),
                    endingsFound = setOf("e1"),
                ),
                progress.progress("fox").first(),
            )
            assertEquals(StoryProgress(), progress.progress("other").first())
            progress.resetAll()
            assertEquals(emptyMap(), progress.all().first())
        }

    @Test
    fun settingsAreKept() =
        runTest {
            val settings = DataStoreSettingsRepository(createPreferencesDataStore(settingsPath, backgroundScope))
            assertEquals(Settings(), settings.settings.first())
            settings.update { it.copy(textSize = TextSize.LARGE) }
            assertEquals(TextSize.LARGE, settings.settings.first().textSize)
            settings.update { it.copy(language = Language.ENGLISH) }
            assertEquals(Language.ENGLISH, settings.settings.first().language)
        }

    @Test
    fun spanishIsTheLanguageUntilAGrownUpPicksOne() =
        runTest {
            val settings = DataStoreSettingsRepository(createPreferencesDataStore(settingsPath, backgroundScope))
            assertEquals(Language.SPANISH, settings.settings.first().language)
            settings.update { it.copy(language = Language.ENGLISH) }
            settings.update { it.copy(textSize = TextSize.MEDIUM) }
            assertEquals(Language.ENGLISH, settings.settings.first().language, "a grown-up's pick stays")
        }
}
