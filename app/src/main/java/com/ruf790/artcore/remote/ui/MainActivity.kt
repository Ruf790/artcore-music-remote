package com.ruf790.artcore.remote.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ruf790.artcore.remote.BuildConfig
import com.ruf790.artcore.remote.R
import com.ruf790.artcore.remote.Remote
import com.ruf790.artcore.remote.data.Store
import com.ruf790.artcore.remote.net.LinkState
import com.ruf790.artcore.remote.proto.radioStations
import com.ruf790.artcore.remote.remote
import com.ruf790.artcore.remote.service.RemoteService
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Page { CONNECT, TRACKS, NOW }

/** android.permission.ACCESS_LOCAL_NETWORK: needed from Android 17 (API 37) to talk to the PC on the Wi-Fi. */
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {

    private val r: Remote get() = remote
    private var localNetGranted = mutableStateOf(true)
    /** The app is on the screen: the PCs on the network are listened for only then. */
    private var visible = mutableStateOf(false)
    private val askLocalNet = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        localNetGranted.value = ok
        if (ok) {
            if (r.discovery.running) {                     // the socket opened without the permission: again
                r.discovery.stop()
                r.discovery.start()
            }
            r.retryNow()
        }
    }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** The language from the settings (or the phone's). */
    override fun attachBaseContext(base: Context) {
        val lang = Store(base).language
        if (lang.isEmpty()) {
            super.attachBaseContext(base)
            return
        }
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(lang))
        super.attachBaseContext(base.createConfigurationContext(conf))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (r.store.theme == "dark") SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        localNetGranted.value = hasLocalNetwork()
        if (r.paired) r.connectSaved()
        setContent {
            var dark by remember { mutableStateOf(r.store.theme == "dark") }
            RemoteTheme(dark) {
                App(r, onTheme = { d ->
                    r.store.theme = if (d) "dark" else "light"
                    dark = d
                    recreate()
                }, onLanguage = { lang ->
                    r.store.language = lang
                    recreate()
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visible.value = true
        r.retryNow()
        RemoteService.sync(this, r)
    }

    override fun onResume() {
        super.onResume()
        localNetGranted.value = hasLocalNetwork()
    }

    override fun onStop() {
        super.onStop()
        visible.value = false
    }

    private fun hasLocalNetwork(): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(this, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    fun requestLocalNetwork() = askLocalNet.launch(LOCAL_NETWORK)

    fun maybeAskNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !r.store.askedNotifications &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            r.store.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The phone's volume keys change the volume of ArtCore Music while the app is open and connected. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (r.link.value is LinkState.Online && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            r.volumeStep(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (r.link.value is LinkState.Online && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Composable
    private fun App(r: Remote, onTheme: (Boolean) -> Unit, onLanguage: (String) -> Unit) {
        val link by r.link.collectAsState()
        val playlists by r.playlists.collectAsState()
        val state by r.state.collectAsState()
        val pcName by r.pcName.collectAsState()
        val found by r.discovery.found.collectAsState()
        val model = UiModel(playlists, state, pcName, link)
        var page by remember { mutableStateOf(if (r.paired) Page.TRACKS else Page.CONNECT) }
        var tab by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("") }
        var focusSearch by remember { mutableStateOf(false) }
        var settings by remember { mutableStateOf(false) }
        // tablets, unfolded foldables: the tracks and the player side by side (Material "expanded" width)
        val wide = LocalConfiguration.current.screenWidthDp >= 840
        val pal = LocalPalette.current
        var about by remember { mutableStateOf(false) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val localNet by localNetGranted

        // paired just now → the tracks; the PC forgot this phone → pairing again
        LaunchedEffect(link) {
            if (link is LinkState.Online && page == Page.CONNECT) page = Page.TRACKS
            if (link is LinkState.Online) maybeAskNotifications()
            if (link is LinkState.Refused && page != Page.CONNECT && !r.paired) page = Page.CONNECT
            if (link is LinkState.Refused && (link as LinkState.Refused).code in setOf("bad_token", "forgotten")) {
                r.forget()
                page = Page.CONNECT
            }
        }
        // the found PCs are listened for on the pairing page, and while the saved PC is away (it may have a new address)
        val shown by visible
        LaunchedEffect(page, link is LinkState.Online, shown) {
            if (shown && (page == Page.CONNECT || (r.paired && link !is LinkState.Online))) r.discovery.start()
            else r.discovery.stop()
        }
        LaunchedEffect(playlists.items.size, playlists.playing) {
            if (tab != RADIO_TAB && playlists.items.none { it.id == tab }) {
                tab = playlists.playing.takeIf { p -> playlists.items.any { it.id == p } } ?: playlists.items.firstOrNull()?.id ?: 0
            }
        }
        BackHandler(enabled = (page == Page.NOW && !wide) || drawer.isOpen) {
            if (drawer.isOpen) scope.launch { drawer.close() } else page = Page.TRACKS
        }

        val covers = Covers { id, size -> r.cover(id, size)?.let { bitmapCache(it) } }
        val actions = remember { RemoteActions(r) }
        val openMenu: () -> Unit = { scope.launch { drawer.open() } }
        val exit: () -> Unit = {
            r.quit()
            finishAndRemoveTask()
        }
        val more = listOf(
            stringResource(R.string.choose_pc) to { page = Page.CONNECT },
            stringResource(R.string.settings) to { settings = true },
            stringResource(R.string.about) to { about = true },
            stringResource(R.string.exit) to exit,
        )

        ModalNavigationDrawer(drawerState = drawer, drawerContent = {
            DrawerMenu(model, onPlaylist = { id ->
                tab = id; query = ""; page = Page.TRACKS; scope.launch { drawer.close() }
            }, onRadio = {
                tab = RADIO_TAB; query = ""; page = Page.TRACKS; scope.launch { drawer.close() }
            }, onPc = {
                page = Page.CONNECT; scope.launch { drawer.close() }
            }, onSettings = {
                settings = true; scope.launch { drawer.close() }
            }, onAbout = {
                about = true; scope.launch { drawer.close() }
            }, onExit = exit)
        }) {
            if (wide && page != Page.CONNECT) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.42f).fillMaxHeight()) {
                        TracksScreen(
                            model, covers, r::position, actions, tab, { tab = it }, query, { query = it; focusSearch = false },
                            focusSearch, openMenu, {}, more, showMini = false,
                        )
                    }
                    Spacer(Modifier.width(1.dp).fillMaxHeight().background(pal.line))
                    Box(Modifier.weight(0.58f).fillMaxHeight()) {
                        NowPlayingScreen(model, covers, r::position, actions, openMenu,
                            onSearch = { focusSearch = true }, overflow = more)
                    }
                }
            } else when (page) {
                Page.CONNECT -> ConnectScreen(
                    found = found, link = link, busyPc = pcName,
                    onConnect = { b, code -> r.pair(b, code) }, onMenu = openMenu,
                    needsLocalNetwork = !localNet, onGrantLocalNetwork = { requestLocalNetwork() },
                )
                Page.TRACKS -> TracksScreen(
                    model, covers, r::position, actions, tab, { tab = it }, query, { query = it; focusSearch = false },
                    focusSearch, openMenu, { page = Page.NOW }, more,
                )
                Page.NOW -> NowPlayingScreen(model, covers, r::position, actions, openMenu,
                    onSearch = { focusSearch = true; page = Page.TRACKS }, overflow = more)
            }
        }

        if (about) AboutDialog(onDismiss = { about = false })

        if (settings) {
            SettingsDialog(
                dark = r.store.theme == "dark", language = r.store.language, paired = r.paired,
                onDismiss = { settings = false }, onTheme = { settings = false; onTheme(it) },
                onLanguage = { settings = false; onLanguage(it) },
                onDisconnect = { settings = false; r.forget(); page = Page.CONNECT },
            )
        }
    }

    private val bitmaps = HashMap<Bitmap, ImageBitmap>()

    private fun bitmapCache(b: Bitmap): ImageBitmap {
        if (bitmaps.size > 300) bitmaps.clear()
        return bitmaps.getOrPut(b) { b.asImageBitmap() }
    }
}

