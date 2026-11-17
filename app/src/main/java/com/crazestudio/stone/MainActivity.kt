package com.crazestudio.stone

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

private val Orange = Color(0xFFFF7900)
private data class StoneColors(
    val bg: Color, val panel: Color, val panel2: Color, val line: Color,
    val text: Color, val muted: Color, val success: Color
)
private val DarkStone = StoneColors(Color(0xFF0C0C0C), Color(0xFF171717), Color(0xFF202020), Color(0xFF353535), Color(0xFFF4F4F4), Color(0xFF9A9A9A), Color(0xFFB8D8C0))
private val LightStone = StoneColors(Color(0xFFF2F2F2), Color(0xFFFFFFFF), Color(0xFFE9E9E9), Color(0xFFD2D2D2), Color(0xFF171717), Color(0xFF656565), Color(0xFF486A50))
private val ObsidianStone = StoneColors(Color(0xFF050505), Color(0xFF101010), Color(0xFF191919), Color(0xFF2B2B2B), Color(0xFFF4F4F4), Color(0xFF888888), Color(0xFFB8D8C0))
private val MidnightStone = StoneColors(Color(0xFF11131A), Color(0xFF191B23), Color(0xFF222530), Color(0xFF373B48), Color(0xFFEEF0F5), Color(0xFF979CAB), Color(0xFFB8D8C0))
private val LocalStoneColors = androidx.compose.runtime.staticCompositionLocalOf { DarkStone }

class MainActivity : ComponentActivity() {
    private lateinit var projectRoot: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 12, 12)
        window.navigationBarColor = android.graphics.Color.rgb(12, 12, 12)
        projectRoot = File(filesDir, "projects").apply { mkdirs() }
        seedProject()
        setContent { StoneApp(this) }
    }

    fun projects(): List<File> = projectRoot.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
    fun defaultProject(): File = projects().firstOrNull() ?: seedProject()

    fun createProject(rawName: String): File {
        val name = rawName.trim().replace(Regex("[^A-Za-z0-9._ -]"), "").take(48).ifBlank { "Untitled Project" }
        val folder = uniqueFolder(File(projectRoot, name)).apply { mkdirs() }
        File(folder, "README.md").writeText("# $name\n\nCreated with STONE.\n")
        File(folder, "index.html").writeText("<!doctype html>\n<html lang=\"en\">\n<head><meta charset=\"utf-8\"><title>$name</title><link rel=\"stylesheet\" href=\"style.css\"></head>\n<body><main><h1>Hello, Stone.</h1><p>Your next project starts here.</p></main><script src=\"script.js\"></script></body>\n</html>\n")
        File(folder, "style.css").writeText("body { font-family: system-ui, sans-serif; margin: 3rem; background: #111; color: #f4f4f4; }\nh1 { letter-spacing: -0.04em; }\n")
        File(folder, "script.js").writeText("console.log('Hello from Stone');\n")
        return folder
    }

    private fun uniqueFolder(folder: File): File {
        if (!folder.exists()) return folder
        var index = 2
        while (File(folder.parentFile, "${folder.name} ($index)").exists()) index++
        return File(folder.parentFile, "${folder.name} ($index)")
    }

    private fun seedProject(): File {
        val folder = File(projectRoot, "StoneSample")
        if (!folder.exists()) {
            folder.mkdirs()
            File(folder, "main.py").writeText("def main():\n    print(\"Hello from Stone\")\n\nif __name__ == \"__main__\":\n    main()\n")
            File(folder, "index.html").writeText("<!doctype html>\n<html>\n  <head><title>Stone sample</title></head>\n  <body><h1>Build something.</h1></body>\n</html>\n")
            File(folder, "README.md").writeText("# Stone Sample\n\nA starter workspace for the Stone Android IDE.\n")
        }
        return folder
    }

    suspend fun importFolder(tree: Uri): File = withContext(Dispatchers.IO) {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val name = contentResolver.query(rootUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "Imported Project"
        } ?: "Imported Project"
        val safeName = name.replace(Regex("[^A-Za-z0-9._ -]"), "").take(48).ifBlank { "Imported Project" }
        val target = uniqueFolder(File(projectRoot, safeName)).apply { mkdirs() }
        copySafChildren(tree, id, target)
        target
    }

    private fun copySafChildren(tree: Uri, parentId: String, target: File) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0)
                val name = cursor.getString(1).orEmpty().replace("/", "_").replace("\\", "_").take(180)
                if (name.isBlank() || name == "." || name == "..") continue
                val destination = File(target, name)
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    destination.mkdirs()
                    copySafChildren(tree, childId, destination)
                } else {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                    contentResolver.openInputStream(uri)?.use { input ->
                        destination.parentFile?.mkdirs()
                        FileOutputStream(destination).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    suspend fun importZip(uri: Uri): File = withContext(Dispatchers.IO) {
        val target = File(projectRoot, "Imported ${System.currentTimeMillis().toString().takeLast(5)}").apply { mkdirs() }
        val root = target.canonicalPath + File.separator
        contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = File(target, entry.name)
                    require(output.canonicalPath.startsWith(root) || output.canonicalPath == target.canonicalPath) { "Unsafe ZIP entry rejected" }
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Could not read selected ZIP")
        target
    }

    suspend fun cloneRepository(url: String, rawName: String): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://", true)) { "Only HTTPS Git URLs are accepted." }
        val fallback = url.substringAfterLast('/').removeSuffix(".git")
        val name = rawName.trim().ifBlank { fallback }.replace(Regex("[^A-Za-z0-9._ -]"), "").take(48).ifBlank { "Cloned Project" }
        val target = uniqueFolder(File(projectRoot, name))
        Git.cloneRepository().setURI(url).setDirectory(target).call().close()
        target
    }

    suspend fun executeCommand(command: String, directory: File): String = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "Enter a command first." }
        val safeDirectory = if (directory.canonicalPath.startsWith(filesDir.canonicalPath)) directory else filesDir
        val log = File(cacheDir, "stone-terminal-${System.currentTimeMillis()}.log")
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(safeDirectory).redirectErrorStream(true).redirectOutput(log).start()
        val complete = process.waitFor(20, TimeUnit.SECONDS)
        if (!complete) {
            process.destroyForcibly()
            "Process stopped after 20 seconds (command timeout)."
        } else {
            val text = if (log.exists()) log.readText().take(12000) else ""
            (text.ifBlank { "(no output)" }) + "\n[exit ${process.exitValue()}]"
        }
    }

    suspend fun gitStatus(folder: File): List<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(folder).use { git ->
                val status = git.status().call()
                buildList {
                    status.untracked.sorted().forEach { add("U  $it") }
                    status.added.sorted().forEach { add("A  $it") }
                    status.changed.sorted().forEach { add("M  $it") }
                    status.modified.sorted().forEach { add("M  $it") }
                    status.missing.sorted().forEach { add("D  $it") }
                    status.removed.sorted().forEach { add("D  $it") }
                    status.conflicting.sorted().forEach { add("!  $it") }
                    if (isEmpty()) add("Working tree clean")
                }
            }
        } catch (_: Exception) { listOf("Git is not initialized in this project.") }
    }

    suspend fun initGit(folder: File): String = withContext(Dispatchers.IO) {
        try {
            if (File(folder, ".git").exists()) "Git is already initialized."
            else { Git.init().setDirectory(folder).call().close(); "Initialized an empty Git repository." }
        } catch (e: Exception) { e.message ?: "Could not initialize Git." }
    }

    suspend fun commit(folder: File, message: String): String = withContext(Dispatchers.IO) {
        try {
            Git.open(folder).use { git ->
                git.add().addFilepattern(".").call()
                val commit = git.commit().setMessage(message.ifBlank { "Update from Stone" })
                    .setAuthor("Stone User", "user@stone.local").call()
                "Committed ${commit.id.abbreviate(8)}"
            }
        } catch (e: Exception) { e.message ?: "Commit failed." }
    }
}

