package com.crazestudio.stone.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazestudio.stone.ui.components.StoneDialog
import com.crazestudio.stone.ui.components.StoneInput
import com.crazestudio.stone.ui.components.StoneMark
import com.crazestudio.stone.ui.screens.DebugScreen
import com.crazestudio.stone.ui.screens.DiagnosticsSheet
import com.crazestudio.stone.ui.screens.EditorScreen
import com.crazestudio.stone.ui.screens.ExplorerScreen
import com.crazestudio.stone.ui.screens.ExtensionsScreen
import com.crazestudio.stone.ui.screens.GitScreen
import com.crazestudio.stone.ui.screens.HomeScreen
import com.crazestudio.stone.ui.screens.NewProjectDialog
import com.crazestudio.stone.ui.screens.ProjectsScreen
import com.crazestudio.stone.ui.screens.SettingsScreen
import com.crazestudio.stone.ui.screens.SplashScreen
import com.crazestudio.stone.ui.screens.TerminalScreen
import com.crazestudio.stone.ui.screens.ThemesScreen
import com.crazestudio.stone.ui.screens.ToolsScreen
import com.crazestudio.stone.ui.screens.WebRunnerScreen
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.StoneOrange
import com.crazestudio.stone.ui.theme.StoneOrangeMuted
import com.crazestudio.stone.ui.theme.StoneTheme
import kotlinx.coroutines.delay
import java.io.File

