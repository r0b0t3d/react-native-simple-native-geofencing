package com.simplegeofencing.reactnative


import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log

class ShowTimeoutNotification : IntentService(TAG) {
    private val channel: NotificationChannel? = null
    private val CHANNEL_ID = "channel_01"

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Timeout of Geofences")
    }

    override fun onHandleIntent(intent: Intent?) {
        //Notify for timeout
        postNotification(
                intent!!.getStringExtra("notifyStringTitle"),
                intent.getStringExtra("notifyStringDescription"),
                intent.getStringExtra("notifyChannelStringTitle"),
                intent.getStringExtra("notifyChannelStringDescription")
        )
    }

    /*
       Notifications
    */
    private fun getNotificationBuilder(title: String,
                                       content: String,
                                       channelTitle: String,
                                       channelDescription: String): NotificationCompat.Builder {
        //Onclick
        //Intent intent = new Intent(this.reactContext, AlertDetails.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        //PendingIntent pendingIntent = PendingIntent.getActivity(this.reactContext, 0, intent, 0);

        //Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(this.applicationInfo.icon)
                .setLargeIcon(BitmapFactory.decodeResource(this.resources,
                        this.applicationInfo.icon))
                .setAutoCancel(true)
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel == null) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, channelTitle, importance)
            channel.description = channelDescription
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = this.getSystemService(NotificationManager::class.java)
            notificationManager!!.createNotificationChannel(channel)
            notification.setChannelId(CHANNEL_ID)
        }
        return notification
    }

    fun postNotification(Title: String,
                         Content: String,
                         channelTitle: String,
                         channelDescription: String) {
        val notificationManager = NotificationManagerCompat.from(this)

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(getNextNotifId(this.applicationContext),
                getNotificationBuilder(Title, Content, channelTitle, channelDescription).build())
    }

    companion object {
        private val TAG = "GeofenceTimeout"
        private val PREFERENCE_LAST_NOTIF_ID = "PREFERENCE_LAST_NOTIF_ID"


        private fun getNextNotifId(context: Context): Int {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            var id = sharedPreferences.getInt(PREFERENCE_LAST_NOTIF_ID, 0) + 1
            if (id == Integer.MAX_VALUE) {
                id = 0
            }
            sharedPreferences.edit().putInt(PREFERENCE_LAST_NOTIF_ID, id).apply()
            return id
        }
    }
}
