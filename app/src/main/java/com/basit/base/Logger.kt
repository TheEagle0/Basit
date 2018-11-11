package com.basit.base

import android.media.AudioManager
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.basit.BuildConfig
import com.basit.base.LogTag.isLoggingEnabled
import com.google.android.exoplayer2.Player

object LogTag {

    val isLoggingEnabled = BuildConfig.DEBUG
    const val BASIT_PLAYER_TAG = "BASIT_PLAYER"

}

fun logError(tag: String, msg: () -> String = { "" }, f: () -> Unit = {}) {
    if (isLoggingEnabled) {
        Log.e(tag, msg());f()
    }
}

fun logDebug(tag: String, msg: () -> String = { "" }, f: () -> Unit = {}) {
    if (isLoggingEnabled) {
        Log.d(tag, msg());f()
    }
}

fun logVerbose(tag: String, msg: () -> String = { "" }, f: () -> Unit = {}) {
    if (isLoggingEnabled) {
        Log.v(tag, msg());f()
    }
}

fun logInfo(tag: String, msg: () -> String = { "" }, f: () -> Unit = {}) {
    if (isLoggingEnabled) {
        Log.i(tag, msg());f()
    }
}

fun Int.toAudioFocusString() = when (this) {
    AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
    AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
    else -> throw IllegalArgumentException("Unknown audio focus change value --> $this")
}

fun Int.toPlaybackState(): String = when (this) {
    PlaybackStateCompat.STATE_PLAYING -> "STATE_PLAYING"
    PlaybackStateCompat.STATE_BUFFERING -> "STATE_BUFFERING"
    PlaybackStateCompat.STATE_ERROR -> "STATE_ERROR"
    PlaybackStateCompat.STATE_NONE -> "STATE_NONE"
    PlaybackStateCompat.STATE_STOPPED -> "STATE_STOPPED"
    PlaybackStateCompat.STATE_PAUSED -> "STATE_PAUSED"
    else -> throw IllegalArgumentException("Unknown playback state --> $this")
}

fun Int.toExoPlayerState(): String = when (this) {
    Player.STATE_IDLE -> "STATE_IDLE"
    Player.STATE_READY -> "STATE_READY"
    Player.STATE_BUFFERING -> "STATE_BUFFERING"
    Player.STATE_ENDED -> "STATE_ENDED"
    else -> throw IllegalArgumentException("Unknown exoPlayer state --> $this")
}