private data class NavDestination(
    val page: StonePage,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private val TopNavDestinations = listOf(
    NavDestination(StonePage.HOME, "Home", Icons.Default.Home, "nav_home"),
    NavDestination(StonePage.PROJECTS, "Projects", Icons.Default.Folder, "nav_projects"),
    NavDestination(StonePage.EDITOR, "Editor", Icons.Default.Edit, "nav_editor"),
    NavDestination(StonePage.TERMINAL, "Terminal", Icons.Default.Terminal, "nav_terminal"),
    NavDestination(StonePage.TOOLS, "Tools", Icons.Default.Menu, "nav_tools")
)

@Composable
fun StoneApp(viewModel: StoneWorkspaceViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val terminalTabs by viewModel.terminalTabs.collectAsStateWithLifecycle()
    val activeTerminalId by viewModel.activeTerminalId.collectAsStateWithLifecycle()

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var cloneUrl by remember { mutableStateOf("") }
    var cloneName by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }

    val activeProject = remember(uiState.activeProjectPath) {
        viewModel.activeProjectFile()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            viewModel.importFolder(uri)
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importZip(uri)
    }

    LaunchedEffect(Unit) {
        delay(900)
        viewModel.finishSplash()
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            delay(2000)
            viewModel.clearToast()
        }
    }

    BackHandler(enabled = uiState.currentPage != StonePage.HOME && uiState.currentPage != StonePage.SPLASH) {
        viewModel.navigateBack()
    }

    StoneTheme(prefs.themeName) {
        val colors = LocalStoneColors.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.bg
        ) {
            if (uiState.currentPage == StonePage.SPLASH) {
                SplashScreen()
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    val isWide = maxWidth >= 600.dp
                    val isExpanded = maxWidth >= 840.dp

                    val showCompactBottomBar = !isWide && uiState.currentPage in setOf(
                        StonePage.HOME,
                        StonePage.PROJECTS,
                        StonePage.TOOLS
                    )

                    Scaffold(
                        containerColor = colors.bg,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (showCompactBottomBar) {
                                StoneBottomBar(
                                    currentPage = uiState.currentPage,
                                    onSelect = { viewModel.navigateTo(it, resetStack = (it == StonePage.HOME)) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .imePadding()
                        ) {
                            if (isWide) {
                                StoneNavigationRail(
                                    currentPage = uiState.currentPage,
                                    onSelect = { viewModel.navigateTo(it, resetStack = (it == StonePage.HOME)) },
                                    onWebRunner = { viewModel.launchWebPreview() },
                                    onGit = { viewModel.navigateTo(StonePage.GIT) }
                                )
                                VerticalDivider(color = colors.line)
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                StonePageContent(
                                    uiState = uiState,
                                    prefs = prefs,
                                    activeProject = activeProject,
                                    terminalTabs = terminalTabs,
                                    activeTerminalId = activeTerminalId,
                                    isWide = isWide,
                                    isExpanded = isExpanded,
                                    viewModel = viewModel,
                                    onNewProject = { showNewProjectDialog = true },
                                    onOpenFolder = { folderPicker.launch(null) },
                                    onCloneRepo = { cloneUrl = ""; cloneName = ""; showCloneDialog = true },
                                    onImportZip = {
                                        zipPicker.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/x-zip-compressed",
                                                "application/octet-stream"
                                            )
                                        )
                                    },
                                    onSearch = { searchText = ""; showSearchDialog = true }
                                )
                            }
                        }
                    }
                }
            }

            if (showNewProjectDialog) {
                NewProjectDialog(
                    onDismiss = { showNewProjectDialog = false },
                    onCreate = { name, template ->
                        showNewProjectDialog = false
                        viewModel.createProject(name, template)
                    }
                )
            }

            if (showCloneDialog) {
                StoneDialog(
                    title = "Clone HTTPS Repository",
                    confirmLabel = "Clone",
                    onDismiss = { showCloneDialog = false },
                    onConfirm = {
                        showCloneDialog = false
                        viewModel.cloneRepository(cloneUrl, cloneName)
                    }
                ) {
                    StoneInput(cloneUrl, { cloneUrl = it }, "https://github.com/user/repo.git")
                    StoneInput(cloneName, { cloneName = it }, "Folder name (optional)")
                }
            }

            if (showSearchDialog) {
                StoneDialog(
                    title = "Search Projects",
                    confirmLabel = "Done",
                    onDismiss = { showSearchDialog = false },
                    onConfirm = { showSearchDialog = false }
                ) {
                    StoneInput(searchText, { searchText = it }, "Search workspace names…")
                    uiState.projects
                        .filter { it.name.contains(searchText, ignoreCase = true) }
                        .take(8)
                        .forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(colors.panel2)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(file.name, color = colors.text, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                TextButton(onClick = {
                                    showSearchDialog = false
                                    viewModel.selectProject(file, openExplorer = true)
                                }) {
                                    Text("Open", color = StoneOrange)
                                }
                            }
                        }
                }
            }

            if (uiState.diagnosticsOpen) {
                DiagnosticsSheet(
                    storageBytes = uiState.storageUsedBytes,
                    activeShells = terminalTabs.size,
                    projectCount = uiState.projects.size,
                    activities = uiState.activities,
                    onDismiss = { viewModel.setDiagnosticsOpen(false) }
                )
            }
        }
    }
}