private enum class Page { SPLASH, HOME, PROJECTS, EDITOR, TERMINAL, EXPLORER, EXTENSIONS, GIT, DEBUG, SETTINGS, THEMES, TOOLS }

@Composable
private fun StonePalette(theme: String, content: @Composable () -> Unit) {
    val palette = when (theme) {
        "Stone Light" -> LightStone
        "Obsidian" -> ObsidianStone
        "Midnight" -> MidnightStone
        else -> DarkStone
    }
    val scheme = if (theme == "Stone Light") lightColorScheme(
        primary = Orange, background = palette.bg, onBackground = palette.text,
        surface = palette.panel, onSurface = palette.text, outline = palette.line
    ) else darkColorScheme(
        primary = Orange, background = palette.bg, onBackground = palette.text,
        surface = palette.panel, onSurface = palette.text, outline = palette.line
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalStoneColors provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
private fun StoneApp(activity: MainActivity) {
    var page by rememberSaveable { mutableStateOf(Page.SPLASH) }
    val projects = remember { mutableStateListOf<File>().apply { addAll(activity.projects()) } }
    var projectPath by rememberSaveable { mutableStateOf(activity.defaultProject().absolutePath) }
    val project = File(projectPath)
    val tabs = remember { mutableStateListOf<String>() }
    var activePath by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf("") }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var theme by rememberSaveable { mutableStateOf("Stone Dark") }
    var fontSize by rememberSaveable { mutableFloatStateOf(14f) }
    var command by rememberSaveable { mutableStateOf("") }
    var terminalOutput by rememberSaveable { mutableStateOf("STONE TERMINAL\nAndroid app sandbox. Enter a command below.\n") }
    var commitMessage by rememberSaveable { mutableStateOf("Update project") }
    var gitStatus by remember { mutableStateOf(listOf("Loading Git status…")) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var newProjectDialog by remember { mutableStateOf(false) }
    var cloneDialog by remember { mutableStateOf(false) }
    var searchDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var cloneUrl by remember { mutableStateOf("") }
    var cloneName by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var debugCommand by rememberSaveable { mutableStateOf("python main.py") }
    val extensions = remember { mutableStateMapOf<String, Boolean>().apply {
        put("HTML / CSS / JavaScript", true); put("Python", true); put("Java", false); put("C / C++", false); put("GitLens", false)
    } }
    val scope = rememberCoroutineScope()

    fun refreshProjects() { projects.clear(); projects.addAll(activity.projects()) }
    fun saveFile() {
        val path = activePath ?: return
        runCatching { File(path).writeText(editor) }.onSuccess { dirty = false; statusMessage = "Saved ${File(path).name}" }
            .onFailure { statusMessage = it.message ?: "Save failed." }
    }
    fun openFile(file: File) {
        if (!file.isFile) return
        if (dirty) saveFile()
        activePath = file.absolutePath
        if (!tabs.contains(file.absolutePath)) tabs.add(file.absolutePath)
        editor = runCatching { file.readText() }.getOrElse { "Could not open file: ${it.message}" }
        dirty = false
        page = Page.EDITOR
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                activity.contentResolver.takePersistableUriPermission(uri, flags)
                val imported = activity.importFolder(uri)
                refreshProjects(); projectPath = imported.absolutePath; page = Page.EXPLORER
                statusMessage = "Imported ${imported.name}"
            } catch (e: Exception) { statusMessage = e.message ?: "Folder import failed." }
        }
    }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val imported = activity.importZip(uri)
                refreshProjects(); projectPath = imported.absolutePath; page = Page.EXPLORER
                statusMessage = "Imported ${imported.name}"
            } catch (e: Exception) { statusMessage = e.message ?: "ZIP import failed." }
        }
    }

    StonePalette(theme) {
        Surface(Modifier.fillMaxSize(), color = LocalStoneColors.current.bg) {
            when (page) {
                Page.SPLASH -> SplashScreen()
                Page.HOME -> HomeScreen(projects, onNew = { projectName = ""; newProjectDialog = true }, onOpen = { folderPicker.launch(null) },
                    onClone = { cloneUrl = ""; cloneName = ""; cloneDialog = true }, onImport = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    onProject = { projectPath = it.absolutePath; page = Page.EXPLORER }, onNavigate = { page = it }, onSearch = { searchDialog = true })
                Page.PROJECTS -> ProjectsScreen(projects, onBack = { page = Page.HOME }, onProject = { projectPath = it.absolutePath; page = Page.EXPLORER }, onNew = { projectName = ""; newProjectDialog = true }, onOpen = { folderPicker.launch(null) })
                Page.EDITOR -> EditorScreen(project, tabs, activePath, editor, dirty, fontSize,
                    onText = { editor = it; dirty = true },
                    onTab = { path -> if (dirty) saveFile(); activePath = path; editor = runCatching { File(path).readText() }.getOrDefault(""); dirty = false },
                    onClose = { path -> tabs.remove(path); if (activePath == path) { activePath = tabs.lastOrNull(); editor = activePath?.let { runCatching { File(it).readText() }.getOrDefault("") } ?: ""; dirty = false } },
                    onSave = { saveFile() }, onTerminal = { page = Page.TERMINAL }, onRun = {
                        val file = activePath?.let(::File)
                        if (file != null) {
                            command = when (file.extension.lowercase(Locale.ROOT)) {
                                "py" -> "python ${file.name}"; "js", "mjs" -> "node ${file.name}"; "php" -> "php ${file.name}"; "sh" -> "sh ${file.name}"
                                else -> "echo 'No runtime configured for .${file.extension} files in this Android environment.'"
                            }
                            page = Page.TERMINAL
                        }
                    }, onTool = { page = it }, onExplorer = { page = Page.EXPLORER })
                Page.EXPLORER -> ExplorerScreen(project, onBack = { page = Page.HOME }, onOpen = { openFile(it) }, onCreate = { relative ->
                    val root = project.canonicalFile
                    val file = File(root, relative.trim()).canonicalFile
                    if (file.path.startsWith(root.path + File.separator) && file.name.isNotBlank()) {
                        file.parentFile?.mkdirs()
                        if (!file.exists()) file.writeText("")
                        openFile(file)
                    } else statusMessage = "Invalid file path."
                }, onDelete = { file -> runCatching { file.deleteRecursively() }; statusMessage = "Removed ${file.name}" })
                Page.TERMINAL -> TerminalScreen(terminalOutput, command, onCommand = { command = it }, onBack = { page = Page.EDITOR }, onClear = { terminalOutput = "STONE TERMINAL\n" }, onRun = {
                    val cmd = command.trim()
                    if (cmd.isNotEmpty()) { terminalOutput += "\n${project.name} $ $cmd\n"; command = ""; scope.launch {
                        val result = runCatching { activity.executeCommand(cmd, project) }.getOrElse { it.message ?: "Command failed." }
                        terminalOutput += "$result\n"
                    } }
                })
                Page.GIT -> GitScreen(project, gitStatus, commitMessage, { commitMessage = it }, onBack = { page = Page.HOME }, onRefresh = { scope.launch { gitStatus = activity.gitStatus(project) } }, onInit = { scope.launch { statusMessage = activity.initGit(project); gitStatus = activity.gitStatus(project) } }, onCommit = { scope.launch { statusMessage = activity.commit(project, commitMessage); gitStatus = activity.gitStatus(project) } })
                Page.EXTENSIONS -> ExtensionsScreen(extensions, { name -> extensions[name] = !(extensions[name] ?: false) }, { page = Page.HOME })
                Page.DEBUG -> DebugScreen(debugCommand, { debugCommand = it }, { command = debugCommand; page = Page.TERMINAL }, { page = Page.HOME })
                Page.SETTINGS -> SettingsScreen(theme, fontSize, { theme = it }, { fontSize = it }, { page = Page.HOME }, { page = Page.THEMES })
                Page.THEMES -> ThemesScreen(theme, { theme = it }, { page = Page.SETTINGS })
                Page.TOOLS -> ToolsScreen({ page = Page.TERMINAL }, { page = Page.GIT }, { page = Page.DEBUG }, { page = Page.EXTENSIONS }, { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, { page = Page.SETTINGS }, { page = Page.HOME })
            }
        }
    }

    if (newProjectDialog) StoneDialog("New Project", "Create", onDismiss = { newProjectDialog = false }, onConfirm = {
        val folder = activity.createProject(projectName); refreshProjects(); projectPath = folder.absolutePath; page = Page.EXPLORER; newProjectDialog = false
    }) { StoneInput(projectName, { projectName = it }, "Project name") }

    if (cloneDialog) StoneDialog("Clone Repository", "Clone", onDismiss = { cloneDialog = false }, onConfirm = {
        cloneDialog = false
        val url = cloneUrl; val name = cloneName
        scope.launch {
            runCatching { activity.cloneRepository(url, name) }.onSuccess { folder -> refreshProjects(); projectPath = folder.absolutePath; page = Page.EXPLORER; statusMessage = "Cloned ${folder.name}" }
                .onFailure { statusMessage = it.message ?: "Clone failed." }
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StoneInput(cloneUrl, { cloneUrl = it }, "https://github.com/user/repository.git")
            StoneInput(cloneName, { cloneName = it }, "Folder name (optional)")
        }
    }

    if (searchDialog) StoneDialog("Search Projects", "Done", onDismiss = { searchDialog = false }, onConfirm = { searchDialog = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StoneInput(searchText, { searchText = it }, "Search project names")
            projects.filter { it.name.contains(searchText, true) }.forEach { file ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(LocalStoneColors.current.panel2).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(file.name, color = LocalStoneColors.current.text, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = { projectPath = file.absolutePath; searchDialog = false; page = Page.EXPLORER }) { Text("Open", color = Orange) }
                }
            }
        }
    }

    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1200); if (page == Page.SPLASH) page = Page.HOME }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show(); kotlinx.coroutines.delay(2300); statusMessage = null }
    }
    LaunchedEffect(page, projectPath) { if (page == Page.GIT) gitStatus = activity.gitStatus(project) }
}


