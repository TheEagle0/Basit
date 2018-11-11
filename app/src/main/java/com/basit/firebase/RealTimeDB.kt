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

package com.basit.firebase

import arrow.core.Either
import arrow.core.Eval
import com.basit.entity.BasitError
import com.basit.entity.BasitSuccess
import com.basit.entity.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.basit.entity.Firebase.DBRoot as FDBRoot
import com.basit.entity.Firebase.PlayList as FPlayList

object RealTimeDB {

    private val db: Eval<FirebaseDatabase> = Eval.later {
        val db = FirebaseDatabase.getInstance()
        db.setPersistenceEnabled(true)
        db
    }

    suspend fun getFBDBRoot() = suspendCoroutine<Either<BasitError, BasitSuccess<Firebase.DBRoot>>> { continuation ->
        db.value().reference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {}
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    if (snapshot.value != null) {
                        val playLists: MutableList<Firebase.PlayList> = mutableListOf()
                        snapshot.children.first().children.forEach {
                            playLists.add(it.getValue(Firebase.PlayList::class.java)!!)
                        }
                        val root: Firebase.DBRoot = Firebase.DBRoot(playLists.toList())
                        continuation.resume(Either.right(BasitSuccess(root)))
                    } else {
                        continuation.resume(Either.left(BasitError("Error : data does exist but is null")))
                    }
                } else {
                    continuation.resume(Either.left(BasitError("Error : data does not exist")))
                }
            }
        })
    }
}
