package de.davscha.blockcode.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Wert einer Lücke im Block-Satz. isVar = true → es ist ein Variablenname, kein fester Text. */
@Serializable
data class SlotValue(val text: String, val isVar: Boolean = false)

/** Ein Block im Programm. body/elseBody enthalten die eingerückten Kinder (bei Schleifen, Wenn usw.). */
@Serializable
data class Block(
    val uid: String = newUid(),
    val defId: String,
    val values: Map<String, SlotValue> = emptyMap(),
    val body: List<Block> = emptyList(),
    val elseBody: List<Block> = emptyList(),
)

@Serializable
data class Program(
    val name: String,
    val blocks: List<Block> = emptyList(),
)

fun newUid(): String = UUID.randomUUID().toString().substring(0, 8)

/** Neuer Block mit den Standardwerten seiner Definition. */
fun BlockDef.newBlock(): Block =
    Block(defId = id, values = slots.associate { it.key to SlotValue(it.default, it.defaultIsVar) })

/** Kopie mit neuen IDs (für „Duplizieren“). */
fun Block.deepCopy(): Block =
    copy(uid = newUid(), body = body.map { it.deepCopy() }, elseBody = elseBody.map { it.deepCopy() })

// ---------------------------------------------------------------------------
// Baum-Operationen. Ein „Container“ ist die Wurzel-Liste (parent = null)
// oder body (branch 0) bzw. elseBody (branch 1) eines Blocks.
// ---------------------------------------------------------------------------

data class Loc(val parent: String?, val branch: Int, val index: Int)

fun List<Block>.locate(uid: String, parent: String? = null, branch: Int = 0): Loc? {
    forEachIndexed { i, b ->
        if (b.uid == uid) return Loc(parent, branch, i)
        b.body.locate(uid, b.uid, 0)?.let { return it }
        b.elseBody.locate(uid, b.uid, 1)?.let { return it }
    }
    return null
}

fun List<Block>.find(uid: String): Block? {
    for (b in this) {
        if (b.uid == uid) return b
        b.body.find(uid)?.let { return it }
        b.elseBody.find(uid)?.let { return it }
    }
    return null
}

fun List<Block>.container(parent: String?, branch: Int): List<Block> {
    if (parent == null) return this
    val p = find(parent) ?: return emptyList()
    return if (branch == 0) p.body else p.elseBody
}

fun List<Block>.withContainer(parent: String?, branch: Int, newList: List<Block>): List<Block> {
    if (parent == null) return newList
    return map { b ->
        if (b.uid == parent) {
            if (branch == 0) b.copy(body = newList) else b.copy(elseBody = newList)
        } else {
            b.copy(
                body = b.body.withContainer(parent, branch, newList),
                elseBody = b.elseBody.withContainer(parent, branch, newList),
            )
        }
    }
}

fun List<Block>.update(uid: String, change: (Block) -> Block): List<Block> = map { b ->
    if (b.uid == uid) change(b)
    else b.copy(body = b.body.update(uid, change), elseBody = b.elseBody.update(uid, change))
}

fun List<Block>.remove(uid: String): List<Block> {
    val loc = locate(uid) ?: return this
    val list = container(loc.parent, loc.branch).toMutableList()
    list.removeAt(loc.index)
    return withContainer(loc.parent, loc.branch, list)
}

fun List<Block>.insert(parent: String?, branch: Int, index: Int, block: Block): List<Block> {
    val list = container(parent, branch).toMutableList()
    list.add(index.coerceIn(0, list.size), block)
    return withContainer(parent, branch, list)
}

fun List<Block>.move(uid: String, delta: Int): List<Block> {
    val loc = locate(uid) ?: return this
    val list = container(loc.parent, loc.branch).toMutableList()
    val target = loc.index + delta
    if (target !in list.indices) return this
    val b = list.removeAt(loc.index)
    list.add(target, b)
    return withContainer(loc.parent, loc.branch, list)
}

/** Einrücken: Block wandert ans Ende des vorherigen Blocks, wenn der einen Inhalt haben kann. */
fun List<Block>.indent(uid: String): List<Block> {
    val loc = locate(uid) ?: return this
    if (loc.index == 0) return this
    val siblings = container(loc.parent, loc.branch)
    val prev = siblings[loc.index - 1]
    if (BlockDefs.byId(prev.defId)?.hasBody != true) return this
    val block = siblings[loc.index]
    val removed = remove(uid)
    val prevNow = removed.find(prev.uid) ?: return this
    return removed.insert(prev.uid, 0, prevNow.body.size, block)
}

/** Ausrücken: Block wandert hinter seinen Eltern-Block. */
fun List<Block>.outdent(uid: String): List<Block> {
    val loc = locate(uid) ?: return this
    val parent = loc.parent ?: return this
    val block = find(uid) ?: return this
    val removed = remove(uid)
    val pLoc = removed.locate(parent) ?: return this
    return removed.insert(pLoc.parent, pLoc.branch, pLoc.index + 1, block)
}

fun List<Block>.flatten(): List<Block> = flatMap { listOf(it) + it.body.flatten() + it.elseBody.flatten() }

/** Alle Variablennamen im Programm (für die Auswahl beim Antippen einer Lücke). */
fun List<Block>.knownVariables(): List<String> {
    val names = linkedSetOf<String>()
    for (b in flatten()) {
        val def = BlockDefs.byId(b.defId) ?: continue
        for (slot in def.slots) {
            val v = b.values[slot.key]?.text?.trim().orEmpty()
            if (slot.kind == SlotKind.VAR && v.isNotEmpty()) names += pyIdent(v)
            if (slot.kind == SlotKind.PARAMS) v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { names += pyIdent(it.substringBefore('=').trim()) }
        }
        if (def.id == "try") names += "fehler"
    }
    return names.toList()
}

/** Macht aus beliebigem Text einen gültigen Python-Namen („Meine Datei“ → meine_datei). */
fun pyIdent(raw: String): String {
    var s = raw.trim().lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
    if (s.isEmpty()) s = "wert"
    if (s.first().isDigit()) s = "_$s"
    if (s in PY_KEYWORDS) s += "_"
    return s
}

private val PY_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else",
    "except", "false", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "none",
    "nonlocal", "not", "or", "pass", "raise", "return", "true", "try", "while", "with", "yield",
)