@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(LocalStoneColors.current.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StoneMark(82.dp)
            Text("STONE", color = LocalStoneColors.current.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, modifier = Modifier.padding(top = 14.dp))
            Text("by CrazeStudio", color = LocalStoneColors.current.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(150.dp))
            Box(Modifier.width(150.dp).height(4.dp).clip(CircleShape).background(LocalStoneColors.current.line)) { Box(Modifier.fillMaxWidth(.56f).fillMaxHeight().clip(CircleShape).background(Orange)) }
        }
    }
}

@Composable
private fun HomeScreen(projects: List<File>, onNew: () -> Unit, onOpen: () -> Unit, onClone: () -> Unit, onImport: () -> Unit, onProject: (File) -> Unit, onNavigate: (Page) -> Unit, onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            StoneBrand(true); Spacer(Modifier.weight(1f)); IconAction(Icons.Default.Search, "Search", onSearch); IconAction(Icons.Default.NotificationsNone, "Notifications", click = {})
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp)) {
            Text("Hello, Mehetab 👋", color = LocalStoneColors.current.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Continue your work", color = LocalStoneColors.current.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp, bottom = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoneCard(Modifier.weight(1f).height(62.dp), onClick = { onNavigate(Page.PROJECTS) }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, tint = LocalStoneColors.current.muted, modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 12.dp)) { Text("My Projects", color = LocalStoneColors.current.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("${projects.size} projects", color = LocalStoneColors.current.muted, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.width(9.dp))
                Surface(onClick = onNew, color = Orange, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(62.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(27.dp)) } }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActionTile(Icons.Default.Add, "New Project", "Create from template", onNew, Modifier.weight(1f))
                ActionTile(Icons.Default.FolderOpen, "Open", "From storage", onOpen, Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActionTile(Icons.Default.Download, "Clone Repo", "From GitHub", onClone, Modifier.weight(1f))
                ActionTile(Icons.Default.UploadFile, "Import", "ZIP or folder", onImport, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Projects", color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Spacer(Modifier.weight(1f)); TextButton(onClick = { onNavigate(Page.PROJECTS) }) { Text("View all", color = LocalStoneColors.current.muted, fontSize = 11.sp) }
            }
            if (projects.isEmpty()) EmptyState("No projects yet", "Create a project or import an existing folder.")
            projects.take(6).forEach { file ->
                StoneCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { onProject(file) }) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = LocalStoneColors.current.muted, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(file.name, color = LocalStoneColors.current.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("~/${file.name}", color = LocalStoneColors.current.muted, fontSize = 10.sp) }
                        Icon(Icons.Default.ChevronRight, null, tint = LocalStoneColors.current.muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        BottomNav(onNavigate)
    }
}

@Composable
private fun ProjectsScreen(projects: List<File>, onBack: () -> Unit, onProject: (File) -> Unit, onNew: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Projects", onBack, { IconAction(Icons.Default.Add, "New project", onNew) })
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { SecondaryButton("New Project", onNew, Modifier.weight(1f)); SecondaryButton("Open Folder", onOpen, Modifier.weight(1f)) }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(projects, key = { it.absolutePath }) { file -> StoneCard(Modifier.fillMaxWidth(), onClick = { onProject(file) }) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = LocalStoneColors.current.muted); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(file.name, color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold); Text("${file.walkTopDown().count { it.isFile }} files", color = LocalStoneColors.current.muted, fontSize = 10.sp) }; Icon(Icons.Default.ChevronRight, null, tint = LocalStoneColors.current.muted) }
            } }
        }
    }
}

