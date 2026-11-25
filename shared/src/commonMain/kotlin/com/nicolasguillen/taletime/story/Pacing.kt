package com.nicolasguillen.taletime.story

/**
 * One word of a page's text: where it sits in the text (from [start] until [end]) and when it's read, in
 * milliseconds from the first word.
 */
data class TimedWord(
    val start: Int,
    val end: Int,
    val atMillis: Long,
)

/**
 * When one word of a narrated page is said, from the start of its clip, in milliseconds. `story package` measures it
 * from the delivered clip (tools/storytool/timings.py), for every word: every run of non-space characters, in order.
 */
data class WordTime(
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * When each word of a page appears on screen. A narrated page takes its times from its clip ([narrated]), so the words
 * come as the narrator says them. A page without narration gets the narrator's pace for the age band ([paced]), with a
 * breath after each sentence, so it still reads at the same steady speed.
 */
object Pacing {
    private val WORD = Regex("""\S+""")

    /**
     * Where a narrated clip's first word starts when its timings are missing: the 0.3 s of silence every clip starts
     * with (audio/<lang>/REQUESTED_AUDIO.md).
     */
    const val CLIP_LEAD_MILLIS = 300L

    /**
     * The words of [text] at the times its clip says them: [times], one per word. Without them, or if they were
     * measured for other words, at [wordsPerMinute] (the pace every narrator is asked for), after the clip's lead.
     */
    fun narrated(
        text: String,
        times: List<WordTime>?,
        wordsPerMinute: Int,
    ): List<TimedWord> {
        val words = WORD.findAll(text).toList()
        if (times == null || times.size != words.size) {
            return paced(text, wordsPerMinute).map { it.copy(atMillis = it.atMillis + CLIP_LEAD_MILLIS) }
        }
        return words.zip(times) { word, time -> TimedWord(word.range.first, word.range.last + 1, time.startMillis) }
    }

    /** Words per minute: the `pace` content/style/voice.json asks every narrator for, in every language. Keep the two the same. */
    fun wordsPerMinute(ageBand: String): Int =
        when (ageBand) {
            "3-5" -> 115
            "7-10" -> 155
            else -> 140
        }

    /** The words of [text], one every beat at [wordsPerMinute], with a pause after each sentence and clause. */
    fun paced(
        text: String,
        wordsPerMinute: Int,
    ): List<TimedWord> {
        val beat = 60_000L / wordsPerMinute
        var at = 0L
        return WORD
            .findAll(text)
            .map { match ->
                TimedWord(match.range.first, match.range.last + 1, at).also { at += beat + pauseAfter(match.value, beat) }
            }.toList()
    }

    /** A full beat after a sentence ("and... SPLASH!" too), half a beat after a comma. Closing quotes don't count. */
    private fun pauseAfter(
        word: String,
        beat: Long,
    ): Long =
        when (word.trimEnd('"', '\'', '”', '’', ')').lastOrNull()) {
            '.', '!', '?', '…' -> beat
            ',', ';', ':', '—' -> beat / 2
            else -> 0
        }
}