@Composable
private fun StonePageContent(
    uiState: StoneUiState,
    prefs: com.crazestudio.stone.data.StonePreferences,
    activeProject: File,
    terminalTabs: List<com.crazestudio.stone.terminal.TerminalTabState>,
    activeTerminalId: String?,
    isWide: Boolean,
    isExpanded: Boolean,
    viewModel: StoneWorkspaceViewModel,
    onNewProject: () -> Unit,
    onOpenFolder: () -> Unit,
    onCloneRepo: () -> Unit,
    onImportZip: () -> Unit,
    onSearch: () -> Unit
) {
    val colors = LocalStoneColors.current
    val backOrNull: (() -> Unit)? = if (isWide) null else {
        { viewModel.navigateBack() }
    }

    when (uiState.currentPage) {
        StonePage.SPLASH -> SplashScreen()

        StonePage.HOME -> HomeScreen(
            displayName = prefs.displayName,
            projects = uiState.projects,
            activeProject = activeProject,
            activeTerminalCount = terminalTabs.size,
            onNewProject = onNewProject,
            onOpenFolder = onOpenFolder,
            onCloneRepo = onCloneRepo,
            onImportZip = onImportZip,
            onSelectProject = { viewModel.selectProject(it, openExplorer = true) },
            onNavigate = { viewModel.navigateTo(it) },
            onSearch = onSearch,
            onOpenDiagnostics = { viewModel.setDiagnosticsOpen(true) },
            onLaunchPreview = { viewModel.launchWebPreview() }
        )

        StonePage.PROJECTS -> ProjectsScreen(
            projects = uiState.projects,
            activeProjectPath = activeProject.absolutePath,
            onBack = backOrNull ?: { viewModel.navigateTo(StonePage.HOME) },
            onSelectProject = { viewModel.selectProject(it, openExplorer = true) },
            onNewProject = onNewProject,
            onOpenFolder = onOpenFolder,
            onDeleteProject = { viewModel.deleteProject(it) }
        )

        StonePage.EXPLORER, StonePage.EDITOR -> {
            if (isWide) {
                Row(Modifier.fillMaxSize()) {
                    ExplorerScreen(
                        project = activeProject,
                        revision = uiState.explorerRevision,
                        onBack = null,
                        onOpenFile = { viewModel.openFile(it, navigateToEditor = false) },
                        onRunFile = { file ->
                            when (file.extension.lowercase()) {
                                "js", "mjs" -> viewModel.launchJsRunner(file)
                                "sh" -> {
                                    viewModel.runDebugShell("sh \"${file.name}\"")
                                }
                                else -> viewModel.launchWebPreview(file)
                            }
                        },
                        onCreateEntry = { rel, isDir -> viewModel.createEntry(rel, isDir) },
                        onRenameEntry = { file, newName -> viewModel.renameEntry(file, newName) },
                        onDeleteEntry = { viewModel.deleteEntry(it) },
                        modifier = Modifier.weight(0.34f)
                    )
                    VerticalDivider(color = colors.line)
                    EditorScreen(
                        project = activeProject,
                        tabs = uiState.openTabs,
                        activeTabPath = uiState.activeTabPath,
                        fontSizeSp = prefs.editorFontSizeSp,
                        tabSize = prefs.tabSize,
                        wordWrapEnabled = prefs.wordWrapEnabled,
                        showActivityRail = false,
                        onValueChange = { viewModel.updateActiveEditorValue(it) },
                        onSelectTab = { viewModel.selectEditorTab(it) },
                        onCloseTab = { viewModel.closeEditorTab(it) },
                        onSave = { viewModel.saveActiveFile() },
                        onRun = { viewModel.runCurrentEditorFile() },
                        onUndo = { viewModel.undoEditor() },
                        onRedo = { viewModel.redoEditor() },
                        onInsertSnippet = { viewModel.insertAtCursor(it) },
                        onReplace = { f, r, all -> viewModel.replaceInActiveFile(f, r, all) },
                        onOpenExplorer = { viewModel.navigateTo(StonePage.EXPLORER) },
                        onNavigate = { viewModel.navigateTo(it) },
                        modifier = Modifier.weight(0.66f)
                    )
                }
            } else if (uiState.currentPage == StonePage.EXPLORER) {
                ExplorerScreen(
                    project = activeProject,
                    revision = uiState.explorerRevision,
                    onBack = { viewModel.navigateBack() },
                    onOpenFile = { viewModel.openFile(it, navigateToEditor = true) },
                    onRunFile = { file ->
                        when (file.extension.lowercase()) {
                            "js", "mjs" -> viewModel.launchJsRunner(file)
                            "sh" -> viewModel.runDebugShell("sh \"${file.name}\"")
                            else -> viewModel.launchWebPreview(file)
                        }
                    },
                    onCreateEntry = { rel, isDir -> viewModel.createEntry(rel, isDir) },
                    onRenameEntry = { file, newName -> viewModel.renameEntry(file, newName) },
                    onDeleteEntry = { viewModel.deleteEntry(it) }
                )
            } else {
                EditorScreen(
                    project = activeProject,
                    tabs = uiState.openTabs,
                    activeTabPath = uiState.activeTabPath,
                    fontSizeSp = prefs.editorFontSizeSp,
                    tabSize = prefs.tabSize,
                    wordWrapEnabled = prefs.wordWrapEnabled,
                    showActivityRail = true,
                    onValueChange = { viewModel.updateActiveEditorValue(it) },
                    onSelectTab = { viewModel.selectEditorTab(it) },
                    onCloseTab = { viewModel.closeEditorTab(it) },
                    onSave = { viewModel.saveActiveFile() },
                    onRun = { viewModel.runCurrentEditorFile() },
                    onUndo = { viewModel.undoEditor() },
                    onRedo = { viewModel.redoEditor() },
                    onInsertSnippet = { viewModel.insertAtCursor(it) },
                    onReplace = { f, r, all -> viewModel.replaceInActiveFile(f, r, all) },
                    onOpenExplorer = { viewModel.navigateTo(StonePage.EXPLORER) },
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }
        }

        StonePage.TERMINAL -> {
            if (isExpanded && uiState.activeTabPath != null) {
                Row(Modifier.fillMaxSize()) {
                    EditorScreen(
                        project = activeProject,
                        tabs = uiState.openTabs,
                        activeTabPath = uiState.activeTabPath,
                        fontSizeSp = prefs.editorFontSizeSp,
                        tabSize = prefs.tabSize,
                        wordWrapEnabled = prefs.wordWrapEnabled,
                        showActivityRail = false,
                        onValueChange = { viewModel.updateActiveEditorValue(it) },
                        onSelectTab = { viewModel.selectEditorTab(it) },
                        onCloseTab = { viewModel.closeEditorTab(it) },
                        onSave = { viewModel.saveActiveFile() },
                        onRun = { viewModel.runCurrentEditorFile() },
                        onUndo = { viewModel.undoEditor() },
                        onRedo = { viewModel.redoEditor() },
                        onInsertSnippet = { viewModel.insertAtCursor(it) },
                        onReplace = { f, r, all -> viewModel.replaceInActiveFile(f, r, all) },
                        onOpenExplorer = { viewModel.navigateTo(StonePage.EXPLORER) },
                        onNavigate = { viewModel.navigateTo(it) },
                        modifier = Modifier.weight(0.52f)
                    )
                    VerticalDivider(color = colors.line)
                    TerminalScreen(
                        project = activeProject,
                        tabs = terminalTabs,
                        activeTabId = activeTerminalId,
                        terminalManager = viewModel.terminalManager,
                        fontSizeSp = prefs.editorFontSizeSp,
                        onBack = null,
                        modifier = Modifier.weight(0.48f)
                    )
                }
            } else {
                TerminalScreen(
                    project = activeProject,
                    tabs = terminalTabs,
                    activeTabId = activeTerminalId,
                    terminalManager = viewModel.terminalManager,
                    fontSizeSp = prefs.editorFontSizeSp,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }

        StonePage.WEB_RUNNER -> {
            if (isExpanded && uiState.activeTabPath != null) {
                Row(Modifier.fillMaxSize()) {
                    EditorScreen(
                        project = activeProject,
                        tabs = uiState.openTabs,
                        activeTabPath = uiState.activeTabPath,
                        fontSizeSp = prefs.editorFontSizeSp,
                        tabSize = prefs.tabSize,
                        wordWrapEnabled = prefs.wordWrapEnabled,
                        showActivityRail = false,
                        onValueChange = { viewModel.updateActiveEditorValue(it) },
                        onSelectTab = { viewModel.selectEditorTab(it) },
                        onCloseTab = { viewModel.closeEditorTab(it) },
                        onSave = { viewModel.saveActiveFile() },
                        onRun = { viewModel.runCurrentEditorFile() },
                        onUndo = { viewModel.undoEditor() },
                        onRedo = { viewModel.redoEditor() },
                        onInsertSnippet = { viewModel.insertAtCursor(it) },
                        onReplace = { f, r, all -> viewModel.replaceInActiveFile(f, r, all) },
                        onOpenExplorer = { viewModel.navigateTo(StonePage.EXPLORER) },
                        onNavigate = { viewModel.navigateTo(it) },
                        modifier = Modifier.weight(0.5f)
                    )
                    VerticalDivider(color = colors.line)
                    WebRunnerScreen(
                        project = activeProject,
                        state = uiState.webRunner,
                        onBack = null,
                        onLog = { lvl, msg -> viewModel.appendConsoleLog(lvl, msg) },
                        onClearLogs = { viewModel.clearConsoleLogs() },
                        onToggleViewport = { viewModel.toggleWebViewport() },
                        onToggleNetwork = { viewModel.toggleWebNetworkPolicy() },
                        onReload = { viewModel.reloadWebRunner() },
                        onConsumeSnippet = { viewModel.consumePendingSnippet() },
                        modifier = Modifier.weight(0.5f)
                    )
                }
            } else {
                WebRunnerScreen(
                    project = activeProject,
                    state = uiState.webRunner,
                    onBack = { viewModel.navigateBack() },
                    onLog = { lvl, msg -> viewModel.appendConsoleLog(lvl, msg) },
                    onClearLogs = { viewModel.clearConsoleLogs() },
                    onToggleViewport = { viewModel.toggleWebViewport() },
                    onToggleNetwork = { viewModel.toggleWebNetworkPolicy() },
                    onReload = { viewModel.reloadWebRunner() },
                    onConsumeSnippet = { viewModel.consumePendingSnippet() }
                )
            }
        }

        StonePage.GIT -> GitScreen(
            project = activeProject,
            gitInfo = uiState.gitInfo,
            isBusy = uiState.gitBusy,
            authorName = prefs.gitAuthorName,
            authorEmail = prefs.gitAuthorEmail,
            selectedDiffPath = uiState.selectedDiffPath,
            selectedDiffContent = uiState.selectedDiffContent,
            onBack = { viewModel.navigateBack() },
            onRefresh = { viewModel.refreshGitStatus() },
            onInitRepo = { viewModel.initGitRepo() },
            onCheckoutBranch = { name, create -> viewModel.checkoutBranch(name, create) },
            onCommit = { viewModel.commitChanges(it) },
            onInspectDiff = { viewModel.inspectDiff(it) },
            onCloseDiff = { viewModel.closeDiff() },
            onDiscardFile = { viewModel.discardGitFile(it) }
        )

        StonePage.DEBUG -> DebugScreen(
            project = activeProject,
            selectedMode = uiState.debugMode,
            entryPath = uiState.debugEntryPath,
            shellCommand = uiState.debugShellCommand,
            onUpdateConfig = { m, e, s -> viewModel.updateDebugConfig(m, e, s) },
            onLaunchWebPreview = { viewModel.launchWebPreview(it) },
            onLaunchJsRunner = { viewModel.launchJsRunner(it) },
            onLaunchShell = { viewModel.runDebugShell(it) },
            onBack = { viewModel.navigateBack() }
        )

        StonePage.EXTENSIONS -> ExtensionsScreen(
            enabledProfiles = prefs.enabledProfiles,
            onToggleProfile = { viewModel.toggleProfile(it) },
            onBack = { viewModel.navigateBack() }
        )

        StonePage.SETTINGS -> SettingsScreen(
            prefs = prefs,
            onUpdateDisplayName = { viewModel.updateDisplayName(it) },
            onUpdateFontSize = { viewModel.updateFontSize(it) },
            onUpdateTabSize = { viewModel.updateTabSize(it) },
            onUpdateAutoSave = { viewModel.updateAutoSave(it) },
            onUpdateWordWrap = { viewModel.updateWordWrap(it) },
            onUpdateGitAuthor = { n, e -> viewModel.updateGitAuthor(n, e) },
            onOpenThemes = { viewModel.navigateTo(StonePage.THEMES) },
            onBack = { viewModel.navigateBack() }
        )

        StonePage.THEMES -> ThemesScreen(
            activeTheme = prefs.themeName,
            onSelectTheme = { viewModel.updateTheme(it) },
            onBack = { viewModel.navigateBack() }
        )

        StonePage.TOOLS -> ToolsScreen(
            onTerminal = { viewModel.navigateTo(StonePage.TERMINAL) },
            onWebRunner = { viewModel.launchWebPreview() },
            onJsRunner = { viewModel.launchJsRunner() },
            onGit = { viewModel.navigateTo(StonePage.GIT) },
            onDebug = { viewModel.navigateTo(StonePage.DEBUG) },
            onExtensions = { viewModel.navigateTo(StonePage.EXTENSIONS) },
            onImportZip = onImportZip,
            onSettings = { viewModel.navigateTo(StonePage.SETTINGS) },
            onBack = if (isWide) null else {
                { viewModel.navigateBack() }
            }
        )
    }
}

@Composable
private fun StoneBottomBar(
    currentPage: StonePage,
    onSelect: (StonePage) -> Unit
) {
    val colors = LocalStoneColors.current
    Column {
        HorizontalDivider(color = colors.line)
        NavigationBar(
            containerColor = colors.panel,
            windowInsets = WindowInsets.navigationBars,
            tonalElevation = 0.dp
        ) {
            TopNavDestinations.forEach { dest ->
                val selected = currentPage == dest.page
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(dest.page) },
                    modifier = Modifier.testTag(dest.tag),
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = StoneOrange,
                        selectedTextColor = StoneOrange,
                        indicatorColor = StoneOrangeMuted,
                        unselectedIconColor = colors.muted,
                        unselectedTextColor = colors.muted
                    )
                )
            }
        }
    }
}

