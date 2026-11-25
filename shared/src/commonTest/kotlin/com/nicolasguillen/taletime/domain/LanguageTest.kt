package com.nicolasguillen.taletime.domain

import com.nicolasguillen.taletime.fixtures.CONTENT_STORIES
import com.nicolasguillen.taletime.i18n.EnglishStrings
import com.nicolasguillen.taletime.i18n.SpanishStrings
import com.nicolasguillen.taletime.i18n.stringsFor
import com.nicolasguillen.taletime.story.StoryParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class LanguageTest {
    @Test
    fun readsTheLanguageOfADeviceLocale() {
        assertEquals(Language.SPANISH, Language.of("es"))
        assertEquals(Language.SPANISH, Language.of("es-MX"))
        assertEquals(Language.SPANISH, Language.of("es_419"))
        assertEquals(Language.SPANISH, Language.of("es-419"))
        assertEquals(Language.ENGLISH, Language.of("en-GB"))
        assertNull(Language.of("fr-FR"), "Tale Time doesn't speak French")
        assertNull(Language.of(null))
    }

    @Test
    fun aStoryIsReadInThePickedLanguageWhenItHasIt() {
        val story = StoryParser.parse(CONTENT_STORIES.getValue("fox-and-the-lantern"))
        assertEquals("es", story.languageFor("es"))
        assertEquals("en", story.languageFor("fr"), "else in its first language")
    }

    @Test
    fun theAppHasItsOwnWordsInEveryLanguage() {
        assertEquals(EnglishStrings, stringsFor(Language.ENGLISH))
        assertEquals(SpanishStrings, stringsFor(Language.SPANISH))
        assertNotEquals(EnglishStrings.read, SpanishStrings.read)
        assertEquals("De 4 a 7 años", SpanishStrings.ages("4-7"))
        assertEquals("Ages 4–7", EnglishStrings.ages("4-7"))
    }
}
