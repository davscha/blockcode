package de.davscha.blockcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.blockcode.model.Block
import de.davscha.blockcode.model.BlockDef
import de.davscha.blockcode.model.BlockDefs
import de.davscha.blockcode.model.SlotKind

/** Ein Stück des Block-Satzes: fester Text oder eine Lücke. */
sealed interface Seg {
    data class Words(val text: String) : Seg
    data class Slot(val key: String) : Seg
}

fun parseTemplate(t: String): List<Seg> {
    val out = mutableListOf<Seg>()
    var last = 0
    Regex("\\{(\\w+)\\}").findAll(t).forEach { m ->
        val before = t.substring(last, m.range.first).trim()
        if (before.isNotEmpty()) out += Seg.Words(before)
        out += Seg.Slot(m.groupValues[1])
        last = m.range.last + 1
    }
    val rest = t.substring(last).trim()
    if (rest.isNotEmpty()) out += Seg.Words(rest)
    return out
}

@Composable
fun Workspace(vm: EditorViewModel, modifier: Modifier) {
    Column(modifier.background(C.bg)) {
        SelectionBar(vm)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BlockList(vm, vm.program.blocks)
            Box(
                Modifier
                    .width(300.dp)
                    .height(48.dp)
                    .border(2.dp, C.border2, RoundedCornerShape(10.dp))
                    .clickable { vm.select(null) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (vm.program.blocks.isEmpty()) "Tippe links auf einen Block" else "Auswahl aufheben – neue Blöcke ans Ende",
                    color = Color(0xFF8C93A0), fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(200.dp))
        }
    }
}

@Composable
private fun SelectionBar(vm: EditorViewModel) {
    val sel = vm.selected
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(C.panel)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sel == null) {
            Text("Tippe einen Block an, um ihn zu verschieben, einzurücken oder zu löschen.", color = C.muted, fontSize = 13.sp)
        } else {
            ToolButton(Icons.Filled.KeyboardArrowUp, "Hoch") { vm.moveSelected(-1) }
            ToolButton(Icons.Filled.KeyboardArrowDown, "Runter") { vm.moveSelected(1) }
            ToolButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ausrücken") { vm.outdentSelected() }
            ToolButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Einrücken") { vm.indentSelected() }
            ToolButton(null, "Duplizieren") { vm.duplicateSelected() }
            Spacer(Modifier.weight(1f))
            ToolButton(Icons.Filled.Delete, "Löschen", danger = true) { vm.deleteSelected() }
            ToolButton(Icons.Filled.Close, "Fertig") { vm.select(null) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
}

@Composable
private fun ToolButton(icon: ImageVector?, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) C.error else C.text
    Row(
        Modifier
            .height(40.dp)
            .background(C.panel2, RoundedCornerShape(8.dp))
            .border(1.dp, C.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color)
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, end = 2.dp))
    }
}

@Composable
private fun BlockList(vm: EditorViewModel, blocks: List<Block>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b -> BlockItem(vm, b) }
    }
}

@Composable
private fun BlockItem(vm: EditorViewModel, b: Block) {
    val def = BlockDefs.byId(b.defId) ?: return
    val color = Color(def.category.color)
    if (!def.hasBody) {
        BlockHeader(vm, b, def, RoundedCornerShape(10.dp))
        return
    }
    // „C-Block“: Kopf, farbige Schiene links mit Inhalt, Fuß
    Column {
        BlockHeader(vm, b, def, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 10.dp))
        Body(vm, b.uid, b.body, color, isElse = false)
        if (def.elseLabel != null) {
            Box(
                Modifier
                    .background(color, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .clickable { vm.selectElse(b.uid) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(def.elseLabel, color = Color(def.category.onColor), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Body(vm, b.uid, b.elseBody, color, isElse = true)
        }
        Box(Modifier.width(140.dp).height(14.dp).background(color, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)))
    }
}

@Composable
private fun Body(vm: EditorViewModel, parentUid: String, children: List<Block>, color: Color, isElse: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(18.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
            if (children.isEmpty()) {
                val active = vm.selected == parentUid
                Box(
                    Modifier
                        .width(240.dp)
                        .height(42.dp)
                        .border(2.dp, if (active) Color.White else C.border2, RoundedCornerShape(10.dp))
                        .clickable { if (isElse) vm.selectElse(parentUid) else vm.select(parentUid) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (active) "Nächster Block kommt hier hinein" else "+ hier antippen, dann Block wählen",
                        color = Color(0xFF8C93A0), fontSize = 13.sp,
                    )
                }
            } else {
                BlockList(vm, children)
            }
        }
    }
}

@Composable
private fun BlockHeader(vm: EditorViewModel, b: Block, def: BlockDef, shape: RoundedCornerShape) {
    val bg = Color(def.category.color)
    val on = Color(def.category.onColor)
    val selected = vm.selected == b.uid
    Row(
        Modifier
            .then(if (selected) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .background(bg, shape)
            .clickable { vm.select(b.uid) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parseTemplate(def.template).forEach { seg ->
            when (seg) {
                is Seg.Words -> Text(seg.text, color = on, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, softWrap = false)
                is Seg.Slot -> SlotChip(vm, b, def, seg.key, on)
            }
        }
    }
}

@Composable
private fun SlotChip(vm: EditorViewModel, b: Block, def: BlockDef, key: String, on: Color) {
    val slot = def.slot(key)
    val value = b.values[key]
    val text = value?.text.orEmpty()
    val asPill = value?.isVar == true || slot.kind == SlotKind.VAR
    val shape = if (asPill) RoundedCornerShape(50) else RoundedCornerShape(6.dp)
    val shown = when {
        text.isBlank() && slot.kind == SlotKind.PARAMS -> "(keine)"
        text.isBlank() && slot.kind == SlotKind.EXTENSIONS -> "alle"
        text.isBlank() -> "…"
        slot.kind == SlotKind.TEXT && value?.isVar != true -> "„$text“"
        else -> text
    }
    Box(
        Modifier
            .background(if (asPill) on else Color.White.copy(alpha = 0.88f), shape)
            .clickable { vm.editSlot(b.uid, key) }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            shown,
            color = if (asPill) Color.White.copy(alpha = 0.92f) else on,
            fontSize = 14.sp,
            fontFamily = if (slot.kind == SlotKind.PATH || slot.kind == SlotKind.EXPR || slot.kind == SlotKind.EXTENSIONS) Mono else null,
            softWrap = false,
        )
    }
}