@Composable
private fun EditorScreen(project: File, tabs: List<String>, active: String?, text: String, dirty: Boolean, fontSize: Float,
    onText: (String) -> Unit, onTab: (String) -> Unit, onClose: (String) -> Unit, onSave: () -> Unit, onTerminal: () -> Unit, onRun: () -> Unit, onTool: (Page) -> Unit, onExplorer: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Default.Menu, "Explorer", onExplorer); IconAction(Icons.Default.ArrowBack, "Home", click = { onTool(Page.HOME) })
            Spacer(Modifier.weight(1f)); StoneMark(24.dp); Spacer(Modifier.weight(1f))
            IconAction(Icons.Default.Save, "Save", onSave); IconAction(Icons.Default.PlayArrow, "Run", onRun, Orange); IconAction(Icons.Default.MoreVert, "Terminal", onTerminal)
        }
        Divider(color = LocalStoneColors.current.line)
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(46.dp).fillMaxHeight().background(Color(0xFF101010)), horizontalAlignment = Alignment.CenterHorizontally) {
                RailIcon(Icons.Default.InsertDriveFile, "Explorer", onExplorer)
                RailIcon(Icons.Default.Search, "Search") { onTool(Page.TOOLS) }
                RailIcon(Icons.Default.Code, "Source control") { onTool(Page.GIT) }
                RailIcon(Icons.Default.BugReport, "Run and debug") { onTool(Page.DEBUG) }
                RailIcon(Icons.Default.Extension, "Extensions") { onTool(Page.EXTENSIONS) }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().height(41.dp).background(LocalStoneColors.current.panel).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { path ->
                        Row(Modifier.background(if (path == active) LocalStoneColors.current.panel2 else LocalStoneColors.current.panel).padding(start = 9.dp, end = 5.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, null, tint = LocalStoneColors.current.muted, modifier = Modifier.size(13.dp))
                            Text(File(path).name, color = if (path == active) LocalStoneColors.current.text else LocalStoneColors.current.muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp))
                            if (path == active && dirty) Text("•", color = Orange, fontSize = 12.sp)
                            IconAction(Icons.Default.Close, "Close tab", { onClose(path) }, size = 15.dp)
                        }
                    }
                }
                Text("${project.name}  ›  ${active?.let { runCatching { File(it).relativeTo(project).path }.getOrNull() } ?: "No file"}", color = LocalStoneColors.current.muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().background(LocalStoneColors.current.panel).padding(horizontal = 12.dp, vertical = 7.dp), maxLines = 1)
                if (active == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EmptyState("No file open", "Choose a file from the explorer.") }
                else Row(Modifier.weight(1f).fillMaxWidth().background(LocalStoneColors.current.bg)) {
                    Column(Modifier.width(36.dp).padding(top = 11.dp), horizontalAlignment = Alignment.End) {
                        repeat((text.count { it == '\n' } + 1).coerceAtMost(600)) { number -> Text("${number + 1}", color = Color(0xFF666666), fontFamily = FontFamily.Monospace, fontSize = (fontSize - 2).sp, modifier = Modifier.padding(end = 8.dp).height((fontSize + 7).dp)) }
                    }
                    BasicTextField(value = text, onValueChange = onText, modifier = Modifier.fillMaxSize().padding(top = 10.dp, end = 8.dp), textStyle = TextStyle(color = LocalStoneColors.current.text, fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp), cursorBrush = SolidColor(Orange), visualTransformation = CodeTransform, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, keyboardType = KeyboardType.Ascii))
                }
                Row(Modifier.fillMaxWidth().height(28.dp).background(LocalStoneColors.current.panel).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (dirty) "● Unsaved" else "✓ Saved", color = if (dirty) Orange else LocalStoneColors.current.muted, fontSize = 9.sp)
                    Spacer(Modifier.weight(1f)); Text("Ln 1, Col 1   UTF-8   ${active?.let { File(it).extension.uppercase(Locale.ROOT) } ?: "Plain Text"}", color = LocalStoneColors.current.muted, fontSize = 9.sp)
                    IconAction(Icons.Default.Terminal, "Terminal", onTerminal, size = 16.dp)
                }
            }
        }
    }
}

