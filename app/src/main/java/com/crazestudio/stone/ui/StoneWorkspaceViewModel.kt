package com.crazestudio.stone.ui

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crazestudio.stone.data.GitWorkspaceInfo
import com.crazestudio.stone.data.ProjectRepository
import com.crazestudio.stone.data.ProjectTemplate
import com.crazestudio.stone.data.StonePreferences
import com.crazestudio.stone.data.StonePreferencesRepository
import com.crazestudio.stone.terminal.TerminalSessionManager
import com.crazestudio.stone.terminal.TerminalTabState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StonePage {
    SPLASH,
    HOME,
    PROJECTS,
    EXPLORER,
    EDITOR,
    TERMINAL,
    WEB_RUNNER,
    GIT,
    DEBUG,
    EXTENSIONS,
    SETTINGS,
    THEMES,
    TOOLS
}

enum class WebRunnerMode {
    HTML_PREVIEW,
    JS_CONSOLE
}

enum class ConsoleLevel {
    LOG, INFO, WARN, ERROR
}

data class ConsoleLogEntry(
    val id: Long,
    val level: ConsoleLevel,
    val message: String,
    val timestamp: String
)

data class WebRunnerState(
    val targetFilePath: String? = null,
    val mode: WebRunnerMode = WebRunnerMode.HTML_PREVIEW,
    val mobileViewport: Boolean = false,
    val allowExternalNetwork: Boolean = false,
    val consoleLogs: List<ConsoleLogEntry> = emptyList(),
    val reloadNonce: Int = 0,
    val pendingSnippet: String? = null
)

data class EditorTabState(
    val path: String,
    val name: String,
    val value: TextFieldValue,
    val isDirty: Boolean = false,
    val undoStack: List<TextFieldValue> = emptyList(),
    val redoStack: List<TextFieldValue> = emptyList()
)

data class ActivityRecord(
    val title: String,
    val detail: String,
    val time: String
)

data class StoneUiState(
    val currentPage: StonePage = StonePage.SPLASH,
    val backStack: List<StonePage> = emptyList(),
    val projects: List<File> = emptyList(),
    val activeProjectPath: String = "",
    val explorerRevision: Long = 0L,
    val openTabs: List<EditorTabState> = emptyList(),
    val activeTabPath: String? = null,
    val gitInfo: GitWorkspaceInfo = GitWorkspaceInfo(),
    val gitBusy: Boolean = false,
    val selectedDiffPath: String? = null,
    val selectedDiffContent: String? = null,
    val webRunner: WebRunnerState = WebRunnerState(),
    val debugMode: WebRunnerMode = WebRunnerMode.HTML_PREVIEW,
    val debugEntryPath: String = "index.html",
    val debugShellCommand: String = "ls -la",
    val diagnosticsOpen: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val activities: List<ActivityRecord> = emptyList(),
    val toastMessage: String? = null
)

class StoneWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsRepo = StonePreferencesRepository(application)
    private val projectRepo = ProjectRepository(application.filesDir, application.contentResolver)

    val preferences: StateFlow<StonePreferences> = prefsRepo.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StonePreferences()
    )

    private val _uiState = MutableStateFlow(StoneUiState())
    val uiState: StateFlow<StoneUiState> = _uiState.asStateFlow()

    val terminalManager = TerminalSessionManager(
        appFilesDir = application.filesDir,
        appCacheDir = application.cacheDir,
        scope = viewModelScope,
        onRequestWebPreview = { file -> launchWebPreview(file) },
        onRequestJsRunner = { file -> launchJsRunner(file) }
    )

    val terminalTabs: StateFlow<List<TerminalTabState>> = terminalManager.tabs
    val activeTerminalId: StateFlow<String?> = terminalManager.activeTabId

    private var consoleIdCounter = 1L

    init {
        val initial = projectRepo.ensureDefaultProject()
        val all = projectRepo.listProjects()
        _uiState.update {
            it.copy(
                projects = all,
                activeProjectPath = initial.absolutePath,
                storageUsedBytes = projectRepo.calculateProjectsBytes()
            )
        }
        terminalManager.ensureDefaultSession(initial)
        recordActivity("Workspace ready", "Loaded ${initial.name}")
        refreshGitStatus()
    }

    fun finishSplash() {
        _uiState.update {
            if (it.currentPage == StonePage.SPLASH) it.copy(currentPage = StonePage.HOME) else it
        }
    }

    fun navigateTo(page: StonePage, resetStack: Boolean = false) {
        _uiState.update { state ->
            if (state.currentPage == page) return@update state
            val newStack = if (resetStack || page == StonePage.HOME) {
                emptyList()
            } else {
                (state.backStack + state.currentPage).takeLast(12)
            }
            state.copy(currentPage = page, backStack = newStack)
        }
        if (page == StonePage.GIT) refreshGitStatus()
        if (page == StonePage.HOME || page == StonePage.PROJECTS) refreshProjects()
    }

    fun navigateBack(): Boolean {
        val state = _uiState.value
        if (state.diagnosticsOpen) {
            setDiagnosticsOpen(false)
            return true
        }
        val prev = state.backStack.lastOrNull()
        return if (prev != null) {
            _uiState.update { it.copy(currentPage = prev, backStack = it.backStack.dropLast(1)) }
            true
        } else if (state.currentPage != StonePage.HOME && state.currentPage != StonePage.SPLASH) {
            _uiState.update { it.copy(currentPage = StonePage.HOME, backStack = emptyList()) }
            true
        } else {
            false
        }
    }

    fun setDiagnosticsOpen(open: Boolean) {
        if (open) {
            viewModelScope.launch(Dispatchers.IO) {
                val bytes = projectRepo.calculateProjectsBytes()
                _uiState.update { it.copy(diagnosticsOpen = true, storageUsedBytes = bytes) }
            }
        } else {
            _uiState.update { it.copy(diagnosticsOpen = false) }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun activeProjectFile(): File {
        val path = _uiState.value.activeProjectPath
        return File(path).takeIf { it.isDirectory } ?: projectRepo.ensureDefaultProject()
    }

    fun refreshProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = projectRepo.listProjects()
            val bytes = projectRepo.calculateProjectsBytes()
            _uiState.update { it.copy(projects = list, storageUsedBytes = bytes) }
        }
    }

    fun selectProject(folder: File, openExplorer: Boolean = true) {
        if (!folder.isDirectory) return
        _uiState.update {
            it.copy(
                activeProjectPath = folder.absolutePath,
                openTabs = emptyList(),
                activeTabPath = null,
                selectedDiffPath = null,
                selectedDiffContent = null,
                explorerRevision = it.explorerRevision + 1
            )
        }
        terminalManager.activeTabId.value?.let { tabId ->
            terminalManager.switchProjectDirectory(tabId, folder)
        }
        recordActivity("Opened project", folder.name)
        refreshGitStatus()
        if (openExplorer) navigateTo(StonePage.EXPLORER)
    }

    fun createProject(name: String, template: ProjectTemplate) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = projectRepo.createProject(name, template)
            val list = projectRepo.listProjects()
            val bytes = projectRepo.calculateProjectsBytes()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        projects = list,
                        storageUsedBytes = bytes,
                        activeProjectPath = folder.absolutePath,
                        openTabs = emptyList(),
                        activeTabPath = null,
                        toastMessage = "Created ${folder.name}"
                    )
                }
                terminalManager.activeTabId.value?.let { terminalManager.switchProjectDirectory(it, folder) }
                recordActivity("Created ${template.label}", folder.name)
                navigateTo(StonePage.EXPLORER)
            }
        }
    }

    fun deleteProject(folder: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = folder.name
            runCatching { folder.deleteRecursively() }
            val fallback = projectRepo.ensureDefaultProject()
            val list = projectRepo.listProjects()
            val bytes = projectRepo.calculateProjectsBytes()
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    val needsFallback = state.activeProjectPath == folder.absolutePath
                    state.copy(
                        projects = list,
                        storageUsedBytes = bytes,
                        activeProjectPath = if (needsFallback) fallback.absolutePath else state.activeProjectPath,
                        openTabs = if (needsFallback) emptyList() else state.openTabs,
                        activeTabPath = if (needsFallback) null else state.activeTabPath,
                        toastMessage = "Removed $name"
                    )
                }
                recordActivity("Deleted project", name)
            }
        }
    }

    fun importFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching { projectRepo.importFolder(uri) }
                .onSuccess { folder ->
                    refreshProjects()
                    selectProject(folder, openExplorer = true)
                    showToast("Imported ${folder.name}")
                }
                .onFailure { showToast(it.message ?: "Folder import failed.") }
        }
    }

    fun importZip(uri: Uri) {
        viewModelScope.launch {
            runCatching { projectRepo.importZip(uri) }
                .onSuccess { folder ->
                    refreshProjects()
                    selectProject(folder, openExplorer = true)
                    showToast("Extracted ${folder.name}")
                }
                .onFailure { showToast(it.message ?: "ZIP import failed.") }
        }
    }

    fun cloneRepository(url: String, folderName: String) {
        viewModelScope.launch {
            showToast("Cloning repository...")
            runCatching { projectRepo.cloneRepository(url, folderName) }
                .onSuccess { folder ->
                    refreshProjects()
                    selectProject(folder, openExplorer = true)
                    recordActivity("Git clone", folder.name)
                    showToast("Cloned ${folder.name}")
                }
                .onFailure { showToast(it.message ?: "Clone failed.") }
        }
    }

    // --- Explorer & File Editing ---

    fun createEntry(relativePath: String, isDirectory: Boolean) {
        val project = activeProjectFile()
        val target = projectRepo.resolveSafeChild(project, relativePath)
        if (target == null) {
            showToast("Invalid path inside project.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                if (!target.exists()) target.writeText("")
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(explorerRevision = it.explorerRevision + 1) }
                recordActivity(if (isDirectory) "New folder" else "New file", target.name)
                if (!isDirectory) openFile(target)
            }
        }
    }

    fun renameEntry(target: File, newName: String) {
        val clean = newName.trim().replace("/", "_").replace("\\", "_")
        if (clean.isBlank() || clean == "." || clean == "..") {
            showToast("Invalid name.")
            return
        }
        val dest = File(target.parentFile, clean)
        if (dest.exists()) {
            showToast("An item named $clean already exists.")
            return
        }
        val oldPath = target.absolutePath
        if (target.renameTo(dest)) {
            _uiState.update { state ->
                val updatedTabs = state.openTabs.map { tab ->
                    if (tab.path == oldPath) tab.copy(path = dest.absolutePath, name = dest.name) else tab
                }
                state.copy(
                    openTabs = updatedTabs,
                    activeTabPath = if (state.activeTabPath == oldPath) dest.absolutePath else state.activeTabPath,
                    explorerRevision = state.explorerRevision + 1,
                    toastMessage = "Renamed to ${dest.name}"
                )
            }
            recordActivity("Renamed", "${target.name} → ${dest.name}")
        } else {
            showToast("Rename failed.")
        }
    }

    fun deleteEntry(target: File) {
        val removedPath = target.absolutePath
        val removedName = target.name
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { target.deleteRecursively() }
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    val remaining = state.openTabs.filterNot { it.path == removedPath || it.path.startsWith("$removedPath/") }
                    val newActive = if (state.activeTabPath == removedPath) remaining.lastOrNull()?.path else state.activeTabPath
                    state.copy(
                        openTabs = remaining,
                        activeTabPath = newActive,
                        explorerRevision = state.explorerRevision + 1,
                        toastMessage = "Deleted $removedName"
                    )
                }
                recordActivity("Deleted", removedName)
            }
        }
    }

    fun openFile(file: File, navigateToEditor: Boolean = true) {
        if (!file.isFile) return
        val existing = _uiState.value.openTabs.firstOrNull { it.path == file.absolutePath }
        if (existing != null) {
            _uiState.update { it.copy(activeTabPath = existing.path) }
            if (navigateToEditor) navigateTo(StonePage.EDITOR)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val content = runCatching {
                if (file.length() > 512_000) file.readText().take(512_000) else file.readText()
            }.getOrElse { "Could not open file: ${it.message}" }
            val newTab = EditorTabState(
                path = file.absolutePath,
                name = file.name,
                value = TextFieldValue(content, selection = TextRange(0)),
                isDirty = false
            )
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        openTabs = state.openTabs + newTab,
                        activeTabPath = newTab.path
                    )
                }
                if (navigateToEditor) navigateTo(StonePage.EDITOR)
            }
        }
    }

    fun selectEditorTab(path: String) {
        _uiState.update { it.copy(activeTabPath = path) }
    }

    fun closeEditorTab(path: String) {
        _uiState.update { state ->
            val remaining = state.openTabs.filterNot { it.path == path }
            val nextActive = if (state.activeTabPath == path) remaining.lastOrNull()?.path else state.activeTabPath
            state.copy(openTabs = remaining, activeTabPath = nextActive)
        }
    }

    fun updateActiveEditorValue(newValue: TextFieldValue) {
        val activePath = _uiState.value.activeTabPath ?: return
        val autoSave = preferences.value.autoSaveEnabled
        _uiState.update { state ->
            val updated = state.openTabs.map { tab ->
                if (tab.path != activePath) tab
                else {
                    val textChanged = tab.value.text != newValue.text
                    val nextUndo = if (textChanged) (tab.undoStack + tab.value).takeLast(30) else tab.undoStack
                    tab.copy(
                        value = newValue,
                        isDirty = if (textChanged) !autoSave else tab.isDirty,
                        undoStack = nextUndo,
                        redoStack = if (textChanged) emptyList() else tab.redoStack
                    )
                }
            }
            state.copy(openTabs = updated)
        }
        if (autoSave) {
            val tab = _uiState.value.openTabs.firstOrNull { it.path == activePath } ?: return
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { File(activePath).writeText(tab.value.text) }
            }
        }
    }

    fun insertAtCursor(snippet: String) {
        val activePath = _uiState.value.activeTabPath ?: return
        val tab = _uiState.value.openTabs.firstOrNull { it.path == activePath } ?: return
        val current = tab.value
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(start, current.text.length)
        val replaced = current.text.substring(0, start) + snippet + current.text.substring(end)
        val newCursor = start + snippet.length
        updateActiveEditorValue(TextFieldValue(replaced, selection = TextRange(newCursor)))
    }

    fun undoEditor() {
        val activePath = _uiState.value.activeTabPath ?: return
        _uiState.update { state ->
            val updated = state.openTabs.map { tab ->
                if (tab.path != activePath || tab.undoStack.isEmpty()) tab
                else {
                    val previous = tab.undoStack.last()
                    tab.copy(
                        value = previous,
                        isDirty = true,
                        undoStack = tab.undoStack.dropLast(1),
                        redoStack = (tab.redoStack + tab.value).takeLast(30)
                    )
                }
            }
            state.copy(openTabs = updated)
        }
    }

    fun redoEditor() {
        val activePath = _uiState.value.activeTabPath ?: return
        _uiState.update { state ->
            val updated = state.openTabs.map { tab ->
                if (tab.path != activePath || tab.redoStack.isEmpty()) tab
                else {
                    val next = tab.redoStack.last()
                    tab.copy(
                        value = next,
                        isDirty = true,
                        undoStack = (tab.undoStack + tab.value).takeLast(30),
                        redoStack = tab.redoStack.dropLast(1)
                    )
                }
            }
            state.copy(openTabs = updated)
        }
    }

    fun replaceInActiveFile(findQuery: String, replaceWith: String, replaceAll: Boolean) {
        if (findQuery.isEmpty()) return
        val activePath = _uiState.value.activeTabPath ?: return
        val tab = _uiState.value.openTabs.firstOrNull { it.path == activePath } ?: return
        val original = tab.value.text
        val nextText = if (replaceAll) {
            original.replace(findQuery, replaceWith)
        } else {
            original.replaceFirst(findQuery, replaceWith)
        }
        if (nextText != original) {
            updateActiveEditorValue(TextFieldValue(nextText, selection = TextRange(0)))
            showToast(if (replaceAll) "Replaced all matches" else "Replaced match")
        } else {
            showToast("No matches found")
        }
    }

    fun saveActiveFile() {
        val activePath = _uiState.value.activeTabPath ?: return
        val tab = _uiState.value.openTabs.firstOrNull { it.path == activePath } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { File(activePath).writeText(tab.value.text) }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            openTabs = state.openTabs.map {
                                if (it.path == activePath) it.copy(isDirty = false) else it
                            },
                            toastMessage = "Saved ${tab.name}"
                        )
                    }
                    recordActivity("Saved file", tab.name)
                }.onFailure {
                    showToast(it.message ?: "Save failed.")
                }
            }
        }
    }

    // --- Execution & Web/JS Runner ---

    fun runCurrentEditorFile() {
        val activePath = _uiState.value.activeTabPath
        val file = activePath?.let(::File)
        if (file == null || !file.isFile) {
            navigateTo(StonePage.TERMINAL)
            return
        }
        saveActiveFile()
        when (file.extension.lowercase(Locale.ROOT)) {
            "html", "htm" -> launchWebPreview(file)
            "js", "mjs" -> launchJsRunner(file)
            "sh" -> {
                val tabId = terminalManager.activeTabId.value ?: terminalManager.createSession(activeProjectFile())
                terminalManager.switchProjectDirectory(tabId, file.parentFile ?: activeProjectFile())
                terminalManager.sendCommand(tabId, "sh \"${file.name}\"")
                navigateTo(StonePage.TERMINAL)
            }
            else -> {
                val tabId = terminalManager.activeTabId.value ?: terminalManager.createSession(activeProjectFile())
                terminalManager.switchProjectDirectory(tabId, activeProjectFile())
                terminalManager.sendCommand(tabId, "stone-env")
                navigateTo(StonePage.TERMINAL)
            }
        }
    }

    fun launchWebPreview(file: File? = null) {
        val project = activeProjectFile()
        val resolved = file?.takeIf { it.isFile }
            ?: File(project, "index.html").takeIf { it.isFile }
            ?: project.walkTopDown().firstOrNull { it.isFile && it.extension.lowercase() in listOf("html", "htm") }
        _uiState.update { state ->
            state.copy(
                webRunner = state.webRunner.copy(
                    targetFilePath = resolved?.absolutePath,
                    mode = WebRunnerMode.HTML_PREVIEW,
                    consoleLogs = emptyList(),
                    reloadNonce = state.webRunner.reloadNonce + 1
                )
            )
        }
        recordActivity("WebView preview", resolved?.name ?: project.name)
        navigateTo(StonePage.WEB_RUNNER)
    }

    fun launchJsRunner(file: File? = null, customSnippet: String? = null) {
        val project = activeProjectFile()
        val resolved = file?.takeIf { it.isFile }
            ?: project.walkTopDown().firstOrNull { it.isFile && it.extension.lowercase() in listOf("js", "mjs") }
        _uiState.update { state ->
            state.copy(
                webRunner = state.webRunner.copy(
                    targetFilePath = resolved?.absolutePath,
                    mode = WebRunnerMode.JS_CONSOLE,
                    consoleLogs = emptyList(),
                    reloadNonce = state.webRunner.reloadNonce + 1,
                    pendingSnippet = customSnippet
                )
            )
        }
        recordActivity("JS Console", resolved?.name ?: "REPL")
        navigateTo(StonePage.WEB_RUNNER)
    }

    fun appendConsoleLog(level: ConsoleLevel, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = ConsoleLogEntry(consoleIdCounter++, level, message, time)
        _uiState.update { state ->
            state.copy(
                webRunner = state.webRunner.copy(
                    consoleLogs = (state.webRunner.consoleLogs + entry).takeLast(200)
                )
            )
        }
    }

    fun clearConsoleLogs() {
        _uiState.update { state ->
            state.copy(webRunner = state.webRunner.copy(consoleLogs = emptyList()))
        }
    }

    fun toggleWebViewport() {
        _uiState.update { state ->
            state.copy(webRunner = state.webRunner.copy(mobileViewport = !state.webRunner.mobileViewport))
        }
    }

    fun toggleWebNetworkPolicy() {
        _uiState.update { state ->
            state.copy(
                webRunner = state.webRunner.copy(
                    allowExternalNetwork = !state.webRunner.allowExternalNetwork,
                    reloadNonce = state.webRunner.reloadNonce + 1
                )
            )
        }
    }

    fun reloadWebRunner() {
        _uiState.update { state ->
            state.copy(webRunner = state.webRunner.copy(reloadNonce = state.webRunner.reloadNonce + 1))
        }
    }

    fun triggerReplSnippet(code: String) {
        _uiState.update { state ->
            state.copy(webRunner = state.webRunner.copy(pendingSnippet = code))
        }
    }

    fun consumePendingSnippet() {
        _uiState.update { state ->
            state.copy(webRunner = state.webRunner.copy(pendingSnippet = null))
        }
    }

    // --- Git Operations ---

    fun refreshGitStatus() {
        val project = activeProjectFile()
        viewModelScope.launch {
            _uiState.update { it.copy(gitBusy = true) }
            val info = projectRepo.inspectGit(project)
            _uiState.update { it.copy(gitInfo = info, gitBusy = false) }
        }
    }

    fun initGitRepo() {
        val project = activeProjectFile()
        viewModelScope.launch {
            val msg = projectRepo.initGit(project)
            recordActivity("Git init", project.name)
            showToast(msg)
            refreshGitStatus()
        }
    }

    fun checkoutBranch(name: String, createNew: Boolean) {
        val project = activeProjectFile()
        viewModelScope.launch {
            val msg = projectRepo.checkoutBranch(project, name, createNew)
            recordActivity("Git checkout", name)
            showToast(msg)
            refreshGitStatus()
        }
    }

    fun commitChanges(message: String) {
        val project = activeProjectFile()
        val prefs = preferences.value
        viewModelScope.launch {
            val msg = projectRepo.commit(project, message, prefs.gitAuthorName, prefs.gitAuthorEmail)
            recordActivity("Git commit", message.ifBlank { "Update from STONE" })
            showToast(msg)
            refreshGitStatus()
        }
    }

    fun inspectDiff(relativePath: String) {
        val project = activeProjectFile()
        viewModelScope.launch {
            val diff = projectRepo.readFileDiff(project, relativePath)
            _uiState.update { it.copy(selectedDiffPath = relativePath, selectedDiffContent = diff) }
        }
    }

    fun closeDiff() {
        _uiState.update { it.copy(selectedDiffPath = null, selectedDiffContent = null) }
    }

    fun discardGitFile(relativePath: String) {
        val project = activeProjectFile()
        viewModelScope.launch {
            val msg = projectRepo.discardChanges(project, relativePath)
            showToast(msg)
            refreshGitStatus()
        }
    }

    // --- Debug Launch Configuration ---

    fun updateDebugConfig(mode: WebRunnerMode, entryPath: String, shellCommand: String) {
        _uiState.update {
            it.copy(debugMode = mode, debugEntryPath = entryPath, debugShellCommand = shellCommand)
        }
    }

    fun runDebugShell(command: String) {
        val project = activeProjectFile()
        val tabId = terminalManager.activeTabId.value ?: terminalManager.createSession(project)
        terminalManager.switchProjectDirectory(tabId, project)
        terminalManager.sendCommand(tabId, command)
        navigateTo(StonePage.TERMINAL)
    }

    // --- Preferences (Offline DataStore) ---

    fun updateDisplayName(name: String) = viewModelScope.launch { prefsRepo.setDisplayName(name) }
    fun updateTheme(theme: String) = viewModelScope.launch { prefsRepo.setThemeName(theme) }
    fun updateFontSize(size: Float) = viewModelScope.launch { prefsRepo.setEditorFontSize(size) }
    fun updateTabSize(size: Int) = viewModelScope.launch { prefsRepo.setTabSize(size) }
    fun updateAutoSave(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoSaveEnabled(enabled) }
    fun updateWordWrap(enabled: Boolean) = viewModelScope.launch { prefsRepo.setWordWrapEnabled(enabled) }
    fun updateGitAuthor(name: String, email: String) = viewModelScope.launch { prefsRepo.setGitAuthor(name, email) }
    fun toggleProfile(profile: String) = viewModelScope.launch { prefsRepo.toggleProfile(profile) }

    private fun recordActivity(title: String, detail: String) {
        val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        _uiState.update { state ->
            state.copy(activities = (listOf(ActivityRecord(title, detail, stamp)) + state.activities).take(20))
        }
    }

    override fun onCleared() {
        super.onCleared()
        terminalManager.disposeAll()
    }
}
