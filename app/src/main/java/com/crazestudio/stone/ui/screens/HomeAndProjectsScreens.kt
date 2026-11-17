package com.crazestudio.stone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazestudio.stone.data.ProjectTemplate
import com.crazestudio.stone.ui.ActivityRecord
import com.crazestudio.stone.ui.StonePage
import com.crazestudio.stone.ui.components.ActionTile
import com.crazestudio.stone.ui.components.EmptyState
import com.crazestudio.stone.ui.components.IconAction
import com.crazestudio.stone.ui.components.PageHeader
import com.crazestudio.stone.ui.components.SecondaryButton
import com.crazestudio.stone.ui.components.StoneBrand
import com.crazestudio.stone.ui.components.StoneCard
import com.crazestudio.stone.ui.components.StoneDialog
import com.crazestudio.stone.ui.components.StoneInput
import com.crazestudio.stone.ui.components.StoneMark
import com.crazestudio.stone.ui.theme.JetBrainsMonoFontFamily
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.SpaceGroteskFontFamily
import com.crazestudio.stone.ui.theme.StoneOrange
import java.io.File

@Composable
fun SplashScreen() {
    val colors = LocalStoneColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(colors.panel2.copy(alpha = 0.7f), colors.bg),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StoneMark(84.dp)
            Text(
                text = "STONE",
                color = colors.text,
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "by CrazeStudio · Privacy-First Workspace",
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(120.dp))
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.line)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(StoneOrange)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    displayName: String,
    projects: List<File>,
    activeProject: File,
    activeTerminalCount: Int,
    onNewProject: () -> Unit,
    onOpenFolder: () -> Unit,
    onCloneRepo: () -> Unit,
    onImportZip: () -> Unit,
    onSelectProject: (File) -> Unit,
    onNavigate: (StonePage) -> Unit,
    onSearch: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLaunchPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoneBrand(compact = true)
            Spacer(Modifier.weight(1f))
            IconAction(
                icon = Icons.Default.Search,
                description = "Search projects",
                onClick = onSearch,
                testTag = "home_search_button"
            )
            IconAction(
                icon = Icons.Default.Security,
                description = "Sandbox and privacy diagnostics",
                onClick = onOpenDiagnostics,
                tint = StoneOrange,
                testTag = "home_diagnostics_button"
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .widthIn(max = 840.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Welcome, $displayName",
                color = colors.text,
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Active workspace: ${activeProject.name} · $activeTerminalCount shell session${if (activeTerminalCount == 1) "" else "s"}",
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                StoneCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(66.dp)
                        .testTag("home_projects_card"),
                    onClick = { onNavigate(StonePage.PROJECTS) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = colors.muted, modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "Local Workspaces",
                                color = colors.text,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${projects.size} offline project${if (projects.size == 1) "" else "s"}",
                                color = colors.muted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    onClick = onNewProject,
                    color = StoneOrange,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .size(66.dp)
                        .testTag("home_new_project_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Create project", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Default.Add, "New Project", "From template", onNewProject, Modifier.weight(1f), "action_new_project")
                ActionTile(Icons.Default.FolderOpen, "Open Folder", "System picker", onOpenFolder, Modifier.weight(1f), "action_open_folder")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Default.Download, "Clone Repo", "HTTPS Git", onCloneRepo, Modifier.weight(1f), "action_clone_repo")
                ActionTile(Icons.Default.UploadFile, "Import ZIP", "Safe extract", onImportZip, Modifier.weight(1f), "action_import_zip")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Default.Terminal, "Terminal", "Persistent shell", { onNavigate(StonePage.TERMINAL) }, Modifier.weight(1f), "action_open_terminal")
                ActionTile(Icons.Default.PlayArrow, "Web / JS Run", "Local WebView", onLaunchPreview, Modifier.weight(1f), "action_launch_preview")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Projects",
                    color = colors.text,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onNavigate(StonePage.PROJECTS) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("View all", color = StoneOrange, fontSize = 12.sp)
                }
            }

            if (projects.isEmpty()) {
                EmptyState(
                    title = "No projects yet",
                    detail = "Create a local workspace or import an archive.",
                    actionLabel = "Create Project",
                    onAction = onNewProject
                )
            } else {
                projects.take(6).forEach { file ->
                    val isActive = file.absolutePath == activeProject.absolutePath
                    StoneCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        highlighted = isActive,
                        onClick = { onSelectProject(file) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isActive) StoneOrange else colors.muted,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = file.name,
                                    color = colors.text,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isActive) "Active · ~/${file.name}" else "~/${file.name}",
                                    color = colors.muted,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = colors.muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProjectsScreen(
    projects: List<File>,
    activeProjectPath: String,
    onBack: () -> Unit,
    onSelectProject: (File) -> Unit,
    onNewProject: () -> Unit,
    onOpenFolder: () -> Unit,
    onDeleteProject: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    val filtered = remember(projects, query) {
        if (query.isBlank()) projects else projects.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(
            title = "Projects",
            subtitle = "${projects.size} on-device workspaces",
            onBack = onBack
        ) {
            IconAction(Icons.Default.Add, "New project", onNewProject, testTag = "projects_add_button")
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            StoneInput(
                value = query,
                onValueChange = { query = it },
                placeholder = "Filter projects…",
                testTag = "projects_filter_input"
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("New Project", onNewProject, Modifier.weight(1f), "projects_new_button")
                SecondaryButton("Open Folder", onOpenFolder, Modifier.weight(1f), "projects_open_button")
            }
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("No matching projects", "Try another filter or create a new project.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filtered, key = { it.absolutePath }) { file ->
                        val isActive = file.absolutePath == activeProjectPath
                        val fileCount = remember(file.absolutePath) {
                            file.walkTopDown().count { it.isFile }
                        }
                        StoneCard(
                            modifier = Modifier.fillMaxWidth(),
                            highlighted = isActive,
                            onClick = { onSelectProject(file) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    null,
                                    tint = if (isActive) StoneOrange else colors.muted
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = file.name,
                                        color = colors.text,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "$fileCount files${if (isActive) " · Active" else ""}",
                                        color = colors.muted,
                                        fontSize = 11.sp
                                    )
                                }
                                IconAction(
                                    icon = Icons.Default.DeleteOutline,
                                    description = "Delete project",
                                    onClick = { pendingDelete = file },
                                    tint = colors.muted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        StoneDialog(
            title = "Delete Project",
            confirmLabel = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteProject(target)
                pendingDelete = null
            }
        ) {
            Text(
                text = "Permanently remove '${target.name}' and all its local files?",
                color = colors.text,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, ProjectTemplate) -> Unit
) {
    val colors = LocalStoneColors.current
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(ProjectTemplate.WEB_APP) }

    StoneDialog(
        title = "New Local Project",
        confirmLabel = "Create",
        onDismiss = onDismiss,
        onConfirm = { onCreate(name, template) },
        confirmTestTag = "create_project_confirm"
    ) {
        StoneInput(
            value = name,
            onValueChange = { name = it },
            placeholder = "Project name (e.g. portfolio-site)",
            testTag = "new_project_name_input"
        )
        Text("Starter Template", color = colors.muted, fontSize = 11.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProjectTemplate.entries.forEach { item ->
                val selected = item == template
                Surface(
                    onClick = { template = item },
                    color = if (selected) colors.panel2 else colors.bg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (selected) StoneOrange else colors.line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                color = colors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = item.subtitle,
                                color = colors.muted,
                                fontSize = 11.sp
                            )
                        }
                        if (selected) {
                            Text("ACTIVE", color = StoneOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    storageBytes: Long,
    activeShells: Int,
    projectCount: Int,
    activities: List<ActivityRecord>,
    onDismiss: () -> Unit
) {
    val colors = LocalStoneColors.current
    val formattedStorage = remember(storageBytes) {
        when {
            storageBytes < 1024 -> "$storageBytes B"
            storageBytes < 1024 * 1024 -> "${storageBytes / 1024} KB"
            else -> String.format("%.2f MB", storageBytes / (1024f * 1024f))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        contentColor = colors.text
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = StoneOrange)
                Text(
                    text = "CrazeStudio Sandbox & Privacy",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                IconAction(Icons.Default.Close, "Close", onDismiss)
            }
            Spacer(Modifier.height(12.dp))
            StoneCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticRow("Storage Footprint", "$formattedStorage across $projectCount projects")
                    DiagnosticRow("Active Shells", "$activeShells persistent /system/bin/sh session(s)")
                    DiagnosticRow("Telemetry / Cloud", "Disabled (100% offline local DataStore)")
                    DiagnosticRow("Network Usage", "HTTPS Git clone only (WebView offline by default)")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Recent Session Events",
                color = colors.text,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(visible = activities.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activities.take(6).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.panel2)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(item.detail, color = colors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(item.time, color = colors.muted, fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    val colors = LocalStoneColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.muted, fontSize = 12.sp, modifier = Modifier.width(128.dp))
        Text(value, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = colors.line.copy(alpha = 0.5f))
}