private class RemoteActions(private val r: Remote) : Actions {
    override fun play(playlist: Int, track: Long) = r.play(playlist, track)
    override fun playPause() = r.playPause()
    override fun stop() = r.stop()
    override fun next() = r.next()
    override fun prev() = r.prev()
    override fun seek(sec: Double) = r.seek(sec)
    override fun setVolume(v: Int) = r.setVolume(v)
    override fun toggleMute() = r.toggleMute()
    override fun cycleRepeat() = r.cycleRepeat()
    override fun toggleShuffle() = r.toggleShuffle()
    override fun retry() = r.retryNow()
}

/** ☰: the playlists, the radio, choosing the PC, the settings, exit. */
@Composable
fun DrawerMenu(model: UiModel, onPlaylist: (Int) -> Unit, onRadio: () -> Unit, onPc: () -> Unit, onSettings: () -> Unit,
               onAbout: () -> Unit, onExit: () -> Unit) {
    val p = LocalPalette.current
    ModalDrawerSheet(drawerContainerColor = p.bg) {
        Column(Modifier.fillMaxWidth().background(p.red).padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 18.dp)) {
            Icon(painterResource(R.drawable.ic_notes), null, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.app_name), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            val line = if (model.link is LinkState.Online) stringResource(R.string.connected_to, model.pcName)
            else stringResource(R.string.not_connected)
            Text(line, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.playlists))
            model.playlists.items.forEach { pl ->
                MenuRow(R.drawable.ic_queue, pl.name, stringResource(R.string.tracks_n, pl.count)) { onPlaylist(pl.id) }
            }
            if (radioStations(model.playlists).isNotEmpty() || model.playlists.items.isEmpty()) {
                MenuRow(R.drawable.ic_antenna, stringResource(R.string.radio), "") { onRadio() }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.line)
            MenuRow(R.drawable.ic_computer, stringResource(R.string.choose_pc), model.pcName) { onPc() }
            MenuRow(R.drawable.ic_tune, stringResource(R.string.settings), "") { onSettings() }
            MenuRow(R.drawable.ic_info, stringResource(R.string.about), "") { onAbout() }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.line)
            MenuRow(R.drawable.ic_close, stringResource(R.string.exit), "") { onExit() }
        }
    }
}

