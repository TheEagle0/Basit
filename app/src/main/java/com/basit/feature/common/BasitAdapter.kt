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

package com.basit.feature.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.basit.R
import com.basit.base.addFragment
import com.basit.base.constant.BasitItemType
import com.basit.base.constant.Key
import com.basit.base.getTracksFragmentArgs
import com.basit.entity.BasitItem
import com.basit.entity.Firebase
import com.basit.feature.main.MainActivity
import com.basit.feature.tracks.TracksFragment
import kotlinx.android.synthetic.main.basit_item.view.*

class BasitAdapter(private val activity: AppCompatActivity,
                   private val basitItems: MutableList<BasitItem>,
                   private val type: BasitItemType) : RecyclerView.Adapter<BasitAdapter.BasitHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasitHolder =
        BasitHolder(activity, LayoutInflater.from(activity).inflate(R.layout.basit_item, parent, false), basitItems, type)

    override fun getItemCount(): Int = basitItems.size

    override fun onBindViewHolder(holder: BasitHolder, position: Int) {
        holder.basitItem.basitItemName.text = basitItems[holder.adapterPosition].name
    }

    fun updateAdapter(basitItems: MutableList<BasitItem>) {
        this.basitItems.clear()
        this.basitItems.addAll(basitItems)
        this.notifyDataSetChanged()
    }

    class BasitHolder(private val act: AppCompatActivity,
                      val basitItem: View,
                      private val basitItems: MutableList<BasitItem>,
                      type: BasitItemType) : RecyclerView.ViewHolder(basitItem) {
        init {
            basitItem.setOnClickListener {
                when (type) {
                    is BasitItemType.Track -> onTrackClick()
                    is BasitItemType.PlayList -> onPlayListClick()
                }
            }
        }

        private fun onPlayListClick() {
            val item: Firebase.PlayList = basitItems[adapterPosition] as Firebase.PlayList
            act.addFragment(lazy { TracksFragment() }, true, getTracksFragmentArgs(item))
        }

        private fun onTrackClick() {
            if (adapterPosition < 0) return
            val mainActivity = basitItem.context as MainActivity
            val tracksFragment = mainActivity.getTracksFragment()
            val playList = tracksFragment.playList.value()
            val track = playList.tracks[adapterPosition]
            val playerFragment = mainActivity.getPlayerFragment()
            playerFragment.arguments = Bundle().run {
                putParcelable(Key.KEY_FIREBASE_PLAY_LIST, playList)
                putParcelable(Key.KEY_FIREBASE_TRACK, track)
                this
            }
            playerFragment.play(playList, track)
        }
    }
}