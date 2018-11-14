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

package com.basit.feature.player

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import arrow.core.None
import arrow.core.Option
import arrow.core.Tuple2
import arrow.core.fix
import arrow.instances.option.monad.monad
import com.basit.R
import com.basit.base.constant.Event
import com.basit.base.constant.Key
import com.basit.base.getARString
import com.basit.entity.Firebase
import com.basit.entity.PlayerState
import com.basit.mediaPlayer.PlayerService
import kotlinx.coroutines.*

class PlayerViewModel(val app: Application) : AndroidViewModel(app) {

    private val jobs: MutableList<Job> = mutableListOf()

    val playerState: MutableLiveData<PlayerState> = MutableLiveData()
    val elapsedTimeInMillis: MutableLiveData<Long> = MutableLiveData()
    val maxTimeInMillis: MutableLiveData<Long> = MutableLiveData()
    val repeatMode: MutableLiveData<Int> = MutableLiveData()
    val shuffleMode: MutableLiveData<Int> = MutableLiveData()
    val trackName: MutableLiveData<String> = MutableLiveData()
    val args: MutableLiveData<Tuple2<Firebase.Track, Firebase.PlayList>> = MutableLiveData()

    private var isPlayerPrepared = false

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {

        override fun onConnected() {
            onMediaBrowserServiceConnected()
        }

        override fun onConnectionSuspended() {
        }

        override fun onConnectionFailed() {
        }

    }

    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onSessionEvent(event: String, extras: Bundle) {
            when (event) {
                Event.EVENT_UPDATE_PLAYER_ELAPSED_TIME -> {
                    elapsedTimeInMillis.value = extras.getLong(Key.KEY_PLAYER_CURRENT_PROGRESS_MILLIS)
                }
            }

        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            this@PlayerViewModel.shuffleMode.postValue(shuffleMode)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlayerViewModel.repeatMode.postValue(repeatMode)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            when (state.state) {
                PlaybackStateCompat.STATE_PLAYING -> playerState.postValue(PlayerState.Playing)
                PlaybackStateCompat.STATE_PAUSED -> playerState.postValue(PlayerState.Paused)
                PlaybackStateCompat.STATE_BUFFERING -> playerState.postValue(PlayerState.Buffering)
                PlaybackStateCompat.STATE_ERROR -> playerState.postValue(PlayerState.Error)
                PlaybackStateCompat.STATE_STOPPED -> onPlayerStopped()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            maxTimeInMillis.value = metadata.getMaxTimeInMillis()
            trackName.value = metadata.getTrackName()
            Option.monad().map(getTrack(), getPlayList()) { it }.fix().map { args.postValue(it) }
        }

    }

    private fun onPlayerStopped() {
        playerState.postValue(PlayerState.Stopped)
        trackName.postValue(getARString(R.string.empty_track_name))
        elapsedTimeInMillis.postValue(0)
        maxTimeInMillis.postValue(0)
        isPlayerPrepared = false
    }

    private val serviceComponent by lazy { ComponentName(app, PlayerService::class.java) }

    private val mediaBrowserCompat by lazy {
        MediaBrowserCompat(app, serviceComponent, connectionCallback, null)
    }

    private val mediaControllerCompat by lazy {
        MediaControllerCompat(app, mediaBrowserCompat.sessionToken)
    }

    private fun onMediaBrowserServiceConnected() {
        app.startService(Intent(app, PlayerService::class.java))
        mediaControllerCompat.registerCallback(mediaControllerCallback)
        if (isBuffering()) playerState.postValue(PlayerState.Buffering)
        if (isPlaying()) playerState.postValue(PlayerState.Playing)
        if (isPlaying() || isBuffering()) {
            isPlayerPrepared = true
            maxTimeInMillis.postValue(mediaControllerCompat.metadata.getMaxTimeInMillis())
        }
        if (isShuffleOn()) {
            shuffleMode.postValue(PlaybackStateCompat.SHUFFLE_MODE_ALL)
        } else {
            shuffleMode.postValue(PlaybackStateCompat.SHUFFLE_MODE_NONE)
        }
        if (isRepeatOneOn()) {
            repeatMode.postValue(PlaybackStateCompat.REPEAT_MODE_ONE)
        } else {
            repeatMode.postValue(PlaybackStateCompat.REPEAT_MODE_NONE)
        }
        Option.monad().map(getTrack(), getPlayList()) { it }.fix().map {
            args.postValue(it)
            trackName.postValue(it.a.name)
        }
    }

    fun connectMediaBrowser() {
        mediaBrowserCompat.connect()
    }

    fun disconnectMediaBrowser() {
        mediaBrowserCompat.disconnect()
    }

