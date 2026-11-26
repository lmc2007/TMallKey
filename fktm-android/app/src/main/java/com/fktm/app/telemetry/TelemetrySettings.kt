package com.fktm.app.telemetry

import android.content.Context

internal class TelemetrySettings(context: Context) {
    private val prefs = context.getSharedPreferences("fktm_telemetry", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()
}
