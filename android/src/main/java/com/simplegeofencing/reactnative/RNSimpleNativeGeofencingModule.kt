package com.simplegeofencing.reactnative

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log

import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.*
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener

import java.util.ArrayList

//import android.support.v4.app.NotificationCompat;

class RNSimpleNativeGeofencingModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val mGeofencingClient: GeofencingClient = LocationServices.getGeofencingClient(reactContext)
    private val mGeofenceList: MutableList<Geofence>
    private var mGeofencePendingIntent: PendingIntent? = null
    private val TAG = "SNGeofencing"
    private val CHANNEL_ID = "channel_01"
    private val channel: NotificationChannel? = null
    private val notifyChannelString = arrayOfNulls<String>(2)
    private var notifyStart = false
    private var notifyStop = false
    private var notifyEnter = false
    private var notifyExit = false
    private val notifyStartString = arrayOfNulls<String>(2)
    private val notifyStopString = arrayOfNulls<String>(2)
    private val notifyEnterString = arrayOfNulls<String>(2)
    private val notifyExitString = arrayOfNulls<String>(2)
    private var mStartTime: Long? = null
    private var mDuration: Int = 0
    private val mLocalBroadcastReceiver: LocalBroadcastReceiver
    private val mLocalBroadcastManager: LocalBroadcastManager
    private val geofenceValues: ArrayList<String>
    private val geofenceKeys: ArrayList<String>

    /*
    Helpfunctions
   */

    private val geofencingRequest: GeofencingRequest
        get() {
            val builder = GeofencingRequest.Builder()
            builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            builder.addGeofences(mGeofenceList)
            return builder.build()
        }

    private// Reuse the PendingIntent if we already have it.
    // Add notification data
    // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
    // calling addGeofences() and removeGeofences().
    val geofencePendingIntent: PendingIntent?
        get() {
            if (mGeofencePendingIntent != null) {
                return mGeofencePendingIntent
            }
            val intent = Intent(this.currentActivity, GeofenceTransitionsIntentService::class.java)
            intent.putExtra("notifyEnter", notifyEnter)
            if (notifyEnter == true) {
                intent.putExtra("notifyEnterStringTitle", notifyEnterString[0])
                intent.putExtra("notifyEnterStringDescription", notifyEnterString[1])
            } else {
                intent.putExtra("notifyEnterStringTitle", "")
                intent.putExtra("notifyEnterStringDescription", "")
            }
            intent.putExtra("notifyExit", notifyExit)
            if (notifyExit == true) {
                intent.putExtra("notifyExitStringTitle", notifyExitString[0])
                intent.putExtra("notifyExitStringDescription", notifyExitString[1])
            } else {
                intent.putExtra("notifyExitStringTitle", "")
                intent.putExtra("notifyExitStringDescription", "")
            }
            intent.putExtra("notifyChannelStringTitle", notifyChannelString[0])
            intent.putExtra("notifyChannelStringDescription", notifyChannelString[1])
            intent.putExtra("startTime", System.currentTimeMillis())
            intent.putExtra("duration", mDuration)
            intent.putStringArrayListExtra("geofenceKeys", geofenceKeys)
            intent.putStringArrayListExtra("geofenceValues", geofenceValues)
            mGeofencePendingIntent = PendingIntent.getService(reactContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            return mGeofencePendingIntent
        }


    init {
        this.mGeofenceList = ArrayList()
        this.mLocalBroadcastReceiver = LocalBroadcastReceiver()
        this.mLocalBroadcastManager = LocalBroadcastManager.getInstance(reactContext)
        this.notifyChannelString[0] = "Title"
        this.notifyChannelString[1] = "Description"
        this.geofenceKeys = ArrayList()
        this.geofenceValues = ArrayList()
    }

    override fun getName(): String {
        return "RNSimpleNativeGeofencing"
    }

    /*
    React Native functions
   */

    @ReactMethod
    fun initNotification(pText: ReadableMap) {
        val pChannel = pText.getMap("channel")
        notifyChannelString[0] = pChannel!!.getString("title")
        notifyChannelString[1] = pChannel.getString("description")
        val pStart = pText.getMap("start")
        if (pStart!!.getBoolean("notify")) {
            notifyStart = true
            notifyStartString[0] = pStart.getString("title")
            notifyStartString[1] = pStart.getString("description")
        }
        val pStop = pText.getMap("stop")
        if (pStop!!.getBoolean("notify")) {
            notifyStop = true
            notifyStopString[0] = pStop.getString("title")
            notifyStopString[1] = pStop.getString("description")
        }
        val pEnter = pText.getMap("enter")
        if (pEnter!!.getBoolean("notify")) {
            notifyEnter = true
            notifyEnterString[0] = pEnter.getString("title")
            notifyEnterString[1] = pEnter.getString("description")
        }
        val pExit = pText.getMap("exit")
        if (pExit!!.getBoolean("notify")) {
            notifyExit = true
            notifyExitString[0] = pExit.getString("title")
            notifyExitString[1] = pExit.getString("description")
        }
    }

    @ReactMethod
    fun removeAllGeofences(promise: Promise) {
        mGeofenceList.clear()
        stopMonitoring(promise)
    }

    @ReactMethod
    fun removeGeofence(key: String) {
        var index = -1
        for (i in mGeofenceList.indices) {
            if (mGeofenceList[i].requestId === key) {
                index = i
            }
        }
        if (index != -1) {
            mGeofenceList.removeAt(index)
            //Remove from Client as well
            val item = ArrayList<String>()
            item.add(key)
            mGeofencingClient.removeGeofences(item)
        }
    }


    @ReactMethod
    fun addGeofences(
            geofenceArray: ReadableArray,
            duration: Int,
            promise: Promise) {
        //Add geohashes
        for (i in 0 until geofenceArray.size()) {
            val geofence = geofenceArray.getMap(i)
            buildGeofence(geofence, duration)
        }
        //Start Monitoring
        startMonitoring(promise)
    }

    @ReactMethod
    fun updateGeofences(
            geofenceArray: ReadableArray,
            duration: Int
    ) {
        mGeofenceList.clear()
        silentStopMonitoring()
        //Add geohashes
        for (i in 0 until geofenceArray.size()) {
            val geofence = geofenceArray.getMap(i)
            buildGeofence(geofence, duration)
        }
        silentStartMonitoring()
    }

    fun buildGeofence(geofenceObject: ReadableMap?, duration: Int) {
        mGeofenceList.add(Geofence.Builder()
                .setRequestId(geofenceObject!!.getString("key"))
                .setCircularRegion(
                        geofenceObject.getDouble("latitude"),
                        geofenceObject.getDouble("longitude"),
                        geofenceObject.getInt("radius").toFloat()
                )
                .setExpirationDuration(duration.toLong())
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness(5 * 60 * 1000)
                .build())

        if (geofenceObject.hasKey("value")) {
            geofenceKeys.add(geofenceObject.getString("key") ?: "")
            geofenceValues.add(geofenceObject.getString("value") ?: "")
        }
        mDuration = duration
    }

    @ReactMethod
    fun addGeofence(geofenceObject: ReadableMap, duration: Int, promise: Promise) {
        buildGeofence(geofenceObject, duration)
        Log.i(TAG, "Added geofence: Lat " + geofenceObject.getDouble("latitude") + " Long " + geofenceObject.getDouble("longitude"))
        startMonitoring(promise)
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    fun startMonitoring(promise: Promise) {
        //Context removed by Listeners
        //if (ContextCompat.checkSelfPermission(this.reactContext, Manifest.permission.ACCESS_FINE_LOCATION)
        //        != PackageManager.PERMISSION_GRANTED){
        mGeofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Added Geofences")
                    notifyNow("start")
                    mStartTime = System.currentTimeMillis()
                    mLocalBroadcastManager.registerReceiver(
                            mLocalBroadcastReceiver, IntentFilter("monitorGeofence"))

                    //Launch service to notify after timeout
                    if (notifyStop == true) {
                        val notificationIntent = Intent(reactContext, ShowTimeoutNotification::class.java)
                        notificationIntent.putExtra("notifyChannelStringTitle", notifyChannelString[0])
                        notificationIntent.putExtra("notifyChannelStringDescription", notifyChannelString[1])
                        notificationIntent.putExtra("notifyStringTitle", notifyStopString[0])
                        notificationIntent.putExtra("notifyStringDescription", notifyStopString[1])

                        val contentIntent = PendingIntent.getService(reactContext, 0, notificationIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT)

                        val am = reactContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        am.cancel(contentIntent)
                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mDuration, contentIntent)
                        } else {
                            am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mDuration, contentIntent)
                        }

                    }
                    promise.resolve(true)
                }
                .addOnFailureListener { e ->
                    promise.reject(e)
                    Log.e(TAG, "Adding Geofences: " + e.message)
                }
        //}

    }

    @SuppressLint("MissingPermission")
    fun silentStartMonitoring() {
        //Context removed by Listeners
        //if (ContextCompat.checkSelfPermission(this.reactContext, Manifest.permission.ACCESS_FINE_LOCATION)
        //        != PackageManager.PERMISSION_GRANTED){
        mGeofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener { Log.i(TAG, "Updated Geofences") }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Updating Geofences: " + e.message)
                    e.printStackTrace()
                }
        //}

    }

    @ReactMethod
    fun stopMonitoring(promise: Promise) {
        //Context removed by Listeners
        mGeofencingClient.removeGeofences(geofencePendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Removed Geofences")

                    notifyNow("stop")
                    mLocalBroadcastManager.unregisterReceiver(mLocalBroadcastReceiver)
                    if (notifyStop == true) {
                        val notificationIntent = Intent(reactContext, ShowTimeoutNotification::class.java)
                        notificationIntent.putExtra("notifyChannelStringTitle", notifyChannelString[0])
                        notificationIntent.putExtra("notifyChannelStringDescription", notifyChannelString[1])
                        notificationIntent.putExtra("notifyStringTitle", notifyStopString[0])
                        notificationIntent.putExtra("notifyStringDescription", notifyStopString[1])

                        val contentIntent = PendingIntent.getService(reactContext, 0, notificationIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT)

                        val am = reactContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        am.cancel(contentIntent)
                    }
                    promise.resolve(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Removing Geofences: " + e.message)
                    promise.reject(e);
                }
    }

    fun silentStopMonitoring() {
        //Context removed by Listeners
        mGeofencingClient.removeGeofences(geofencePendingIntent)
                .addOnSuccessListener { Log.i(TAG, "Removed Geofences") }
                .addOnFailureListener { e -> Log.e(TAG, "Removing Geofences: " + e.message) }
    }

    @ReactMethod
    fun testNotify() {
        Log.i(TAG, "TestNotify Callback worked")
        postNotification("TestNotify", "Callback worked", false)
    }

    /*
         Notifications
      */
    private fun notifyNow(action: String) {
        if (action === "start") {
            if (notifyStart == true) {
                postNotification(notifyStartString[0] ?: "", notifyStartString[1] ?: "", true)
            }
        }
        if (action === "stop") {
            if (notifyStop == true) {
                postNotification(notifyStopString[0] ?: "", notifyStopString[1] ?: "", false)
            }
        }
    }

    private fun getNotificationBuilder(title: String, content: String): NotificationCompat.Builder {
        //Onclick
        val intent = Intent(reactApplicationContext, this.currentActivity!!.javaClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val contentIntent = PendingIntent.getActivity(this.reactContext, 0, intent, 0)
        //Intent intent = new Intent(this.getReactApplicationContext(), NotificationEventReceiver.class);
        //PendingIntent contentIntent = PendingIntent.getBroadcast(this.getReactApplicationContext(), NOTIFICATION_ID_STOP, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        //Build notification
        val notification = NotificationCompat.Builder(this.reactContext, CHANNEL_ID)
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentText(content)
                .setSmallIcon(reactApplicationContext.applicationInfo.icon)
                .setContentIntent(contentIntent)
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel == null) {
            val name = notifyChannelString[0]
            val description = notifyChannelString[1]
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = this.reactContext.getSystemService(NotificationManager::class.java)
            notificationManager!!.createNotificationChannel(channel)
            notification.setChannelId(CHANNEL_ID)
        }
        return notification
    }

    fun postNotification(title: String, content: String, start: Boolean) {
        val notificationManager = NotificationManagerCompat.from(this.reactContext)

        var notifyID = NOTIFICATION_ID_STOP
        if (start == true) {
            notifyID = NOTIFICATION_ID_START
        }
        notificationManager.notify(NOTIFICATION_TAG, notifyID, getNotificationBuilder(title, content).build())
    }

    /*
    private static int getNextNotifId(Context context) {
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
      int id = sharedPreferences.getInt(PREFERENCE_LAST_NOTIF_ID, 0) + 1;
      if (id == Integer.MAX_VALUE) { id = 0; }
      sharedPreferences.edit().putInt(PREFERENCE_LAST_NOTIF_ID, id).apply();
      return id;
    }
    */
    //BroadcastReceiver
    inner class LocalBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val currentTime = System.currentTimeMillis()
            val duration = intent.getIntExtra("duration", 3000)
            val startTime = intent.getLongExtra("startTime", System.currentTimeMillis())
            val remainingTime = 0//toIntExact(duration-(currentTime-startTime));
            val id = intent.getStringExtra("id")
            val event = intent.getStringExtra("event")
            Log.i(TAG, "Broadcast received")
            Log.i(TAG, "RemainingTimeReceiver: $remainingTime")
            val serviceIntent = Intent(context, MonitorUpdateService::class.java)
            serviceIntent.putExtra("remainingTime", remainingTime)
            serviceIntent.putExtra("duration", duration)
            serviceIntent.putExtra("startTime", startTime)
            serviceIntent.putExtra("id", id)
            serviceIntent.putExtra("event", event)
            context.startService(serviceIntent)
            HeadlessJsTaskService.acquireWakeLockNow(context)

            //reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            //        .emit("monitorGeofence", remainingTime);

        }
    }

    companion object {
        private val PREFERENCE_LAST_NOTIF_ID = "PREFERENCE_LAST_NOTIF_ID"
        private val NOTIFICATION_TAG = "GeofenceNotification"
        private val NOTIFICATION_ID_START = 1
        private val NOTIFICATION_ID_STOP = 150
    }

}