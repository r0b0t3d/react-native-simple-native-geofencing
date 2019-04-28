package com.simplegeofencing.reactnative


import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.text.TextUtils
import android.util.Log

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

import java.util.ArrayList

class GeofenceTransitionsIntentService : IntentService(TAG) {
    private val CHANNEL_ID = "channel_01"
    private val channel: NotificationChannel? = null
    private var mContext: Context? = null

    val mainActivityClass: Class<*>?
        get() {
            val packageName = applicationContext.packageName
            val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(packageName)
            val className = launchIntent!!.component!!.className
            try {
                return Class.forName(className)
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
                return null
            }

        }


    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Intent created")
    }


    override fun onHandleIntent(intent: Intent?) {
        this.mContext = this.applicationContext
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            val errorMessage = "Error Code: " + geofencingEvent.errorCode.toString()
            Log.e(TAG, errorMessage)
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            // Get the transition details as a String.
            val geofenceTransitionDetails = getGeofenceTransitionDetails(
                    geofenceTransition,
                    triggeringGeofences
            )

            for (geofence in triggeringGeofences) {
                Log.i(TAG, "Outside Monitor")

                //SEND CALLBACK
                val localBroadcastManager = LocalBroadcastManager.getInstance(this)
                val customEvent = Intent("monitorGeofence")
                customEvent.putExtra("startTime", intent!!.getLongExtra("startTime", System.currentTimeMillis()))
                customEvent.putExtra("duration", intent.getIntExtra("duration", 3000))
                val event = if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)  "didEnter" else "didExit"
                customEvent.putExtra("event", event)
                customEvent.putExtra("id", geofence.requestId)
                localBroadcastManager.sendBroadcast(customEvent)
            }

            //Get (only) last triggered geofence which is not monitor
//            val geofencesWithoutMonitor = ArrayList<Geofence>()
//            for (geofence in triggeringGeofences) {
//                if (geofence.requestId != "monitor") {
//                    geofencesWithoutMonitor.add(geofence)
//                }
//            }

            //Check entering a Geofence
            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER && triggeringGeofences.size > 0) {
                if (intent!!.getBooleanExtra("notifyEnter", false)) {
                    val geofence = triggeringGeofences[0]
                    var title = intent.getStringExtra("notifyEnterStringTitle")
                    var description = intent.getStringExtra("notifyEnterStringDescription")
                    val geofenceValues = intent.getStringArrayListExtra("geofenceValues")
                    val geofenceKeys = intent.getStringArrayListExtra("geofenceKeys")
                    val index = geofenceKeys.indexOf(geofence.requestId)
                    try {
                        description = description.replace("[value]", geofenceValues[index])
                        title = title.replace("[value]", geofenceValues[index])
                    } catch (e: IndexOutOfBoundsException) {
                        Log.i(TAG, "No value set")
                    }

                    postNotification(
                            title,
                            description,
                            intent.getStringExtra("notifyChannelStringTitle"),
                            intent.getStringExtra("notifyChannelStringDescription"),
                            intent
                    )
                } else {
                    clearNotification()
                }
            }
            //Check exiting a Geofence
            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT && triggeringGeofences.size > 0) {

                if (intent!!.getBooleanExtra("notifyExit", false)) {
                    val geofence = triggeringGeofences[0]
                    var title = intent.getStringExtra("notifyExitStringTitle")
                    var description = intent.getStringExtra("notifyExitStringDescription")
                    val geofenceValues = intent.getStringArrayListExtra("geofenceValues")
                    val geofenceKeys = intent.getStringArrayListExtra("geofenceKeys")
                    val index = geofenceKeys.indexOf(geofence.requestId)
                    try {
                        description = description.replace("[value]", geofenceValues[index])
                        title = title.replace("[value]", geofenceValues[index])
                    } catch (e: IndexOutOfBoundsException) {
                        Log.i(TAG, "No value set")
                    }

                    postNotification(
                            title,
                            description,
                            intent.getStringExtra("notifyChannelStringTitle"),
                            intent.getStringExtra("notifyChannelStringDescription"),
                            intent
                    )
                } else {
                    clearNotification()
                }

            }

            Log.i(TAG, geofenceTransitionDetails)
            //RNSimpleNativeGeofencingModule.postNotification(TAG, geofenceTransitionDetails);
        } else {
            // Log the error.
            Log.e(TAG, "Invalid transition")
            /*
            postNotification(
                    "Error",
                    "Inside Handel",
                    intent.getStringExtra("notifyChannelStringTitle"),
                    intent.getStringExtra("notifyChannelStringDescription")
            );
            */
        }
    }

    /*
        Helpfunctions for logging
     */
    private fun getGeofenceTransitionDetails(
            geofenceTransition: Int,
            triggeringGeofences: List<Geofence>): String {

        val geofenceTransitionString = getTransitionString(geofenceTransition)

        // Get the Ids of each geofence that was triggered.
        val triggeringGeofencesIdsList = ArrayList<String>()
        for (geofence in triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.requestId)
        }
        val triggeringGeofencesIdsString = TextUtils.join(", ", triggeringGeofencesIdsList)

        return "$geofenceTransitionString: $triggeringGeofencesIdsString"
    }

    private fun getTransitionString(transitionType: Int): String {
        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> return "entered Geofence"
            Geofence.GEOFENCE_TRANSITION_EXIT -> return "exit Geofence"
            else -> return "unknown Transition"
        }
    }

    /*
       Notifications
    */
    private fun getNotificationBuilder(title: String,
                                       content: String,
                                       channelTitle: String,
                                       channelDescription: String,
                                       pIntent: Intent?
    ): NotificationCompat.Builder {
        //Onclick
        val intent = Intent(this.mContext, mainActivityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val contentIntent = PendingIntent.getActivity(this.mContext, 0, intent, 0)
        //Intent intent = new Intent(this.mContext, NotificationEventReceiver.class);
        //PendingIntent contentIntent = PendingIntent.getBroadcast(this.mContext, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        //Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentText(content)
                .setSmallIcon(this.applicationInfo.icon)
                .setContentIntent(contentIntent)
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

    fun postNotification(title: String,
                         content: String,
                         channelTitle: String,
                         channelDescription: String,
                         pIntent: Intent?
    ) {
        val notificationManager = NotificationManagerCompat.from(this)

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID,
                getNotificationBuilder(title, content, channelTitle, channelDescription, pIntent).build())
    }

    fun clearNotification() {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
    }

    companion object {
        private val TAG = "GeofenceService"
        private val NOTIFICATION_TAG = "GeofenceNotification"
        private val NOTIFICATION_ID = 101
        private val PREFERENCE_LAST_NOTIF_ID = "PREFERENCE_LAST_NOTIF_ID"
    }

    /*
    private static int getNextNotifId(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int id = sharedPreferences.getInt(PREFERENCE_LAST_NOTIF_ID, 1) + 1;
        if (id == Integer.MAX_VALUE) { id = 0; }
        sharedPreferences.edit().putInt(PREFERENCE_LAST_NOTIF_ID, id).apply();
        return id;
    }
    */
}
