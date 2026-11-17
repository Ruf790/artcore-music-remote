package com.ruf790.artcore.remote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import com.ruf790.artcore.remote.R
import com.ruf790.artcore.remote.Remote
import com.ruf790.artcore.remote.net.LinkState
import com.ruf790.artcore.remote.proto.PlayerState
import com.ruf790.artcore.remote.proto.Status
import com.ruf790.artcore.remote.remote
import com.ruf790.artcore.remote.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * While the remote is connected and something plays (or is paused) on the PC: the media notification and the
 * lock-screen controls with ⏮ ⏯ ⏹ ⏭ and the cover. The buttons go to ArtCore Music, nothing plays on the phone.
 */
class RemoteService : Service() {

    companion object {
        private const val CHANNEL = "playback"
        private const val NOTE_ID = 1
        const val ACTION_PREV = "com.ruf790.artcore.remote.PREV"
        const val ACTION_TOGGLE = "com.ruf790.artcore.remote.TOGGLE"
        const val ACTION_STOP = "com.ruf790.artcore.remote.STOP"
        const val ACTION_NEXT = "com.ruf790.artcore.remote.NEXT"

        @Volatile var running = false
            private set

        fun wanted(r: Remote): Boolean {
            val st = r.state.value
            return r.link.value is LinkState.Online && st.track != null && st.status != Status.STOP
        }

        /** Start the notification when it is wanted (stopping is up to the service itself). */
        fun sync(ctx: Context, r: Remote) {
            if (running || !wanted(r)) return
            try {
                ctx.startForegroundService(Intent(ctx, RemoteService::class.java))
            } catch (e: Exception) {
                // in the background Android may not allow it; it comes with the next change in the app
            }
        }

        fun stop(ctx: Context) {
            if (running) ctx.stopService(Intent(ctx, RemoteService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private var started = false
    private var shownCover: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        session = MediaSession(this, "ArtCoreMusicRemote").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = remote.playPause()
                override fun onPause() = remote.playPause()
                override fun onSkipToNext() = remote.next()
                override fun onSkipToPrevious() = remote.prev()
                override fun onStop() = remote.stop()
                override fun onSeekTo(pos: Long) = remote.seek(pos / 1000.0)
                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == ACTION_STOP) remote.stop()
                }
            })
            setSessionActivity(openApp())
            isActive = true
        }
        val r = remote
        r.onCover = { _, _, _ -> update(r.state.value) }
        scope.launch {
            combine(r.link, r.state) { _, st -> st }.collect { st ->
                if (!wanted(r)) {
                    stopSelf()
                } else {
                    update(st)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val n = build(remote.state.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTE_ID, n)
            }
        }
        when (intent?.action) {
            ACTION_PREV -> remote.prev()
            ACTION_TOGGLE -> remote.playPause()
            ACTION_STOP -> remote.stop()
            ACTION_NEXT -> remote.next()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        remote.onCover = null
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(name: String, icon: Int, label: Int): Notification.Action {
        val pi = PendingIntent.getService(this, name.hashCode(), Intent(this, RemoteService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), pi).build()
    }

    private fun cover(st: PlayerState): Bitmap? {
        val t = st.track ?: return null
        if (t.stream || t.cover.isEmpty()) return null
        return remote.cover(t.cover, 512)
    }

    private fun update(st: PlayerState) {
        val t = st.track ?: return
        val art = cover(st)
        val title = if (t.stream && st.onAir.isNotEmpty()) st.onAir else t.title
        val artist = if (t.stream) t.title else t.artist
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, if (t.stream) -1L else (st.duration * 1000).toLong())
            .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
            .build())
        val playing = st.status == Status.PLAY
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP or
                (if (t.stream) 0L else PlaybackState.ACTION_SEEK_TO))
            .addCustomAction(PlaybackState.CustomAction.Builder(ACTION_STOP, getString(R.string.stop), R.drawable.ic_stop).build())
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (remote.position() * 1000).toLong(), if (playing) 1f else 0f, SystemClock.elapsedRealtime())
            .build())
        val key = "${t.id}|${st.status}|$title|${art != null}"
        if (key == shownCover && started) return
        shownCover = key
        if (started) getSystemService(NotificationManager::class.java).notify(NOTE_ID, build(st))
    }

    private fun build(st: PlayerState): Notification {
        val t = st.track
        val playing = st.status == Status.PLAY
        val title = when {
            t == null -> getString(R.string.app_name)
            t.stream && st.onAir.isNotEmpty() -> st.onAir
            else -> t.title
        }
        val text = when {
            t == null -> ""
            t.stream -> t.title
            else -> t.artist
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(remote.pcName.value)
            .setLargeIcon(cover(st))
            .setContentIntent(openApp())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setColor(0xFFD7263D.toInt())
            .addAction(action(ACTION_PREV, R.drawable.ic_prev, R.string.prev))
            .addAction(action(ACTION_TOGGLE, if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) R.string.pause else R.string.play))
            .addAction(action(ACTION_STOP, R.drawable.ic_stop, R.string.stop))
            .addAction(action(ACTION_NEXT, R.drawable.ic_next, R.string.next))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 3))
            .build()
    }
}
