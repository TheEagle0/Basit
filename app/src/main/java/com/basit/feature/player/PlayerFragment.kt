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

import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import arrow.core.Eval
import arrow.core.Option
import arrow.core.fix
import arrow.instances.option.monad.monad
import com.basit.R
import com.basit.base.constant.Key
import com.basit.base.hide
import com.basit.base.millisToPlayerTime
import com.basit.base.show
import com.basit.entity.Firebase
import com.basit.entity.PlayerState
import com.basit.feature.main.MainActivity
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_player.*

class PlayerFragment : Fragment() {

    private var isUserSeeking = false

    private val vm: PlayerViewModel  by lazy {
        ViewModelProviders.of(this).get(PlayerViewModel::class.java)
    }

    private val track: Eval<Option<Firebase.Track>> =
        Eval.always { Option.fromNullable(arguments?.getParcelable<Firebase.Track>(Key.KEY_FIREBASE_TRACK)) }
    private val playList: Eval<Option<Firebase.PlayList>> = Eval.always {
        Option.fromNullable(arguments?.getParcelable<Firebase.PlayList>(Key.KEY_FIREBASE_PLAY_LIST))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setClickListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        connectToMediaBrowser()
        observePlayerState()
        observeTrackName()
        observeMaxTime()
        observeElapsedTime()
        observeRepeatMode()
        observeShuffle()
        observeArgs()
    }

    private fun observeArgs() {
        vm.args.observe(this, Observer {
            val bundle = Bundle()
            bundle.putParcelable(Key.KEY_FIREBASE_TRACK, it.a)
            bundle.putParcelable(Key.KEY_FIREBASE_PLAY_LIST, it.b)
            arguments = bundle
        })
    }

    override fun onDestroyView() {
        disconnectFromMediaBrowser()
        super.onDestroyView()
    }

    private fun observeTrackName() {
        vm.trackName.observe(this, Observer { trackName ->
            playingTrackName.text = trackName
        })
    }

    private fun observeShuffle() {
        vm.shuffleMode.observe(this, Observer { shuffleMode ->
            when (shuffleMode) {
                PlaybackStateCompat.SHUFFLE_MODE_ALL -> shuffle.setImageResource(R.drawable.ic_shuffle_active)
                PlaybackStateCompat.SHUFFLE_MODE_NONE -> shuffle.setImageResource(R.drawable.ic_shuffle)
            }
        })
    }

    private fun observeRepeatMode() {
        vm.repeatMode.observe(this, Observer { repeatMode ->
            when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ONE -> repeatOne.setImageResource(R.drawable.ic_repeat_one_active)
                else -> repeatOne.setImageResource(R.drawable.ic_repeat_one)
            }
        })
    }

    private fun observeMaxTime() {
        vm.maxTimeInMillis.observe(this, Observer { max: Long ->
            if (max <= 0) return@Observer
            progress.max = max.toInt()
            progressPercent?.max = max.toInt()
            durationText.text = max.millisToPlayerTime()
        })
    }

    private fun observeElapsedTime() {
        vm.elapsedTimeInMillis.observe(this, Observer { elapsedTimeInMillis: Long ->
            if (elapsedTimeInMillis < 0) return@Observer
            if (!isUserSeeking) {
                progress.progress = elapsedTimeInMillis.toInt()
                progressPercent?.progress = elapsedTimeInMillis.toInt()
                progressText.text = elapsedTimeInMillis.millisToPlayerTime()
            }
        })
    }

    private fun observePlayerState() {
        vm.playerState.observe(this, Observer { state: PlayerState ->
            when (state) {
                PlayerState.Buffering -> showBuffering()
                PlayerState.Playing -> showPlaying()
                PlayerState.Paused -> showPaused()
                PlayerState.Error -> showError()
                PlayerState.Stopped -> showStopped()
            }
        })
    }

    private fun showError() {
        hideBuffering()
        playPause.setImageResource(R.drawable.ic_play)
    }

    private fun showPlaying() {
        hideBuffering()
        playPause.setImageResource(R.drawable.ic_pause)
    }

    private fun showPaused() {
        hideBuffering()
        playPause.setImageResource(R.drawable.ic_play)
    }

    private fun showStopped() {
        hideBuffering()
        playPause.setImageResource(R.drawable.ic_play)
    }

    fun play(playList: Firebase.PlayList, track: Firebase.Track) = internalPlay(playList, track)

    private fun internalPlay(playList: Firebase.PlayList, track: Firebase.Track) {
        vm.playPause(playList, track)
    }

    private fun connectToMediaBrowser() {
        vm.connectMediaBrowser()
    }

    private fun disconnectFromMediaBrowser() {
        vm.disconnectMediaBrowser()
    }

    private fun hideBuffering() = view?.let { buffering.hide() }

    private fun showBuffering() = view?.let { buffering.show() }

    private fun setClickListeners() {
        playPause.setOnClickListener {
            Option.monad().map(playList.value(), track.value()) { r -> r }.fix().map { playListTrack ->
                vm.playPause(playListTrack.a, playListTrack.b)
            }
        }
        repeatOne.setOnClickListener {
            vm.repeatOne()
        }
        shuffle.setOnClickListener {
            vm.shuffle()
        }
        next.setOnClickListener { vm.skipToNext() }
        previous.setOnClickListener { vm.skipToPrevious() }
        (activity as MainActivity).slidingUpPanelLayout?.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {
                progressPercent?.alpha = 1F - slideOffset
            }

            override fun onPanelStateChanged(panel: View,
                                             previousState: SlidingUpPanelLayout.PanelState,
                                             newState: SlidingUpPanelLayout.PanelState) {
            }
        })
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    isUserSeeking = true
                    progressText.text = progress.toLong().millisToPlayerTime()
                    progressPercent?.progress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false
                vm.seekTo(seekBar.progress)
            }
        })
    }

    fun stopPlayerIfPaused() {
        if (!vm.isPlaying()) vm.stop()
    }

}
