package de.davscha.blockcode.model

/** Welche Art von Wert in einer Lücke steht – bestimmt Auswahl-Dialog und erzeugten Python-Code. */
enum class SlotKind {
    TEXT,       // Text in Anführungszeichen, {name} fügt Variablen ein
    NUMBER,     // Zahl
    EXPR,       // freier Python-Ausdruck, z. B. zahl > 10
    VAR,        // Variablenname, in den etwas gespeichert wird
    PATH,       // Datei- oder Ordnerpfad
    EXTENSIONS, // Dateiendungen, z. B. .jpg, .png
    PARAMS,     // Parameterliste einer Funktion
}

data class SlotDef(
    val key: String,
    val kind: SlotKind,
    val default: String,
    val label: String,
    val defaultIsVar: Boolean = false,
)

enum class Category(val title: String, val color: Long, val onColor: Long) {
    ABLAUF("Ablauf", 0xFFF5A524, 0xFF1E1503),
    VARIABLEN("Variablen", 0xFFFF8FB1, 0xFF2E0A16),
    EINAUSGABE("Ein/Ausgabe", 0xFF3DD9C1, 0xFF0D1F1C),
    DATEIEN("Dateien", 0xFF6AA8FF, 0xFF06162E),
    SYSTEM("Netz & System", 0xFF8BD46A, 0xFF0F2206),
    KI("KI", 0xFFB79CFF, 0xFF1A0E3A),
    CODE("Code", 0xFFC9CED8, 0xFF14161B),
}

/** Was ein Block an Python-Zeilen erzeugt. bodyPrefix steht eingerückt vor den Kind-Blöcken. */
data class GenOut(
    val head: List<String>,
    val bodyPrefix: List<String> = emptyList(),
    val elseHead: String? = null,
)

class BlockDef(
    val id: String,
    val category: Category,
    /** Satz mit Lücken, z. B. "Für jedes {var} in {liste}" */
    val template: String,
    val slots: List<SlotDef> = emptyList(),
    val hasBody: Boolean = false,
    /** Beschriftung des zweiten Teils (z. B. "sonst"), null = kein zweiter Teil */
    val elseLabel: String? = null,
    val imports: Set<String> = emptySet(),
    val helpers: Set<String> = emptySet(),
    val gen: (GenCtx) -> GenOut,
) {
    fun slot(key: String) = slots.first { it.key == key }
}

/** Liefert die Lücken eines Blocks als fertige Python-Schnipsel. */
class GenCtx(private val block: Block, private val def: BlockDef) {
    private fun value(key: String): SlotValue =
        block.values[key] ?: def.slot(key).let { SlotValue(it.default, it.defaultIsVar) }

    fun raw(key: String): String = value(key).text

    /** Python-Ausdruck für die Lücke. */
    fun v(key: String): String {
        val sv = value(key)
        val slot = def.slot(key)
        if (sv.isVar) return pyIdent(sv.text)
        val t = sv.text.trim()
        return when (slot.kind) {
            SlotKind.TEXT, SlotKind.PATH -> pyStr(sv.text)
            SlotKind.NUMBER -> t.replace(',', '.').ifEmpty { "0" }
            SlotKind.EXPR -> t.ifEmpty { "None" }
            SlotKind.VAR -> pyIdent(t)
            SlotKind.PARAMS -> t
            SlotKind.EXTENSIONS -> exts(key) ?: "()"
        }
    }

    /** Pfad-Lücke als Path(...)-Ausdruck. */
    fun path(key: String): String = "Path(${v(key)})"

    /** Endungen als Tupel (".jpg", ".png") oder null für „alle“. */
    fun exts(key: String): String? {
        val list = raw(key).split(',', ' ', ';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != "*" && it != "alle" }
            .map { if (it.startsWith(".")) it else ".$it" }
        if (list.isEmpty()) return null
        return "(" + list.joinToString(", ") { pyStr(it) } + if (list.size == 1) ",)" else ")"
    }
}

/** Python-Textliteral. Enthält der Text {name}, wird ein f-String daraus. */
fun pyStr(s: String): String {
    val esc = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    val isF = Regex("\\{[A-Za-z_][A-Za-z0-9_.\\[\\]\"']*(:[^}]*)?\\}").containsMatchIn(s)
    return if (isF) "f\"$esc\"" else "\"$esc\""
}

private const val PATHLIB = "from pathlib import Path"

object BlockDefs {

    private fun s(key: String, kind: SlotKind, default: String, label: String, isVar: Boolean = false) =
        SlotDef(key, kind, default, label, isVar)

