package com.ruf790.artcore.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruf790.artcore.remote.R
import com.ruf790.artcore.remote.net.LinkState
import com.ruf790.artcore.remote.proto.Beacon
import com.ruf790.artcore.remote.proto.DEFAULT_PORT
import com.ruf790.artcore.remote.proto.isLanAddress

/** Pairing: the PCs found on the Wi-Fi, the 4-digit code, Connect. The address by hand is in ⋮. */
@Composable
fun ConnectScreen(
    found: List<Beacon>,
    link: LinkState,
    busyPc: String,
    onConnect: (Beacon, String) -> Unit,
    onMenu: () -> Unit,
    needsLocalNetwork: Boolean = false,
    onGrantLocalNetwork: () -> Unit = {},
) {
    val p = LocalPalette.current
    var manual by remember { mutableStateOf<List<Beacon>>(emptyList()) }
    var selected by remember { mutableStateOf<Beacon?>(null) }
    var code by remember { mutableStateOf("") }
    var askIp by remember { mutableStateOf(false) }
    val all = found + manual.filter { m -> found.none { it.host == m.host && it.port == m.port } }
    val sel = selected?.let { s -> all.firstOrNull { it.host == s.host && it.port == s.port } } ?: all.singleOrNull()
    val busy = link is LinkState.Connecting
    val manualLabel = stringResource(R.string.manual_ip)

    Column(Modifier.fillMaxSize().background(p.bg).imePadding()) {
        StatusBarStrip()
        RedHeader(stringResource(R.string.connect_title), onMenu) {
            Overflow(listOf(manualLabel to { askIp = true }))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (needsLocalNetwork) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.playing).padding(14.dp)) {
                    Text(stringResource(R.string.local_net_needed), color = p.text, fontSize = 14.sp)
                    TextButton(onClick = onGrantLocalNetwork) { Text(stringResource(R.string.grant), color = p.red) }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (all.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(color = p.red, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.searching), color = p.text, fontSize = 16.sp,
                        modifier = Modifier.padding(start = 12.dp))
                }
                Text(stringResource(R.string.searching_hint), color = p.text2, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                TextButton(onClick = { askIp = true }) {
                    Icon(painterResource(R.drawable.ic_edit), null, tint = p.red, modifier = Modifier.size(18.dp))
                    Text("  $manualLabel", color = p.red)
                }
            }
            all.forEach { b ->
                val on = sel != null && sel.host == b.host && sel.port == b.port
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) p.playing else p.bg2)
                        .border(1.5.dp, if (on) p.red else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { selected = b }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_computer), null, tint = if (on) p.red else p.text2,
                        modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(b.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        val line = if (b.version.isNotEmpty()) stringResource(R.string.pc_line, b.host, b.version)
                        else "${b.host}:${b.port} · " + stringResource(R.string.manual_pc)
                        Text(line, color = p.text2, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.code_label), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CodeBoxes(code) { code = it.filter(Char::isDigit).take(4) }
            Text(stringResource(R.string.code_hint), color = p.text2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            if (link is LinkState.Refused) {
                val err = when (link.code) {
                    "bad_code" -> stringResource(R.string.err_bad_code)
                    "too_many" -> stringResource(R.string.err_too_many)
                    "bad_token", "forgotten", "code_needed" -> stringResource(R.string.err_forgotten)
                    "proto" -> stringResource(R.string.err_proto)
                    else -> stringResource(R.string.err_other, link.code)
                }
                Text(err, color = p.red, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
            if (busy || link is LinkState.Waiting) {
                Text(stringResource(R.string.connecting, busyPc), color = p.text2, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(
            onClick = { sel?.let { onConnect(it, code) } },
            enabled = sel != null && code.length == 4 && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = p.red, contentColor = Color.White,
                disabledContainerColor = p.line, disabledContentColor = p.text2),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) {
            Text(stringResource(R.string.connect), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    if (askIp) {
        ManualIpDialog(onDismiss = { askIp = false }) { host, port ->
            val b = Beacon(host, host, port)
            manual = manual + b
            selected = b
            askIp = false
        }
    }
}

/** Four cells for the digits (one text field underneath). */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    BasicTextField(
        value = code, onValueChange = onChange, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { inner ->
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until 4) {
                        val c = code.getOrNull(i)?.toString() ?: ""
                        val active = i == code.length
                        Box(Modifier.size(56.dp, 64.dp).clip(RoundedCornerShape(12.dp)).background(p.bg2)
                            .border(if (active) 2.dp else 1.dp, if (active) p.red else p.line, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center) {
                            Text(c, color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
                Box(Modifier.size(1.dp)) { inner() }
            }
        },
    )
}

@Composable
private fun ManualIpDialog(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    val p = LocalPalette.current
    var host by remember { mutableStateOf("192.168.") }
    var port by remember { mutableStateOf(DEFAULT_PORT.toString()) }
    val ok = isLanAddress(host) && (port.toIntOrNull() ?: 0) in 1024..65535
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.red, focusedLabelColor = p.red, cursorColor = p.red)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_ip)) },
        text = {
            Column {
                OutlinedTextField(host, { host = it.trim() }, label = { Text(stringResource(R.string.ip_address)) },
                    singleLine = true, colors = colors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.port)) }, singleLine = true, colors = colors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(140.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(host, port.toInt()) }, enabled = ok) { Text(stringResource(R.string.add), color = if (ok) p.red else p.text2) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = p.text2) } },
    )
}