@Composable
private fun Section(text: String) {
    val p = LocalPalette.current
    Text(text.uppercase(), color = p.red, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun MenuRow(icon: Int, title: String, sub: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = p.text2, modifier = Modifier.size(22.dp))
        Text(title, color = p.text, fontSize = 15.sp, modifier = Modifier.padding(start = 18.dp).weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, color = p.text2, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun SettingsDialog(
    dark: Boolean, language: String, paired: Boolean,
    onDismiss: () -> Unit, onTheme: (Boolean) -> Unit, onLanguage: (String) -> Unit, onDisconnect: () -> Unit,
) {
    val p = LocalPalette.current
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.bg,
        title = { Text(stringResource(R.string.settings), color = p.text) },
        text = {
            Column {
                Text(stringResource(R.string.theme), color = p.red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Choice(stringResource(R.string.theme_light), !dark) { if (dark) onTheme(false) }
                Choice(stringResource(R.string.theme_dark), dark) { if (!dark) onTheme(true) }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.language), color = p.red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Choice(stringResource(R.string.lang_system), language.isEmpty()) { if (language.isNotEmpty()) onLanguage("") }
                Choice(stringResource(R.string.lang_ru), language == "ru") { if (language != "ru") onLanguage("ru") }
                Choice(stringResource(R.string.lang_en), language == "en") { if (language != "en") onLanguage("en") }
                if (paired) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = p.line)
                    if (confirm) {
                        Text(stringResource(R.string.disconnect_hint), color = p.text2, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp))
                    }
                    TextButton(onClick = { if (confirm) onDisconnect() else confirm = true }) {
                        Icon(painterResource(R.drawable.ic_link_off), null, tint = p.red, modifier = Modifier.size(20.dp))
                        Text("  " + stringResource(R.string.disconnect), color = p.red)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = p.red) } },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    val repo = "https://github.com/Ruf790/artcore-music-remote"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.bg,
        title = { Text(stringResource(R.string.about), color = p.text) },
        text = {
            Column {
                Text(stringResource(R.string.app_name), color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.version_n, BuildConfig.VERSION_NAME), color = p.text2, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.about_text), color = p.text, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.about_license), color = p.text2, fontSize = 13.sp)
                TextButton(onClick = { uri.openUri(repo) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.about_source), color = p.red)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = p.red) } },
    )
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = on, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = p.red))
        Text(text, color = p.text, fontSize = 15.sp)
    }
}
