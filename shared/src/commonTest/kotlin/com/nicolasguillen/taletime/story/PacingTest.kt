package com.nicolasguillen.taletime.story

import com.nicolasguillen.taletime.fixtures.CONTENT_STORIES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacingTest {
    @Test
    fun everyWordGetsItsPlaceInTheTextAndABeatAtTheNarratorsPace() {
        val text = "Hop! Mia landed,  then “whoosh.” Up"
        val words = Pacing.paced(text, wordsPerMinute = 120)
        assertEquals(listOf("Hop!", "Mia", "landed,", "then", "“whoosh.”", "Up"), words.map { text.substring(it.start, it.end) })
        // 500 ms a word, a full beat more after a sentence, half a beat after a comma.
        assertEquals(listOf(0L, 1_000, 1_500, 2_250, 2_750, 3_750), words.map { it.atMillis })
    }

    @Test
    fun aNarratedPageTakesItsTimesFromItsClip() {
        val text = "Hop! Mia landed."
        val times = listOf(WordTime(300, 700), WordTime(1_100, 1_400), WordTime(1_400, 2_000))
        val words = Pacing.narrated(text, times, wordsPerMinute = 120)
        assertEquals(listOf("Hop!", "Mia", "landed."), words.map { text.substring(it.start, it.end) })
        assertEquals(listOf(300L, 1_100, 1_400), words.map { it.atMillis })
    }

    @Test
    fun withoutTimingsForItsWordsANarratedPageComesAtThePaceAfterTheClipsLead() {
        val text = "Hop! Mia landed."
        val paced = Pacing.paced(text, 120).map { it.atMillis + Pacing.CLIP_LEAD_MILLIS }
        assertEquals(paced, Pacing.narrated(text, null, 120).map { it.atMillis })
        // Timings measured for other words (the text changed after the clip was made).
        assertEquals(paced, Pacing.narrated(text, listOf(WordTime(0, 10)), 120).map { it.atMillis })
    }

    @Test
    fun youngerBandsReadSlower() {
        assertEquals(listOf(115, 140, 155), listOf("3-5", "4-7", "7-10").map(Pacing::wordsPerMinute))
    }

    @Test
    fun aRealPageTakesAboutAsLongAsItsNarration() {
        for ((id, json) in CONTENT_STORIES) {
            val story = StoryParser.parse(json)
            val pace = Pacing.wordsPerMinute(story.ageBand)
            for (node in story.nodes.values) {
                val text = node.text[story.languages.first()]
                val words = Pacing.paced(text, pace)
                assertEquals(text.split(Regex("""\s+""")).count { it.isNotEmpty() }, words.size, "$id/${node.id}")
                val seconds = words.last().atMillis / 1000.0
                val atPace = words.size * 60.0 / pace
                assertTrue(seconds in atPace * 0.9..atPace * 1.5, "$id/${node.id}: $seconds s for ${words.size} words")
            }
        }
    }
}