private val CodeTransform = VisualTransformation { source ->
    val builder = AnnotatedString.Builder(source.text)
    val keywords = Regex("\\b(fun|class|object|val|var|const|let|return|if|else|for|while|when|import|from|def|print|public|private|static|void|int|String|new|true|false|null|try|catch|async|await|function|export|default)\\b")
    keywords.findAll(source.text).forEach { builder.addStyle(SpanStyle(color = Orange), it.range.first, it.range.last + 1) }
    Regex("\"[^\"\\n]*\"|'[^'\\n]*'").findAll(source.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFFBDBDBD)), it.range.first, it.range.last + 1) }
    Regex("//[^\\n]*|#[^\\n]*").findAll(source.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFF777777)), it.range.first, it.range.last + 1) }
    TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
}

@Composable
private fun ExplorerScreen(project: File, onBack: () -> Unit, onOpen: (File) -> Unit, onCreate: (String) -> Unit, onDelete: (File) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var relativePath by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    val files = project.walkTopDown().drop(1).filter { showHidden || !it.name.startsWith(".") }.toList().sortedWith(compareBy<File>({ it.isFile }, { it.relativeTo(project).path.lowercase(Locale.ROOT) }))
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Explorer", onBack, { IconAction(Icons.Default.Add, "New file", click = { relativePath = ""; showCreate = true }); IconAction(Icons.Default.MoreVert, "Show hidden files", click = { showHidden = !showHidden }) })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ExpandMore, null, tint = LocalStoneColors.current.muted); Text(project.name, color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            items(files, key = { it.absolutePath }) { file ->
                val depth = file.relativeTo(project).path.count { it == File.separatorChar }
                Row(Modifier.fillMaxWidth().padding(start = (depth * 12 + 5).dp, end = 5.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = LocalStoneColors.current.muted, modifier = Modifier.size(17.dp))
                    Text(file.name, color = if (file.isDirectory) LocalStoneColors.current.text else Color(0xFFD1D1D1), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
                    if (file.isFile) TextButton(onClick = { onOpen(file) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("Open", color = LocalStoneColors.current.muted, fontSize = 10.sp) }
                    IconAction(Icons.Default.Delete, "Delete", { onDelete(file) }, tint = LocalStoneColors.current.muted, size = 15.dp)
                }
            }
        }
    }
    if (showCreate) StoneDialog("New File", "Create", onDismiss = { showCreate = false }, onConfirm = { showCreate = false; if (relativePath.isNotBlank()) onCreate(relativePath); relativePath = "" }) { StoneInput(relativePath, { relativePath = it }, "relative/path/file.ext") }
}

