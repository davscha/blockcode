package de.davscha.blockcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.blockcode.model.BlockDefs
import de.davscha.blockcode.model.Category
import de.davscha.blockcode.model.CodeGen

@Composable
fun EditorScreen(vm: EditorViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .safeDrawingPadding()
    ) {
        TopBar(vm)
        Row(Modifier.weight(1f)) {
            Palette(vm, Modifier.width(240.dp).fillMaxHeight())
            Workspace(vm, Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(C.codeBg)
            ) {
                CodePanel(vm, Modifier.weight(1f))
                TerminalPanel(vm, Modifier.height(300.dp))
            }
        }
    }
    Dialogs(vm)
}

// ---------------------------------------------------------------------------
// Obere Leiste
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(vm: EditorViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0xFF191C22))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).background(C.orange, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("B", color = Color(0xFF1E1503), fontWeight = FontWeight.Bold) }
        Text("Blockcode", color = C.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)

        // Programmname → Programme-Dialog
        Box(
            Modifier
                .height(40.dp)
                .border(1.dp, C.border2, RoundedCornerShape(8.dp))
                .background(Color(0xFF232731), RoundedCornerShape(8.dp))
                .clickable { vm.showPrograms = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("${vm.program.name}.py  ▾", color = C.text, fontSize = 14.sp) }

        Spacer(Modifier.weight(1f))

        Text("Ausführen auf", color = C.muted, fontSize = 13.sp)
        Row(
            Modifier
                .background(Color(0xFF232731), RoundedCornerShape(10.dp))
                .border(1.dp, C.border, RoundedCornerShape(10.dp))
                .padding(3.dp)
        ) {
            Box(Modifier.height(38.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Tablet · bald", color = Color(0xFF6E7582), fontSize = 14.sp)
            }
            Box(
                Modifier.height(38.dp).background(Color(0xFF3A4150), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("PC · SSH", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }

        val dot = when (vm.conn) {
            Conn.VERBUNDEN -> C.teal
            Conn.VERBINDE -> C.orange
            Conn.GETRENNT -> Color(0xFF5A606C)
        }
        Box(Modifier.size(10.dp).background(dot, CircleShape))

        IconButton(onClick = { vm.showSettings = true }) {
            Icon(Icons.Filled.Settings, contentDescription = "Verbindungseinstellungen", tint = C.muted)
        }

        if (vm.running) {
            Button(
                onClick = { vm.stop() },
                colors = ButtonDefaults.buttonColors(containerColor = C.error, contentColor = Color(0xFF2A0705)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp),
            ) { Text("Stopp", fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = { vm.runOnPc() },
                colors = ButtonDefaults.buttonColors(containerColor = C.teal, contentColor = Color(0xFF0D1F1C)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Ausführen", fontWeight = FontWeight.Bold)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
}

// ---------------------------------------------------------------------------
// Block-Palette links
// ---------------------------------------------------------------------------

@Composable
private fun Palette(vm: EditorViewModel, modifier: Modifier) {
    Column(modifier.background(C.panel).padding(12.dp)) {
        Category.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                row.forEach { cat ->
                    val active = vm.category == cat
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(if (active) Color(cat.color) else C.panel2, RoundedCornerShape(8.dp))
                            .border(1.dp, if (active) Color(cat.color) else C.border, RoundedCornerShape(8.dp))
                            .clickable { vm.category = cat },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            cat.title,
                            color = if (active) Color(cat.onColor) else Color(0xFFC9CED8),
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            if (vm.selected == null) "TIPPEN = ANS ENDE" else "TIPPEN = HINTER / IN MARKIERTEN BLOCK",
            color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BlockDefs.inCategory(vm.category), key = { it.id }) { def ->
                val label = def.template.replace(Regex("\\{\\w+\\}"), "…")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(def.category.color), RoundedCornerShape(8.dp))
                        .clickable { vm.addBlockSmart(def) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(label, color = Color(def.category.onColor), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Python-Code rechts oben
// ---------------------------------------------------------------------------

@Composable
private fun CodePanel(vm: EditorViewModel, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val lines = vm.code
    val firstSelected = lines.indexOfFirst { it.uid != null && it.uid == vm.selected }

    LaunchedEffect(vm.selected) {
        if (firstSelected >= 0) listState.animateScrollToItem((firstSelected - 3).coerceAtLeast(0))
    }

    Column(modifier.fillMaxWidth().border(1.dp, C.border)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Python-Code", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(CodeGen.source(vm.program))) }) {
                Text("Kopieren", color = C.muted)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            itemsIndexed(lines) { i, line ->
                val hl = line.uid != null && line.uid == vm.selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (hl) C.highlight else Color.Transparent)
                        .clickable(enabled = line.uid != null) { vm.select(line.uid) }
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        "${i + 1}".padStart(3), color = Color(0xFF4E5562),
                        fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(32.dp),
                    )
                    val isComment = line.text.trimStart().startsWith("#")
                    Text(
                        line.text, fontFamily = Mono, fontSize = 12.sp, lineHeight = 19.sp, softWrap = false,
                        color = when {
                            hl -> Color.White
                            isComment -> Color(0xFF7D8594)
                            else -> Color(0xFFC9CED8)
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Terminal rechts unten
// ---------------------------------------------------------------------------

@Composable
private fun TerminalPanel(vm: EditorViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val lastLen = vm.terminal.lastOrNull()?.text?.length ?: 0

    LaunchedEffect(vm.terminal.size, lastLen) {
        if (vm.terminal.isNotEmpty()) listState.scrollToItem(vm.terminal.lastIndex, Int.MAX_VALUE / 2)
    }

    Column(modifier.fillMaxWidth().background(C.termBg).border(1.dp, C.border)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Terminal", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (vm.conn) {
                    Conn.VERBUNDEN -> "PC · ${vm.settings.host} · verbunden"
                    Conn.VERBINDE -> "verbinde …"
                    Conn.GETRENNT -> "nicht verbunden"
                },
                color = C.muted, fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            if (vm.conn == Conn.VERBUNDEN && !vm.running) {
                TextButton(onClick = { vm.disconnect() }) { Text("Trennen", color = C.muted, fontSize = 12.sp) }
            }
            TextButton(onClick = { vm.clearTerminal() }) { Text("Leeren", color = C.muted, fontSize = 12.sp) }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(vm.terminal.size) { i ->
                val chunk = vm.terminal[i]
                Text(
                    chunk.text, fontFamily = Mono, fontSize = 12.sp, lineHeight = 18.sp,
                    color = if (chunk.isError) C.error else Color(0xFFB8BEC9),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Eingabe fürs Programm", fontSize = 13.sp) },
                singleLine = true,
                enabled = vm.running,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.sendInput(input); input = "" }),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 13.sp, color = C.text),
                modifier = Modifier.weight(1f).height(52.dp),
            )
            IconButton(onClick = { vm.sendInput(input); input = "" }, enabled = vm.running) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Eingabe senden", tint = if (vm.running) C.teal else C.muted)
            }
        }
    }
}
