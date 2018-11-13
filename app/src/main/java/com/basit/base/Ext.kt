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

package com.basit.base

import android.animation.Animator
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.basit.R
import com.basit.base.constant.Key
import com.basit.base.constant.Language.ARABIC
import com.basit.base.constant.UI.PLAYER_PROGRESS_DURATION_TIME_FORMAT
import com.basit.entity.Firebase
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.TimeUnit


/**************************************************************************************************/
fun Long.millisToPlayerTime(): String = if (this <= 0) String.format(Locale(ARABIC), PLAYER_PROGRESS_DURATION_TIME_FORMAT, 0, 0, 0)
else String.format(Locale(ARABIC),
    PLAYER_PROGRESS_DURATION_TIME_FORMAT,
    TimeUnit.MILLISECONDS.toHours(this),
    TimeUnit.MILLISECONDS.toMinutes(this) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(this)),
    TimeUnit.MILLISECONDS.toSeconds(this) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(this)))

fun setLocaleToArabic(context: Context): Context {
    val locale = Locale(ARABIC)
    val configuration = context.resources.configuration
    configuration.setLayoutDirection(locale)
    configuration.setLocale(locale)
    return context.createConfigurationContext(configuration)
}

/**************************************************************************************************/
fun AppCompatActivity.addFragment(f: Lazy<Fragment>, addToBackStack: Boolean = false, arguments: Bundle? = null) {
    val fragment = f.value
    val tag = fragment::class.java.simpleName
    fragment.arguments = arguments
    supportFragmentManager.beginTransaction().setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).add(R.id.container, fragment, tag).run {
        if (addToBackStack) this.addToBackStack(tag)
        commit()
    }
}

fun getTracksFragmentArgs(playList: Firebase.PlayList): Bundle {
    val arg = Bundle()
    arg.putParcelable(Key.KEY_FIREBASE_PLAY_LIST, playList)
    return arg
}

fun getMediaSessionExtras(playList: Firebase.PlayList, track: Firebase.Track): Bundle {
    val bundle = Bundle()
    bundle.putParcelable(Key.KEY_FIREBASE_PLAY_LIST, playList)
    bundle.putParcelable(Key.KEY_FIREBASE_TRACK, track)
    return bundle
}

private val loadingViewId = View.generateViewId()

fun setLoading(isLoading: Boolean, root: View, context: Context, container: FrameLayout) {
    val oldPb: ProgressBar? = root.findViewById(loadingViewId)
    if (oldPb == null && isLoading) {
        val pb = ProgressBar(context)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        pb.layoutParams = params
        pb.id = loadingViewId
        pb.alpha = 0F
        container.addView(pb)
        pb.show()
    } else if (oldPb != null && !isLoading) oldPb.hide()
}

private const val ANIMATION_DURATION = 300L
private const val SHOW_ALPHA = 1F
private const val HIDE_ALPHA = 0F

fun View.show(onShow: () -> Unit = {}) {
    this.animate().setDuration(ANIMATION_DURATION).alpha(SHOW_ALPHA).setListener(object : Animator.AnimatorListener {
        override fun onAnimationRepeat(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator?) {
            onShow()
        }

        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationStart(animation: Animator?) {}
    }).start()
}

fun View.hide(onHide: () -> Unit = {}) {
    this.animate().setDuration(ANIMATION_DURATION).alpha(HIDE_ALPHA).setListener(object : Animator.AnimatorListener {
        override fun onAnimationRepeat(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator?) {
            onHide()
        }

        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationStart(animation: Animator?) {}
    }).start()
}

fun Fragment.snack(msg: String, duration: Int = Snackbar.LENGTH_LONG) {
    runBlocking(Dispatchers.Main) {
        view?.let { nonNullView ->
            Snackbar.make(nonNullView, msg, duration).show()
        }
    }
}

/**************************************************************************************************/
fun getARString(id: Int): String {
    val configuration = Configuration(BasitApp.basit.resources.configuration)
    configuration.setLocale(Locale(ARABIC))
    return BasitApp.basit.createConfigurationContext(configuration).resources.getString(id)
}

/**************************************************************************************************/
fun launchWithLock(lock: Mutex, block: suspend () -> Unit): Unit = { GlobalScope.launch { lock.withLock { block() } } }()

suspend fun <T> async(block: suspend () -> T): T = GlobalScope.async { block() }.await()