@Composable
private fun TerminalScreen(output: String, command: String, onCommand: (String) -> Unit, onBack: () -> Unit, onRun: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Terminal", onBack, { TextButton(onClick = onClear) { Text("Clear", color = LocalStoneColors.current.muted, fontSize = 12.sp) } })
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) { Text("TERMINAL", color = LocalStoneColors.current.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(20.dp)); Text("OUTPUT", color = LocalStoneColors.current.muted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("+", color = LocalStoneColors.current.muted, fontSize = 20.sp) }
        Divider(color = LocalStoneColors.current.line)
        Text(output, color = Color(0xFFE3E3E3), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(15.dp))
        Divider(color = LocalStoneColors.current.line)
        Row(Modifier.fillMaxWidth().background(LocalStoneColors.current.panel).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("›", color = LocalStoneColors.current.success, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            BasicTextField(value = command, onValueChange = onCommand, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), textStyle = TextStyle(color = LocalStoneColors.current.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp), cursorBrush = SolidColor(Orange), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.None, autoCorrect = false), decorationBox = { inner -> Box { if (command.isEmpty()) Text("Enter command…", color = LocalStoneColors.current.muted, fontSize = 13.sp); inner() } })
            IconAction(Icons.Default.PlayArrow, "Run command", onRun, Orange)
        }
    }
}

@Composable
private fun GitScreen(project: File, status: List<String>, message: String, onMessage: (String) -> Unit, onBack: () -> Unit, onRefresh: () -> Unit, onInit: () -> Unit, onCommit: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Git", onBack, { IconAction(Icons.Default.Refresh, "Refresh", onRefresh) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)) {
            Text("Source Control", color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
            StoneCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Code, null, tint = LocalStoneColors.current.muted); Text("main", color = LocalStoneColors.current.text, modifier = Modifier.padding(start = 10.dp).weight(1f), fontSize = 12.sp); Text("⌄", color = LocalStoneColors.current.muted) } }
            Text("Changes (${status.count { it.startsWith("U") || it.startsWith("M") || it.startsWith("A") || it.startsWith("D") }})", color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 17.dp, bottom = 8.dp))
            StoneCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { status.forEach { line -> Row(verticalAlignment = Alignment.CenterVertically) { Text(line.take(1), color = Orange, fontFamily = FontFamily.Monospace); Text(line.drop(2), color = Color(0xFFD5D5D5), fontSize = 12.sp, modifier = Modifier.padding(start = 9.dp)) } } } }
            StoneInput(message, onMessage, "Commit message", Modifier.padding(top = 15.dp))
            Button(onClick = onCommit, modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(45.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Check, null); Text("  Commit", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onInit, modifier = Modifier.fillMaxWidth()) { Text("Initialize Git repository", color = LocalStoneColors.current.muted) }
            Text("Project: ${project.name}", color = LocalStoneColors.current.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ExtensionsScreen(installed: Map<String, Boolean>, onToggle: (String) -> Unit, onBack: () -> Unit) {
    val catalogue = listOf("HTML / CSS / JavaScript" to "Built-in web language profile", "Python" to "Python syntax profile and templates", "Java" to "Java syntax profile and templates", "C / C++" to "C-family syntax profile and templates", "GitLens" to "Local repository status and commit helpers")
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Extensions", onBack)
        StoneInput(query, { query = it }, "Search extensions…", Modifier.padding(horizontal = 15.dp, vertical = 10.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) { Text("Marketplace", color = LocalStoneColors.current.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("Installed (${installed.values.count { it }})", color = LocalStoneColors.current.muted, fontSize = 12.sp); Text("Updates", color = LocalStoneColors.current.muted, fontSize = 12.sp) }
        Divider(color = Orange, thickness = 2.dp, modifier = Modifier.width(85.dp).padding(start = 15.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)) {
            catalogue.filter { it.first.contains(query, true) || it.second.contains(query, true) }.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = LocalStoneColors.current.panel2, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, tint = LocalStoneColors.current.text) } }
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(item.first, color = LocalStoneColors.current.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(item.second, color = LocalStoneColors.current.muted, fontSize = 10.sp); Text("Stone profile", color = LocalStoneColors.current.muted, fontSize = 9.sp) }
                    TextButton(onClick = { onToggle(item.first) }) { Text(if (installed[item.first] == true) "Installed" else "Enable", color = if (installed[item.first] == true) LocalStoneColors.current.muted else Orange, fontSize = 11.sp) }
                }
                Divider(color = LocalStoneColors.current.line)
            }
        }
    }
}

