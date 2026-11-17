package com.ruf790.artcore.remote.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruf790.artcore.remote.R
import com.ruf790.artcore.remote.net.LinkState
import com.ruf790.artcore.remote.proto.PlayerState
import com.ruf790.artcore.remote.proto.Playlists

/** What every screen shows (from [com.ruf790.artcore.remote.Remote], or made up in the screenshot test). */
data class UiModel(
    val playlists: Playlists = Playlists(),
    val state: PlayerState = PlayerState(),
    val pcName: String = "",
    val link: LinkState = LinkState.Idle,
)

/** The buttons. */
interface Actions {
    fun play(playlist: Int, track: Long) {}
    fun playPause() {}
    fun stop() {}
    fun next() {}
    fun prev() {}
    fun seek(sec: Double) {}
    fun setVolume(v: Int) {}
    fun toggleMute() {}
    fun cycleRepeat() {}
    fun toggleShuffle() {}
    fun retry() {}
}

/** Album covers by id; null while it is not here (yet) or there is none. */
fun interface Covers {
    fun get(id: String, size: Int): ImageBitmap?
}

/** The strip behind the status bar, dark red (the app draws edge to edge). */
@Composable
fun StatusBarStrip() {
    Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(LocalPalette.current.statusBar))
}

@Composable
fun HeaderIcon(@DrawableRes icon: Int, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = desc, tint = tint)
    }
}

/** The red bar: ☰, the title, the actions on the right. */
@Composable
fun RedHeader(title: String, onMenu: () -> Unit, actions: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(p.red).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderIcon(R.drawable.ic_menu, stringResource(R.string.menu), onClick = onMenu)
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** A cover, or a placeholder: a note for files without a picture, the on-air sign for the radio. */
@Composable
fun CoverBox(image: ImageBitmap?, stream: Boolean, modifier: Modifier, corner: Dp = 6.dp, iconSize: Dp = 22.dp) {
    val p = LocalPalette.current
    Box(modifier.clip(RoundedCornerShape(corner)).background(if (p.dark) Color(0xFF242832) else Color(0xFFE9E9EE)),
        contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(if (stream) R.drawable.ic_antenna else R.drawable.ic_note), contentDescription = null,
                tint = p.text2.copy(alpha = 0.7f), modifier = Modifier.size(iconSize))
        }
    }
}

/** Three bouncing bars next to the playing track (still when paused). */
@Composable
fun Equalizer(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "eq")
    val bars = listOf(420, 300, 520).mapIndexed { i, ms ->
        if (playing) {
            tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "bar$i").value
        } else {
            listOf(0.45f, 0.8f, 0.6f)[i]
        }
    }
    Row(modifier.size(16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { h ->
            Box(Modifier.width(4.dp).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

/** The loudspeaker in the header: a slider for the volume of ArtCore Music. */
@Composable
fun VolumeButton(volume: Int, muted: Boolean, actions: Actions) {
    var open by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    Box {
        HeaderIcon(if (muted || volume == 0) R.drawable.ic_volume_off else R.drawable.ic_volume,
            stringResource(R.string.volume)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            var v by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
            Column(Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.volume), color = p.text, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    Text("${v.toInt()}%", color = p.text2)
                }
                Slider(
                    value = v, onValueChange = { v = it; actions.setVolume(it.toInt()) }, valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = p.red, activeTrackColor = p.red,
                        inactiveTrackColor = p.line),
                )
                TextButton(onClick = { actions.toggleMute() }) {
                    Icon(painterResource(if (muted) R.drawable.ic_volume else R.drawable.ic_volume_off), null, tint = p.red)
                    Text("  " + stringResource(R.string.mute), color = p.red)
                }
            }
        }
    }
}

/** "Connecting…" / "Connection lost…" under the header while the PC is not there. */
@Composable
fun LinkBanner(link: LinkState, pc: String, actions: Actions) {
    val text = when (link) {
        is LinkState.Online, is LinkState.Idle, is LinkState.Refused -> return
        is LinkState.Connecting -> if (link.attempt == 0) stringResource(R.string.connecting, pc)
        else stringResource(R.string.reconnecting, pc)
        is LinkState.Waiting -> stringResource(R.string.reconnecting, pc)
    }
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(p.bg2).padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = p.text2, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2)
        TextButton(onClick = { actions.retry() }) { Text(stringResource(R.string.retry), color = p.red) }
    }
}

/** A round button: the big red ⏯, or a plain one. */
@Composable
fun RoundButton(@DrawableRes icon: Int, desc: String, size: Dp, bg: Color, tint: Color, iconSize: Dp,
                modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(size).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = desc, tint = tint, modifier = Modifier.size(iconSize))
    }
}
