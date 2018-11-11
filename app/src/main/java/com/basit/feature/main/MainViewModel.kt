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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import arrow.core.Either
import com.basit.entity.Firebase
import com.basit.firebase.RealTimeDB
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val jobs: MutableList<Job> = mutableListOf()
    private val basitItems: MutableLiveData<Firebase.DBRoot> = MutableLiveData()
    private val msg: MutableLiveData<String> = MutableLiveData()
    private val loading: MutableLiveData<Boolean> = MutableLiveData()
    private val mutex = Mutex()

    fun getMsg(): LiveData<String> = msg

    fun getLoading(): LiveData<Boolean> = loading

    fun getPlayLists(): LiveData<Firebase.DBRoot> {
        jobs += GlobalScope.launch {
            mutex.withLock {
                runBlocking(Dispatchers.Main) { loading.value = true }
                val result = RealTimeDB.getFBDBRoot()
                when (result) {
                    is Either.Right -> {
                        val data = result.b.data
                        basitItems.postValue(data)
                    }
                    is Either.Left -> {
                        msg.postValue(result.a.msg)
                    }
                }
                runBlocking(Dispatchers.Main) { loading.value = false }
            }
        }
        return basitItems
    }

    override fun onCleared() {
        jobs.forEach { if (it.isActive) it.cancel() }
    }
}