@Composable
private fun DebugScreen(command: String, onCommand: (String) -> Unit, onRun: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Run & Debug", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(15.dp)) {
            Text("Launch Configuration", color = LocalStoneColors.current.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            StoneCard(Modifier.fillMaxWidth().padding(top = 10.dp)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Text("Local process", color = LocalStoneColors.current.text, modifier = Modifier.weight(1f), fontSize = 12.sp); Text("⌄", color = LocalStoneColors.current.muted) } }
            Text("Command", color = LocalStoneColors.current.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 17.dp, bottom = 7.dp))
            StoneInput(command, onCommand, "python main.py")
            Button(onClick = onRun, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(45.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.PlayArrow, null); Text("  Start Process", fontWeight = FontWeight.SemiBold) }
            Text("Breakpoints", color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            StoneCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.BugReport, null, tint = LocalStoneColors.current.muted); Text("Language debug adapters are not connected yet.", color = LocalStoneColors.current.muted, fontSize = 10.sp, modifier = Modifier.padding(start = 9.dp)) } }
            listOf("Variables", "Call Stack", "Watch").forEach { title -> StoneCard(Modifier.fillMaxWidth().padding(top = 8.dp)) { Row(Modifier.padding(12.dp)) { Text(title, color = LocalStoneColors.current.text, fontSize = 12.sp, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = LocalStoneColors.current.muted) } } }
        }
    }
}

@Composable
private fun SettingsScreen(theme: String, fontSize: Float, onTheme: (String) -> Unit, onFont: (Float) -> Unit, onBack: () -> Unit, onThemes: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(15.dp)) {
            StoneInput("", {}, "Search settings…")
            SettingsGroup("Editor") {
                Text("Font size · ${fontSize.toInt()} sp", color = LocalStoneColors.current.text, fontSize = 12.sp)
                Slider(value = fontSize, onValueChange = onFont, valueRange = 11f..22f, steps = 10, colors = SliderDefaults.colors(thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = LocalStoneColors.current.line))
                SettingRow(Icons.Default.Code, "Font family", "Monospace")
                SettingRow(Icons.Default.Save, "Auto save", "Off")
            }
            SettingsGroup("Theme") { SettingRow(Icons.Default.Palette, "Appearance and colors", theme, onThemes) }
            SettingsGroup("Terminal") { SettingRow(Icons.Default.Terminal, "Shell environment", "Android app sandbox"); SettingRow(Icons.Default.Settings, "Startup", "Project directory") }
            SettingsGroup("Tools") { SettingRow(Icons.Default.BugReport, "Build, run and debugger", "Local process runner"); SettingRow(Icons.Default.Extension, "Extensions", "Stone language profiles") }
            SettingsGroup("About") { SettingRow(Icons.Default.Code, "Stone", "Version 1.0.0 · CrazeStudio") }
        }
    }
}

@Composable
private fun ThemesScreen(theme: String, onTheme: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Themes", onBack)
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Stone Dark", "Stone Light", "Obsidian", "Midnight").forEach { name ->
                StoneCard(Modifier.fillMaxWidth(), onClick = { onTheme(name) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (name == "Stone Light") Color(0xFFECECEC) else Color(0xFF111111), shape = RoundedCornerShape(7.dp), modifier = Modifier.size(60.dp, 42.dp), border = BorderStroke(1.dp, LocalStoneColors.current.line)) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.fillMaxWidth(.7f).height(3.dp).background(if (name == "Stone Light") Color.DarkGray else Color.LightGray, CircleShape)); Box(Modifier.fillMaxWidth(.45f).height(3.dp).background(Orange, CircleShape)); Box(Modifier.fillMaxWidth(.85f).height(3.dp).background(if (name == "Stone Light") Color.Gray else Color.DarkGray, CircleShape)) }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(name, color = LocalStoneColors.current.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); if (name == "Stone Dark") Text("(Default)", color = LocalStoneColors.current.muted, fontSize = 10.sp) }
                        Text(if (theme == name) "●" else "○", color = if (theme == name) Orange else LocalStoneColors.current.muted, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen(onTerminal: () -> Unit, onGit: () -> Unit, onDebug: () -> Unit, onExtensions: () -> Unit, onImport: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalStoneColors.current.bg)) {
        PageHeader("Tools", onBack)
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ToolRow(Icons.Default.Terminal, "Terminal", "Run commands in the app sandbox", onTerminal)
            ToolRow(Icons.Default.Code, "Git", "Repository status and local commits", onGit)
            ToolRow(Icons.Default.BugReport, "Run & Debug", "Launch a local process", onDebug)
            ToolRow(Icons.Default.Extension, "Extensions", "Manage local language profiles", onExtensions)
            ToolRow(Icons.Default.UploadFile, "Import ZIP", "Import a project archive", onImport)
            ToolRow(Icons.Default.Settings, "Settings", "Editor, terminal and appearance", onSettings)
        }
    }
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    StoneCard(Modifier.fillMaxWidth(), onClick = onClick) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = LocalStoneColors.current.muted); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, color = LocalStoneColors.current.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(detail, color = LocalStoneColors.current.muted, fontSize = 10.sp) }; Icon(Icons.Default.ChevronRight, null, tint = LocalStoneColors.current.muted) } }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 17.dp)) { Text(title, color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 9.dp)); StoneCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), content = content) } }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalStoneColors.current.muted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(title, color = LocalStoneColors.current.text, fontSize = 12.sp); Text(detail, color = LocalStoneColors.current.muted, fontSize = 10.sp) }
        IconAction(Icons.Default.ChevronRight, "Open", onClick, size = 16.dp)
    }
}

