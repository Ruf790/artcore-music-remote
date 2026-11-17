package com.ruf790.artcore.remote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ruf790.artcore.remote.net.LinkState
import com.ruf790.artcore.remote.proto.Beacon
import com.ruf790.artcore.remote.proto.Group
import com.ruf790.artcore.remote.proto.PlayerState
import com.ruf790.artcore.remote.proto.PlaylistInfo
import com.ruf790.artcore.remote.proto.Playlists
import com.ruf790.artcore.remote.proto.Status
import com.ruf790.artcore.remote.proto.Track
import com.ruf790.artcore.remote.ui.Actions
import com.ruf790.artcore.remote.ui.ConnectScreen
import com.ruf790.artcore.remote.ui.Covers
import com.ruf790.artcore.remote.ui.NowPlayingScreen
import com.ruf790.artcore.remote.ui.RemoteTheme
import com.ruf790.artcore.remote.ui.TracksScreen
import com.ruf790.artcore.remote.ui.UiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Screenshots of the three screens in the light and the dark theme (CI pulls them from the phone).
 * The data is the test media of ArtCore Music (tests/make_media.py) — it lives here, in the test, not in the app.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun cover(c0: Int, c1: Int): ImageBitmap {
        val b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 512f, 512f, c0, c1, Shader.TileMode.CLAMP) }
        Canvas(b).drawRect(0f, 0f, 512f, 512f, p)
        return b.asImageBitmap()
    }

    private val covers: Covers by lazy {
        val blue = cover(0xFF0D6EFD.toInt(), 0xFF6F42C1.toInt())
        Covers { id, _ -> if (id == "test-album" || id == "surround") blue else null }
    }

    private val album = listOf(
        Track(11, "Первый трек", "ArtCore Band", "Test Album", "2024", "Electronic", "1", 221.0, cover = "test-album"),
        Track(12, "Second Song", "ArtCore Band", "Test Album", "2024", "Electronic", "2", 302.0, cover = "test-album"),
        Track(13, "Третий", "ArtCore Band", "Test Album", "2024", "Electronic", "3", 254.0, cover = "test-album"),
    )
    private val surround = listOf(
        Track(21, "FLAC 5.1", "Surround Test", "Multichannel", number = "1", duration = 302.0, cover = "surround"),
        Track(22, "02 ac3", duration = 247.0, cover = "surround"),
        Track(23, "03 dts", duration = 268.0, cover = "surround"),
        Track(24, "05 flac71", duration = 199.0, cover = "surround"),
    )
    private val cue = listOf(
        Track(31, "Группа крови", "Кино", "Альбом образом", "1999", "Rock", "1", 284.0, cover = "cue"),
        Track(32, "Звезда по имени Солнце", "Кино", "Альбом образом", "1999", "Rock", "2", 275.0, cover = "cue"),
        Track(33, "Кукушка", "Виктор Цой", "Альбом образом", "1999", "Rock", "3", 395.0, cover = "cue"),
    )
    private val radio = Track(41, "Test FM", genre = "Electronic", stream = true)

    private val playlists = Playlists(3, 0, listOf(
        PlaylistInfo(0, "Плейлист", count = 11, groups = listOf(
            Group("album", "Test Album", "ArtCore Band", "2024", album),
            Group("album", "Multichannel", "Surround Test", "", surround),
            Group("album", "Альбом образом", "Кино", "1999", cue),
            Group("radio", tracks = listOf(radio)),
        )),
        PlaylistInfo(1, "Misc", count = 3, groups = listOf(Group("singles", tracks = listOf(
            Track(51, "Opus tone", "Misc", duration = 3.0, cover = "o"),
            Track(52, "AAC tone", duration = 3.0, cover = "a"),
            Track(53, "plain", duration = 3.0, cover = "p"),
        )))),
        PlaylistInfo(2, "★ Избранное", kind = "favorites", count = 0),
    ))

    private fun model(track: Track, next: Track?, index: Int, format: String) = UiModel(
        playlists = playlists,
        state = PlayerState(Status.PLAY, 0, track, 1.6, track.duration, 80, false, "all", false, index, 11, next, format),
        pcName = "RUFFNECK-PC",
        link = LinkState.Online("RUFFNECK-PC"),
    )

    private fun shoot(screen: String, dark: Boolean, ru: Boolean, content: @Composable () -> Unit) {
        val name = screen + (if (dark) "_dark" else "_light") + (if (ru) "_ru" else "_en")
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge(
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            )
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val ctx = LocalContext.current
            val conf = Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(if (ru) "ru" else "en")) }
            val local = remember { ctx.createConfigurationContext(conf) }
            CompositionLocalProvider(LocalContext provides local, LocalConfiguration provides conf) {
                RemoteTheme(dark) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        Thread.sleep(800)
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val none = object : Actions {}

    private fun tracks(dark: Boolean, ru: Boolean) = shoot("tracks", dark, ru) {
        TracksScreen(model(album[1], album[2], 2, "MP3 · 192 kbps · 48 kHz"), covers, { 114.0 }, none, 0, {}, "", {}, false,
            {}, {}, listOf("PC" to {}))
    }

    private fun now(dark: Boolean, ru: Boolean) = shoot("now_playing", dark, ru) {
        val m = model(surround[0], surround[1], 4, "FLAC · 24 bit · 96 kHz · 5.1")
        NowPlayingScreen(m, covers, { 114.0 }, none,
            {}, {}, listOf("PC" to {}))
    }

    private fun connect(dark: Boolean, ru: Boolean) = shoot("connect", dark, ru) {
        ConnectScreen(
            found = listOf(Beacon("RUFFNECK-PC", "192.168.1.10", 47870, "1.8.0", "a"),
                Beacon("STUDIO", "192.168.1.24", 47870, "1.8.0", "b")),
            link = LinkState.Idle, busyPc = "", onConnect = { _, _ -> }, onMenu = {},
        )
    }

    @Test fun tracksLightRu() = tracks(false, true)
    @Test fun tracksDarkRu() = tracks(true, true)
    @Test fun nowPlayingLightRu() = now(false, true)
    @Test fun nowPlayingDarkRu() = now(true, true)
    @Test fun connectLightRu() = connect(false, true)
    @Test fun connectDarkRu() = connect(true, true)
    @Test fun tracksLightEn() = tracks(false, false)
    @Test fun tracksDarkEn() = tracks(true, false)
    @Test fun nowPlayingLightEn() = now(false, false)
    @Test fun nowPlayingDarkEn() = now(true, false)
    @Test fun connectLightEn() = connect(false, false)
    @Test fun connectDarkEn() = connect(true, false)
}
