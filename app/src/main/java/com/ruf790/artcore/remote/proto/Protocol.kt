package com.ruf790.artcore.remote.proto

import org.json.JSONArray
import org.json.JSONObject

/**
 * The ArtCore Music remote protocol, version 1 (music/remote.py in ArtCore Music 1.8.0).
 * Every message is a JSON object with "type". The phone says hello first (with the code or the token),
 * the PC answers welcome, then playlists and state; state again on every change, position once a second.
 */
const val PROTO = 1
const val DEFAULT_PORT = 47870
const val DISCOVERY_PORT = 47871
const val APP_KEY = "artcore-music"

data class Track(
    val id: Long,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val number: String = "",
    val duration: Double = 0.0,
    val stream: Boolean = false,
    val cover: String = "",
    val hasCover: Boolean? = null,
)

data class Group(
    val kind: String,              // album | radio | singles
    val title: String = "",
    val artist: String = "",
    val year: String = "",
    val tracks: List<Track> = emptyList(),
)

data class PlaylistInfo(
    val id: Int,
    val name: String,
    val kind: String = "",
    val count: Int = 0,
    val duration: Double = 0.0,
    val groups: List<Group> = emptyList(),
) {
    val tracks: List<Track> get() = groups.flatMap { it.tracks }
}

data class Playlists(val rev: Int = 0, val playing: Int = -1, val items: List<PlaylistInfo> = emptyList())

enum class Status { PLAY, PAUSE, STOP }

data class PlayerState(
    val status: Status = Status.STOP,
    val playlist: Int = -1,
    val track: Track? = null,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val volume: Int = 0,
    val muted: Boolean = false,
    val repeat: String = "off",    // off | all | one
    val shuffle: Boolean = false,
    val index: Int = 0,
    val count: Int = 0,
    val next: Track? = null,
    val format: String = "",
    val onAir: String = "",
)

/** A PC heard on the network (UDP beacon) or typed in by hand. */
data class Beacon(
    val name: String,
    val host: String,
    val port: Int,
    val version: String = "",
    val id: String = "",
)

sealed class Incoming {
    data class Welcome(val pc: String, val version: String, val id: String, val token: String?) : Incoming()
    data class Error(val code: String) : Incoming()
    data class PlaylistsMsg(val playlists: Playlists) : Incoming()
    data class StateMsg(val state: PlayerState) : Incoming()
    data class Position(val position: Double, val duration: Double, val status: Status) : Incoming()
    data class Cover(val id: String, val size: Int, val jpeg: ByteArray?) : Incoming()
    data object Unknown : Incoming()
}

/** Refusals that a new attempt with the same credentials would not fix. */
val FATAL_ERRORS = setOf("bad_code", "too_many", "bad_token", "forgotten", "proto", "code_needed")

object Protocol {

    fun parse(text: String): Incoming = try {
        val o = JSONObject(text)
        when (o.optString("type")) {
            "welcome" -> Incoming.Welcome(
                o.optString("pc"), o.optString("version"), o.optString("id"),
                o.optString("token").takeIf { it.isNotEmpty() },
            )
            "error" -> Incoming.Error(o.optString("error", "unknown"))
            "playlists" -> Incoming.PlaylistsMsg(playlists(o))
            "state" -> Incoming.StateMsg(state(o))
            "position" -> Incoming.Position(
                o.optDouble("position", 0.0), o.optDouble("duration", 0.0), status(o.optString("status")),
            )
            "cover" -> {
                val data = o.optString("data")
                Incoming.Cover(
                    o.optString("id"), o.optInt("size", 512),
                    if (data.isEmpty()) null else decodeBase64(data),
                )
            }
            else -> Incoming.Unknown
        }
    } catch (e: Exception) {
        Incoming.Unknown
    }

    fun status(s: String): Status = when (s) {
        "play" -> Status.PLAY
        "pause" -> Status.PAUSE
        else -> Status.STOP
    }

