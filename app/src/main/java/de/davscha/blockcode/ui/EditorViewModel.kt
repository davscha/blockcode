package de.davscha.blockcode.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.davscha.blockcode.model.Block
import de.davscha.blockcode.model.BlockDef
import de.davscha.blockcode.model.BlockDefs
import de.davscha.blockcode.model.Category
import de.davscha.blockcode.model.CodeGen
import de.davscha.blockcode.model.CodeLine
import de.davscha.blockcode.model.Program
import de.davscha.blockcode.model.ProgramStore
import de.davscha.blockcode.model.SlotValue
import de.davscha.blockcode.model.deepCopy
import de.davscha.blockcode.model.exampleProgram
import de.davscha.blockcode.model.find
import de.davscha.blockcode.model.indent
import de.davscha.blockcode.model.insert
import de.davscha.blockcode.model.knownVariables
import de.davscha.blockcode.model.locate
import de.davscha.blockcode.model.move
import de.davscha.blockcode.model.newBlock
import de.davscha.blockcode.model.outdent
import de.davscha.blockcode.model.remove
import de.davscha.blockcode.model.update
import de.davscha.blockcode.ssh.SshRunner
import de.davscha.blockcode.ssh.SshSettings
import de.davscha.blockcode.ssh.forgetHostKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class TermChunk(val text: String, val isError: Boolean)
data class SlotEdit(val uid: String, val key: String)
enum class Conn { GETRENNT, VERBINDE, VERBUNDEN }

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProgramStore(app)

    var program by mutableStateOf(Program("programm"))
        private set
    var programNames by mutableStateOf(listOf<String>())
        private set
    var selected by mutableStateOf<String?>(null)
        private set
    var category by mutableStateOf(Category.ABLAUF)
    var code by mutableStateOf(listOf<CodeLine>())
        private set
    var variables by mutableStateOf(listOf<String>())
        private set

    var settings by mutableStateOf(SshSettings.load(app))
        private set
    var conn by mutableStateOf(Conn.GETRENNT)
        private set
    var running by mutableStateOf(false)
        private set
    val terminal = mutableStateListOf<TermChunk>()

    // Dialoge
    var slotEdit by mutableStateOf<SlotEdit?>(null)
    var showPassword by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showPrograms by mutableStateOf(false)

    private val runner = SshRunner(
        context = app,
        scope = viewModelScope,
        onOutput = { text, err -> viewModelScope.launch(Dispatchers.Main) { appendTerm(text, err) } },
        onFinished = { exit ->
            viewModelScope.launch(Dispatchers.Main) {
                running = false
                appendTerm(if (exit == null) "\n[beendet]\n" else "\n[beendet, Code $exit]\n", exit != null && exit != 0)
            }
        },
    )

    init {
        var names = store.list()
        if (names.isEmpty()) {
            store.save(exampleProgram())
            names = store.list()
        }
        programNames = names
        program = store.load(names.first()) ?: exampleProgram()
        refresh()
    }

    // ---------------- Programm bearbeiten ----------------

    private fun change(blocks: List<Block>) {
        program = program.copy(blocks = blocks)
        refresh()
        val p = program
        viewModelScope.launch(Dispatchers.IO) { store.save(p) }
    }

    private fun refresh() {
        code = CodeGen.generate(program)
        variables = program.blocks.knownVariables()
    }

    fun select(uid: String?) {
        elseTarget = null
        selected = if (selected == uid) null else uid
    }

    fun editSlot(uid: String, key: String) {
        elseTarget = null
        selected = uid
        slotEdit = SlotEdit(uid, key)
    }

    /**
     * Block aus der Palette einfügen:
     * ausgewählter Block mit Inhalt (Schleife, Wenn …) → ans Ende hinein,
     * sonst direkt hinter den ausgewählten Block, ohne Auswahl → ganz ans Ende.
     */
    fun addBlock(def: BlockDef) {
        val block = def.newBlock()
        val blocks = program.blocks
        val sel = selected?.let { blocks.find(it) }
        val newBlocks = when {
            sel == null -> blocks + block
            BlockDefs.byId(sel.defId)?.hasBody == true -> blocks.insert(sel.uid, 0, sel.body.size, block)
            else -> {
                val loc = blocks.locate(sel.uid)!!
                blocks.insert(loc.parent, loc.branch, loc.index + 1, block)
            }
        }
        change(newBlocks)
        selected = block.uid
    }

    /** In den „sonst“-Teil eines Blocks einfügen (Tipp auf dessen leere Fläche). */
    fun selectElse(uid: String) {
        selected = uid
        elseTarget = uid
    }

    private var elseTarget: String? = null

    fun addBlockSmart(def: BlockDef) {
        val target = elseTarget
        if (target != null && target == selected) {
            val parent = program.blocks.find(target)
            if (parent != null) {
                val block = def.newBlock()
                change(program.blocks.insert(target, 1, parent.elseBody.size, block))
                selected = block.uid
                elseTarget = null
                return
            }
        }
        elseTarget = null
        addBlock(def)
    }

    fun setSlot(uid: String, key: String, value: SlotValue) {
        change(program.blocks.update(uid) { it.copy(values = it.values + (key to value)) })
    }

    fun deleteSelected() {
        val s = selected ?: return
        change(program.blocks.remove(s))
        selected = null
    }

    fun duplicateSelected() {
        val s = selected ?: return
        val b = program.blocks.find(s) ?: return
        val loc = program.blocks.locate(s) ?: return
        val copy = b.deepCopy()
        change(program.blocks.insert(loc.parent, loc.branch, loc.index + 1, copy))
        selected = copy.uid
    }

    fun moveSelected(delta: Int) = selected?.let { change(program.blocks.move(it, delta)) }
    fun indentSelected() = selected?.let { change(program.blocks.indent(it)) }
    fun outdentSelected() = selected?.let { change(program.blocks.outdent(it)) }

    // ---------------- Programme verwalten ----------------

    fun openProgram(name: String) {
        store.load(name)?.let {
            program = it
            selected = null
            refresh()
        }
        showPrograms = false
    }

    fun newProgram(rawName: String) {
        val name = uniqueName(store.safeName(rawName))
        program = Program(name)
        store.save(program)
        programNames = store.list()
        selected = null
        refresh()
        showPrograms = false
    }

    fun renameProgram(rawName: String) {
        val name = store.safeName(rawName)
        if (name == program.name || name in programNames) return
        store.rename(program.name, name)
        program = program.copy(name = name)
        store.save(program)
        programNames = store.list()
        refresh()
    }

    fun deleteProgram(name: String) {
        store.delete(name)
        programNames = store.list()
        if (name == program.name) {
            if (programNames.isEmpty()) newProgram("programm") else openProgram(programNames.first())
        }
    }

    private fun uniqueName(base: String): String {
        if (base !in programNames) return base
        var i = 2
        while ("${base}_$i" in programNames) i++
        return "${base}_$i"
    }

    // ---------------- Ausführen per SSH ----------------

    fun saveSettings(s: SshSettings) {
        settings = s
        s.save(getApplication())
        showSettings = false
    }

    fun forgetKeys() = forgetHostKeys(getApplication())

    fun runOnPc() {
        if (running) return
        if (settings.user.isBlank()) {
            showSettings = true
            return
        }
        if (!runner.isConnected) {
            conn = Conn.GETRENNT
            showPassword = true
            return
        }
        startRun()
    }

    fun connectAndRun(password: String) {
        showPassword = false
        conn = Conn.VERBINDE
        appendTerm("Verbinde mit ${settings.user}@${settings.host} …\n", false)
        viewModelScope.launch {
            try {
                runner.connect(settings, password)
                conn = Conn.VERBUNDEN
                appendTerm("Verbunden.\n", false)
                startRun()
            } catch (e: Exception) {
                conn = Conn.GETRENNT
                appendTerm("Verbindung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}\n", true)
            }
        }
    }

    private fun startRun() {
        running = true
        runner.run(settings, "${program.name}.py", CodeGen.source(program))
    }

    fun sendInput(line: String) {
        appendTerm(line + "\n", false)
        runner.sendInput(line)
    }

    fun stop() = runner.stop()

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) { runner.disconnect() }
        conn = Conn.GETRENNT
        appendTerm("Getrennt.\n", false)
    }

    fun clearTerminal() = terminal.clear()

    private fun appendTerm(text: String, err: Boolean) {
        val last = terminal.lastOrNull()
        if (last != null && last.isError == err && last.text.length < 4000) {
            terminal[terminal.lastIndex] = last.copy(text = last.text + text)
        } else {
            terminal.add(TermChunk(text, err))
        }
        while (terminal.size > 400) terminal.removeAt(0)
    }

    override fun onCleared() {
        runner.disconnect()
    }
}
