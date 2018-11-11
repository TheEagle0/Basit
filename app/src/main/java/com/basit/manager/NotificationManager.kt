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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.basit.R
import com.basit.base.constant.Notification.PLAYER_NOTIFICATION_CHANNEL_ID
import com.basit.base.getARString
import com.basit.feature.main.MainActivity
import androidx.media.app.NotificationCompat.MediaStyle as MediaStyleCompat

class NotificationManager {
    companion object {
        const val NOTIFICATION_ID = 1
        fun notify(context: Service, mediaSession: MediaSessionCompat, paused: Boolean): Notification {
            // You only need to create the channel on API 26+ devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel(context)
            val notificationBuilder = NotificationCompat.Builder(context, PLAYER_NOTIFICATION_CHANNEL_ID)
            notificationBuilder.setStyle(MediaStyleCompat().setShowActionsInCompactView(1).setMediaSession(mediaSession.sessionToken).setShowCancelButton(
                true).setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP)))
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary)).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(mediaSession.controller.metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                .setContentText(mediaSession.controller.metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_previous,
                    getARString(R.string.previous),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
                .addAction(if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    if (paused) getARString(R.string.play)
                    else getARString(R.string.pause),
                    if (paused) MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PLAY)
                    else MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PAUSE))
                .addAction(android.R.drawable.ic_media_next,
                    getARString(R.string.next),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
                .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0))
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.album_art))
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notification = notificationBuilder.build()
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            return notification
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun createChannel(context: Context) {
            val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // The id of the channel.
            // The user-visible name of the channel.
            val name = context.getString(R.string.user_notification_channel_name)
            // The user-visible description of the channel.
            val description = context.getString(R.string.user_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(PLAYER_NOTIFICATION_CHANNEL_ID, name, importance)
            // Configure the notification channel.
            mChannel.description = description
            mChannel.setShowBadge(false)
            mChannel.setSound(null, null)
            mChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            mNotificationManager.createNotificationChannel(mChannel)
        }
    }
}