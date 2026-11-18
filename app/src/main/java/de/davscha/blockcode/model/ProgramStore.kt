package de.davscha.blockcode.model

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Speichert Programme als JSON-Dateien im App-Speicher. */
class ProgramStore(context: Context) {
    private val dir = File(context.filesDir, "programme").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun list(): List<String> =
        dir.listFiles { f -> f.extension == "json" }?.map { it.nameWithoutExtension }?.sorted().orEmpty()

    fun load(name: String): Program? = runCatching {
        json.decodeFromString<Program>(File(dir, "$name.json").readText())
    }.getOrNull()

    fun save(program: Program) {
        File(dir, "${program.name}.json").writeText(json.encodeToString(program))
    }

    fun delete(name: String) {
        File(dir, "$name.json").delete()
    }

    fun rename(old: String, new: String) {
        File(dir, "$old.json").renameTo(File(dir, "$new.json"))
    }

    /** Dateiname wird auch als Name des Python-Skripts auf dem PC benutzt. */
    fun safeName(raw: String): String = pyIdent(raw).ifEmpty { "programm" }
}

/** Beispielprogramm für den ersten Start. */
fun exampleProgram(): Program {
    fun b(id: String, vararg values: Pair<String, SlotValue>, body: List<Block> = emptyList()): Block {
        val def = BlockDefs.byId(id)!!
        val base = def.newBlock()
        return base.copy(values = base.values + values, body = body)
    }
    return Program(
        name = "dateien_zaehlen",
        blocks = listOf(
            b("input_path",
                "frage" to SlotValue("Welcher Ordner?"),
                "vorschlag" to SlotValue("Z:\\"),
                "var" to SlotValue("ordner")),
            b("set", "var" to SlotValue("anzahl"), "wert" to SlotValue("0")),
            b("for_files",
                "var" to SlotValue("datei"),
                "ordner" to SlotValue("ordner", isVar = true),
                "endung" to SlotValue(".txt, .md"),
                body = listOf(
                    b("print", "text" to SlotValue("Gefunden: {datei.name}")),
                    b("change", "var" to SlotValue("anzahl"), "n" to SlotValue("1")),
                )),
            b("print", "text" to SlotValue("Fertig! {anzahl} Dateien")),
        ),
    )
}