    fun track(o: JSONObject?): Track? {
        if (o == null) return null
        return Track(
            id = o.optLong("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            year = o.optString("year"),
            genre = o.optString("genre"),
            number = o.optString("n"),
            duration = o.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it },
            stream = o.optBoolean("stream"),
            cover = o.optString("cover"),
            hasCover = if (o.has("has_cover") && !o.isNull("has_cover")) o.optBoolean("has_cover") else null,
        )
    }

    fun playlists(o: JSONObject): Playlists {
        val items = mutableListOf<PlaylistInfo>()
        val arr = o.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val groups = mutableListOf<Group>()
            val ga = p.optJSONArray("groups") ?: JSONArray()
            for (g in 0 until ga.length()) {
                val go = ga.optJSONObject(g) ?: continue
                val ta = go.optJSONArray("tracks") ?: JSONArray()
                val tracks = (0 until ta.length()).mapNotNull { track(ta.optJSONObject(it)) }
                groups += Group(go.optString("kind"), go.optString("title"), go.optString("artist"),
                    go.optString("year"), tracks)
            }
            items += PlaylistInfo(p.optInt("id", i), p.optString("name"), p.optString("kind"),
                p.optInt("count"), p.optDouble("duration", 0.0), groups)
        }
        return Playlists(o.optInt("rev"), o.optInt("playing", -1), items)
    }

    fun state(o: JSONObject) = PlayerState(
        status = status(o.optString("status")),
        playlist = o.optInt("playlist", -1),
        track = track(o.optJSONObject("track")),
        position = o.optDouble("position", 0.0),
        duration = o.optDouble("duration", 0.0),
        volume = o.optInt("volume"),
        muted = o.optBoolean("muted"),
        repeat = o.optString("repeat", "off"),
        shuffle = o.optBoolean("shuffle"),
        index = o.optInt("index"),
        count = o.optInt("count"),
        next = track(o.optJSONObject("next")),
        format = o.optString("format"),
        onAir = o.optString("on_air"),
    )

    /** The UDP beacon: {"app": "artcore-music", "proto", "name", "version", "port", "id"}. */
    fun beacon(bytes: ByteArray, length: Int, host: String): Beacon? = try {
        val o = JSONObject(String(bytes, 0, length, Charsets.UTF_8))
        if (o.optString("app") != APP_KEY || o.optInt("proto") != PROTO) null
        else Beacon(o.optString("name").ifEmpty { host }, host, o.optInt("port", DEFAULT_PORT),
            o.optString("version"), o.optString("id"))
    } catch (e: Exception) {
        null
    }

    // ── phone → PC
    private fun msg(type: String, fill: JSONObject.() -> Unit = {}): String =
        JSONObject().apply { put("type", type); fill() }.toString()

    fun hello(device: String, deviceId: String, code: String?, token: String?) = msg("hello") {
        put("proto", PROTO)
        put("device", device)
        put("id", deviceId)
        if (!token.isNullOrEmpty()) put("token", token)
        if (!code.isNullOrEmpty()) put("code", code)
    }

    fun play(playlist: Int, track: Long) = msg("play") { put("playlist", playlist); put("track", track) }
    fun simple(type: String) = msg(type)
    fun seek(position: Double) = msg("seek") { put("position", position) }
    fun volume(value: Int) = msg("volume") { put("value", value.coerceIn(0, 100)) }
    fun volumeDelta(delta: Int) = msg("volume") { put("delta", delta) }
    fun repeat(mode: String) = msg("repeat") { put("mode", mode) }
    fun shuffle(on: Boolean) = msg("shuffle") { put("on", on) }
    fun getCover(id: String, size: Int) = msg("get_cover") { put("id", id); put("size", size) }

    fun nextRepeat(mode: String) = when (mode) {
        "off" -> "all"
        "all" -> "one"
        else -> "off"
    }

    private fun decodeBase64(s: String): ByteArray? = try {
        java.util.Base64.getDecoder().decode(s)
    } catch (e: IllegalArgumentException) {
        null
    }
}
