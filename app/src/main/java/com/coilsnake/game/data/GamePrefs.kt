package com.coilsnake.game.data

import android.content.Context

class GamePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("coil_snake_prefs", Context.MODE_PRIVATE)

    var bestScore: Int
        get() = prefs.getInt("coil-best", 0)
        set(value) { prefs.edit().putInt("coil-best", value).apply() }

    var winsP1: Int
        get() = prefs.getInt("coil-wins-0", 0)
        set(value) { prefs.edit().putInt("coil-wins-0", value).apply() }

    var winsP2: Int
        get() = prefs.getInt("coil-wins-1", 0)
        set(value) { prefs.edit().putInt("coil-wins-1", value).apply() }

    var skinP1: Int
        get() = prefs.getInt("coil-skin-0", 0)
        set(value) { prefs.edit().putInt("coil-skin-0", value).apply() }

    var skinP2: Int
        get() = prefs.getInt("coil-skin-1", 1)
        set(value) { prefs.edit().putInt("coil-skin-1", value).apply() }

    var meIndex: Int
        get() = prefs.getInt("coil-me", 0).coerceIn(0, 1)
        set(value) { prefs.edit().putInt("coil-me", value.coerceIn(0, 1)).apply() }

    var lastHostIp: String
        get() = prefs.getString("coil-ip", "") ?: ""
        set(value) { prefs.edit().putString("coil-ip", value).apply() }
}
