package com.ruf790.artcore.remote.data

import android.content.Context
import java.util.UUID

/** The PC this phone is paired with (its token replaces the code from the second connection on). */
data class SavedPc(val id: String, val name: String, val host: String, val port: Int, val token: String)

/** Small settings in SharedPreferences: the paired PC, this phone's id, the theme and the language. */
class Store(context: Context) {
    private val sp = context.getSharedPreferences("remote", Context.MODE_PRIVATE)

    val deviceId: String
        get() = sp.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("device_id", it).apply()
        }

    var pc: SavedPc?
        get() {
            val token = sp.getString("pc_token", null) ?: return null
            val host = sp.getString("pc_host", null) ?: return null
            return SavedPc(sp.getString("pc_id", "") ?: "", sp.getString("pc_name", host) ?: host, host,
                sp.getInt("pc_port", 47870), token)
        }
        set(v) {
            val e = sp.edit()
            if (v == null) {
                e.remove("pc_token").remove("pc_host").remove("pc_id").remove("pc_name").remove("pc_port")
            } else {
                e.putString("pc_id", v.id).putString("pc_name", v.name).putString("pc_host", v.host)
                    .putInt("pc_port", v.port).putString("pc_token", v.token)
            }
            e.apply()
        }

    /** "light" (default) | "dark". */
    var theme: String
        get() = sp.getString("theme", "light") ?: "light"
        set(v) = sp.edit().putString("theme", v).apply()

    /** "" = the phone's language | "ru" | "en". */
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) = sp.edit().putString("language", v).apply()

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notifications", false)
        set(v) = sp.edit().putBoolean("asked_notifications", v).apply()
}
