package com.nicolasguillen.taletime.domain

import com.nicolasguillen.taletime.fixtures.CONTENT_CATALOG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    @Test
    fun readsTheRealShelfWithAFreeStoryFirst() {
        // content/catalog.json has only what the shelf that ships inside the app starts from: it parses too.
        val entries = CatalogParser.parse(CONTENT_CATALOG)
        assertTrue(entries.isNotEmpty())
        val first = entries.first()
        assertEquals(Access.FREE, first.access)
        assertTrue(first.isOpen(owned = emptySet()))
        assertFalse(first.downloadable, "nothing to download until the catalog comes from the host")
    }

    @Test
    fun readsThePublishedCatalog() {
        val entry =
            CatalogParser
                .parse(
                    """{ "format": 1, "stories": [ {
                        "id": "fox", "access": "free", "ageBand": "4-7", "endings": 5,
                        "title": { "en": "The Fox", "es": "El zorro" },
                        "languages": { "en": { "bytes": 1200000, "narration": true }, "es": { "bytes": 900000, "narration": false } },
                        "cover": { "file": "cover-0123456789.webp", "bytes": 81234, "sha256": "${"a".repeat(64)}" },
                        "manifest": { "file": "manifest-9876543210.json", "bytes": 1964, "sha256": "${"b".repeat(64)}" },
                        "somethingNew": true
                    } ] }""",
                ).single()
        assertTrue(entry.downloadable)
        assertEquals(listOf("en", "es"), entry.languages.keys.toList(), "the story's first language first")
        assertEquals("El zorro", entry.title("es"))
        assertEquals("The Fox", entry.title("fr"), "a language it isn't in: its first")
        assertEquals("es", entry.firstChoice("es"))
        assertEquals("en", entry.firstChoice("fr"))
        assertEquals("cover-0123456789.webp", entry.cover?.file)
        assertEquals(5, entry.endings)
        assertEquals(listOf("en", "es"), entry.published)
    }

    @Test
    fun theShelfInsideTheAppKnowsItsLanguagesFromItsTitles() {
        val entry =
            CatalogParser
                .parse(
                    """{ "stories": [ { "id": "fox", "access": "free", "endings": 4, "title": { "en": "The Fox", "es": "El zorro" } } ] }""",
                ).single()
        assertEquals(listOf("en", "es"), entry.published)
        assertEquals(4, entry.endings)
        assertEquals(0, CatalogParser.parse("""{ "stories": [ { "id": "a", "access": "free" } ] }""").single().endings, "unknown")
    }

    @Test
    fun aPaidStoryOpensOnceItsProductIsOwned() {
        val entries =
            CatalogParser.parse(
                """{ "stories": [
                    { "id": "a", "access": "free" },
                    { "id": "b", "access": "paid", "product": "story.b", "version": 3, "somethingNew": true }
                ] }""",
            )
        val paid = entries[1]
        assertEquals(CatalogEntry("b", Access.PAID, "story.b"), paid)
        assertNull(paid.manifest)
        assertFalse(paid.isOpen(owned = emptySet()))
        assertFalse(paid.isOpen(owned = setOf("story.other")))
        assertTrue(paid.isOpen(owned = setOf("story.b")))
    }

    @Test
    fun aPaidStoryWithoutAProductIsLeftOff() {
        val entries = CatalogParser.parse("""{ "stories": [ { "id": "b", "access": "paid" } ] }""")
        assertEquals(emptyList(), entries)
    }
}
