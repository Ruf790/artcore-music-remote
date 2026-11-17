package com.ruf790.artcore.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruf790.artcore.remote.R
import com.ruf790.artcore.remote.proto.Status
import com.ruf790.artcore.remote.proto.fmtTime
import com.ruf790.artcore.remote.proto.subtitle
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    model: UiModel,
    covers: Covers,
    position: () -> Double,
    actions: Actions,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    overflow: List<Pair<String, () -> Unit>>,
) {
    val p = LocalPalette.current
    val st = model.state
    val t = st.track
    val pos by produceState(position(), st) {
        while (true) {
            value = position()
            delay(250)
        }
    }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        StatusBarStrip()
        RedHeader(stringResource(R.string.now_playing), onMenu) {
            HeaderIcon(R.drawable.ic_search, stringResource(R.string.search), onClick = onSearch)
            VolumeButton(st.volume, st.muted, actions)
            Overflow(overflow)
        }
        LinkBanner(model.link, model.pcName, actions)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            val title = when {
                t == null -> stringResource(R.string.nothing_playing)
                t.stream && st.onAir.isNotEmpty() -> st.onAir
                else -> t.title
            }
            Text(title, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = when {
                t == null -> ""
                t.stream -> t.title
                else -> subtitle(t)
            }
            Text(sub, color = p.text2, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(18.dp))

            // the cover on a light grey card, the format in its corner
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                Box(Modifier.size(side).clip(RoundedCornerShape(18.dp)).background(p.coverBg), contentAlignment = Alignment.Center) {
                    val img = t?.let { if (it.stream) null else covers.get(it.cover, 512) }
                    if (img != null) {
                        CoverBox(img, false, Modifier.fillMaxSize().padding(14.dp), corner = 10.dp)
                    } else {
                        CoverBox(null, true, Modifier.fillMaxSize(0.5f).aspectRatio(1f), corner = 60.dp, iconSize = 64.dp)
                    }
                    if (st.format.isNotEmpty()) {
                        Text(st.format, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000))
                                .padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // RUFFNECK-PC · playlist · 2 / 248
            val plName = model.playlists.items.firstOrNull { it.id == st.playlist }?.name ?: ""
            val where = listOf(model.pcName, plName, if (st.index > 0) "${st.index} / ${st.count}" else "")
                .filter { it.isNotEmpty() }.joinToString(" · ")
            Text(where, color = p.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))

            // the seek bar: the thumb follows the finger, the PC seeks when it is let go
            var drag by remember { mutableStateOf<Float?>(null) }
            val live = t?.stream == true
            val dur = st.duration
            val shown = drag ?: if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f
            Slider(
                value = if (live) 1f else shown,
                onValueChange = { if (!live && dur > 0) drag = it },
                onValueChangeFinished = { drag?.let { actions.seek(it * dur) }; drag = null },
                enabled = t != null && !live && dur > 0,
                colors = SliderDefaults.colors(thumbColor = p.red, activeTrackColor = p.red, inactiveTrackColor = p.line,
                    disabledThumbColor = if (live) p.red else p.line, disabledActiveTrackColor = if (live) p.red else p.line,
                    disabledInactiveTrackColor = p.line),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmtTime(drag?.let { it * dur } ?: pos), color = p.text2, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(if (live) stringResource(R.string.live_short) else fmtTime(dur), color = p.text2, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))

            // shuffle, ⏮, ⏹, ⏯, ⏭, repeat
            val playing = st.status == Status.PLAY
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RoundButton(R.drawable.ic_shuffle, stringResource(R.string.shuffle), 44.dp, Color.Transparent,
                    if (st.shuffle) p.red else p.text2.copy(alpha = 0.6f), 24.dp) { actions.toggleShuffle() }
                RoundButton(R.drawable.ic_prev, stringResource(R.string.prev), 48.dp, Color.Transparent, p.text, 32.dp) {
                    actions.prev()
                }
                RoundButton(R.drawable.ic_stop, stringResource(R.string.stop), 48.dp, Color.Transparent, p.text, 28.dp) {
                    actions.stop()
                }
                RoundButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    stringResource(if (playing) R.string.pause else R.string.play), 72.dp, p.red, Color.White, 38.dp,
                    modifier = Modifier.shadow(10.dp, RoundedCornerShape(50), ambientColor = p.red, spotColor = p.red)) {
                    actions.playPause()
                }
                RoundButton(R.drawable.ic_next, stringResource(R.string.next), 48.dp, Color.Transparent, p.text, 32.dp) {
                    actions.next()
                }
                RoundButton(if (st.repeat == "one") R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                    stringResource(R.string.repeat), 44.dp, Color.Transparent,
                    if (st.repeat != "off") p.red else p.text2.copy(alpha = 0.6f), 24.dp) { actions.cycleRepeat() }
            }
            Spacer(Modifier.height(16.dp))
        }
        // Up next: track · artist · 3:40  (radio: live broadcast · genre)
        val nextLine = when {
            t == null -> ""
            t.stream -> listOf(stringResource(R.string.live), t.genre).filter { it.isNotEmpty() }.joinToString(" · ")
            st.next != null -> stringResource(R.string.up_next,
                listOf(st.next.title, st.next.artist, if (st.next.duration > 0) fmtTime(st.next.duration) else "")
                    .filter { it.isNotEmpty() }.joinToString(" · "))
            else -> ""
        }
        Box(Modifier.fillMaxWidth().background(p.bg2).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(nextLine.ifEmpty { " " }, color = p.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(p.bg2))
    }
}
