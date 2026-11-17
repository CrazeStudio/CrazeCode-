package com.crazestudio.stone.terminal

import androidx.compose.ui.text.AnnotatedString
import com.crazestudio.stone.pkg.StonePackageManager
import com.crazestudio.stone.ui.theme.AnsiPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.UUID

data class TerminalTabState(
    val id: String,
    val title: String,
    val workingDir: File,
    val revision: Long = 0L,
    val isRunning: Boolean = false,
    val isAlive: Boolean = true,
    val commandHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val ctrlActive: Boolean = false,
    val lastExitCode: Int? = null
)

private class ActiveShellHandle(
    val id: String,
    val buffer: AnsiTerminalBuffer = AnsiTerminalBuffer(),
    var process: Process? = null,
    var writer: BufferedWriter? = null,
    var readerJob: Job? = null
)

class TerminalSessionManager(
    private val appFilesDir: File,
    private val appCacheDir: File,
    private val scope: CoroutineScope,
    private val onRequestWebPreview: (File) -> Unit,
    private val onRequestJsRunner: (File) -> Unit
) {
    private val handles = linkedMapOf<String, ActiveShellHandle>()
    private val _tabs = MutableStateFlow<List<TerminalTabState>>(emptyList())
    val tabs: StateFlow<List<TerminalTabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private var sessionCounter = 1

    /** apt-style package manager; installs into <filesDir>/usr and puts usr/bin on PATH. */
    private val pkgManager by lazy { StonePackageManager(appFilesDir, appCacheDir) }

    fun ensureDefaultSession(projectDir: File) {
        if (_tabs.value.isEmpty()) {
            createSession(projectDir)
        }
    }

    fun createSession(projectDir: File): String {
        val safeDir = sanitizeDir(projectDir)
        val id = UUID.randomUUID().toString()
        val title = "sh ${sessionCounter++}"
        val handle = ActiveShellHandle(id = id)
        handles[id] = handle

        handle.buffer.append("\u001B[1;38;2;255;121;0mSTONE SHELL\u001B[0m · Android Sandbox (/system/bin/sh)\n")
        handle.buffer.append("\u001B[2mType 'help' or 'stone-env' for available binaries & web runners. Try 'apt update' then 'apt install jq'.\u001B[0m\n")

        val state = TerminalTabState(
            id = id,
            title = title,
            workingDir = safeDir,
            revision = 1L,
            isAlive = true
        )
        _tabs.update { it + state }
        _activeTabId.value = id
        spawnShellProcess(handle, safeDir)
        return id
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun closeSession(tabId: String, fallbackDir: File) {
        val handle = handles.remove(tabId)
        handle?.readerJob?.cancel()
        runCatching { handle?.writer?.close() }
        runCatching { handle?.process?.destroyForcibly() }

        _tabs.update { list -> list.filterNot { it.id == tabId } }
        if (_tabs.value.isEmpty()) {
            createSession(fallbackDir)
        } else if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
    }

    fun renderBuffer(tabId: String, palette: AnsiPalette): AnnotatedString {
        val handle = handles[tabId] ?: return AnnotatedString("")
        return handle.buffer.toAnnotatedString(palette)
    }

    fun getPlainBuffer(tabId: String): String {
        return handles[tabId]?.buffer?.plainText().orEmpty()
    }

    fun toggleCtrl(tabId: String) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) it.copy(ctrlActive = !it.ctrlActive) else it }
        }
    }

    fun clearSession(tabId: String) {
        val handle = handles[tabId] ?: return
        handle.buffer.clear()
        bumpRevision(tabId)
    }

    fun interruptSession(tabId: String) {
        val handle = handles[tabId] ?: return
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        handle.buffer.append("\u001B[33m^C\u001B[0m\n")
        handle.readerJob?.cancel()
        runCatching { handle.writer?.close() }
        runCatching { handle.process?.destroyForcibly() }
        spawnShellProcess(handle, tab.workingDir)
        updateTab(tabId) { it.copy(isRunning = false, ctrlActive = false, revision = it.revision + 1) }
    }

    fun stepHistory(tabId: String, older: Boolean): String? {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return null
        if (tab.commandHistory.isEmpty()) return null
        val nextIndex = if (older) {
            if (tab.historyIndex == -1) tab.commandHistory.lastIndex
            else (tab.historyIndex - 1).coerceAtLeast(0)
        } else {
            if (tab.historyIndex == -1) -1
            else if (tab.historyIndex >= tab.commandHistory.lastIndex) -1
            else tab.historyIndex + 1
        }
        updateTab(tabId) { it.copy(historyIndex = nextIndex) }
        return if (nextIndex == -1) "" else tab.commandHistory[nextIndex]
    }

    fun completeInput(tabId: String, currentInput: String): Pair<String, List<String>> {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return currentInput to emptyList()
        val token = currentInput.substringAfterLast(' ')
        val dir = tab.workingDir
        val matches = dir.listFiles()
            ?.map { if (it.isDirectory) "${it.name}/" else it.name }
            ?.filter { it.startsWith(token, ignoreCase = false) }
            ?.sorted()
            .orEmpty()
        if (matches.isEmpty()) return currentInput to emptyList()
        if (matches.size == 1) {
            val prefix = currentInput.dropLast(token.length)
            return (prefix + matches.first()) to emptyList()
        }
        val common = longestCommonPrefix(matches)
        val updated = if (common.length > token.length) {
            currentInput.dropLast(token.length) + common
        } else currentInput
        return updated to matches.take(8)
    }

    fun sendCommand(tabId: String, rawCommand: String) {
        val handle = handles[tabId] ?: return
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return

        // Handle Ctrl+C/L shortcuts if modifier toggled
        if (tab.ctrlActive && cmd.equals("c", ignoreCase = true)) {
            interruptSession(tabId)
            return
        }
        if (tab.ctrlActive && cmd.equals("l", ignoreCase = true)) {
            clearSession(tabId)
            updateTab(tabId) { it.copy(ctrlActive = false) }
            return
        }

        val updatedHistory = (tab.commandHistory.filterNot { it == cmd } + cmd).takeLast(60)
        updateTab(tabId) {
            it.copy(
                commandHistory = updatedHistory,
                historyIndex = -1,
                ctrlActive = false
            )
        }

        val shortDir = tab.workingDir.name.ifBlank { "/" }
        handle.buffer.append("\n\u001B[1;38;2;255;121;0mstone\u001B[0m:\u001B[36m$shortDir\u001B[0m$ $cmd\n")

        when {
            cmd == "clear" -> {
                handle.buffer.clear()
                bumpRevision(tabId)
                return
            }
            cmd == "help" || cmd == "stone-env" -> {
                printSandboxEnvironment(handle, tab.workingDir)
                bumpRevision(tabId)
                return
            }
            isPackageCommand(cmd) -> {
                runPackageCommand(tabId, handle, cmd)
                return
            }
            cmd.startsWith("preview") -> {
                val arg = cmd.removePrefix("preview").trim().ifBlank { "index.html" }
                val target = resolveSafeWorkingFile(tab.workingDir, arg)
                if (target != null && target.isFile) {
                    handle.buffer.append("\u001B[32mOpening ${target.name} in STONE WebView Preview...\u001B[0m\n")
                    bumpRevision(tabId)
                    onRequestWebPreview(target)
                } else {
                    handle.buffer.append("\u001B[31mFile not found in working directory: $arg\u001B[0m\n")
                    bumpRevision(tabId)
                }
                return
            }
            cmd.startsWith("runjs") -> {
                val arg = cmd.removePrefix("runjs").trim().ifBlank { "app.js" }
                val target = resolveSafeWorkingFile(tab.workingDir, arg)
                if (target != null && target.isFile) {
                    handle.buffer.append("\u001B[32mExecuting ${target.name} in STONE JS Console...\u001B[0m\n")
                    bumpRevision(tabId)
                    onRequestJsRunner(target)
                } else {
                    handle.buffer.append("\u001B[31mJS file not found: $arg\u001B[0m\n")
                    bumpRevision(tabId)
                }
                return
            }
        }

        if (handle.process?.isAlive != true) {
            spawnShellProcess(handle, tab.workingDir)
        }

        updateTab(tabId) { it.copy(isRunning = true, revision = it.revision + 1) }

        scope.launch(Dispatchers.IO) {
            try {
                val marker = "__STONE_END_${System.nanoTime()}__"
                val writer = handle.writer ?: throw IllegalStateException("Shell stream unavailable")
                writer.write(cmd)
                writer.newLine()
                // Report actual exit code + resolved PWD back to parser
                writer.write("printf '\\n${marker}:%d:%s\\n' \"$?\" \"\$PWD\"")
                writer.newLine()
                writer.flush()
            } catch (e: Exception) {
                handle.buffer.append("\u001B[31mShell write failed: ${e.message}\u001B[0m\n")
                updateTab(tabId) { it.copy(isRunning = false, isAlive = false, revision = it.revision + 1) }
            }
        }
    }

    fun switchProjectDirectory(tabId: String, projectDir: File) {
        val safe = sanitizeDir(projectDir)
        val handle = handles[tabId] ?: return
        updateTab(tabId) { it.copy(workingDir = safe) }
        if (handle.process?.isAlive == true) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    handle.writer?.apply {
                        write("cd \"${safe.absolutePath.replace("\"", "\\\"")}\"")
                        newLine()
                        flush()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ package manager

    private fun isPackageCommand(cmd: String): Boolean {
        val first = cmd.removePrefix("sudo ").trim().substringBefore(' ')
        return first in StonePackageManager.COMMAND_NAMES
    }

    private fun runPackageCommand(tabId: String, handle: ActiveShellHandle, cmd: String) {
        val args = cmd.removePrefix("sudo ").trim().split(Regex("\\s+"))
        updateTab(tabId) { it.copy(isRunning = true, revision = it.revision + 1) }
        scope.launch(Dispatchers.IO) {
            val code = try {
                pkgManager.execute(args) { text ->
                    handle.buffer.append(text)
                    bumpRevision(tabId)
                }
            } catch (e: Exception) {
                handle.buffer.append("\u001B[31mE: ${e.message}\u001B[0m\n")
                1
            }
            updateTab(tabId) { it.copy(isRunning = false, lastExitCode = code, revision = it.revision + 1) }
        }
    }

    private fun spawnShellProcess(handle: ActiveShellHandle, workingDir: File) {
        val safeDir = sanitizeDir(workingDir)
        pkgManager.ensureDirs()
        val pb = ProcessBuilder("/system/bin/sh")
            .directory(safeDir)
            .redirectErrorStream(true)
        val basePath = pb.environment()["PATH"] ?: "/system/bin:/system/xbin"
        val env = mapOf(
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "HOME" to safeDir.absolutePath,
            "TMPDIR" to appCacheDir.absolutePath,
            "PROJECT_DIR" to safeDir.absolutePath,
            "PREFIX" to pkgManager.prefix.absolutePath,
            "PATH" to "${pkgManager.binDir.absolutePath}:$basePath",
            "STONE_SANDBOX" to "1"
        )
        pb.environment().putAll(env)

        val process = pb.start()
        handle.process = process
        handle.writer = BufferedWriter(OutputStreamWriter(process.outputStream))

        handle.readerJob = scope.launch(Dispatchers.IO) {
            val reader = process.inputStream.bufferedReader()
            val buffer = CharArray(2048)
            val lineCarry = StringBuilder()
            try {
                while (isActive) {
                    val count = reader.read(buffer)
                    if (count <= 0) break
                    lineCarry.append(buffer, 0, count)
                    processCarryBuffer(handle, lineCarry)
                }
            } catch (_: Exception) {
                // Stream closed on destroy/interrupt
            } finally {
                val alive = runCatching { process.isAlive }.getOrDefault(false)
                if (!alive) {
                    val code = runCatching { process.exitValue() }.getOrNull()
                    updateTab(handle.id) {
                        it.copy(isRunning = false, isAlive = false, lastExitCode = code, revision = it.revision + 1)
                    }
                }
            }
        }
        updateTab(handle.id) { it.copy(isAlive = true, isRunning = false, revision = it.revision + 1) }
    }

    private fun processCarryBuffer(handle: ActiveShellHandle, carry: StringBuilder) {
        val text = carry.toString()
        val markerRegex = Regex("__STONE_END_\\d+__:(-?\\d+):([^\\n]+)\\n?")
        val match = markerRegex.find(text)
        if (match != null) {
            val before = text.substring(0, match.range.first)
            val after = text.substring(match.range.last + 1)
            if (before.isNotEmpty()) {
                handle.buffer.append(before)
            }
            val exitCode = match.groupValues[1].toIntOrNull()
            val newPwd = match.groupValues[2].trim()
            val resolvedDir = File(newPwd).takeIf { it.isDirectory && it.canonicalPath.startsWith(appFilesDir.canonicalPath) }
            updateTab(handle.id) {
                it.copy(
                    workingDir = resolvedDir ?: it.workingDir,
                    isRunning = false,
                    lastExitCode = exitCode,
                    revision = it.revision + 1
                )
            }
            carry.clear()
            carry.append(after)
        } else {
            // Keep tail if partial sentinel might span reads
            val safeCut = text.lastIndexOf("__STONE_END_")
            if (safeCut >= 0) {
                val head = text.substring(0, safeCut)
                if (head.isNotEmpty()) {
                    handle.buffer.append(head)
                    bumpRevision(handle.id)
                }
                carry.delete(0, safeCut)
            } else {
                handle.buffer.append(text)
                carry.clear()
                bumpRevision(handle.id)
            }
        }
    }

    private fun printSandboxEnvironment(handle: ActiveShellHandle, workingDir: File) {
        val bins = listOf("sh", "toybox", "ls", "cat", "grep", "find", "sed", "awk", "uname", "ps", "df", "id", "ping", "curl")
        val found = bins.filter { File("/system/bin/$it").exists() || File("/system/xbin/$it").exists() }
        val absent = listOf("python3", "node", "gcc", "git").filterNot {
            File("/system/bin/$it").exists() || File(pkgManager.binDir, it).exists()
        }

        handle.buffer.append("\u001B[1;36mSTONE Verified Runtime Report\u001B[0m\n")
        handle.buffer.append("Working Dir : ${workingDir.absolutePath}\n")
        handle.buffer.append("Shell       : /system/bin/sh (persistent PTY-style session)\n")
        handle.buffer.append("Prefix      : ${pkgManager.prefix.absolutePath} (packages install to \$PREFIX/bin)\n")
        handle.buffer.append("\u001B[32mAvailable   :\u001B[0m ${found.joinToString(", ")}\n")
        handle.buffer.append("\u001B[33mNot installed:\u001B[0m ${absent.joinToString(", ")}\n")
        handle.buffer.append("\u001B[1;38;2;255;121;0mSTONE Builtins:\u001B[0m\n")
        handle.buffer.append("  apt <cmd>            Package manager: update, install, remove, upgrade, list, search, show\n")
        handle.buffer.append("  preview [file.html]  Open local WebView HTML/CSS/JS preview\n")
        handle.buffer.append("  runjs [file.js]      Execute JavaScript in STONE JS Console\n")
        handle.buffer.append("  stone-env / help     Show this real environment report\n")
        handle.buffer.append("  clear                Clear terminal scrollback\n")
        handle.buffer.append("Note: Git operations are built-in via JGit in the Git tab.\n")
    }

    private fun sanitizeDir(dir: File): File {
        val rootCanonical = appFilesDir.canonicalPath
        val dirCanonical = runCatching { dir.canonicalPath }.getOrDefault("")
        return if (dir.isDirectory && dirCanonical.startsWith(rootCanonical)) dir else appFilesDir
    }

    private fun resolveSafeWorkingFile(workingDir: File, relative: String): File? {
        val root = workingDir.canonicalFile
        val target = File(root, relative).canonicalFile
        return if (target.path.startsWith(root.path + File.separator) || target == root) target else null
    }

    private fun longestCommonPrefix(items: List<String>): String {
        if (items.isEmpty()) return ""
        var prefix = items[0]
        for (item in items.drop(1)) {
            while (!item.startsWith(prefix)) {
                prefix = prefix.dropLast(1)
                if (prefix.isEmpty()) return ""
            }
        }
        return prefix
    }

    private fun bumpRevision(tabId: String) {
        updateTab(tabId) { it.copy(revision = it.revision + 1) }
    }

    private fun updateTab(tabId: String, transform: (TerminalTabState) -> TerminalTabState) {
        _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
    }

    fun disposeAll() {
        handles.values.forEach { h ->
            h.readerJob?.cancel()
            runCatching { h.writer?.close() }
            runCatching { h.process?.destroyForcibly() }
        }
        handles.clear()
    }
}
