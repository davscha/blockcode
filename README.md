# Blockcode

Block-Editor für Android-Tablets im Stil des Warcraft-3-Trigger-Editors: Programme werden aus **Satz-Blöcken mit Lücken** gebaut („Für jede [datei] in [ordner] mit Endung [.jpg]“), daneben entsteht live echter Python-Code. Ausgeführt wird per **SSH auf dem PC**.

## Download

**[blockcode.apk (neuester Build)](https://github.com/davscha/blockcode/releases/latest/download/blockcode.apk)**

Jeder Push auf `main` baut über GitHub Actions eine neue APK und veröffentlicht sie als Release. Alle Builds sind mit demselben Schlüssel signiert, neue Versionen lassen sich also über alte installieren.

## Version 0.1

- Block-Palette mit 7 Kategorien: Ablauf, Variablen, Ein/Ausgabe, Dateien, Netz & System, KI (Ollama), Code
- Lücken antippen → Auswahl-Dialog (Variablen, Endungen, Text mit `{variable}`)
- Blöcke verschieben, ein-/ausrücken, duplizieren, löschen
- Eigene Funktionen per „Neue Funktion … mit …“ und „Rufe … auf“
- Python-Code-Ansicht mit Hervorhebung des markierten Blocks
- Ausführen auf dem PC: Upload per SFTP nach `%USERPROFILE%\blockcode`, Start per SSH, Ausgabe und Eingabe im Terminal
- Programme werden automatisch als JSON auf dem Tablet gespeichert

## Einrichtung

1. Zahnrad → PC-Adresse, Benutzer (wie bei `ssh`, z. B. die Microsoft-Konto-Mail) eintragen.
2. **Ausführen** → Passwort eingeben (wird nicht gespeichert).
3. Python muss auf dem PC als `python` erreichbar sein. KI-Blöcke sprechen Ollama auf dem PC unter `localhost:11434` an.

Der Startbefehl ist für die Windows-Eingabeaufforderung voreingestellt und lässt sich in den Einstellungen ändern.

## Geplant

- v0.2: Ausführen auf dem Tablet (Termux + Ubuntu), KI-Leiste „Beschreibe, was passieren soll“
- Eigene Blöcke als Satz mit Lücken, Block-Pakete
- Drag & Drop

## Aufbau

| Datei | Inhalt |
|---|---|
| `model/BlockDefs.kt` | alle Blöcke: Satz, Lücken, erzeugter Python-Code |
| `model/Program.kt` | Programm-Baum und Operationen (einfügen, einrücken …) |
| `model/CodeGen.kt` | Blöcke → Python |
| `ssh/SshRunner.kt` | SFTP-Upload und SSH-Ausführung (sshj) |
| `ui/` | Compose-Oberfläche |

Neuen Block hinzufügen = einen Eintrag in `BlockDefs.all`.

Anleitung zur SSH-Bibliothek: [docs/sshj_SSH_und_SFTP.md](docs/sshj_SSH_und_SFTP.md)
