package com.simplegeofencing.reactnative

import android.content.Context
import android.content.SharedPreferences

class GeofenceStorage(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("geofencing", 0)

    fun addGeofence(requestId: String) {
        prefs.edit().putBoolean(requestId, true).apply()
    }

    fun removeGeofence(requestId: String) {
        prefs.edit().remove(requestId).apply()
    }

    fun getAllIds(): MutableSet<String> {
        return prefs.all.keys
    }

    fun removeAll() {
        prefs.edit().clear().apply()
    }
}