@Composable
private fun PageHeader(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(53.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(Icons.Default.ArrowBack, "Back", back)
        Text(title, color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
        actions()
    }
    Divider(color = LocalStoneColors.current.line)
}

@Composable
private fun StoneBrand(compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) { StoneMark(if (compact) 24.dp else 34.dp); Text("STONE", color = LocalStoneColors.current.text, fontSize = if (compact) 17.sp else 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 8.dp)) }
}

@Composable
private fun StoneMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val left = Path().apply { moveTo(w * .04f, h * .25f); lineTo(w * .32f, h * .05f); lineTo(w * .57f, h * .05f); lineTo(w * .29f, h * .95f); lineTo(w * .04f, h * .95f); close() }
        val right = Path().apply { moveTo(w * .43f, h * .05f); lineTo(w * .72f, h * .05f); lineTo(w * .98f, h * .25f); lineTo(w * .70f, h * .95f); lineTo(w * .45f, h * .95f); lineTo(w * .72f, h * .35f); close() }
        drawPath(left, Color(0xFFF1F1F1)); drawPath(right, Color(0xFFB6B6B6))
    }
}

@Composable
private fun BottomNav(onNavigate: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF151515)).padding(top = 6.dp, bottom = 7.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(Triple(Page.HOME, Icons.Default.Home, "Home"), Triple(Page.PROJECTS, Icons.Default.Folder, "Projects"), Triple(Page.GIT, Icons.Default.Code, "Git"), Triple(Page.TOOLS, Icons.Default.Menu, "Tools")).forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) { IconAction(item.second, item.third, { onNavigate(item.first) }, tint = if (item.first == Page.HOME) Orange else LocalStoneColors.current.muted); Text(item.third, color = if (item.first == Page.HOME) Orange else LocalStoneColors.current.muted, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, title: String, detail: String, click: () -> Unit, modifier: Modifier = Modifier) {
    StoneCard(modifier.height(76.dp), onClick = click) { Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = LocalStoneColors.current.muted, modifier = Modifier.size(18.dp)); Text(title, color = LocalStoneColors.current.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp)) }; Text(detail, color = LocalStoneColors.current.muted, fontSize = 9.sp, modifier = Modifier.padding(start = 25.dp, top = 3.dp)) } }
}

@Composable
private fun StoneCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(onClick = onClick ?: {}, enabled = onClick != null, modifier = modifier, shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = LocalStoneColors.current.panel2, disabledContainerColor = LocalStoneColors.current.panel2), border = BorderStroke(1.dp, LocalStoneColors.current.line), elevation = CardDefaults.cardElevation(0.dp), content = content)
}

@Composable
private fun IconAction(icon: ImageVector, description: String, click: () -> Unit, tint: Color = Color.Unspecified, size: Dp = 20.dp) {
    val resolvedTint = if (tint == Color.Unspecified) LocalStoneColors.current.text else tint
    IconButton(onClick = click, modifier = Modifier.size(38.dp)) { Icon(icon, contentDescription = description, tint = resolvedTint, modifier = Modifier.size(size)) }
}

@Composable
private fun RailIcon(icon: ImageVector, label: String, click: () -> Unit) = IconAction(icon, label, click, LocalStoneColors.current.muted, 20.dp)

@Composable
private fun SecondaryButton(label: String, click: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = click, modifier = modifier.height(42.dp), colors = ButtonDefaults.buttonColors(containerColor = LocalStoneColors.current.panel2, contentColor = LocalStoneColors.current.text), border = BorderStroke(1.dp, LocalStoneColors.current.line), shape = RoundedCornerShape(10.dp)) { Text(label, fontSize = 11.sp) }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Icon(Icons.Default.FolderOpen, null, tint = LocalStoneColors.current.muted, modifier = Modifier.size(32.dp)); Text(title, color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp)); Text(detail, color = LocalStoneColors.current.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
}

@Composable
private fun StoneInput(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValue, modifier = modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(placeholder, color = LocalStoneColors.current.muted, fontSize = 12.sp) }, textStyle = TextStyle(color = LocalStoneColors.current.text, fontSize = 12.sp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LocalStoneColors.current.text, unfocusedTextColor = LocalStoneColors.current.text, cursorColor = Orange, focusedBorderColor = Orange, unfocusedBorderColor = LocalStoneColors.current.line, focusedContainerColor = LocalStoneColors.current.panel, unfocusedContainerColor = LocalStoneColors.current.panel), shape = RoundedCornerShape(10.dp))
}

@Composable
private fun StoneDialog(title: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, color = LocalStoneColors.current.text, fontWeight = FontWeight.SemiBold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Orange) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LocalStoneColors.current.muted) } }, containerColor = LocalStoneColors.current.panel, titleContentColor = LocalStoneColors.current.text, textContentColor = LocalStoneColors.current.text)
}
