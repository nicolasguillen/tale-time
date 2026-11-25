package com.nicolasguillen.taletime.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Narration with Android's MediaPlayer: the clips are small local files, so no streaming player is needed. While a clip
 * plays it holds the audio focus for a moment (music in another app pauses or ducks), and lets go of it when paused or
 * released.
 */
class AndroidNarrationPlayer(
    context: Context,
) : NarrationPlayer {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(SPEECH)
            .build()
    private var player: MediaPlayer? = null
    private var paused = false

    override suspend fun play(path: String) =
        withContext(Dispatchers.Main) {
            stopCurrent()
            val clip = MediaPlayer()
            player = clip
            try {
                clip.setAudioAttributes(SPEECH)
                clip.setDataSource(path)
                clip.prepare()
                suspendCancellableCoroutine { done ->
                    clip.setOnCompletionListener { if (done.isActive) done.resume(Unit) }
                    clip.setOnErrorListener { _, what, extra ->
                        if (done.isActive) done.resumeWithException(IOException("Can't play $path ($what, $extra)"))
                        true
                    }
                    if (!paused) start(clip)
                }
            } finally {
                if (player === clip) stopCurrent()
            }
        }

    override fun positionMillis(): Long? = player?.let { runCatching { it.currentPosition.toLong() }.getOrNull() }

    override fun pause() {
        paused = true
        player?.let { if (it.isPlaying) it.pause() }
        audio.abandonAudioFocusRequest(focus)
    }

    override fun resume() {
        if (!paused) return
        paused = false
        player?.let { start(it) }
    }

    override fun release() {
        stopCurrent()
        audio.abandonAudioFocusRequest(focus)
    }

    private fun start(clip: MediaPlayer) {
        audio.requestAudioFocus(focus)
        clip.start()
    }

    private fun stopCurrent() {
        player?.release()
        player = null
    }

    private companion object {
        val SPEECH: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
    }
}
