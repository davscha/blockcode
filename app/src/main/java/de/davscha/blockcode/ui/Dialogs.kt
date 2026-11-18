package de.davscha.blockcode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.blockcode.model.BlockDefs
import de.davscha.blockcode.model.SlotKind
import de.davscha.blockcode.model.SlotValue
import de.davscha.blockcode.model.find
import de.davscha.blockcode.ssh.SshSettings

@Composable
fun Dialogs(vm: EditorViewModel) {
    vm.slotEdit?.let { SlotDialog(vm, it) }
    if (vm.showPassword) PasswordDialog(vm)
    if (vm.showSettings) SettingsDialog(vm)
    if (vm.showPrograms) ProgramsDialog(vm)
}

private val COMMON_EXTS = listOf(".txt", ".md", ".csv", ".json", ".jpg", ".png", ".webp", ".mp4", ".mkv", ".mp3", ".pdf", ".py")

// ---------------------------------------------------------------------------
// Lücke bearbeiten – WC3-Stil: antippen, auswählen, fertig
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SlotDialog(vm: EditorViewModel, edit: SlotEdit) {
    val block = vm.program.blocks.find(edit.uid)
    val def = block?.let { BlockDefs.byId(it.defId) }
    if (block == null || def == null) {
        vm.slotEdit = null
        return
    }
    val slot = def.slot(edit.key)
    val current = block.values[edit.key] ?: SlotValue(slot.default, slot.defaultIsVar)
    var text by remember(edit) { mutableStateOf(current.text) }
    var isVar by remember(edit) { mutableStateOf(current.isVar) }
    val canBeVar = slot.kind in setOf(SlotKind.TEXT, SlotKind.PATH, SlotKind.NUMBER)
    val vars = vm.variables

    fun commit(v: SlotValue = SlotValue(text, isVar && canBeVar)) {
        vm.setSlot(edit.uid, edit.key, v)
        vm.slotEdit = null
    }

    AlertDialog(
        onDismissRequest = { vm.slotEdit = null },
        title = { Text(slot.label) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = slot.kind != SlotKind.TEXT,
                    label = { Text(hintFor(slot.kind, isVar)) },
                    textStyle = TextStyle(fontFamily = if (slot.kind == SlotKind.TEXT && !isVar) null else Mono, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (slot.kind == SlotKind.NUMBER && !isVar) KeyboardType.Decimal else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (canBeVar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Als Variable verwenden", modifier = Modifier.weight(1f))
                        Switch(checked = isVar, onCheckedChange = { isVar = it })
                    }
                }

                if (slot.kind == SlotKind.EXTENSIONS) {
                    Text("Häufige Endungen", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    val chosen = text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        COMMON_EXTS.forEach { ext ->
                            val on = ext in chosen
                            FilterChip(
                                selected = on,
                                onClick = {
                                    val next = if (on) chosen - ext else chosen + ext
                                    text = next.joinToString(", ")
                                },
                                label = { Text(ext, fontFamily = Mono) },
                            )
                        }
                    }
                }

                if (vars.isNotEmpty() && slot.kind != SlotKind.EXTENSIONS) {
                    Text(
                        when (slot.kind) {
                            SlotKind.TEXT -> "Variable in den Text einfügen"
                            SlotKind.VAR -> "Vorhandene Namen"
                            else -> "Variable antippen"
                        },
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vars.forEach { name ->
                            AssistChip(
                                onClick = {
                                    when (slot.kind) {
                                        SlotKind.TEXT -> {
                                            if (isVar) commit(SlotValue(name, true)) else text += "{$name}"
                                        }
                                        SlotKind.EXPR, SlotKind.PARAMS -> {
                                            text = if (text.isBlank()) name else "$text $name"
                                        }
                                        SlotKind.VAR -> commit(SlotValue(name))
                                        else -> commit(SlotValue(name, true))
                                    }
                                    Unit
                                },
                                label = { Text(name, fontFamily = Mono) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { commit() }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = { vm.slotEdit = null }) { Text("Abbrechen") } },
    )
}

private fun hintFor(kind: SlotKind, isVar: Boolean): String = when {
    isVar -> "Name der Variable"
    kind == SlotKind.TEXT -> "Text – {name} setzt eine Variable ein"
    kind == SlotKind.NUMBER -> "Zahl"
    kind == SlotKind.EXPR -> "Python-Ausdruck, z. B. zahl > 10"
    kind == SlotKind.VAR -> "Name"
    kind == SlotKind.PATH -> "Pfad, z. B. Z:\\Bilder"
    kind == SlotKind.EXTENSIONS -> "z. B. .jpg, .png (leer = alle)"
    kind == SlotKind.PARAMS -> "z. B. ordner, breite=1280"
    else -> ""
}

// ---------------------------------------------------------------------------
// Passwort (wird nicht gespeichert)
// ---------------------------------------------------------------------------

@Composable
private fun PasswordDialog(vm: EditorViewModel) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.showPassword = false },
        title = { Text("Mit PC verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${vm.settings.user}@${vm.settings.host}", fontFamily = Mono, fontSize = 13.sp)
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.connectAndRun(pw) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Das Passwort wird nicht gespeichert. Die Verbindung bleibt offen, bis du trennst.", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { vm.connectAndRun(pw) }) { Text("Verbinden & ausführen") } },
        dismissButton = { TextButton(onClick = { vm.showPassword = false }) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------------------
// Verbindungseinstellungen
// ---------------------------------------------------------------------------

@Composable
private fun SettingsDialog(vm: EditorViewModel) {
    val s = vm.settings
    var host by remember { mutableStateOf(s.host) }
    var port by remember { mutableStateOf(s.port.toString()) }
    var user by remember { mutableStateOf(s.user) }
    var dir by remember { mutableStateOf(s.remoteDir) }
    var python by remember { mutableStateOf(s.python) }
    var command by remember { mutableStateOf(s.command) }
    var keysCleared by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { vm.showSettings = false },
        title = { Text("Verbindung zum PC") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).width(520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(host, { host = it }, label = { Text("PC-Adresse") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp),
                    )
                }
                OutlinedTextField(user, { user = it }, label = { Text("Benutzer (wie bei ssh)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dir, { dir = it }, label = { Text("Ordner im Benutzerordner für Skripte") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(python, { python = it }, label = { Text("Python-Befehl") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    command, { command = it }, label = { Text("Startbefehl ({ordner}, {python}, {datei})") },
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp), modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = { command = SshSettings.DEFAULT_COMMAND }) { Text("Startbefehl zurücksetzen") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.forgetKeys(); keysCleared = true }) {
                        Text(if (keysCleared) "Schlüssel gelöscht" else "Gemerkte Schlüssel löschen")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(
                    SshSettings(
                        host = host.trim(), port = port.toIntOrNull() ?: 22, user = user.trim(),
                        remoteDir = dir.trim().trim('/', '\\').ifEmpty { "blockcode" },
                        python = python.trim().ifEmpty { "python" }, command = command,
                    )
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = { vm.showSettings = false }) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------------------
// Programme verwalten
// ---------------------------------------------------------------------------

@Composable
private fun ProgramsDialog(vm: EditorViewModel) {
    var newName by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf(vm.program.name) }
    AlertDialog(
        onDismissRequest = { vm.showPrograms = false },
        title = { Text("Programme") },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    vm.programNames.forEach { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.openProgram(name) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$name.py", fontFamily = Mono, modifier = Modifier.weight(1f),
                                fontWeight = if (name == vm.program.name) FontWeight.Bold else FontWeight.Normal,
                            )
                            IconButton(onClick = { vm.deleteProgram(name) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "$name löschen", tint = C.error)
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(rename, { rename = it }, label = { Text("Aktuelles umbenennen") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.renameProgram(rename) }) { Text("Umbenennen") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Neues Programm") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.newProgram(newName.ifBlank { "programm" }) }) { Text("Anlegen") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.showPrograms = false }) { Text("Schließen") } },
    )
}
