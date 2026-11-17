package com.crazestudio.stone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazestudio.stone.ui.EditorTabState
import com.crazestudio.stone.ui.StonePage
import com.crazestudio.stone.ui.components.EmptyState
import com.crazestudio.stone.ui.components.IconAction
import com.crazestudio.stone.ui.components.PageHeader
import com.crazestudio.stone.ui.components.StoneDialog
import com.crazestudio.stone.ui.components.StoneInput
import com.crazestudio.stone.ui.components.StoneMark
import com.crazestudio.stone.ui.components.buildCodeVisualTransformation
import com.crazestudio.stone.ui.theme.JetBrainsMonoFontFamily
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.SpaceGroteskFontFamily
import com.crazestudio.stone.ui.theme.StoneOrange
import java.io.File
import java.util.Locale

private data class ExplorerEntry(
    val file: File,
    val depth: Int
)

@Composable
fun ExplorerScreen(
    project: File,
    revision: Long,
    onBack: (() -> Unit)?,
    onOpenFile: (File) -> Unit,
    onRunFile: (File) -> Unit,
    onCreateEntry: (String, Boolean) -> Unit,
    onRenameEntry: (File, String) -> Unit,
    onDeleteEntry: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    var showHidden by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var createDialogIsDir by remember { mutableStateOf<Boolean?>(null) }
    var createPathInput by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    val collapsedDirs = remember(project.absolutePath) { mutableStateMapOf<String, Boolean>() }

    val entries = remember(project.absolutePath, revision, showHidden, filterQuery, collapsedDirs.toMap()) {
        buildList {
            fun walk(dir: File, depth: Int) {
                val children = dir.listFiles()
                    ?.filter { showHidden || !it.name.startsWith(".") }
                    ?.sortedWith(compareBy<File>({ it.isFile }, { it.name.lowercase(Locale.ROOT) }))
                    .orEmpty()
                for (child in children) {
                    val matchesFilter = filterQuery.isBlank() || child.name.contains(filterQuery, ignoreCase = true)
                    if (child.isDirectory) {
                        if (matchesFilter || filterQuery.isBlank()) {
                            add(ExplorerEntry(child, depth))
                        }
                        if (collapsedDirs[child.absolutePath] != true) {
                            walk(child, depth + 1)
                        }
                    } else if (matchesFilter) {
                        add(ExplorerEntry(child, depth))
                    }
                }
            }
            walk(project, 0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(
            title = "Explorer",
            subtitle = project.name,
            onBack = onBack
        ) {
            IconAction(
                icon = Icons.Default.Add,
                description = "New file",
                onClick = { createPathInput = ""; createDialogIsDir = false },
                testTag = "explorer_new_file"
            )
            IconAction(
                icon = Icons.Default.CreateNewFolder,
                description = "New folder",
                onClick = { createPathInput = ""; createDialogIsDir = true },
                testTag = "explorer_new_folder"
            )
            IconAction(
                icon = if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                description = if (showHidden) "Hide dotfiles" else "Show dotfiles",
                onClick = { showHidden = !showHidden },
                testTag = "explorer_toggle_hidden"
            )
        }

        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            StoneInput(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = "Filter files in ${project.name}…",
                testTag = "explorer_filter_input"
            )
        }

        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Folder is empty",
                    detail = "Create a file or directory to start building.",
                    actionLabel = "New File",
                    onAction = { createPathInput = ""; createDialogIsDir = false }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(entries, key = { it.file.absolutePath }) { entry ->
                    val file = entry.file
                    val isCollapsed = collapsedDirs[file.absolutePath] == true
                    val isRunnable = file.isFile && file.extension.lowercase() in listOf("html", "htm", "js", "mjs", "sh")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                if (file.isDirectory) {
                                    collapsedDirs[file.absolutePath] = !isCollapsed
                                } else {
                                    onOpenFile(file)
                                }
                            }
                            .padding(start = (entry.depth * 14 + 8).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                file.isDirectory && isCollapsed -> Icons.Default.Folder
                                file.isDirectory -> Icons.Default.ExpandMore
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (file.isDirectory) StoneOrange else colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = file.name,
                            color = if (file.isDirectory) colors.text else colors.text.copy(alpha = 0.88f),
                            fontFamily = if (file.isDirectory) SpaceGroteskFontFamily else JetBrainsMonoFontFamily,
                            fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        if (isRunnable) {
                            IconAction(
                                icon = Icons.Default.PlayArrow,
                                description = "Run ${file.name}",
                                onClick = { onRunFile(file) },
                                tint = StoneOrange,
                                iconSize = 18.dp
                            )
                        }
                        IconAction(
                            icon = Icons.Default.Edit,
                            description = "Rename ${file.name}",
                            onClick = { renameTarget = file; renameInput = file.name },
                            tint = colors.muted,
                            iconSize = 16.dp
                        )
                        IconAction(
                            icon = Icons.Default.DeleteOutline,
                            description = "Delete ${file.name}",
                            onClick = { deleteTarget = file },
                            tint = colors.muted,
                            iconSize = 16.dp
                        )
                    }
                }
            }
        }
    }

    createDialogIsDir?.let { isDir ->
        StoneDialog(
            title = if (isDir) "New Folder" else "New File",
            confirmLabel = "Create",
            onDismiss = { createDialogIsDir = null },
            onConfirm = {
                if (createPathInput.isNotBlank()) {
                    onCreateEntry(createPathInput, isDir)
                }
                createDialogIsDir = null
            }
        ) {
            StoneInput(
                value = createPathInput,
                onValueChange = { createPathInput = it },
                placeholder = if (isDir) "src/components" else "src/app.js"
            )
        }
    }

    renameTarget?.let { target ->
        StoneDialog(
            title = "Rename ${target.name}",
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = {
                onRenameEntry(target, renameInput)
                renameTarget = null
            }
        ) {
            StoneInput(renameInput, { renameInput = it }, "New name")
        }
    }

    deleteTarget?.let { target ->
        StoneDialog(
            title = "Delete ${target.name}?",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteEntry(target)
                deleteTarget = null
            }
        ) {
            Text(
                text = "This removes ${target.name} from disk permanently.",
                color = colors.text,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun EditorScreen(
    project: File,
    tabs: List<EditorTabState>,
    activeTabPath: String?,
    fontSizeSp: Float,
    tabSize: Int,
    wordWrapEnabled: Boolean,
    showActivityRail: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsertSnippet: (String) -> Unit,
    onReplace: (String, String, Boolean) -> Unit,
    onOpenExplorer: () -> Unit,
    onNavigate: (StonePage) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    val activeTab = tabs.firstOrNull { it.path == activeTabPath }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val cursorPosition = remember(activeTab?.value?.selection, activeTab?.value?.text) {
        val tab = activeTab ?: return@remember 1 to 1
        val idx = tab.value.selection.start.coerceIn(0, tab.value.text.length)
        val prefix = tab.value.text.substring(0, idx)
        val line = prefix.count { it == '\n' } + 1
        val lastNl = prefix.lastIndexOf('\n')
        val col = if (lastNl < 0) idx + 1 else idx - lastNl
        line to col
    }

    val matchCount = remember(activeTab?.value?.text, findQuery) {
        val text = activeTab?.value?.text.orEmpty()
        if (findQuery.isEmpty() || text.isEmpty()) 0
        else Regex.escape(findQuery).toRegex().findAll(text).count()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Editor Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(Icons.Default.Menu, "Open Explorer", onOpenExplorer, testTag = "editor_explorer_button")
            IconAction(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, tint = if (activeTab?.undoStack?.isNotEmpty() == true) colors.text else colors.muted)
            IconAction(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo, tint = if (activeTab?.redoStack?.isNotEmpty() == true) colors.text else colors.muted)
            Spacer(Modifier.weight(1f))
            StoneMark(22.dp)
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Default.Search, "Find and Replace", { showFindBar = !showFindBar }, tint = if (showFindBar) StoneOrange else colors.text)
            IconAction(Icons.Default.Save, "Save file", onSave, tint = if (activeTab?.isDirty == true) StoneOrange else colors.text, testTag = "editor_save_button")
            IconAction(Icons.Default.PlayArrow, "Run file", onRun, tint = StoneOrange, testTag = "editor_run_button")
        }
        HorizontalDivider(color = colors.line)

        Row(Modifier.weight(1f)) {
            if (showActivityRail) {
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .background(colors.rail),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconAction(Icons.Default.InsertDriveFile, "Explorer", onOpenExplorer, tint = colors.muted)
                    IconAction(Icons.Default.Terminal, "Terminal", { onNavigate(StonePage.TERMINAL) }, tint = colors.muted)
                    IconAction(Icons.Default.Code, "Source Control", { onNavigate(StonePage.GIT) }, tint = colors.muted)
                    IconAction(Icons.Default.BugReport, "Run & Debug", { onNavigate(StonePage.DEBUG) }, tint = colors.muted)
                    IconAction(Icons.Default.Extension, "Extensions", { onNavigate(StonePage.EXTENSIONS) }, tint = colors.muted)
                }
            }

            Column(Modifier.weight(1f)) {
                // Tab Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(colors.panel)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val selected = tab.path == activeTabPath
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(if (selected) colors.panel2 else colors.panel)
                                .clickable { onSelectTab(tab.path) }
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tab.name,
                                color = if (selected) colors.text else colors.muted,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (tab.isDirty) {
                                Text(" ●", color = StoneOrange, fontSize = 11.sp)
                            }
                            IconAction(
                                icon = Icons.Default.Close,
                                description = "Close ${tab.name}",
                                onClick = { onCloseTab(tab.path) },
                                tint = colors.muted,
                                iconSize = 15.dp
                            )
                        }
                    }
                }

                // Breadcrumb
                val relPath = activeTab?.let {
                    runCatching { File(it.path).relativeTo(project).path }.getOrNull() ?: it.name
                } ?: "No file selected"
                Text(
                    text = "${project.name}  ›  $relPath",
                    color = colors.muted,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.panel)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                // Find & Replace bar
                AnimatedVisibility(visible = showFindBar && activeTab != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.panel2)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StoneInput(findQuery, { findQuery = it }, "Find…", Modifier.weight(1f))
                            Text(
                                text = if (findQuery.isEmpty()) "0" else "$matchCount",
                                color = StoneOrange,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StoneInput(replaceQuery, { replaceQuery = it }, "Replace with…", Modifier.weight(1f))
                            TextButton(
                                onClick = { onReplace(findQuery, replaceQuery, false) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) { Text("One", color = colors.text, fontSize = 11.sp) }
                            TextButton(
                                onClick = { onReplace(findQuery, replaceQuery, true) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) { Text("All", color = StoneOrange, fontSize = 11.sp) }
                        }
                    }
                }

                // Main Editor Surface
                if (activeTab == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = "No file open",
                            detail = "Pick a file from the project Explorer to begin editing.",
                            actionLabel = "Open Explorer",
                            onAction = onOpenExplorer
                        )
                    }
                } else {
                    val verticalScroll = rememberScrollState()
                    val horizontalScroll = rememberScrollState()
                    val ext = File(activeTab.path).extension
                    val visualTransformation = remember(ext, colors) {
                        buildCodeVisualTransformation(ext, colors)
                    }
                    val lineHeightSp = (fontSizeSp + 7f).sp
                    val lineCount = remember(activeTab.value.text) {
                        (activeTab.value.text.count { it == '\n' } + 1).coerceAtLeast(1)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(colors.bg)
                            .verticalScroll(verticalScroll)
                            .padding(bottom = 24.dp)
                    ) {
                        // Synchronized line numbers gutter
                        Column(
                            modifier = Modifier
                                .width(44.dp)
                                .padding(top = 10.dp, end = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (lineNo in 1..lineCount.coerceAtMost(2500)) {
                                val isCurrent = lineNo == cursorPosition.first
                                Text(
                                    text = "$lineNo",
                                    color = if (isCurrent) StoneOrange else colors.muted.copy(alpha = 0.65f),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = (fontSizeSp - 2f).sp,
                                    lineHeight = lineHeightSp
                                )
                            }
                        }

                        val fieldModifier = Modifier
                            .weight(1f)
                            .padding(top = 10.dp, end = 12.dp)
                            .let { mod ->
                                if (wordWrapEnabled) mod
                                else mod
                                    .horizontalScroll(horizontalScroll)
                                    .widthIn(min = 520.dp)
                            }
                            .testTag("code_editor_field")

                        BasicTextField(
                            value = activeTab.value,
                            onValueChange = onValueChange,
                            modifier = fieldModifier,
                            textStyle = TextStyle(
                                color = colors.text,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = fontSizeSp.sp,
                                lineHeight = lineHeightSp
                            ),
                            cursorBrush = SolidColor(StoneOrange),
                            visualTransformation = visualTransformation,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii
                            )
                        )
                    }

                    // Developer Symbol Quick Bar
                    val indent = " ".repeat(tabSize)
                    val snippets = listOf(
                        "TAB" to indent,
                        "{" to "{",
                        "}" to "}",
                        "(" to "(",
                        ")" to ")",
                        "[" to "[",
                        "]" to "]",
                        ";" to ";",
                        "\"" to "\"",
                        "'" to "'",
                        "=" to "=",
                        "=>" to " => "
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.panel2)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        snippets.forEach { (label, snippet) ->
                            Surface(
                                onClick = { onInsertSnippet(snippet) },
                                color = colors.panel,
                                shape = RoundedCornerShape(7.dp),
                                border = BorderStroke(1.dp, colors.line),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = colors.text,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(colors.panel)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            activeTab == null -> "Idle"
                            activeTab.isDirty -> "● Unsaved"
                            else -> "✓ Saved"
                        },
                        color = if (activeTab?.isDirty == true) StoneOrange else colors.success,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.weight(1f))
                    val extLabel = activeTab?.let { File(it.path).extension.uppercase(Locale.ROOT).ifBlank { "TXT" } } ?: "PLAIN"
                    Text(
                        text = "Ln ${cursorPosition.first}, Col ${cursorPosition.second}   UTF-8   $extLabel",
                        color = colors.muted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    IconAction(
                        icon = Icons.Default.Terminal,
                        description = "Open Terminal",
                        onClick = { onNavigate(StonePage.TERMINAL) },
                        iconSize = 16.dp
                    )
                }
            }
        }
    }
}
