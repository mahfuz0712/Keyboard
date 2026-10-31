package com.mux.android.keyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings shared by the settings app (Compose) and the keyboard service.
 * Both run in the same process, so the keyboard always sees the latest values.
 */
object KeyPrefs {
    private var prefs: SharedPreferences? = null

    var amber by mutableStateOf(false)
        private set
    var haptic by mutableStateOf(true)
        private set
    var sound by mutableStateOf(false)
        private set
    var numberRow by mutableStateOf(true)
        private set
    var autoCap by mutableStateOf(true)
        private set
    var doubleSpace by mutableStateOf(true)
        private set
    var preview by mutableStateOf(true)
        private set
    var bangla by mutableStateOf(true)
        private set
    var keyHeight by mutableIntStateOf(48)   // dp
        private set
    var longPress by mutableIntStateOf(350)  // ms
        private set

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
        prefs = p
        amber = p.getBoolean("amber", false)
        haptic = p.getBoolean("haptic", true)
        sound = p.getBoolean("sound", false)
        numberRow = p.getBoolean("numberRow", true)
        autoCap = p.getBoolean("autoCap", true)
        doubleSpace = p.getBoolean("doubleSpace", true)
        preview = p.getBoolean("preview", true)
        bangla = p.getBoolean("bangla", true)
        keyHeight = p.getInt("keyHeight", 48).coerceIn(40, 68)
        longPress = p.getInt("longPress", 350).coerceIn(200, 700)
    }

    private fun save(key: String, v: Boolean) {
        prefs?.edit()?.putBoolean(key, v)?.apply()
    }

    fun flip(key: String) {
        when (key) {
            "amber" -> { amber = !amber; save(key, amber) }
            "haptic" -> { haptic = !haptic; save(key, haptic) }
            "sound" -> { sound = !sound; save(key, sound) }
            "numberRow" -> { numberRow = !numberRow; save(key, numberRow) }
            "autoCap" -> { autoCap = !autoCap; save(key, autoCap) }
            "doubleSpace" -> { doubleSpace = !doubleSpace; save(key, doubleSpace) }
            "preview" -> { preview = !preview; save(key, preview) }
            "bangla" -> { bangla = !bangla; save(key, bangla) }
        }
    }

    fun nudgeHeight(delta: Int) {
        keyHeight = (keyHeight + delta).coerceIn(40, 68)
        prefs?.edit()?.putInt("keyHeight", keyHeight)?.apply()
    }

    fun nudgeLongPress(delta: Int) {
        longPress = (longPress + delta).coerceIn(200, 700)
        prefs?.edit()?.putInt("longPress", longPress)?.apply()
    }
}
