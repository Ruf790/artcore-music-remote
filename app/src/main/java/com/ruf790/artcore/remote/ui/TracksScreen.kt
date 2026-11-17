package com.ruf790.artcore.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruf790.artcore.remote.R
import com.ruf790.artcore.remote.proto.Group
import com.ruf790.artcore.remote.proto.Status
import com.ruf790.artcore.remote.proto.Track
import com.ruf790.artcore.remote.proto.filterGroups
import com.ruf790.artcore.remote.proto.fmtTime
import com.ruf790.artcore.remote.proto.radioStations
import kotlinx.coroutines.delay

/** The tab that gathers the radio stations of every playlist (menu → Radio). */
const val RADIO_TAB = -1

/** A row of the list: a group header or a track (with the playlist it plays from). */
private sealed class Row_ {
    data class Head(val key: String, val group: Group) : Row_()
    data class Item(val key: String, val playlist: Int, val track: Track) : Row_()
}

private fun rows(model: UiModel, tab: Int, query: String): List<Row_> {
    val out = mutableListOf<Row_>()
    if (tab == RADIO_TAB) {
        val st = radioStations(model.playlists).filter { com.ruf790.artcore.remote.proto.matches(it.second, query) }
        if (st.isNotEmpty()) out += Row_.Head("radio", Group("radio"))
        st.forEach { (pl, t) -> out += Row_.Item("r${t.id}", pl, t) }
        return out
    }
    val pl = model.playlists.items.firstOrNull { it.id == tab } ?: return out
    filterGroups(pl.groups, query).forEachIndexed { i, g ->
        out += Row_.Head("g$i", g)
        g.tracks.forEach { t -> out += Row_.Item("t${t.id}", pl.id, t) }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    model: UiModel,
    covers: Covers,
    position: () -> Double,
    actions: Actions,
    tab: Int,
    onTab: (Int) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    focusSearch: Boolean,
    onMenu: () -> Unit,
    onOpenNow: () -> Unit,
    overflow: List<Pair<String, () -> Unit>>,
) {
    val p = LocalPalette.current
    val st = model.state
    Column(Modifier.fillMaxSize().background(p.bg)) {
        StatusBarStrip()
        RedHeader(stringResource(R.string.tracks), onMenu) {
            VolumeButton(st.volume, st.muted, actions)
            Overflow(overflow)
        }
        val items = model.playlists.items
        if (items.isNotEmpty()) {
            val sel = if (tab == RADIO_TAB) items.size else items.indexOfFirst { it.id == tab }.coerceAtLeast(0)
            val hasRadio = tab == RADIO_TAB
            PrimaryScrollableTabRow(
                selectedTabIndex = sel,
                containerColor = p.red,
                contentColor = Color.White,
                edgePadding = 8.dp,
                divider = {},
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(sel, matchContentSize = false),
                        width = 64.dp, height = 3.dp, color = Color.White,
                    )
                },
            ) {
                items.forEachIndexed { i, pl ->
                    Tab(selected = i == sel, onClick = { onTab(pl.id) }, selectedContentColor = Color.White,
                        unselectedContentColor = Color.White.copy(alpha = 0.72f),
                        text = { Text(pl.name, fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
                if (hasRadio) {
                    Tab(selected = true, onClick = {}, selectedContentColor = Color.White,
                        text = { Text(stringResource(R.string.radio), fontWeight = FontWeight.Bold) })
                }
            }
        }
        LinkBanner(model.link, model.pcName, actions)
        SearchField(query, onQuery, focusSearch)
        val list = remember(model.playlists, tab, query) { rows(model, tab, query) }
        val state = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                val msg = when {
                    query.isNotBlank() -> R.string.no_results
                    tab == RADIO_TAB -> R.string.no_radio
                    else -> R.string.empty_playlist
                }
                if (items.isNotEmpty()) {
                    Text(stringResource(msg), color = p.text2, modifier = Modifier.align(Alignment.Center))
                }
            }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(list, key = { when (it) { is Row_.Head -> it.key; is Row_.Item -> it.key } }) { r ->
                    when (r) {
                        is Row_.Head -> GroupHead(r.group)
                        is Row_.Item -> TrackRow(r.track, covers,
                            playing = st.track?.id == r.track.id && st.status != Status.STOP,
                            paused = st.status == Status.PAUSE) { actions.play(r.playlist, r.track.id) }
                    }
                }
            }
        }
        MiniPlayer(model, covers, position, actions, onOpenNow)
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(p.bg))
    }
}

