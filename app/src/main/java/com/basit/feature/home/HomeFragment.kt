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

package com.basit.feature.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import arrow.core.Eval
import com.basit.R
import com.basit.base.constant.BasitItemType
import com.basit.base.setLoading
import com.basit.base.snack
import com.basit.feature.common.BasitAdapter
import com.basit.feature.main.MainActivity
import com.basit.feature.main.MainViewModel
import kotlinx.android.synthetic.main.fragment_home.*
import com.basit.entity.Firebase.DBRoot as FDBRoot
import com.basit.entity.Firebase.PlayList as FPlayList

class HomeFragment : Fragment(), Observer<FDBRoot> {

    private val vm: Eval<MainViewModel> by lazy { (activity as MainActivity).vm }

    private val basitItemType: BasitItemType = BasitItemType.PlayList

    private val basitAdapter: BasitAdapter by lazy {
        BasitAdapter(this.activity as AppCompatActivity, mutableListOf(), basitItemType)
    }

    override fun onChanged(data: FDBRoot?) {
        if (data == null) return
        basitAdapter.updateAdapter(data.playLists.toMutableList())
        collapse()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupPlayListsList()
        observeLoading()
        observePlayLists()
        observeMessage()
    }

    private fun collapse() {
        abl.setExpanded(false, true)
    }

    private fun observeLoading() {
        vm.value().getLoading().observe(this, Observer { setLoading(it, view!!, context!!, loadingContainer) })
    }

    private fun observeMessage() {
        vm.value().getMsg().observe(this, Observer { msg: String ->
            snack(msg)
        })
    }

    private fun observePlayLists() {
        vm.value().getPlayLists().observe(this, this)
    }

    private fun setupPlayListsList() {
        val ctx: Context = this.context ?: return
        basitList.layoutManager = GridLayoutManager(ctx, resources.getInteger(R.integer.basit_items_count))
        basitList.setHasFixedSize(true)
        basitList.adapter = basitAdapter
    }

}
