package com.crazestudio.stone.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazestudio.stone.data.GitWorkspaceInfo
import com.crazestudio.stone.ui.WebRunnerMode
import com.crazestudio.stone.ui.components.IconAction
import com.crazestudio.stone.ui.components.PageHeader
import com.crazestudio.stone.ui.components.StoneCard
import com.crazestudio.stone.ui.components.StoneDialog
import com.crazestudio.stone.ui.components.StoneInput
import com.crazestudio.stone.ui.theme.JetBrainsMonoFontFamily
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.SpaceGroteskFontFamily
import com.crazestudio.stone.ui.theme.StoneOrange
import java.io.File

@Composable
fun GitScreen(
    project: File,
    gitInfo: GitWorkspaceInfo,
    isBusy: Boolean,
    authorName: String,
    authorEmail: String,
    selectedDiffPath: String?,
    selectedDiffContent: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onInitRepo: () -> Unit,
    onCheckoutBranch: (String, Boolean) -> Unit,
    onCommit: (String) -> Unit,
    onInspectDiff: (String) -> Unit,
    onCloseDiff: () -> Unit,
    onDiscardFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    var commitMessage by remember { mutableStateOf("Update project") }
    var showBranchDialog by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(
            title = "Git Source Control",
            subtitle = project.name,
            onBack = onBack
        ) {
            IconAction(
                icon = Icons.Default.Refresh,
                description = "Refresh Git status",
                onClick = onRefresh,
                testTag = "git_refresh_button"
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!gitInfo.isRepo) {
                StoneCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "No Git repository in ${project.name}",
                            color = colors.text,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = gitInfo.error ?: "Initialize a local Git repository to track file diffs, switch branches, and create offline commits.",
                            color = colors.muted,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onInitRepo,
                            colors = ButtonDefaults.buttonColors(containerColor = StoneOrange),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("git_init_button")
                        ) {
                            Text("Initialize Git Repository", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Active Branch Card
                StoneCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showBranchDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Code, null, tint = StoneOrange)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text("Active Branch", color = colors.muted, fontSize = 10.sp)
                            Text(
                                text = gitInfo.branchName,
                                color = colors.text,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text("Switch / New", color = StoneOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Working Tree Changes
                Text(
                    text = "Working Changes (${gitInfo.changes.size})",
                    color = colors.text,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                StoneCard(Modifier.fillMaxWidth()) {
                    if (gitInfo.changes.isEmpty()) {
                        Text(
                            text = "Working tree clean — no modified or untracked files.",
                            color = colors.success,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    } else {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            gitInfo.changes.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onInspectDiff(item.path) }
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.code,
                                        color = StoneOrange,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Text(
                                        text = item.path,
                                        color = colors.text,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (item.code == "M") {
                                        IconAction(
                                            icon = Icons.Default.Restore,
                                            description = "Discard changes to ${item.path}",
                                            onClick = { onDiscardFile(item.path) },
                                            tint = colors.muted,
                                            iconSize = 16.dp
                                        )
                                    }
                                }
                                HorizontalDivider(color = colors.line.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                // Commit Section
                StoneInput(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = "Commit message…",
                    testTag = "git_commit_input"
                )
                Text(
                    text = "Author: $authorName <$authorEmail> (configured in Settings)",
                    color = colors.muted,
                    fontSize = 11.sp
                )
                Button(
                    onClick = { onCommit(commitMessage) },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = StoneOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("git_commit_button")
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Commit All Changes", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                // Recent Commits
                if (gitInfo.recentCommits.isNotEmpty()) {
                    Text(
                        text = "Recent Commits",
                        color = colors.text,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    StoneCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            gitInfo.recentCommits.forEach { commit ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = commit.shortId,
                                        color = StoneOrange,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 10.dp)
                                    ) {
                                        Text(commit.message, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("${commit.author} · ${commit.timeLabel}", color = colors.muted, fontSize = 10.sp)
                                    }
                                }
                                HorizontalDivider(color = colors.line.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBranchDialog) {
        StoneDialog(
            title = "Branches",
            confirmLabel = "Create Branch",
            onDismiss = { showBranchDialog = false },
            onConfirm = {
                if (newBranchName.isNotBlank()) {
                    onCheckoutBranch(newBranchName, true)
                    newBranchName = ""
                    showBranchDialog = false
                }
            }
        ) {
            if (gitInfo.branches.isNotEmpty()) {
                Text("Switch Local Branch", color = colors.muted, fontSize = 11.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    gitInfo.branches.forEach { br ->
                        Surface(
                            onClick = {
                                onCheckoutBranch(br, false)
                                showBranchDialog = false
                            },
                            color = if (br == gitInfo.branchName) colors.panel2 else colors.bg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (br == gitInfo.branchName) StoneOrange else colors.line),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = br,
                                color = colors.text,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
            Text("Or Create New Branch", color = colors.muted, fontSize = 11.sp)
            StoneInput(newBranchName, { newBranchName = it }, "feature/new-ui")
        }
    }

    if (selectedDiffPath != null && selectedDiffContent != null) {
        StoneDialog(
            title = "Diff · $selectedDiffPath",
            confirmLabel = "Close",
            onDismiss = onCloseDiff,
            onConfirm = onCloseDiff
        ) {
            Text(
                text = selectedDiffContent,
                color = colors.text,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                modifier = Modifier
                    .height(260.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun DebugScreen(
    project: File,
    selectedMode: WebRunnerMode,
    entryPath: String,
    shellCommand: String,
    onUpdateConfig: (WebRunnerMode, String, String) -> Unit,
    onLaunchWebPreview: (File?) -> Unit,
    onLaunchJsRunner: (File?) -> Unit,
    onLaunchShell: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    var launchTarget by remember { mutableStateOf("WEB") } // WEB, JS, SHELL

    val projectFiles = remember(project.absolutePath) {
        project.walkTopDown()
            .filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }
            .map { it.relativeTo(project).path }
            .sorted()
            .take(15)
            .toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(
            title = "Run & Launch",
            subtitle = project.name,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Execution Environment",
                color = colors.text,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("HTML WebView", launchTarget == "WEB", Modifier.weight(1f)) {
                    launchTarget = "WEB"
                    onUpdateConfig(WebRunnerMode.HTML_PREVIEW, entryPath, shellCommand)
                }
                ModeChip("JS Console", launchTarget == "JS", Modifier.weight(1f)) {
                    launchTarget = "JS"
                    onUpdateConfig(WebRunnerMode.JS_CONSOLE, entryPath, shellCommand)
                }
                ModeChip("Shell Cmd", launchTarget == "SHELL", Modifier.weight(1f)) {
                    launchTarget = "SHELL"
                }
            }

            if (launchTarget == "SHELL") {
                Text("Shell Command", color = colors.muted, fontSize = 11.sp)
                StoneInput(
                    value = shellCommand,
                    onValueChange = { onUpdateConfig(selectedMode, entryPath, it) },
                    placeholder = "sh info.sh"
                )
                Button(
                    onClick = { onLaunchShell(shellCommand) },
                    colors = ButtonDefaults.buttonColors(containerColor = StoneOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Run in Persistent Shell", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("Entry File (relative to ${project.name})", color = colors.muted, fontSize = 11.sp)
                StoneInput(
                    value = entryPath,
                    onValueChange = { onUpdateConfig(selectedMode, it, shellCommand) },
                    placeholder = if (launchTarget == "WEB") "index.html" else "app.js"
                )

                val suggestions = projectFiles.filter {
                    val ext = it.substringAfterLast('.', "").lowercase()
                    if (launchTarget == "WEB") ext in listOf("html", "htm") else ext in listOf("js", "mjs")
                }
                if (suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { filePath ->
                            Surface(
                                onClick = { onUpdateConfig(selectedMode, filePath, shellCommand) },
                                color = colors.panel2,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (entryPath == filePath) StoneOrange else colors.line)
                            ) {
                                Text(
                                    text = filePath,
                                    color = colors.text,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val candidate = File(project, entryPath).takeIf { it.isFile }
                        if (launchTarget == "WEB") onLaunchWebPreview(candidate) else onLaunchJsRunner(candidate)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StoneOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (launchTarget == "WEB") "Launch WebView Preview" else "Run in JS Console",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            StoneCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Android Runtime Honesty",
                        color = colors.text,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "HTML, CSS, ES6+ JavaScript, and Android /system/bin/sh scripts execute natively inside STONE's local sandbox. External native ELF toolchains (gcc, apt, python3) are not bundled on stock Android.",
                        color = colors.muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalStoneColors.current
    Surface(
        onClick = onClick,
        color = if (selected) StoneOrange else colors.panel2,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) StoneOrange else colors.line),
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else colors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
        )
    }
}
