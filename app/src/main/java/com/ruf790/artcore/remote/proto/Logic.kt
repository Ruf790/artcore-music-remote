package com.ruf790.artcore.remote.proto

import java.util.Locale

/** 0:07, 3:41, 1:02:05. */
fun fmtTime(seconds: Double): String {
    val s = if (seconds.isNaN() || seconds < 0) 0L else seconds.toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

private fun norm(s: String) = s.lowercase(Locale.ROOT).replace('ё', 'е')

/** The search line: every word must be in the title, the artist or the album (ё = е, any case). */
fun matches(t: Track, query: String): Boolean {
    val words = norm(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val hay = norm(t.title + " " + t.artist + " " + t.album)
    return words.all { hay.contains(it) }
}

/** Groups with only the matching tracks; empty groups are dropped. */
fun filterGroups(groups: List<Group>, query: String): List<Group> {
    if (query.isBlank()) return groups
    return groups.mapNotNull { g ->
        val tr = g.tracks.filter { matches(it, query) }
        if (tr.isEmpty()) null else g.copy(tracks = tr)
    }
}

/** Every radio station of every playlist: (playlist id, station). */
fun radioStations(p: Playlists): List<Pair<Int, Track>> =
    p.items.flatMap { pl -> pl.tracks.filter { it.stream }.map { pl.id to it } }

/**
 * Where the track is now: the PC sends the position once a second; in between the phone moves it on by itself
 * (smooth seek bar), never past the end.
 */
fun currentPosition(base: Double, baseAtMs: Long, nowMs: Long, playing: Boolean, duration: Double): Double {
    if (!playing) return base
    val p = base + (nowMs - baseAtMs).coerceAtLeast(0) / 1000.0
    return if (duration > 0) p.coerceAtMost(duration) else p
}

/** Reconnecting after a lost connection: 1, 2, 4, 8 s, then every 10 s. */
object Backoff {
    const val MAX_MS = 10_000L
    fun delayMs(attempt: Int): Long = if (attempt <= 0) 0 else minOf(MAX_MS, 1000L shl minOf(attempt - 1, 4))

    /** Network trouble: try again. A refusal (wrong code, forgotten phone …): wait for the user. */
    fun shouldRetry(error: String?): Boolean = error == null || error !in FATAL_ERRORS
}

/** "artist · album (year)" under the title. */
fun subtitle(t: Track): String {
    val album = when {
        t.album.isNotEmpty() && t.year.isNotEmpty() -> "${t.album} (${t.year})"
        t.album.isNotEmpty() -> t.album
        else -> t.year
    }
    return listOf(t.artist, album).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Looks like an IPv4 address of the home network (for the manual entry). */
fun isLanAddress(host: String): Boolean {
    val parts = host.trim().split('.')
    if (parts.size != 4) return false
    val n = parts.map { it.toIntOrNull() ?: return false }
    if (n.any { it !in 0..255 }) return false
    return n[0] == 10 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31)
}