@Composable
fun Overflow(items: List<Pair<String, () -> Unit>>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIcon(R.drawable.ic_more, stringResource(R.string.more)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, act) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; act() })
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, focus: Boolean) {
    val p = LocalPalette.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { fr.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(p.bg2).padding(horizontal = 12.dp).height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = p.text2, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = p.text2, fontSize = 15.sp, maxLines = 1)
            BasicTextField(query, onQuery, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(fr),
                textStyle = TextStyle(color = p.text, fontSize = 15.sp), cursorBrush = SolidColor(p.red),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
        }
        if (query.isNotEmpty()) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = p.text2,
                modifier = Modifier.size(20.dp).clickable { onQuery("") })
        }
    }
}

@Composable
private fun GroupHead(g: Group) {
    val p = LocalPalette.current
    val title = when (g.kind) {
        "radio" -> stringResource(R.string.group_radio)
        "singles" -> stringResource(R.string.group_singles)
        else -> g.title.ifEmpty { stringResource(R.string.group_singles) }
    }
    val sub = if (g.kind == "album") listOf(g.artist, g.year).filter { it.isNotEmpty() }.joinToString(" · ") else ""
    Row(Modifier.fillMaxWidth().background(p.bg).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom) {
        Text(title.uppercase(), color = p.red, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (sub.isNotEmpty()) {
            Text("  ·  $sub", color = p.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrackRow(t: Track, covers: Covers, playing: Boolean, paused: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(if (playing) p.playing else p.bg).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverBox(covers.get(t.cover, 128), t.stream, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, color = if (playing) p.red else p.text, fontSize = 16.sp,
                    fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                val sub = if (t.stream) t.genre.ifEmpty { stringResource(R.string.live_short) } else t.artist
                if (sub.isNotEmpty()) {
                    Text(sub, color = p.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (playing) {
                Equalizer(!paused, p.red)
            } else if (!t.stream && t.duration > 0) {
                Text(fmtTime(t.duration), color = p.text2, fontSize = 13.sp)
            }
        }
        HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 0.5.dp, color = p.line)
    }
}

/** The panel at the bottom: cover, title, "artist · 1:54 / 5:02", ⏹ and the red ⏯; a thin red progress line on top. */
@Composable
fun MiniPlayer(model: UiModel, covers: Covers, position: () -> Double, actions: Actions, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val st = model.state
    val t = st.track ?: return
    val pos by produceState(position(), st) {
        while (true) {
            value = position()
            delay(250)
        }
    }
    val playing = st.status == Status.PLAY
    Column(Modifier.fillMaxWidth().background(p.bg).clickable(onClick = onOpen)) {
        HorizontalDivider(thickness = 0.5.dp, color = p.line)
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.line)) {
            val frac = if (t.stream || st.duration <= 0) (if (t.stream) 1f else 0f)
            else (pos / st.duration).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(p.red))
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CoverBox(covers.get(t.cover, 128), t.stream, Modifier.size(46.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                val title = if (t.stream && st.onAir.isNotEmpty()) st.onAir else t.title
                Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                val who = if (t.stream) t.title else t.artist
                val time = if (t.stream) stringResource(R.string.live_short)
                else "${fmtTime(pos)} / ${fmtTime(st.duration)}"
                Text(listOf(who, time).filter { it.isNotEmpty() }.joinToString(" · "), color = p.text2,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundButton(R.drawable.ic_stop, stringResource(R.string.stop), 40.dp, Color.Transparent, p.text2, 24.dp) {
                actions.stop()
            }
            Spacer(Modifier.width(6.dp))
            RoundButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                stringResource(if (playing) R.string.pause else R.string.play), 46.dp, p.red, Color.White, 26.dp) {
                actions.playPause()
            }
        }
    }
}