@Composable
private fun StoneNavigationRail(
    currentPage: StonePage,
    onSelect: (StonePage) -> Unit,
    onWebRunner: () -> Unit,
    onGit: () -> Unit
) {
    val colors = LocalStoneColors.current
    NavigationRail(
        containerColor = colors.rail,
        header = {
            Box(Modifier.padding(vertical = 12.dp)) {
                StoneMark(26.dp)
            }
        }
    ) {
        TopNavDestinations.forEach { dest ->
            val selected = currentPage == dest.page || (dest.page == StonePage.EDITOR && currentPage == StonePage.EXPLORER)
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(dest.page) },
                modifier = Modifier.testTag("${dest.tag}_rail"),
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label, fontSize = 10.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = StoneOrange,
                    selectedTextColor = StoneOrange,
                    indicatorColor = StoneOrangeMuted,
                    unselectedIconColor = colors.muted,
                    unselectedTextColor = colors.muted
                )
            )
        }
        NavigationRailItem(
            selected = currentPage == StonePage.WEB_RUNNER,
            onClick = onWebRunner,
            icon = { Icon(Icons.Default.Public, contentDescription = "Web Preview") },
            label = { Text("Web", fontSize = 10.sp) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = StoneOrange,
                selectedTextColor = StoneOrange,
                indicatorColor = StoneOrangeMuted,
                unselectedIconColor = colors.muted,
                unselectedTextColor = colors.muted
            )
        )
        NavigationRailItem(
            selected = currentPage == StonePage.GIT,
            onClick = onGit,
            icon = { Icon(Icons.Default.Code, contentDescription = "Git") },
            label = { Text("Git", fontSize = 10.sp) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = StoneOrange,
                selectedTextColor = StoneOrange,
                indicatorColor = StoneOrangeMuted,
                unselectedIconColor = colors.muted,
                unselectedTextColor = colors.muted
            )
        )
    }
}
