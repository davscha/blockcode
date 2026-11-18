package de.davscha.blockcode.model

/** Eine Zeile Python-Code. uid zeigt, aus welchem Block sie stammt (für die Hervorhebung). */
data class CodeLine(val text: String, val uid: String? = null)

object CodeGen {
    private const val INDENT = "    "

    fun generate(program: Program): List<CodeLine> {
        val out = mutableListOf<CodeLine>()
        val used = program.blocks.flatten().mapNotNull { BlockDefs.byId(it.defId) }

        val helpers = used.flatMap { it.helpers }.distinct()
        val imports = (used.flatMap { it.imports } +
            helpers.flatMap { BlockDefs.helperCode[it]?.first.orEmpty() }).toSet()

        out += CodeLine("# ${program.name} – erstellt mit Blockcode")
        val plain = imports.filter { it.startsWith("import ") }.sorted()
        val from = imports.filter { it.startsWith("from ") }.sorted()
        if (plain.isNotEmpty() || from.isNotEmpty()) out += CodeLine("")
        (plain + from).forEach { out += CodeLine(it) }

        for (h in helpers) {
            val code = BlockDefs.helperCode[h]?.second ?: continue
            out += CodeLine("")
            out += CodeLine("")
            code.forEach { out += CodeLine(it) }
        }
        if (helpers.isNotEmpty()) out += CodeLine("")
        out += CodeLine("")

        if (program.blocks.isEmpty()) {
            out += CodeLine("# Noch keine Blöcke – tippe links auf einen Block.")
        } else {
            emit(program.blocks, 0, out)
        }
        return out
    }

    fun source(program: Program): String = generate(program).joinToString("\n") { it.text } + "\n"

    private fun emit(blocks: List<Block>, depth: Int, out: MutableList<CodeLine>) {
        val ind = INDENT.repeat(depth)
        val ind1 = INDENT.repeat(depth + 1)
        for (b in blocks) {
            val def = BlockDefs.byId(b.defId)
            if (def == null) {
                out += CodeLine("$ind# unbekannter Block: ${b.defId}", b.uid)
                continue
            }
            val g = def.gen(GenCtx(b, def))
            g.head.forEach { out += CodeLine(ind + it, b.uid) }
            if (def.hasBody) {
                g.bodyPrefix.forEach { out += CodeLine(ind1 + it, b.uid) }
                if (b.body.isEmpty()) {
                    if (g.bodyPrefix.isEmpty()) out += CodeLine(ind1 + "pass", b.uid)
                } else {
                    emit(b.body, depth + 1, out)
                }
            }
            if (def.elseLabel != null && g.elseHead != null) {
                out += CodeLine(ind + g.elseHead, b.uid)
                if (b.elseBody.isEmpty()) out += CodeLine(ind1 + "pass", b.uid)
                else emit(b.elseBody, depth + 1, out)
            }
        }
    }
}
