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

package com.basit.manager

import android.net.Uri
import android.util.Log
import arrow.core.Either
import arrow.core.Eval
import arrow.core.None
import arrow.syntax.function.memoize
import com.basit.base.BasitApp
import com.basit.base.constant.Dir
import com.basit.entity.Basit
import com.basit.entity.BasitError
import com.basit.entity.BasitSuccess
import com.basit.entity.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2core.DownloadBlock
import com.tonyodev.fetch2core.Extras
import com.tonyodev.fetch2core.Func
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.tonyodev.fetch2.Download as FDownload

object DownloadManager {
    private const val TAG = "DownloadManager"
    private val app = BasitApp.basit
    private val downloadMutex = Mutex()
    private val getDownloadsMutex = Mutex()
    private const val UPDATE_INTERVAL_IN_MILLIS: Long = 1000L
    private val fetcherConfiguration: Eval<FetchConfiguration> = Eval.later {
        val fetcherConfigurationBuilder =
            FetchConfiguration.Builder(app).enableFileExistChecks(true).setProgressReportingInterval(UPDATE_INTERVAL_IN_MILLIS)
                .enableAutoStart(true).enableLogging(true).setDownloadConcurrentLimit(10)
        fetcherConfigurationBuilder.build()
    }
    private val downloadsUri: Eval<Uri> = Eval.later {
        Uri.parse(app.getExternalFilesDir(Dir.DOWNLOADS_DIR_TYPE)!!.absolutePath)
    }
    private val fetcher: () -> Fetch = { Fetch.getInstance(fetchConfiguration = fetcherConfiguration.value()) }.memoize()

    private fun isStorageAvailable() = app.getExternalFilesDir(Dir.DOWNLOADS_DIR_TYPE) != null

    private fun isStorageNotAvailable() = !isStorageAvailable()

    private fun Basit.Track.getFullFillName(): String =
        downloadsUri.value().buildUpon().appendPath("$playListId").appendPath("$id").toString()

    private fun createRequest(track: Basit.Track) = {
        val request = Request(track.fullURL, track.getFullFillName())
        request.tag = track.name
        request.groupId = track.playListId
        request.extras = Extras(track.trackToMap())
        request
    }()

    private fun Basit.Track.trackToMap(): Map<String, String> {
        val map: MutableMap<String, String> = mutableMapOf()
        map[::id.name] = "$id"
        map[::name.name] = name
        map[::playListId.name] = "$playListId"
        map[::playListName.name] = playListName
        map[::fullURL.name] = fullURL
        return map.toMap()
    }

    private fun Map<String, String>.toDownloadEntity(download: FDownload): Download.Track {
        return Download.Track(this["id"]!!.toInt(),
            this["name"]!!,
            this["playListId"]!!.toInt(),
            this["playListName"]!!,
            this["fullURL"]!!,
            download.progress)
    }

    suspend fun download(track: Basit.Track): Either<BasitError, BasitSuccess<None>> = downloadMutex.withLock {
        suspendCoroutine { continuation ->
            if (isStorageNotAvailable()) {
                continuation.resume(Either.left(BasitError("Error : Please check your storage")))
                return@suspendCoroutine
            }
            val result = createRequest(track)
            fetcher().enqueue(result, Func {
                Log.i(TAG, "New request successfully enqueued -> $it")
                continuation.resume(Either.right(BasitSuccess(None)))
                return@Func
            }, Func {
                Log.i(TAG, "New request did NOT enqueued : $it")
                continuation.resume(Either.left(BasitError("$TAG : Error can not enqueue request with track id -> ${track.id}")))
                return@Func
            })
        }
    }

    fun startObservingDownloads() {
    }

    fun stopObservingDownloads() {
    }

    suspend fun getDownloads() = getDownloadsMutex.withLock {
        suspendCoroutine<List<Download.Track>> { continuation ->
            fetcher().getDownloads(Func {
                val downloads = it.map { download -> download.extras.map.toDownloadEntity(download) }
                continuation.resume(downloads)
            })
        }
    }

    object DownloadsListner : FetchListener {
        override fun onAdded(download: FDownload) {}
        override fun onCancelled(download: FDownload) {}
        override fun onCompleted(download: FDownload) {}
        override fun onDeleted(download: FDownload) {}
        override fun onDownloadBlockUpdated(download: FDownload, downloadBlock: DownloadBlock, totalBlocks: Int) {
        }

        override fun onError(download: FDownload, error: Error, throwable: Throwable?) {}
        override fun onPaused(download: FDownload) {}
        override fun onProgress(download: FDownload, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        }

        override fun onQueued(download: FDownload, waitingOnNetwork: Boolean) {}
        override fun onRemoved(download: FDownload) {}
        override fun onResumed(download: FDownload) {}
        override fun onStarted(download: FDownload, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
        }

        override fun onWaitingNetwork(download: FDownload) {}
    }
}