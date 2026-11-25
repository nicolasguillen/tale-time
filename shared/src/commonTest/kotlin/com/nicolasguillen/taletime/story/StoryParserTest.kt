package com.nicolasguillen.taletime.story

import com.nicolasguillen.taletime.fakes.TINY
import com.nicolasguillen.taletime.fixtures.CONTENT_STORIES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StoryParserTest {
    @Test
    fun readsTheSharedFixture() {
        val story = StoryParser.parse(TINY)
        assertEquals("tiny", story.id)
        assertEquals(listOf("en", "es"), story.languages)
        assertEquals("p1", story.start)
        assertEquals(listOf("p1", "f1", "p2", "e1", "e2"), story.nodes.keys.toList())
        val fork = assertIs<Fork>(story.node("f1"))
        assertEquals(listOf("Red door", "Blue door"), fork.options.map { it.label["en"] })
        assertEquals(listOf("Puerta roja", "Puerta azul"), fork.options.map { it.label["es"] })
        assertEquals(listOf("f1-a", "f1-b"), fork.options.map { it.image })
        val ending = assertIs<Ending>(story.node("e1"))
        assertEquals(EndingKind.SILLY, ending.kind)
        assertEquals("Cake", ending.title["en"])
    }

    /** The app reads every story the pipeline wrote, and none of them breaks the reader's graph rules. */
    @Test
    fun readsEveryStoryInContent() {
        assertTrue(CONTENT_STORIES.isNotEmpty())
        for ((id, json) in CONTENT_STORIES) {
            val story = StoryParser.parse(json)
            assertEquals(id, story.id)
            assertEquals(emptyList(), StoryGraph.problems(story), "content/stories/$id")
            assertTrue(story.endings.isNotEmpty(), id)
        }
    }

    @Test
    fun aMissingLanguageFallsBackToTheFirst() {
        val title = StoryParser.parse(TINY).title
        assertEquals("Tiny", title["de"])
    }

    @Test
    fun theLanguageItWasWrittenInComesFirstWhateverTheOrderOfTheFiles() {
        val story = StoryParser.parse(linkedMapOf("es" to TINY.getValue("es"), "en" to TINY.getValue("en")))
        assertEquals(listOf("en", "es"), story.languages)
        assertEquals(listOf("en"), StoryParser.parse(mapOf("en" to TINY.getValue("en"))).languages)
    }

    @Test
    fun refusesATranslationWithAnotherStructure() {
        val es = TINY.getValue("es").replace("\"next\": \"e2\"", "\"next\": \"e1\"")
        assertTrue(es != TINY["es"])
        assertFailsWith<StoryFormatException> { StoryParser.parse(TINY + ("es" to es)) }
    }

    @Test
    fun readsATranslationAloneWhenOnlyItIsDownloaded() {
        val story = StoryParser.parse(mapOf("es" to TINY.getValue("es")))
        assertEquals(listOf("es"), story.languages)
        assertEquals("Chiquito", story.title["es"])
        assertEquals("es", story.languageFor("en"))
    }

    @Test
    fun needsAtMostOneFirstLanguageAndFilesInTheirOwnLanguage() {
        val es = TINY.getValue("es")
        assertFailsWith<StoryFormatException> { StoryParser.parse(TINY + ("es" to es.replace("\"translatedFrom\": \"en\",", ""))) }
        assertFailsWith<StoryFormatException> { StoryParser.parse(mapOf("de" to TINY.getValue("en"))) }
        assertFailsWith<StoryFormatException> { StoryParser.parse(emptyMap()) }
    }

    @Test
    fun translationsAloneMustComeFromTheSameLanguage() {
        val es = TINY.getValue("es")
        val fr =
            es
                .replace(
                    "\"language\": \"es\"",
                    "\"language\": \"fr\"",
                ).replace("\"translatedFrom\": \"en\"", "\"translatedFrom\": \"de\"")
        assertFailsWith<StoryFormatException> { StoryParser.parse(mapOf("es" to es, "fr" to fr)) }
    }

    @Test
    fun refusesAThreeOptionFork() {
        val broken =
            TINY.mapValues { (_, json) ->
                json.replace(
                    """"image": "f1-b", "target": "p2" }""",
                    """"image": "f1-b", "target": "p2" }, { "label": "Green", "image": "f1-c", "target": "e2" }""",
                )
            }
        assertTrue(broken != TINY)
        assertFailsWith<StoryFormatException> { StoryParser.parse(broken) }
    }

    @Test
    fun refusesAnUnknownEndingKind() {
        assertFailsWith<StoryFormatException> { StoryParser.parse(TINY.mapValues { it.value.replace("\"silly\"", "\"sad\"") }) }
    }

    @Test
    fun refusesSomethingThatIsNotAStory() {
        assertFailsWith<StoryFormatException> { StoryParser.parse(mapOf("en" to """{ "id": "x" }""")) }
        assertFailsWith<StoryFormatException> { StoryParser.parse(mapOf("en" to "not json")) }
        assertFailsWith<StoryFormatException> { StoryParser.parse(emptyMap()) }
    }
}
