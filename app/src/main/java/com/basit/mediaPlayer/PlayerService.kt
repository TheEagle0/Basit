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

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.basit.base.constant.Key
import com.basit.entity.Firebase

class PlayerService : MediaBrowserServiceCompat() {

    private lateinit var player: Player

    private lateinit var mutableMediaSession: MediaSessionCompat

    val mediaSession by lazy { mutableMediaSession }

    private val mediaSessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {

        override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
            val playList: Firebase.PlayList = extras!!.getParcelable(Key.KEY_FIREBASE_PLAY_LIST)!!
            releaseOldPlayerInstance()
            player = Player(playList, this@PlayerService)
            mutableMediaSession.setExtras(extras.toPlayerBundle())
            player.preparePlayer()
        }

        override fun onSkipToPrevious() = player.skipToPrevious()

        override fun onPlay() = player.play()

        override fun onStop() {
            player.stop()
            releaseMediaSession()
            stopSelf()
        }

        override fun onSkipToNext() = player.skipToNext()

        override fun onSetRepeatMode(repeatMode: Int) = player.setRepeatMode(repeatMode)

        override fun onSetShuffleMode(shuffleMode: Int) = player.setShuffleMode(shuffleMode)

        override fun onPause() = player.pause()

        override fun onSeekTo(pos: Long) = player.seekTo(pos)

        override fun onSkipToQueueItem(id: Long) = player.skipToTrack(id.toInt())
    }

    private fun releaseOldPlayerInstance() {
        if (::player.isInitialized) {
            player.pause()
            player.stop()
            player.release()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return Service.START_NOT_STICKY
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(null)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = BrowserRoot("Root", null)

    override fun onCreate() {
        super.onCreate()
        initMediaSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        handleOnDestroy()
    }

    private fun handleOnDestroy() {
        releaseMediaSession()
        if (::player.isInitialized) player.release()
    }

    private fun releaseMediaSession() {
        if (::mutableMediaSession.isInitialized) {
            mutableMediaSession.isActive = false
            mutableMediaSession.release()
        }
    }

    private fun Bundle?.toPlayerBundle(): Bundle {
        val bundle = Bundle()
        this?.let { extras ->
            val playList: Firebase.PlayList = extras.getParcelable(Key.KEY_FIREBASE_PLAY_LIST)!!
            val track: Firebase.Track = extras.getParcelable(Key.KEY_FIREBASE_TRACK)!!
            extras.classLoader = Firebase.PlayList::class.java.classLoader
            bundle.putParcelable(Key.KEY_FIREBASE_PLAY_LIST, playList)
            extras.classLoader = Firebase.Track::class.java.classLoader
            bundle.putParcelable(Key.KEY_FIREBASE_TRACK, track)
        }
        return bundle
    }

    private fun initMediaSession(): MediaSessionCompat {
        val mediaSession = MediaSessionCompat(this, "BasitPlayerService")
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
        mediaSession.setCallback(mediaSessionCallback)
        sessionToken = mediaSession.sessionToken
        mediaSession.isActive = true
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        this.mutableMediaSession = mediaSession
        return mediaSession
    }

}
