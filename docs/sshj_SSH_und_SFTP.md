# sshj – SSH und SFTP aus Kotlin

Mit **sshj** kann eine Android-App sich per SSH mit einem anderen Rechner verbinden, Dateien per SFTP hochladen und dort Befehle ausführen. Genau das macht Blockcode beim Knopf **Ausführen**: Python-Skript auf den PC kopieren, starten, Ausgabe zurückholen.

Im Terminal tippst du dafür `ssh win` und dann Befehle – sshj macht dasselbe, nur aus dem Programm heraus.

📖 **Dokumentation**
- sshj auf GitHub: https://github.com/hierynomus/sshj
- Beispiele: https://github.com/hierynomus/sshj/tree/master/examples/src/main/java/net/schmizz/sshj/examples
- Windows OpenSSH: https://learn.microsoft.com/de-de/windows-server/administration/openssh/openssh_overview

---

## 1. Einbinden

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.hierynomus:sshj:0.39.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")
}
```

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

```kotlin
// einmal beim Start
Security.removeProvider("BC")
Security.insertProviderAt(BouncyCastleProvider(), 1)
```

sshj braucht für die Verschlüsselung **BouncyCastle**. Android bringt eine abgespeckte Version mit demselben Namen „BC“ mit, der wichtige Verfahren fehlen. Deshalb wird sie entfernt und die vollständige Version an Platz 1 gesetzt.

💡 Ohne diesen Schritt kommt beim Verbinden oft ein Fehler wie „no such algorithm: X25519“.

---

## 2. Verbinden und anmelden

```kotlin
val client = SSHClient(DefaultConfig())
client.addHostKeyVerifier(PromiscuousVerifier())   // nur zum Ausprobieren!
client.connect("192.168.178.41", 22)
client.authPassword("davscha78@googlemail.com", passwort)

println(client.isAuthenticated)   // true
```

- `connect()` baut die Verbindung auf, `authPassword()` meldet an – wie die Passwortabfrage bei `ssh win`.
- Der **HostKeyVerifier** prüft, ob du wirklich mit deinem PC sprichst. `PromiscuousVerifier` akzeptiert alles – praktisch zum Testen, aber unsicher.

💡 Blockcode nutzt stattdessen „Trust on first use“: Beim ersten Mal wird der Fingerabdruck des PCs gespeichert, später verglichen. Das ist dasselbe, was `ssh` mit `~/.ssh/known_hosts` macht.

---

## 3. Datei hochladen (SFTP)

```kotlin
class TextFile(private val fileName: String, text: String) : InMemorySourceFile() {
    private val bytes = text.toByteArray(Charsets.UTF_8)
    override fun getName(): String = fileName
    override fun getLength(): Long = bytes.size.toLong()
    override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
}

client.newSFTPClient().use { sftp ->
    sftp.mkdirs("blockcode")
    sftp.put(TextFile("hallo.py", "print('Hallo vom Tablet')"), "blockcode/hallo.py")
}
```

- `newSFTPClient()` öffnet einen Datei-Kanal auf derselben Verbindung.
- Relative Pfade wie `blockcode/hallo.py` beginnen im **Benutzerordner** auf dem PC, also `C:\Users\davsc\blockcode\hallo.py`.
- `InMemorySourceFile` lädt Text direkt aus dem Speicher hoch, ohne vorher eine Datei auf dem Tablet anzulegen.
- `use { }` schließt den SFTP-Kanal am Ende automatisch – wie `with open(...)` in Python.

---

## 4. Befehl ausführen und Ausgabe lesen

```kotlin
client.startSession().use { session ->
    val cmd = session.exec("cd /d \"%USERPROFILE%\\blockcode\" && python -u hallo.py")
    val ausgabe = cmd.inputStream.reader(Charsets.UTF_8).readText()
    cmd.join()
    println(ausgabe)            // Hallo vom Tablet
    println(cmd.exitStatus)     // 0
}
```

- Eine **Session** ist ein Kanal für genau einen Befehl.
- `exec()` startet den Befehl in der Standard-Shell des PCs – bei dir die Windows-Eingabeaufforderung, daher `cd /d`.
- `inputStream` ist die normale Ausgabe (`print`), `errorStream` die Fehlerausgabe (Tracebacks).
- `python -u` schaltet die Pufferung ab, damit jede Zeile sofort ankommt und nicht erst am Ende.

💡 Für Eingaben (`input()` in Python) schreibst du in `cmd.outputStream` und rufst `flush()` auf.

💡 Netzwerk-Aufrufe dürfen auf Android nie im Haupt-Thread laufen. In Blockcode läuft alles in `Dispatchers.IO`.

---

## 5. Trennen

```kotlin
client.disconnect()
```

Schließt alle Kanäle und die Verbindung – wie `exit` im Terminal.

---

## ✏️ Übungsaufgabe

Schreibe eine kleine Funktion `pcBefehl(befehl: String): String`, die

1. sich mit deinem PC verbindet,
2. den Befehl ausführt (z. B. `dir Z:\`),
3. die Ausgabe als Text zurückgibt und die Verbindung trennt.

Rufe sie in einer Coroutine mit `Dispatchers.IO` auf und zeige das Ergebnis in einem `Text()` an.

Zusatz: Gib zusätzlich den `errorStream` aus, wenn der Befehl einen Fehler liefert (z. B. `dir X:\`).

---

## 📝 Zusammenfassung

| Schritt | sshj | Terminal-Gegenstück |
|---|---|---|
| Verbinden | `client.connect(host, port)` | `ssh win` |
| Anmelden | `client.authPassword(user, pw)` | Passwort eingeben |
| Datei hochladen | `sftp.put(datei, ziel)` | `scp datei win:ziel` |
| Befehl ausführen | `session.exec(befehl)` | Befehl tippen |
| Ausgabe lesen | `cmd.inputStream` | Text im Terminal |
| Trennen | `client.disconnect()` | `exit` |

Auf Android immer BouncyCastle austauschen, Netzwerk im Hintergrund-Thread laufen lassen und für echte Nutzung einen Host-Key-Check verwenden.
