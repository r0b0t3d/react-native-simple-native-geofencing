package com.simplegeofencing.reactnative


import android.content.Intent
import android.os.Bundle
import android.util.Log

import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig


class MonitorUpdateService : HeadlessJsTaskService() {
    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        var extras = intent!!.extras
        if (extras == null) {
            extras = Bundle()
        }
        Log.i("MonitorUpdate: extras", "remainingTime: " + extras.getInt("remainingTime"))
        return HeadlessJsTaskConfig(
                "monitorGeofence",
                Arguments.fromBundle(extras),
                extras.getInt("duration", 50000000).toLong(), // timeout for the task
                true // optional: defines whether or not  the task is allowed in foreground. Default is false
        )

    }
}
