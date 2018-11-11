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

package com.basit.feature.main

import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import arrow.core.Eval
import com.basit.BuildConfig
import com.basit.R
import com.basit.base.addFragment
import com.basit.base.setLocaleToArabic
import com.basit.feature.home.HomeFragment
import com.basit.feature.player.PlayerFragment
import com.basit.feature.tracks.TracksFragment
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    val vm: Eval<MainViewModel> = Eval.later {
        ViewModelProviders.of(this).get(MainViewModel::class.java)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(setLocaleToArabic(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableStrictMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) addHomeFragment()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            getPlayerFragment().stopPlayerIfPaused()
        }
    }

    override fun onBackPressed() {
        if (isSlidingPanelExpanded()) {
            collapseSlidingPanel()
            return
        }
        super.onBackPressed()
    }

    private fun addHomeFragment() {
        addFragment(lazy { HomeFragment() }, false)
    }

    fun getPlayerFragment(): PlayerFragment {
        return supportFragmentManager.findFragmentByTag("PlayerFragment") as PlayerFragment
    }

    private fun enableStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects().detectLeakedClosableObjects().penaltyLog().penaltyDeath().build())
        }
    }

    private fun isSlidingPanelExpanded(): Boolean {
        return slidingUpPanelLayout?.panelState == SlidingUpPanelLayout.PanelState.EXPANDED
    }

    private fun collapseSlidingPanel() {
        slidingUpPanelLayout?.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
    }

    fun getTracksFragment(): TracksFragment {
        return supportFragmentManager.findFragmentByTag("TracksFragment") as TracksFragment
    }

}

