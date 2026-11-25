package com.nicolasguillen.taletime.platform

/**
 * Plays a story's narration clips (plan.md §7.4), one at a time, from the files the story downloaded. Each screen that
 * reads aloud gets its own ([platformModule] makes a new one each time) and [release]s it when it's done.
 *
 * Every call happens on the main thread.
 */
interface NarrationPlayer {
    /**
     * Plays the clip at [path] from its start and returns when it ends. Cancelling the call stops the clip. Throws if
     * the clip can't be played. While [paused], the clip waits, and plays once [resume] is called.
     */
    suspend fun play(path: String)

    /** How far into the clip playing the narrator is, in milliseconds; null when no clip is playing. */
    fun positionMillis(): Long?

    /** The app left the screen: the clip playing stops where it is, and so does the next one. */
    fun pause()

    /** Back on screen: the clip goes on from where it stopped. */
    fun resume()

    /** Stops for good and lets go of the system's player. */
    fun release()
}
