/*
 *
 *  * Copyright [2019] [Muhammad Elkady] @kady.muhammad@gmail.com
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.basit.mediaPlayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import arrow.syntax.function.memoize
import com.basit.R
import com.basit.base.*
import com.basit.base.constant.Event
import com.basit.base.constant.Key
import com.basit.entity.Firebase
import com.basit.manager.NotificationManager
import com.basit.manager.NotificationManager.Companion.NOTIFICATION_ID
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache

class Player(private val playList: Firebase.PlayList, private val playerService: PlayerService) :
    Runnable, AudioManager.OnAudioFocusChangeListener {

    private val uris = playList.tracks.map { Uri.parse(StringBuilder(playList.baseURL).append("/").append(it.URL).toString()) }

    private val noisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            logInfo(LogTag.BASIT_PLAYER_TAG, { "Noisy receiver was triggered with intent = $intent" })
            pause()
        }
    }

    private var isNoisyReceiverRegistered = false

    private lateinit var exoPlayer: SimpleExoPlayer

    private var playOnFocus: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    private val audioFocusRequest: () -> AudioFocusRequest = {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
            setAudioAttributes(AudioAttributes.Builder().run {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                build()
            })
            setAcceptsDelayedFocusGain(true)
            setOnAudioFocusChangeListener(this@Player, audioFocusHandler)
            build()
        }
    }.memoize()

    override fun onAudioFocusChange(focusChange: Int) {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Audio focus was changed to -> ${focusChange.toAudioFocusString()}" })
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isPlaying()) exoPlayer.volume = 1F
                if (playOnFocus && !isPlaying()) play()
                playOnFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                exoPlayer.volume = .3F
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                playOnFocus = true
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                playOnFocus = false
                pause()
            }
        }
    }

    fun preparePlayer(): SimpleExoPlayer {
        logInfo(LogTag.BASIT_PLAYER_TAG, {
            val hasOldInstance = ::exoPlayer.isInitialized
            "Preparing player ... hasOldInstance = $hasOldInstance play list id = ${playList.id} "
        })
        if (::exoPlayer.isInitialized) {
            exoPlayer.stop()
            exoPlayer.release()
        }
        val concatenatingMediaSources = ConcatenatingMediaSource(*uris.toMediaSources())
        exoPlayer = ExoPlayerFactory.newSimpleInstance(app, trackSelector)
        exoPlayer.addListener(PlayerListenerAdapter())
        exoPlayer.prepare(concatenatingMediaSources)
        return exoPlayer
    }

    private fun List<Uri>.toMediaSources() = map { ExtractorMediaSource.Factory(cacheDataSource).createMediaSource(it) }.toTypedArray()

    private fun registerNoisyReceiver() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Registering noisy receiver" })
        val filter = IntentFilter()
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        playerService.registerReceiver(noisyReceiver, filter)
        isNoisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Unregistering noisy receiver ... isAlreadyUnRegistered $isNoisyReceiverRegistered" })
        if (isNoisyReceiverRegistered) playerService.unregisterReceiver(noisyReceiver)
        isNoisyReceiverRegistered = false
    }

    private fun isPlaying(): Boolean = exoPlayer.playWhenReady

    fun play() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Play was called in player" })
        if (requestAudioFocus()) exoPlayer.playWhenReady = true
    }

    fun pause() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Pause was called in player" })
        exoPlayer.playWhenReady = false
        if (!playOnFocus) abandonAudioFocus()
    }

    fun skipToNext() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Skip to next was called in player" })
        if (exoPlayer.currentWindowIndex < uris.lastIndex) exoPlayer.seekTo(exoPlayer.currentWindowIndex + 1, 0)
        else exoPlayer.seekTo(0, 0)
        if (!isPlaying()) play()
    }

    fun skipToPrevious() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Skip to previous was called in player" })
        if (exoPlayer.currentWindowIndex == 0) exoPlayer.seekTo(uris.lastIndex, 0)
        else exoPlayer.seekTo(exoPlayer.currentWindowIndex - 1, 0)
        if (!isPlaying()) play()
    }

    fun stop() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Stop was called in player" })
        stopForeground(true)
        exoPlayer.stop()
    }

    fun release() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Release was called in player" })
        exoPlayer.release()
    }

    private fun buildMetadata(): MediaMetadataCompat {
        val currentTrack = playList.tracks[exoPlayer.currentWindowIndex]
        with(currentTrack) {
            metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "$id")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTrack.name)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, exoPlayer.duration)
                .putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, playList.id.toLong())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, playList.name)
        }
        return metaDataBuilder.build()
    }

    private fun setMetaData() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Set meta data was called in player" })
        playerService.mediaSession.setMetadata(buildMetadata())
    }

    private fun setPlaybackState(inPlaybackState: Int) {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Set playback state was called in player with ${inPlaybackState.toPlaybackState()}" })
        val actions = when (inPlaybackState) {
            PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_BUFFERING ->
                PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            else -> PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }
        val playbackState =
            playbackStateBuilder.setState(inPlaybackState, exoPlayer.currentPosition, exoPlayer.playbackParameters.speed).setActions(actions).build()
        playerService.mediaSession.setPlaybackState(playbackState)
    }

    fun seekTo(pos: Long) {
        exoPlayer.seekTo(pos)
    }

    fun skipToTrack(trackId: Int) = exoPlayer.seekTo(playList.tracks.indexOfFirst { it.id == trackId }, 0L)

    override fun run() {
        timeElapsedHandler.postDelayed(this, TIME_ELAPSED_HANDLER_UPDATE_INTERVAL)
        bundle.putLong(Key.KEY_PLAYER_CURRENT_PROGRESS_MILLIS, exoPlayer.currentPosition)
        playerService.mediaSession.sendSessionEvent(Event.EVENT_UPDATE_PLAYER_ELAPSED_TIME, bundle)
    }

    fun setRepeatMode(repeatMode: Int) {
        when (repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                playerService.mediaSession.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
            }
            else -> {
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                playerService.mediaSession.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
            }
        }
    }

    fun setShuffleMode(shuffleMode: Int) {
        when (shuffleMode) {
            PlaybackStateCompat.SHUFFLE_MODE_ALL -> {
                exoPlayer.shuffleModeEnabled = true
                playerService.mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)
            }
            PlaybackStateCompat.SHUFFLE_MODE_NONE -> {
                exoPlayer.shuffleModeEnabled = false
                playerService.mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
            }
        }
    }

    private inner class PlayerListenerAdapter : Player.EventListener {

        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {}
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
        override fun onSeekProcessed() {}
        override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        }

        override fun onPlayerError(error: ExoPlaybackException) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {}

        override fun onPositionDiscontinuity(reason: Int) = onSeek()

        override fun onRepeatModeChanged(repeatMode: Int) {}

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            logInfo(LogTag.BASIT_PLAYER_TAG, {
                "ExoPlayer state changed to ${playbackState.toExoPlayerState()} , playWhenReady = $playWhenReady"
            })
            when (playbackState) {
                Player.STATE_BUFFERING -> onBuffering()
                Player.STATE_ENDED -> onEnded()
                Player.STATE_READY -> onReady(playWhenReady)
            }
        }
    }

    private fun onReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            //Playing
            setMetaData()
            setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            timeElapsedHandler.post(this@Player)
            startForeground(false)
            registerNoisyReceiver()
        } else {
            //Paused
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            timeElapsedHandler.removeCallbacks(this@Player)
            stopForeground(false)
            notifyOnly(true)
            unregisterNoisyReceiver()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = playerService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager.requestAudioFocus(audioFocusRequest()) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            else audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AUDIOFOCUS_GAIN) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Requesting audio focus result = $result" })
        return result
    }

    private fun abandonAudioFocus() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "Abandon audio focus" })
        val audioManager = playerService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager.abandonAudioFocusRequest(audioFocusRequest())
        else audioManager.abandonAudioFocus(this)
    }

    private fun onEnded() {
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        timeElapsedHandler.removeCallbacks(this@Player)
        release()
        playerService.stopForeground(true)
        abandonAudioFocus()
    }

    private fun onBuffering() {
        setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
        setMetaData()
        notifyOnly(false)
        stopForeground(false)
    }

    private fun onSeek() {
        setMetaData()
        notifyOnly(false)
        playerService.mediaSession.setExtras(getMediaSessionExtras(playList, playList.tracks[exoPlayer.currentWindowIndex]))
    }

    private fun startForeground(paused: Boolean) {
        playerService.startForeground(NOTIFICATION_ID, NotificationManager.notify(playerService, playerService.mediaSession, paused))
    }

    private fun stopForeground(removeNotification: Boolean) {
        playerService.stopForeground(removeNotification)
    }

    private fun notifyOnly(paused: Boolean) {
        NotificationManager.notify(playerService, playerService.mediaSession, paused)
    }


    companion object {
        private val bundle = Bundle()
        private val timeElapsedHandler = Handler()
        private val audioFocusHandler = Handler()
        private const val TIME_ELAPSED_HANDLER_UPDATE_INTERVAL = 1000L
        private const val AGENT = "com.basit"
        private val app: BasitApp = BasitApp.basit
        private val metaDataBuilder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(app.resources, R.drawable.album_art)).run {
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, app.getString(R.string.app_name))
                putString(MediaMetadataCompat.METADATA_KEY_GENRE, app.getString(R.string.media_metadata_genre))
            }
        private val playbackStateBuilder: PlaybackStateCompat.Builder = PlaybackStateCompat.Builder()
        private val cache: SimpleCache = SimpleCache(app.cacheDir, LeastRecentlyUsedCacheEvictor(Long.MAX_VALUE))
        private val dataSourceFactory: DefaultHttpDataSourceFactory = DefaultHttpDataSourceFactory(AGENT, null, Int.MAX_VALUE, Int.MAX_VALUE, true)
        private val cacheDataSource: CacheDataSourceFactory = CacheDataSourceFactory(cache, dataSourceFactory)
        private val trackSelector: DefaultTrackSelector = DefaultTrackSelector()
    }
}