    val all: List<BlockDef> = listOf(
        // ---------------- Ablauf ----------------
        BlockDef("if", Category.ABLAUF, "Wenn {bed} dann",
            listOf(s("bed", SlotKind.EXPR, "zahl > 10", "Bedingung")), hasBody = true) {
            GenOut(listOf("if ${it.v("bed")}:"))
        },
        BlockDef("ifelse", Category.ABLAUF, "Wenn {bed} dann",
            listOf(s("bed", SlotKind.EXPR, "zahl > 10", "Bedingung")), hasBody = true, elseLabel = "sonst") {
            GenOut(listOf("if ${it.v("bed")}:"), elseHead = "else:")
        },
        BlockDef("for_each", Category.ABLAUF, "Für jedes {var} in {liste}",
            listOf(s("var", SlotKind.VAR, "eintrag", "Name für jeden Eintrag"),
                s("liste", SlotKind.EXPR, "liste", "Liste")), hasBody = true) {
            GenOut(listOf("for ${it.v("var")} in ${it.v("liste")}:"))
        },
        BlockDef("repeat", Category.ABLAUF, "Wiederhole {n} mal",
            listOf(s("n", SlotKind.NUMBER, "3", "Anzahl")), hasBody = true) {
            GenOut(listOf("for _ in range(int(${it.v("n")})):"))
        },
        BlockDef("while", Category.ABLAUF, "Solange {bed}",
            listOf(s("bed", SlotKind.EXPR, "zahl < 10", "Bedingung")), hasBody = true) {
            GenOut(listOf("while ${it.v("bed")}:"))
        },
        BlockDef("try", Category.ABLAUF, "Versuche", hasBody = true, elseLabel = "bei Fehler (→ fehler)") {
            GenOut(listOf("try:"), elseHead = "except Exception as fehler:")
        },
        BlockDef("break", Category.ABLAUF, "Schleife abbrechen") { GenOut(listOf("break")) },
        BlockDef("continue", Category.ABLAUF, "Weiter mit nächstem Durchlauf") { GenOut(listOf("continue")) },
        BlockDef("wait", Category.ABLAUF, "Warte {s} Sekunden",
            listOf(s("s", SlotKind.NUMBER, "1", "Sekunden")), imports = setOf("import time")) {
            GenOut(listOf("time.sleep(${it.v("s")})"))
        },
        BlockDef("def", Category.ABLAUF, "Neue Funktion {name} mit {params}",
            listOf(s("name", SlotKind.VAR, "meine_funktion", "Name der Funktion"),
                s("params", SlotKind.PARAMS, "", "Parameter (mit Komma getrennt)")), hasBody = true) {
            GenOut(listOf("def ${it.v("name")}(${it.v("params")}):"))
        },
        BlockDef("call", Category.ABLAUF, "Rufe {name} auf mit {args}",
            listOf(s("name", SlotKind.VAR, "meine_funktion", "Funktion"),
                s("args", SlotKind.PARAMS, "", "Werte (mit Komma getrennt)"))) {
            GenOut(listOf("${it.v("name")}(${it.v("args")})"))
        },
        BlockDef("call_result", Category.ABLAUF, "Rufe {name} auf mit {args} → merke als {var}",
            listOf(s("name", SlotKind.VAR, "meine_funktion", "Funktion"),
                s("args", SlotKind.PARAMS, "", "Werte (mit Komma getrennt)"),
                s("var", SlotKind.VAR, "ergebnis", "Ergebnis speichern als"))) {
            GenOut(listOf("${it.v("var")} = ${it.v("name")}(${it.v("args")})"))
        },
        BlockDef("return", Category.ABLAUF, "Gib zurück {wert}",
            listOf(s("wert", SlotKind.EXPR, "ergebnis", "Wert"))) {
            GenOut(listOf("return ${it.v("wert")}"))
        },

        // ---------------- Variablen ----------------
        BlockDef("set", Category.VARIABLEN, "Setze {var} auf {wert}",
            listOf(s("var", SlotKind.VAR, "zahl", "Variable"), s("wert", SlotKind.EXPR, "0", "Wert"))) {
            GenOut(listOf("${it.v("var")} = ${it.v("wert")}"))
        },
        BlockDef("set_text", Category.VARIABLEN, "Setze {var} auf Text {text}",
            listOf(s("var", SlotKind.VAR, "name", "Variable"), s("text", SlotKind.TEXT, "Hallo", "Text"))) {
            GenOut(listOf("${it.v("var")} = ${it.v("text")}"))
        },
        BlockDef("change", Category.VARIABLEN, "Erhöhe {var} um {n}",
            listOf(s("var", SlotKind.VAR, "zahl", "Variable"), s("n", SlotKind.NUMBER, "1", "Betrag"))) {
            GenOut(listOf("${it.v("var")} += ${it.v("n")}"))
        },
        BlockDef("list_new", Category.VARIABLEN, "Neue leere Liste {var}",
            listOf(s("var", SlotKind.VAR, "liste", "Name der Liste"))) {
            GenOut(listOf("${it.v("var")} = []"))
        },
        BlockDef("list_add", Category.VARIABLEN, "Füge {wert} zu {liste} hinzu",
            listOf(s("wert", SlotKind.EXPR, "eintrag", "Wert"), s("liste", SlotKind.VAR, "liste", "Liste"))) {
            GenOut(listOf("${it.v("liste")}.append(${it.v("wert")})"))
        },
        BlockDef("count", Category.VARIABLEN, "Zähle Einträge in {liste} → merke als {var}",
            listOf(s("liste", SlotKind.EXPR, "liste", "Liste"), s("var", SlotKind.VAR, "anzahl", "Ergebnis"))) {
            GenOut(listOf("${it.v("var")} = len(${it.v("liste")})"))
        },

        // ---------------- Ein/Ausgabe ----------------
        BlockDef("print", Category.EINAUSGABE, "Zeige {text}",
            listOf(s("text", SlotKind.TEXT, "Hallo Welt!", "Text"))) {
            GenOut(listOf("print(${it.v("text")})"))
        },
        BlockDef("print_var", Category.EINAUSGABE, "Zeige Wert von {wert}",
            listOf(s("wert", SlotKind.EXPR, "zahl", "Wert"))) {
            GenOut(listOf("print(${it.v("wert")})"))
        },
        BlockDef("input", Category.EINAUSGABE, "Frage {frage} → merke als {var}",
            listOf(s("frage", SlotKind.TEXT, "Wie heißt du?", "Frage"), s("var", SlotKind.VAR, "antwort", "Antwort speichern als"))) {
            GenOut(listOf("${it.v("var")} = input(${pyStr(it.raw("frage") + " ")})"))
        },
        BlockDef("input_num", Category.EINAUSGABE, "Frage nach Zahl {frage} → merke als {var}",
            listOf(s("frage", SlotKind.TEXT, "Wie viele?", "Frage"), s("var", SlotKind.VAR, "zahl", "Zahl speichern als"))) {
            GenOut(listOf("${it.v("var")} = float(input(${pyStr(it.raw("frage") + " ")}).replace(\",\", \".\"))"))
        },
        BlockDef("input_path", Category.EINAUSGABE, "Frage nach Ordner {frage} Vorschlag {vorschlag} → merke als {var}",
            listOf(s("frage", SlotKind.TEXT, "Welcher Ordner?", "Frage"),
                s("vorschlag", SlotKind.PATH, "Z:\\", "Vorschlag"),
                s("var", SlotKind.VAR, "ordner", "Ordner speichern als")),
            imports = setOf(PATHLIB)) {
            val prompt = pyStr(it.raw("frage") + " [" + it.raw("vorschlag") + "]: ")
            GenOut(listOf("${it.v("var")} = Path(input($prompt).strip() or ${it.v("vorschlag")})"))
        },

        // ---------------- Dateien ----------------
        BlockDef("for_files", Category.DATEIEN, "Für jede {var} in {ordner} mit Endung {endung}",
            listOf(s("var", SlotKind.VAR, "datei", "Name für jede Datei"),
                s("ordner", SlotKind.PATH, "ordner", "Ordner", isVar = true),
                s("endung", SlotKind.EXTENSIONS, ".txt", "Endungen (leer = alle)")),
            hasBody = true, imports = setOf(PATHLIB)) {
            val d = it.v("var")
            val e = it.exts("endung")
            val cond = if (e == null) "not $d.is_file()" else "not $d.is_file() or $d.suffix.lower() not in $e"
            GenOut(
                head = listOf("for $d in sorted(${it.path("ordner")}.iterdir()):"),
                bodyPrefix = listOf("if $cond:", "    continue"),
            )
        },
        BlockDef("exists", Category.DATEIEN, "Wenn {pfad} existiert",
            listOf(s("pfad", SlotKind.PATH, "datei", "Datei oder Ordner", isVar = true)),
            hasBody = true, imports = setOf(PATHLIB)) {
            GenOut(listOf("if ${it.path("pfad")}.exists():"))
        },
        BlockDef("read_text", Category.DATEIEN, "Lies Text aus {datei} → merke als {var}",
            listOf(s("datei", SlotKind.PATH, "datei", "Datei", isVar = true), s("var", SlotKind.VAR, "text", "Text speichern als")),
            imports = setOf(PATHLIB)) {
            GenOut(listOf("${it.v("var")} = ${it.path("datei")}.read_text(encoding=\"utf-8\", errors=\"replace\")"))
        },
        BlockDef("write_text", Category.DATEIEN, "Schreibe {text} in Datei {datei}",
            listOf(s("text", SlotKind.TEXT, "Hallo", "Text"), s("datei", SlotKind.PATH, "ergebnis.txt", "Datei")),
            imports = setOf(PATHLIB)) {
            GenOut(listOf("${it.path("datei")}.write_text(str(${it.v("text")}), encoding=\"utf-8\")"))
        },
        BlockDef("append_text", Category.DATEIEN, "Hänge {text} an Datei {datei} an",
            listOf(s("text", SlotKind.TEXT, "Neue Zeile", "Text"), s("datei", SlotKind.PATH, "log.txt", "Datei")),
            imports = setOf(PATHLIB)) {
            GenOut(listOf(
                "with ${it.path("datei")}.open(\"a\", encoding=\"utf-8\") as f:",
                "    f.write(str(${it.v("text")}) + \"\\n\")",
            ))
        },
        BlockDef("filename", Category.DATEIEN, "Nimm Dateinamen von {datei} → merke als {var}",
            listOf(s("datei", SlotKind.PATH, "datei", "Datei", isVar = true), s("var", SlotKind.VAR, "name", "Name speichern als")),
            imports = setOf(PATHLIB)) {
            GenOut(listOf("${it.v("var")} = ${it.path("datei")}.name"))
        },
        BlockDef("join_path", Category.DATEIEN, "Verbinde {ordner} mit {name} → merke als {var}",
            listOf(s("ordner", SlotKind.PATH, "ordner", "Ordner", isVar = true),
                s("name", SlotKind.TEXT, "neu.txt", "Datei- oder Ordnername"),
                s("var", SlotKind.VAR, "ziel", "Pfad speichern als")),
            imports = setOf(PATHLIB)) {
            GenOut(listOf("${it.v("var")} = ${it.path("ordner")} / ${it.v("name")}"))
        },
        BlockDef("mkdir", Category.DATEIEN, "Erstelle Ordner {pfad}",
            listOf(s("pfad", SlotKind.PATH, "neuer_ordner", "Ordner")), imports = setOf(PATHLIB)) {
            GenOut(listOf("${it.path("pfad")}.mkdir(parents=True, exist_ok=True)"))
        },
        BlockDef("copy", Category.DATEIEN, "Kopiere {quelle} nach {ziel}",
            listOf(s("quelle", SlotKind.PATH, "datei", "Quelle", isVar = true), s("ziel", SlotKind.PATH, "ziel", "Ziel", isVar = true)),
            imports = setOf(PATHLIB, "import shutil")) {
            GenOut(listOf("shutil.copy2(${it.path("quelle")}, ${it.path("ziel")})"))
        },
        BlockDef("move", Category.DATEIEN, "Verschiebe {quelle} nach {ziel}",
            listOf(s("quelle", SlotKind.PATH, "datei", "Quelle", isVar = true), s("ziel", SlotKind.PATH, "ziel", "Ziel", isVar = true)),
            imports = setOf(PATHLIB, "import shutil")) {
            GenOut(listOf("shutil.move(str(${it.path("quelle")}), str(${it.path("ziel")}))"))
        },
        BlockDef("delete", Category.DATEIEN, "Lösche Datei {datei}",
            listOf(s("datei", SlotKind.PATH, "datei", "Datei", isVar = true)), imports = setOf(PATHLIB)) {
            GenOut(listOf("${it.path("datei")}.unlink(missing_ok=True)"))
        },

        // ---------------- Netz & System ----------------
        BlockDef("http_get", Category.SYSTEM, "Lade Webseite {url} → merke als {var}",
            listOf(s("url", SlotKind.TEXT, "https://example.com", "Adresse"), s("var", SlotKind.VAR, "seite", "Inhalt speichern als")),
            imports = setOf("import urllib.request")) {
            GenOut(listOf("${it.v("var")} = urllib.request.urlopen(${it.v("url")}).read().decode(\"utf-8\", errors=\"replace\")"))
        },
        BlockDef("download", Category.SYSTEM, "Lade Datei {url} herunter nach {ziel}",
            listOf(s("url", SlotKind.TEXT, "https://example.com/datei.zip", "Adresse"), s("ziel", SlotKind.PATH, "datei.zip", "Speichern als")),
            imports = setOf("import urllib.request", PATHLIB)) {
            GenOut(listOf("urllib.request.urlretrieve(${it.v("url")}, str(${it.path("ziel")}))"))
        },
        BlockDef("run_cmd", Category.SYSTEM, "Führe Befehl aus {befehl}",
            listOf(s("befehl", SlotKind.TEXT, "dir", "Befehl")), imports = setOf("import subprocess")) {
            GenOut(listOf("subprocess.run(${it.v("befehl")}, shell=True)"))
        },
        BlockDef("run_cmd_out", Category.SYSTEM, "Führe Befehl aus {befehl} → merke Ausgabe als {var}",
            listOf(s("befehl", SlotKind.TEXT, "dir", "Befehl"), s("var", SlotKind.VAR, "ausgabe", "Ausgabe speichern als")),
            imports = setOf("import subprocess")) {
            GenOut(listOf("${it.v("var")} = subprocess.run(${it.v("befehl")}, shell=True, capture_output=True, text=True).stdout"))
        },

        // ---------------- KI ----------------
        BlockDef("ollama_ask", Category.KI, "Frage Ollama {modell} : {prompt} → merke als {var}",
            listOf(s("modell", SlotKind.TEXT, "gemma3", "Modell"),
                s("prompt", SlotKind.TEXT, "Erzähl mir einen kurzen Witz.", "Anweisung"),
                s("var", SlotKind.VAR, "antwort", "Antwort speichern als")),
            helpers = setOf("ollama")) {
            GenOut(listOf("${it.v("var")} = frage_ollama(${it.v("modell")}, ${it.v("prompt")})"))
        },
        BlockDef("ollama_image", Category.KI, "Beschreibe Bild {bild} mit {modell} : {prompt} → merke als {var}",
            listOf(s("bild", SlotKind.PATH, "datei", "Bilddatei", isVar = true),
                s("modell", SlotKind.TEXT, "gemma3", "Modell"),
                s("prompt", SlotKind.TEXT, "Beschreibe das Bild in Tags.", "Anweisung"),
                s("var", SlotKind.VAR, "antwort", "Antwort speichern als")),
            helpers = setOf("ollama")) {
            GenOut(listOf("${it.v("var")} = frage_ollama(${it.v("modell")}, ${it.v("prompt")}, bild=${it.v("bild")})"))
        },
        BlockDef("ollama_sum", Category.KI, "Fasse {text} zusammen mit {modell} → merke als {var}",
            listOf(s("text", SlotKind.EXPR, "text", "Text"),
                s("modell", SlotKind.TEXT, "gemma3", "Modell"),
                s("var", SlotKind.VAR, "zusammenfassung", "Ergebnis speichern als")),
            helpers = setOf("ollama")) {
            GenOut(listOf("${it.v("var")} = frage_ollama(${it.v("modell")}, \"Fasse kurz auf Deutsch zusammen:\\n\" + str(${it.v("text")}))"))
        },

        // ---------------- Code ----------------
        BlockDef("raw", Category.CODE, "Python {code}",
            listOf(s("code", SlotKind.EXPR, "print(\"Hallo\")", "Python-Zeile"))) {
            GenOut(listOf(it.raw("code").ifBlank { "pass" }))
        },
        BlockDef("comment", Category.CODE, "Notiz {text}",
            listOf(s("text", SlotKind.EXPR, "Hier passiert …", "Notiz"))) {
            GenOut(it.raw("text").lines().map { line -> "# $line" })
        },
    )

    private val index = all.associateBy { it.id }
    fun byId(id: String): BlockDef? = index[id]
    fun inCategory(c: Category) = all.filter { it.category == c }

    /** Hilfsfunktionen, die einmal oben ins Programm geschrieben werden. */
    val helperCode: Map<String, Pair<Set<String>, List<String>>> = mapOf(
        "ollama" to (setOf("import json", "import base64", "import urllib.request", PATHLIB) to listOf(
            "OLLAMA_URL = \"http://localhost:11434/api/generate\"",
            "",
            "",
            "def frage_ollama(modell, prompt, bild=None):",
            "    daten = {\"model\": modell, \"prompt\": prompt, \"stream\": False}",
            "    if bild:",
            "        daten[\"images\"] = [base64.b64encode(Path(bild).read_bytes()).decode()]",
            "    anfrage = urllib.request.Request(",
            "        OLLAMA_URL,",
            "        data=json.dumps(daten).encode(\"utf-8\"),",
            "        headers={\"Content-Type\": \"application/json\"},",
            "    )",
            "    with urllib.request.urlopen(anfrage) as antwort:",
            "        return json.loads(antwort.read())[\"response\"]",
        )),
    )
}
