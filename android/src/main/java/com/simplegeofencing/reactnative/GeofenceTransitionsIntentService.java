package com.simplegeofencing.reactnative;


import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.ArrayList;
import java.util.List;

public class GeofenceTransitionsIntentService extends IntentService {
    private static final String TAG = "GeofenceService";
    private final String CHANNEL_ID = "channel_01";
    private static final String NOTIFICATION_TAG = "GeofenceNotification";
    private static final int NOTIFICATION_ID = 101;
    private NotificationChannel channel;
    private static final String PREFERENCE_LAST_NOTIF_ID = "PREFERENCE_LAST_NOTIF_ID";
    private Context mContext;

    public GeofenceTransitionsIntentService(){
        super(TAG);
}


    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Intent created");
    }


    protected void onHandleIntent(Intent intent) {
        this.mContext = this.getApplicationContext();
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            String errorMessage = "Error Code: " + String.valueOf(geofencingEvent.getErrorCode());
            Log.e(TAG, errorMessage);
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            // Get the transition details as a String.
            final String geofenceTransitionDetails = getGeofenceTransitionDetails(
                    geofenceTransition,
                    triggeringGeofences
            );

            //Check Monitor
            if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT){
                for (Geofence geofence : triggeringGeofences) {
                    if(geofence.getRequestId().equals("monitor")){
                        Log.i(TAG, "Outside Monitor");

                        //SEND CALLBACK
                        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
                        Intent customEvent= new Intent("outOfMonitorGeofence");
                        customEvent.putExtra("startTime", intent.getLongExtra("startTime", System.currentTimeMillis()));
                        customEvent.putExtra("duration", intent.getIntExtra("duration", 3000));
                        localBroadcastManager.sendBroadcast(customEvent);

                    }
                }
            }

            //Get (only) last triggered geofence which is not monitor
            ArrayList<Geofence> geofencesWithoutMonitor = new ArrayList<>();
            for (Geofence geofence : triggeringGeofences) {
                if(!geofence.getRequestId().equals("monitor")){
                    geofencesWithoutMonitor.add(geofence);
                }
            }

            //Check entering a Geofence
            if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER && geofencesWithoutMonitor.size() > 0){
                if(intent.getBooleanExtra("notifyEnter", false)){
                    Geofence geofence = geofencesWithoutMonitor.get(0);
                    String title = intent.getStringExtra("notifyEnterStringTitle");
                    String description = intent.getStringExtra("notifyEnterStringDescription");
                    ArrayList<String> geofenceValues = intent.getStringArrayListExtra("geofenceValues");
                    ArrayList<String> geofenceKeys = intent.getStringArrayListExtra("geofenceKeys");
                    int index = geofenceKeys.indexOf(geofence.getRequestId());
                    try {
                        description = description.replace("[value]", geofenceValues.get(index));
                        title = title.replace("[value]", geofenceValues.get(index));
                    } catch (IndexOutOfBoundsException e) {
                        Log.i(TAG, "No value set");
                    }

                    postNotification(
                            title,
                            description,
                            intent.getStringExtra("notifyChannelStringTitle"),
                            intent.getStringExtra("notifyChannelStringDescription"),
                            intent
                    );
                }else{
                    clearNotification();
                }
            }
            //Check exiting a Geofence
            if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT && geofencesWithoutMonitor.size() > 0){

                if (intent.getBooleanExtra("notifyExit", false)){
                    Geofence geofence = geofencesWithoutMonitor.get(0);
                    String title = intent.getStringExtra("notifyExitStringTitle");
                    String description = intent.getStringExtra("notifyExitStringDescription");
                    ArrayList<String> geofenceValues = intent.getStringArrayListExtra("geofenceValues");
                    ArrayList<String> geofenceKeys = intent.getStringArrayListExtra("geofenceKeys");
                    int index = geofenceKeys.indexOf(geofence.getRequestId());
                    try {
                        description = description.replace("[value]", geofenceValues.get(index));
                        title = title.replace("[value]", geofenceValues.get(index));
                    } catch (IndexOutOfBoundsException e) {
                        Log.i(TAG, "No value set");
                    }

                    postNotification(
                            title,
                            description,
                            intent.getStringExtra("notifyChannelStringTitle"),
                            intent.getStringExtra("notifyChannelStringDescription"),
                            intent
                    );
                }else{
                    clearNotification();
                }

            }

            Log.i(TAG, geofenceTransitionDetails);
            //RNSimpleNativeGeofencingModule.postNotification(TAG, geofenceTransitionDetails);
        } else {
            // Log the error.
            Log.e(TAG, "Invalid transition");
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
    private String getGeofenceTransitionDetails(
            int geofenceTransition,
            List<Geofence> triggeringGeofences) {

        String geofenceTransitionString = getTransitionString(geofenceTransition);

        // Get the Ids of each geofence that was triggered.
        ArrayList<String> triggeringGeofencesIdsList = new ArrayList<>();
        for (Geofence geofence : triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.getRequestId());
        }
        String triggeringGeofencesIdsString = TextUtils.join(", ",  triggeringGeofencesIdsList);

        return geofenceTransitionString + ": " + triggeringGeofencesIdsString;
    }
    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return "entered Geofence";
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return "exit Geofence";
            default:
                return "unknown Transition";
        }
    }

    /*
       Notifications
    */
    private NotificationCompat.Builder getNotificationBuilder(String title,
                                                              String content,
                                                              String channelTitle,
                                                              String channelDescription,
                                                              Intent pIntent
    ) {
        //Onclick
        Intent intent = new Intent(this.mContext, getMainActivityClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this.mContext, 0, intent, 0);
        //Intent intent = new Intent(this.mContext, NotificationEventReceiver.class);
        //PendingIntent contentIntent = PendingIntent.getBroadcast(this.mContext, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        //Build notification
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setContentText(content)
                .setSmallIcon(this.getApplicationInfo().icon)
                .setContentIntent(contentIntent);
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel==null) {
            CharSequence name = channelTitle;
            String description = channelDescription;
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = this.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            notification.setChannelId(CHANNEL_ID);
        }
        return notification;
    }

    public void postNotification(String title,
                                 String content,
                                 String channelTitle,
                                 String channelDescription,
                                 Intent pIntent
    ){
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID,
                getNotificationBuilder(title, content, channelTitle, channelDescription, pIntent).build());
    }
    public void clearNotification(){
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
    }

    public Class getMainActivityClass() {
        String packageName = getApplicationContext().getPackageName();
        Intent launchIntent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
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
