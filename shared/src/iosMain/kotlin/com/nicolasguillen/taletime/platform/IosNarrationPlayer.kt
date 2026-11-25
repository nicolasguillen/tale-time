package com.nicolasguillen.taletime.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Narration with AVAudioPlayer. The audio session is for spoken audio that plays even with the ring switch on silent,
 * like an audiobook: reading aloud is what the app does (plan.md §1, "No reading required to play").
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosNarrationPlayer : NarrationPlayer {
    private var player: AVAudioPlayer? = null

    /** AVAudioPlayer holds its delegate weakly: keep it here while its clip plays. */
    private var delegate: ClipEnds? = null
    private var paused = false

    override suspend fun play(path: String) =
        withContext(Dispatchers.Main) {
            stopCurrent()
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeSpokenAudio, options = 0u, error = null)
            session.setActive(true, error = null)
            val clip =
                memScoped {
                    val error = alloc<ObjCObjectVar<NSError?>>()
                    AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = error.ptr).takeIf { error.value == null }
                } ?: throw IllegalStateException("Can't play $path")
            player = clip
            try {
                suspendCancellableCoroutine { done ->
                    val ends =
                        ClipEnds { ok ->
                            when {
                                !done.isActive -> Unit
                                ok -> done.resume(Unit)
                                else -> done.resumeWithException(IllegalStateException("Can't play $path"))
                            }
                        }
                    delegate = ends
                    clip.delegate = ends
                    clip.prepareToPlay()
                    if (!paused && !clip.play()) done.resumeWithException(IllegalStateException("Can't play $path"))
                }
            } finally {
                if (player === clip) stopCurrent()
            }
        }

    override fun positionMillis(): Long? = player?.let { (it.currentTime * 1000).toLong() }

    override fun pause() {
        paused = true
        player?.pause()
    }

    override fun resume() {
        if (!paused) return
        paused = false
        player?.play()
    }

    override fun release() = stopCurrent()

    private fun stopCurrent() {
        player?.let {
            it.delegate = null
            it.stop()
        }
        player = null
        delegate = null
    }
}

/** Tells when a clip ends: [onEnd] (true) at its end, (false) if it can't be decoded. */
private class ClipEnds(
    private val onEnd: (Boolean) -> Unit,
) : NSObject(),
    AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(
        player: AVAudioPlayer,
        successfully: Boolean,
    ) = onEnd(successfully)

    override fun audioPlayerDecodeErrorDidOccur(
        player: AVAudioPlayer,
        error: NSError?,
    ) = onEnd(false)
}
