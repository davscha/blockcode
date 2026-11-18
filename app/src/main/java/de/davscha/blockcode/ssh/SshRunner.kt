package de.davscha.blockcode.ssh

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.InMemorySourceFile
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.PublicKey
import java.security.Security

/** Verbindungsdaten zum PC. Das Passwort wird nicht gespeichert. */
data class SshSettings(
    val host: String = "192.168.178.41",
    val port: Int = 22,
    val user: String = "",
    val remoteDir: String = "blockcode",
    val python: String = "python",
    /** {ordner}, {python} und {datei} werden ersetzt. Standard: Windows-cmd. */
    val command: String = DEFAULT_COMMAND,
) {
    companion object {
        const val DEFAULT_COMMAND =
            "set \"PYTHONIOENCODING=utf-8\" && cd /d \"%USERPROFILE%\\{ordner}\" && {python} -u \"{datei}\""

        fun load(ctx: Context): SshSettings {
            val p = ctx.getSharedPreferences("ssh", Context.MODE_PRIVATE)
            val d = SshSettings()
            return SshSettings(
                host = p.getString("host", d.host)!!,
                port = p.getInt("port", d.port),
                user = p.getString("user", d.user)!!,
                remoteDir = p.getString("remoteDir", d.remoteDir)!!,
                python = p.getString("python", d.python)!!,
                command = p.getString("command", d.command)!!,
            )
        }
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("ssh", Context.MODE_PRIVATE).edit()
            .putString("host", host).putInt("port", port).putString("user", user)
            .putString("remoteDir", remoteDir).putString("python", python).putString("command", command)
            .apply()
    }
}

/**
 * Lädt ein Python-Skript per SFTP auf den PC und startet es per SSH.
 * Ausgaben kommen zeilenweise über [onOutput] zurück.
 */
class SshRunner(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onOutput: (text: String, isError: Boolean) -> Unit,
    private val onFinished: (exitCode: Int?) -> Unit,
) {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var command: Session.Command? = null
    private var job: Job? = null

    val isConnected: Boolean get() = client?.isConnected == true && client?.isAuthenticated == true
    val isRunning: Boolean get() = job?.isActive == true

    init {
        // Android bringt nur ein abgespecktes BouncyCastle mit – durch das vollständige ersetzen.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    suspend fun connect(s: SshSettings, password: String) = withContext(Dispatchers.IO) {
        disconnect()
        val c = SSHClient(DefaultConfig())
        c.addHostKeyVerifier(TofuVerifier(context))
        c.connectTimeout = 8000
        c.connect(s.host, s.port)
        c.authPassword(s.user, password)
        client = c
    }

    fun run(s: SshSettings, fileName: String, code: String) {
        val c = client ?: return onOutput("Nicht verbunden.\n", true)
        job = scope.launch(Dispatchers.IO) {
            var exit: Int? = null
            try {
                // 1. Skript hochladen (relativ zum Benutzerordner auf dem PC)
                c.newSFTPClient().use { sftp ->
                    sftp.mkdirs(s.remoteDir)
                    sftp.put(TextFile(fileName, code), "${s.remoteDir}/$fileName")
                }
                // 2. Starten
                val cmdLine = s.command
                    .replace("{ordner}", s.remoteDir.replace('/', '\\'))
                    .replace("{python}", s.python)
                    .replace("{datei}", fileName)
                onOutput("> ${s.python} $fileName\n", false)
                val sess = c.startSession()
                session = sess
                val cmd = sess.exec(cmdLine)
                command = cmd
                val err = launch { pump(cmd.errorStream, true) }
                pump(cmd.inputStream, false)
                err.join()
                cmd.join()
                exit = cmd.exitStatus
            } catch (e: Exception) {
                onOutput("Fehler: ${e.message ?: e.javaClass.simpleName}\n", true)
            } finally {
                runCatching { command?.close() }
                runCatching { session?.close() }
                command = null
                session = null
                onFinished(exit)
            }
        }
    }

    /** Eine Zeile an das laufende Programm schicken (z. B. Antwort auf input()). */
    fun sendInput(line: String) {
        val cmd = command ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                cmd.outputStream.write((line + "\r\n").toByteArray(Charsets.UTF_8))
                cmd.outputStream.flush()
            }
        }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) {
            runCatching { command?.close() }
            runCatching { session?.close() }
        }
    }

    fun disconnect() {
        runCatching { command?.close() }
        runCatching { session?.close() }
        runCatching { client?.disconnect() }
        client = null
    }

    private fun pump(stream: InputStream, isError: Boolean) {
        // Reader statt Bytes, damit Umlaute nicht zwischen zwei Paketen zerrissen werden
        val reader = stream.reader(Charsets.UTF_8)
        val buf = CharArray(2048)
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            if (n > 0) onOutput(String(buf, 0, n), isError)
        }
    }

    private class TextFile(private val fileName: String, text: String) : InMemorySourceFile() {
        private val bytes = text.toByteArray(Charsets.UTF_8)
        override fun getName(): String = fileName
        override fun getLength(): Long = bytes.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
    }
}

/**
 * „Trust on first use“: Beim ersten Verbinden wird der Schlüssel des PCs gemerkt.
 * Ändert er sich später, wird die Verbindung abgelehnt.
 */
private class TofuVerifier(context: Context) : HostKeyVerifier {
    private val prefs = context.getSharedPreferences("hostkeys", Context.MODE_PRIVATE)

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val id = "$hostname:$port"
        val fp = SecurityUtils.getFingerprint(key)
        val known = prefs.getString(id, null)
        if (known == null) {
            prefs.edit().putString(id, fp).apply()
            return true
        }
        if (known != fp) throw IllegalStateException(
            "Der Schlüssel von $id hat sich geändert. Falls du den SSH-Server neu eingerichtet hast: " +
                "in den Einstellungen „Gemerkte Schlüssel löschen“ antippen."
        )
        return true
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}

fun forgetHostKeys(context: Context) {
    context.getSharedPreferences("hostkeys", Context.MODE_PRIVATE).edit().clear().apply()
}
