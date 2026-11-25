package com.nicolasguillen.taletime.domain

import com.nicolasguillen.taletime.fakes.TINY
import com.nicolasguillen.taletime.fakes.tinyEntry
import com.nicolasguillen.taletime.story.Step
import com.nicolasguillen.taletime.story.StoryParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfTest {
    private val bundle = StoryBundle(StoryParser.parse(TINY), mapOf("cover" to "/stories/tiny/cover.webp"), reading = "es")
    private val free = Library(ready = true, stories = listOf(tinyEntry()))
    private val paid = Library(ready = true, stories = listOf(tinyEntry("paid")))
    private val downloaded = mapOf("tiny" to bundle)

    @Test
    fun aBookNotDownloadedYetShowsItsCoverAndTitleOnly() {
        val library = free.copy(covers = mapOf("tiny" to "/stories/tiny/cover-from-catalog.webp"))
        val book = shelf(library, emptyMap(), owned = emptySet(), progress = emptyMap()).single()
        assertFalse(book.downloaded)
        assertTrue(book.open)
        assertEquals("/stories/tiny/cover-from-catalog.webp", book.cover)
        assertEquals("Chiquito", book.title("es"))
        assertEquals("Tiny", book.title("en"))
        assertEquals(emptyList(), book.endings)
    }

    @Test
    fun aDownloadedBookIsTitledInTheLanguageItsReadIn() {
        val book = shelf(free, downloaded, owned = emptySet(), progress = emptyMap()).single()
        assertTrue(book.downloaded)
        assertEquals("Chiquito", book.title("en"))
        assertEquals("/stories/tiny/cover.webp", book.cover)
        assertEquals(listOf("e1", "e2"), book.endings.map { it.id })
        assertFalse(book.inProgress)
        assertEquals(emptySet(), book.endingsFound)
    }

    @Test
    fun aDownloadShowsOnItsBook() {
        val library = free.copy(downloads = mapOf("tiny" to Download.Running("es", 0.4f)))
        val book = shelf(library, emptyMap(), emptySet(), emptyMap()).single()
        assertTrue(book.downloading)
        assertNull(shelf(free, emptyMap(), emptySet(), emptyMap()).single().download)
    }

    @Test
    fun aPaidBookIsLockedUntilBought() {
        assertFalse(shelf(paid, downloaded, emptySet(), emptyMap()).single().open)
        assertTrue(shelf(paid, downloaded, setOf("story.tiny"), emptyMap()).single().open)
    }

    @Test
    fun progressShowsAsEndingsFoundAndContinue() {
        val saved = StoryProgress(path = listOf(Step("p1"), Step("f1")), endingsFound = setOf("e1", "removed-in-v2"))
        val book = shelf(free, downloaded, emptySet(), mapOf("tiny" to saved)).single()
        assertTrue(book.inProgress)
        assertEquals(setOf("e1"), book.endingsFound)
    }

    @Test
    fun aFinishedOrBrokenPathIsNotInProgress() {
        val finished = StoryProgress(path = listOf(Step("p1"), Step("f1", 0), Step("e1")))
        val broken = StoryProgress(path = listOf(Step("p1"), Step("gone")))
        for (saved in listOf(finished, broken)) {
            assertFalse(shelf(free, downloaded, emptySet(), mapOf("tiny" to saved)).single().inProgress)
        }
    }
}
