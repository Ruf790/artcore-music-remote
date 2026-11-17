package com.ruf790.artcore.remote.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ruf790.artcore.remote.R
import com.ruf790.artcore.remote.proto.Status
import com.ruf790.artcore.remote.proto.subtitle
import kotlinx.coroutines.delay
import java.util.Date
import android.graphics.Color as AndroidColor

// ───────────────────────── colours from the cover ─────────────────────────

/**
 * A vivid colour of the cover for the accent (header, buttons, seek bar), dark enough for white text on it.
 * null for a cover without a colour (grey, black and white, no picture): the red of the app stays.
 */
fun coverAccent(img: ImageBitmap?): Color? =
    try {
        if (img == null) null else computeAccent(img)
    } catch (e: Exception) {
        null
    }

private fun computeAccent(img: ImageBitmap): Color? {
    val n = 24
    val small = Bitmap.createScaledBitmap(img.asAndroidBitmap(), n, n, true)
    val px = IntArray(n * n)
    small.getPixels(px, 0, n, 0, 0, n, n)
    // twelve hues of 30°: each pixel votes with its saturation × brightness; the strongest hue wins
    val w = FloatArray(12)
    val r = FloatArray(12)
    val g = FloatArray(12)
    val b = FloatArray(12)
    val hsv = FloatArray(3)
    for (c in px) {
        AndroidColor.colorToHSV(c, hsv)
        if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
        val k = (hsv[0] / 30f).toInt().coerceIn(0, 11)
        val wt = hsv[1] * hsv[2]
        w[k] += wt
        r[k] += AndroidColor.red(c) * wt
        g[k] += AndroidColor.green(c) * wt
        b[k] += AndroidColor.blue(c) * wt
    }
    var best = 0
    for (i in 1 until 12) if (w[i] > w[best]) best = i
    if (w[best] < n * n * 0.04f) return null
    val rgb = AndroidColor.rgb((r[best] / w[best]).toInt(), (g[best] / w[best]).toInt(), (b[best] / w[best]).toInt())
    AndroidColor.colorToHSV(rgb, hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.55f)
    hsv[2] = hsv[2].coerceIn(0.5f, 0.85f)
    var out = Color(AndroidColor.HSVToColor(hsv))
    var guard = 0
    while (out.luminance() > 0.35f && guard < 8) {      // yellow and light green: darker, for the white text
        hsv[2] *= 0.88f
        out = Color(AndroidColor.HSVToColor(hsv))
        guard++
    }
    return out
}

// ───────────────────────── desk mode ─────────────────────────

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The tablet as a display of the home player: black (AMOLED), a big cover, the clock, the track; the screen stays on
 * and is dimmed, the bars are hidden, the picture moves a little every minute against burn-in.
 * A tap shows ⏮ ⏯ ⏭ and ✕ for five seconds; Back leaves.
 */
@Composable
fun DeskMode(
    model: UiModel,
    covers: Covers,
    position: () -> Double,
    actions: Actions,
    coverColors: Boolean,
    onExit: () -> Unit,
) {
    val base = LocalPalette.current
    val st = model.state
    val t = st.track
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        var oldBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = window.attributes
            oldBrightness = lp.screenBrightness
            lp.screenBrightness = 0.2f
            window.attributes = lp
            val bars = WindowCompat.getInsetsController(window, view)
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val lp = window.attributes
                lp.screenBrightness = oldBrightness
                window.attributes = lp
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(onBack = onExit)

    val img = t?.let { if (it.stream) null else covers.get(it.cover, 512) }
    val accent = remember(img, coverColors) { if (coverColors) coverAccent(img) else null } ?: base.red
    val accentNow by animateColorAsState(accent, tween(600), label = "deskAccent")

    var controls by remember { mutableStateOf(true) }
    LaunchedEffect(controls) {
        if (controls) {
            delay(5000)
            controls = false
        }
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 50L)
        }
    }
    val clock = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(Date(now))
            .replaceFirstChar { it.uppercase() }
    }
    val shift = ((now / 60_000L) % 6).toInt() * 3

    val title = when {
        t == null -> stringResource(R.string.nothing_playing)
        t.stream && st.onAir.isNotEmpty() -> st.onAir
        else -> t.title
    }
    val sub = when {
        t == null -> ""
        t.stream -> t.title
        else -> subtitle(t)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().offset(shift.dp, shift.dp).padding(32.dp)) {
            val boxW = maxWidth        // inside Row / Column the receiver of maxWidth is not reachable
            val boxH = maxHeight
            if (boxW > boxH) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    DeskCover(img, t?.stream == true, boxH)
                    Spacer(Modifier.width(56.dp))
                    DeskTexts(clock, date, title, sub, accentNow, 104.sp, false, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    DeskCover(img, t?.stream == true, minOf(boxW, boxH * 0.5f))
                    Spacer(Modifier.height(32.dp))
                    DeskTexts(clock, date, title, sub, accentNow, 72.sp, true, Modifier.fillMaxWidth())
                }
            }
        }
        DeskProgress(position, model, accentNow, Modifier.align(Alignment.BottomStart))
        if (controls) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                RoundButton(R.drawable.ic_prev, stringResource(R.string.prev), 56.dp, Color(0x33FFFFFF), Color.White, 30.dp) {
                    actions.prev()
                }
                val playing = st.status == Status.PLAY
                RoundButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    stringResource(if (playing) R.string.pause else R.string.play), 72.dp, accentNow, Color.White, 38.dp) {
                    actions.playPause()
                }
                RoundButton(R.drawable.ic_next, stringResource(R.string.next), 56.dp, Color(0x33FFFFFF), Color.White, 30.dp) {
                    actions.next()
                }
            }
            RoundButton(R.drawable.ic_close, stringResource(R.string.close), 48.dp, Color(0x33FFFFFF), Color.White, 24.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) { onExit() }
        }
    }
}

@Composable
private fun DeskCover(img: ImageBitmap?, stream: Boolean, side: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(side).clip(RoundedCornerShape(20.dp)).background(Color(0xFF181B21)), contentAlignment = Alignment.Center) {
        if (img != null) {
            CoverBox(img, false, Modifier.fillMaxSize(), corner = 20.dp)
        } else {
            CoverBox(null, stream, Modifier.fillMaxSize(0.4f), corner = 40.dp, iconSize = 64.dp)
        }
    }
}

@Composable
private fun DeskTexts(
    clock: String, date: String, title: String, sub: String, accent: Color,
    clockSize: TextUnit, center: Boolean, modifier: Modifier,
) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(clock, color = Color.White.copy(alpha = 0.9f), fontSize = clockSize, fontWeight = FontWeight.Light, maxLines = 1)
        Text(date, color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(36.dp))
        Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 3,
            overflow = TextOverflow.Ellipsis, textAlign = align)
        Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 20.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = align, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A thin line along the bottom edge: how far the track has played. */
@Composable
private fun DeskProgress(position: () -> Double, model: UiModel, accent: Color, modifier: Modifier) {
    val st = model.state
    val pos by produceState(position(), st) {
        while (true) {
            value = position()
            delay(1000)
        }
    }
    val live = st.track?.stream == true
    val frac = if (st.duration > 0 && !live) (pos / st.duration).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(3.dp).background(Color(0x22FFFFFF))) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(accent))
    }
}
