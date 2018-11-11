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

package com.basit.entity

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize


object Firebase {

    @Parcelize
    data class Track(override val id: Int = -1, override val name: String = "", val URL: String = "") : Parcelable,
        BasitItem

    @Parcelize
    data class PlayList(override val id: Int = -1,
                        override val name: String = "",
                        val baseURL: String = "",
                        val tracks: List<Track> = listOf()) : Parcelable, BasitItem

    @Parcelize
    data class DBRoot(val playLists: List<PlayList>) : Parcelable

}