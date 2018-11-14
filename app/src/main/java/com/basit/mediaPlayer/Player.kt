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

import android.app.Application
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
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import com.basit.mediaPlayer.Player as BPlayer

object Player : Runnable, AudioManager.OnAudioFocusChangeListener {

    init {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "init" })
    }

    /**###########################################################################################*/
    private const val TIME_ELAPSED_HANDLER_UPDATE_INTERVAL = 1000L
    private const val AGENT = "com.basit"
    /**###########################################################################################*/
    private val playerMutex = Mutex()
    private val bundle = Bundle()
    private val timeElapsedHandler = Handler()
    private val audioFocusHandler = Handler()
    /**###########################################################################################*/
    private var playOnFocus: Boolean = false
    private var isNoisyReceiverRegistered: Boolean = false
    /**###########################################################################################*/
    @RequiresApi(Build.VERSION_CODES.O)
    private val audioFocusRequest: AudioFocusRequest =
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
    /**###########################################################################################*/
    private val noisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            pause()
        }
    }
    /**###########################################################################################*/
    private val metaDataBuilder: MediaMetadataCompat.Builder by lazy {
        MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(app.resources, R.drawable.album_art)).run {
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, app.getString(R.string.app_name))
                putString(MediaMetadataCompat.METADATA_KEY_GENRE, app.getString(R.string.media_metadata_genre))
            }
    }
    private val playbackStateBuilder: PlaybackStateCompat.Builder = PlaybackStateCompat.Builder()
    /**###########################################################################################*/
    private lateinit var playList: Firebase.PlayList
    private lateinit var playerService: PlayerService
    private lateinit var uris: List<Uri>
    private lateinit var app: BasitApp
    /**###########################################################################################*/
    private lateinit var exoPlayer: SimpleExoPlayer
    private lateinit var cache: SimpleCache
    private lateinit var dataSourceFactory: DefaultHttpDataSourceFactory
    private lateinit var cacheDataSource: CacheDataSourceFactory
    private lateinit var trackSelector: DefaultTrackSelector
    /**###########################################################################################*/
    fun initPlayer(app: Application) {
        exoPlayer = ExoPlayerFactory.newSimpleInstance(app, trackSelector)
        exoPlayer.addListener(PlayerListenerAdapter(this))
    }

    /**###########################################################################################*/
    fun initPlayerComponents(app: Application) {
        cache = SimpleCache(app.cacheDir, LeastRecentlyUsedCacheEvictor(Long.MAX_VALUE))
        dataSourceFactory = DefaultHttpDataSourceFactory(AGENT, null, 0, 0, true)
        cacheDataSource = CacheDataSourceFactory(cache, dataSourceFactory)
        trackSelector = DefaultTrackSelector()
    }

    /**###########################################################################################*/
    fun preparePlayer(): Unit = launchWithLock(playerMutex) {
        val concatenatingMediaSources = ConcatenatingMediaSource(*uris.toMediaSources())
        runBlocking(Dispatchers.Main) { exoPlayer.prepare(concatenatingMediaSources, true, true) }
    }

    /**###########################################################################################*/
    operator fun invoke(playList: Firebase.PlayList, playerService: PlayerService): BPlayer {
        this.app = playerService.application as BasitApp
        this.playList = playList
        this.playerService = playerService
        this.uris = playList.tracks.map { Uri.parse(playList.baseURL).buildUpon().appendEncodedPath(it.URL).build() }
        return this@Player
    }

    /**###########################################################################################*/
    private fun onAudioFocusLossTransientCanDuck() = { exoPlayer.volume = .3F }()

    /**###########################################################################################*/
    private fun onAudioFocusLossTransient() = { playOnFocus = true;pause() }()

    /**###########################################################################################*/
    private fun onAudioFocusLoss() = { playOnFocus = false;pause() }()

    /**###########################################################################################*/
    private fun onAudioFocusGain() = { if (isPlaying()) exoPlayer.volume = 1F;if (playOnFocus && !isPlaying()) play();playOnFocus = false }()

    /**###########################################################################################*/
    private suspend fun List<Uri>.toMediaSources(): Array<ExtractorMediaSource> = async {
        map { uri ->
            ExtractorMediaSource.Factory(cacheDataSource).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(Int.MAX_VALUE)).createMediaSource(uri)
        }.toTypedArray()
    }

    /**###########################################################################################*/
    private fun registerNoisyReceiver() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "registerNoisyReceiver" })
        val filter = IntentFilter()
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        playerService.registerReceiver(noisyReceiver, filter)
        isNoisyReceiverRegistered = true
    }

    /**###########################################################################################*/
    private fun unregisterNoisyReceiver() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "unregisterNoisyReceiver" })
        if (isNoisyReceiverRegistered) playerService.unregisterReceiver(noisyReceiver)
        isNoisyReceiverRegistered = false
    }

    /**###########################################################################################*/
    private fun isPlaying(): Boolean = exoPlayer.playWhenReady

    /**###########################################################################################*/
    fun play() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "play" })
        if (requestAudioFocus()) exoPlayer.playWhenReady = true
    }

    /**###########################################################################################*/
    fun pause() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "pause" })
        exoPlayer.playWhenReady = false
        if (!playOnFocus) abandonAudioFocus()
    }

    /**###########################################################################################*/
    fun skipToNext() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "skipToNext" })
        if (exoPlayer.currentWindowIndex < uris.lastIndex) exoPlayer.seekTo(exoPlayer.currentWindowIndex + 1, 0)
        else exoPlayer.seekTo(0, 0)
        if (!isPlaying()) play()
    }

    /**###########################################################################################*/
    fun skipToPrevious() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "skipToPrevious" })
        if (exoPlayer.currentWindowIndex == 0) exoPlayer.seekTo(uris.lastIndex, 0)
        else exoPlayer.seekTo(exoPlayer.currentWindowIndex - 1, 0)
        if (!isPlaying()) play()
    }

    /**###########################################################################################*/
    fun stop() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "stop" })
        stopForeground(true)
        exoPlayer.stop()
    }

    /**###########################################################################################*/
    fun release() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "release" })
        exoPlayer.release()
    }

    /**###########################################################################################*/
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

    /**###########################################################################################*/
    private fun setMetaData() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "setMetaData" })
        playerService.mediaSession.setMetadata(buildMetadata())
    }

    /**###########################################################################################*/
    private fun setPlaybackState(inPlaybackState: Int) {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "setPlaybackState --> ${inPlaybackState.toPlaybackState()}" })
        val actions = when (inPlaybackState) {
            PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_BUFFERING -> PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            else -> PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }
        val playbackState =
            playbackStateBuilder.setState(inPlaybackState, exoPlayer.currentPosition, exoPlayer.playbackParameters.speed).setActions(actions)
                .build()
        playerService.mediaSession.setPlaybackState(playbackState)
    }

    /**###########################################################################################*/
    fun seekTo(pos: Long) {
        exoPlayer.seekTo(pos)
    }

    /**###########################################################################################*/
    fun skipToTrack(trackId: Int) = launchWithLock(playerMutex) {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "skipToTrack with id --> $trackId" })
        runBlocking(Dispatchers.Main) { exoPlayer.seekTo(playList.tracks.indexOfFirst { it.id == trackId }, 0L) }
    }

    /**###########################################################################################*/
    override fun run() {
        timeElapsedHandler.postDelayed(this, TIME_ELAPSED_HANDLER_UPDATE_INTERVAL)
        bundle.putLong(Key.KEY_PLAYER_CURRENT_PROGRESS_MILLIS, exoPlayer.currentPosition)
        playerService.mediaSession.sendSessionEvent(Event.EVENT_UPDATE_PLAYER_ELAPSED_TIME, bundle)
    }

    /**###########################################################################################*/
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

    /**###########################################################################################*/
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

    /**###########################################################################################*/
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

    /**###########################################################################################*/
    @Suppress(names = ["DEPRECATION"])
    private fun requestAudioFocus(): Boolean {
        val audioManager = playerService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            else audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AUDIOFOCUS_GAIN) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        logInfo(LogTag.BASIT_PLAYER_TAG, { "requestAudioFocus --> $result" })
        return result
    }

    /**###########################################################################################*/
    @Suppress(names = ["DEPRECATION"])
    private fun abandonAudioFocus() {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "abandonAudioFocus --> " })
        val audioManager = playerService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager.abandonAudioFocusRequest(audioFocusRequest)
        else audioManager.abandonAudioFocus(this)
    }

    /**###########################################################################################*/
    private fun onEnded() {
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        timeElapsedHandler.removeCallbacks(this@Player)
        release()
        playerService.stopForeground(true)
        abandonAudioFocus()
    }

    /**###########################################################################################*/
    private fun onBuffering() {
        setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
        setMetaData()
        notifyOnly(false)
        stopForeground(false)
    }

    /**###########################################################################################*/
    private fun onSeek() {
        setMetaData()
        notifyOnly(false)
        playerService.mediaSession.setExtras(getMediaSessionExtras(playList, playList.tracks[exoPlayer.currentWindowIndex]))
    }

    /**###########################################################################################*/
    private fun startForeground(paused: Boolean) {
        playerService.startForeground(NOTIFICATION_ID, NotificationManager.notify(playerService, playerService.mediaSession, paused))
    }

    /**###########################################################################################*/
    private fun stopForeground(removeNotification: Boolean) {
        playerService.stopForeground(removeNotification)
    }

    /**###########################################################################################*/
    private fun notifyOnly(paused: Boolean) {
        NotificationManager.notify(playerService, playerService.mediaSession, paused)
    }

    /**###########################################################################################*/
    override fun onAudioFocusChange(focusChange: Int) {
        logInfo(LogTag.BASIT_PLAYER_TAG, { "onAudioFocusChange --> ${focusChange.toAudioFocusString()}" })
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> onAudioFocusGain()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onAudioFocusLossTransientCanDuck()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onAudioFocusLossTransient()
            AudioManager.AUDIOFOCUS_LOSS -> onAudioFocusLoss()
        }
    }

    /**###########################################################################################*/
    private object PlayerListenerAdapter : Player.EventListener {
        private lateinit var player: BPlayer
        operator fun invoke(player: BPlayer): PlayerListenerAdapter {
            this.player = player
            return this
        }

        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {}
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
        override fun onSeekProcessed() {}
        override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {}
        override fun onPlayerError(error: ExoPlaybackException) {
            logInfo(LogTag.BASIT_PLAYER_TAG, { "onPlayerError --> ${error.message}" })
        }

        override fun onLoadingChanged(isLoading: Boolean) {}
        override fun onPositionDiscontinuity(reason: Int) = player.onSeek()
        override fun onRepeatModeChanged(repeatMode: Int) {}
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            logInfo(LogTag.BASIT_PLAYER_TAG, { "onPlayerStateChanged --> ${playbackState.toExoPlayerState()} , playWhenReady = $playWhenReady" })
            with(player) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onBuffering()
                    Player.STATE_ENDED -> onEnded()
                    Player.STATE_READY -> onReady(playWhenReady)
                }
            }
        }
    }
    /**###########################################################################################*/
}