    private fun MediaMetadataCompat?.getTrackName(): String =
        (this?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)) ?: app.getString(R.string.empty_track_name)

    private fun MediaMetadataCompat?.getMaxTimeInMillis(): Long {
        return this?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: -1
    }

    fun skipToPrevious() {
        if (!isPlayerPrepared) return
        mediaControllerCompat.transportControls.skipToPrevious()
    }

    fun skipToNext() {
        if (!isPlayerPrepared) return
        mediaControllerCompat.transportControls.skipToNext()
    }

    fun seekTo(pos: Int) = mediaControllerCompat.transportControls.seekTo(pos.toLong())

    private fun skipTo(trackId: Int) {
        if (!isPlayerPrepared) return
        mediaControllerCompat.transportControls.skipToQueueItem(trackId.toLong())
    }

    fun playPause(playList: Firebase.PlayList, track: Firebase.Track) =
        jobs.add(GlobalScope.launch(Dispatchers.Main + CoroutineExceptionHandler { _, throwable ->
            throwable.printStackTrace()
        }) {

            if (!isPlayerPrepared) {
                internalPlay(playList, track)
                isPlayerPrepared = true
                return@launch
            }

            val oldTrackId = getTrack().orNull()?.id ?: -1
            val oldPlayListId = getPlayList().orNull()?.id ?: -1

            val newTrackId = track.id
            val newPlayListId = playList.id

            if (oldPlayListId == -1 || oldTrackId == -1) {
                internalPlay(playList, track)
                return@launch
            }
            if (oldPlayListId == newPlayListId && oldTrackId == newTrackId) {
                if (isPlaying()) pause() else play()
                return@launch
            }
            if (oldPlayListId == newPlayListId && oldTrackId != newTrackId) {
                skipTo(newTrackId)
                play()
                return@launch
            }
            if (oldPlayListId != newPlayListId && oldTrackId != newTrackId) {
                internalPlay(playList, track)
                return@launch
            }
        })

    private fun internalPlay(playList: Firebase.PlayList, track: Firebase.Track) {
        val bundle = Bundle()
        bundle.putParcelable(Key.KEY_FIREBASE_PLAY_LIST, playList)
        bundle.putParcelable(Key.KEY_FIREBASE_TRACK, track)
        mediaControllerCompat.transportControls.prepareFromMediaId("${playList.id}", bundle)
        mediaControllerCompat.transportControls.play()
    }

    private fun pause() {
        mediaControllerCompat.transportControls.pause()
    }

    private fun play() {
        mediaControllerCompat.transportControls.play()
    }

    fun repeatOne() {
        if (!isPlayerPrepared) return
        val oldRepeatMode = mediaControllerCompat.repeatMode
        if (oldRepeatMode != PlaybackStateCompat.REPEAT_MODE_ONE) mediaControllerCompat.transportControls.setRepeatMode(
            PlaybackStateCompat.REPEAT_MODE_ONE)
        else mediaControllerCompat.transportControls.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
    }

    private fun isShuffleOn(): Boolean {
        return mediaControllerCompat.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
    }

    private fun isRepeatOneOn(): Boolean {
        return mediaControllerCompat.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE
    }

    fun isPlaying(): Boolean {
        if (!mediaBrowserCompat.isConnected || mediaControllerCompat.playbackState == null) return false
        val currentState = mediaControllerCompat.playbackState.state
        return currentState == PlaybackStateCompat.STATE_PLAYING
    }

    fun stop() {
        mediaControllerCompat.transportControls.stop()
    }

    private fun isBuffering(): Boolean {
        if (!mediaBrowserCompat.isConnected || mediaControllerCompat.playbackState == null) return false
        val currentState = mediaControllerCompat.playbackState.state
        return currentState == PlaybackStateCompat.STATE_BUFFERING
    }

    override fun onCleared() {
        jobs.forEach { if (it.isActive) it.cancel() }
    }

    fun shuffle() {
        if (!isPlayerPrepared) return
        val shuffle = mediaControllerCompat.shuffleMode
        if (shuffle != PlaybackStateCompat.SHUFFLE_MODE_ALL) mediaControllerCompat.transportControls.setShuffleMode(
            PlaybackStateCompat.SHUFFLE_MODE_ALL)
        else mediaControllerCompat.transportControls.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
    }

    private fun getTrack(): Option<Firebase.Track> {
        val extras: Bundle? = mediaControllerCompat.extras
        return if (extras == null) {
            None
        } else {
            extras.classLoader = Firebase.Track::class.java.classLoader
            Option.fromNullable(extras.getParcelable(Key.KEY_FIREBASE_TRACK))
        }
    }

    private fun getPlayList(): Option<Firebase.PlayList> {
        val extras: Bundle? = mediaControllerCompat.extras
        return if (extras == null) {
            None
        } else {
            extras.classLoader = Firebase.PlayList::class.java.classLoader
            Option.fromNullable(extras.getParcelable(Key.KEY_FIREBASE_PLAY_LIST))
        }
    }

}