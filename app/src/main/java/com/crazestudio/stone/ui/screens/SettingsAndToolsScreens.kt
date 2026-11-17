package com.crazestudio.stone.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazestudio.stone.data.StonePreferences
import com.crazestudio.stone.ui.components.PageHeader
import com.crazestudio.stone.ui.components.StoneCard
import com.crazestudio.stone.ui.components.StoneDialog
import com.crazestudio.stone.ui.components.StoneInput
import com.crazestudio.stone.ui.theme.JetBrainsMonoFontFamily
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.SpaceGroteskFontFamily
import com.crazestudio.stone.ui.theme.StoneOrange

@Composable
fun SettingsScreen(
    prefs: StonePreferences,
    onUpdateDisplayName: (String) -> Unit,
    onUpdateFontSize: (Float) -> Unit,
    onUpdateTabSize: (Int) -> Unit,
    onUpdateAutoSave: (Boolean) -> Unit,
    onUpdateWordWrap: (Boolean) -> Unit,
    onUpdateGitAuthor: (String, String) -> Unit,
    onOpenThemes: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showGitAuthorDialog by remember { mutableStateOf(false) }
    var nameDraft by remember(prefs.displayName) { mutableStateOf(prefs.displayName) }
    var gitNameDraft by remember(prefs.gitAuthorName) { mutableStateOf(prefs.gitAuthorName) }
    var gitEmailDraft by remember(prefs.gitAuthorEmail) { mutableStateOf(prefs.gitAuthorEmail) }

    fun matches(vararg terms: String): Boolean {
        if (searchQuery.isBlank()) return true
        return terms.any { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(title = "Settings", subtitle = "Offline DataStore", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StoneInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search settings…",
                testTag = "settings_search_input"
            )

            if (matches("profile", "workspace", "name", "author", "git", "email")) {
                SettingsGroup("Profile & Git Identity") {
                    if (matches("profile", "workspace", "name")) {
                        SettingActionRow(
                            icon = Icons.Default.Person,
                            title = "Workspace display name",
                            detail = prefs.displayName,
                            onClick = { nameDraft = prefs.displayName; showProfileDialog = true }
                        )
                    }
                    if (matches("git", "author", "email")) {
                        SettingActionRow(
                            icon = Icons.Default.Code,
                            title = "Local Git commit author",
                            detail = "${prefs.gitAuthorName} <${prefs.gitAuthorEmail}>",
                            onClick = {
                                gitNameDraft = prefs.gitAuthorName
                                gitEmailDraft = prefs.gitAuthorEmail
                                showGitAuthorDialog = true
                            }
                        )
                    }
                }
            }

            if (matches("editor", "font", "size", "tab", "auto", "save", "wrap")) {
                SettingsGroup("Editor") {
                    if (matches("font", "size", "editor")) {
                        Text(
                            text = "Font size · ${prefs.editorFontSizeSp.toInt()} sp",
                            color = colors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = prefs.editorFontSizeSp,
                            onValueChange = onUpdateFontSize,
                            valueRange = 11f..22f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = StoneOrange,
                                activeTrackColor = StoneOrange,
                                inactiveTrackColor = colors.line
                            )
                        )
                    }
                    if (matches("tab", "indent", "editor")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tab width", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 4, 8).forEach { size ->
                                    val selected = prefs.tabSize == size
                                    Surface(
                                        onClick = { onUpdateTabSize(size) },
                                        color = if (selected) StoneOrange else colors.panel,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (selected) StoneOrange else colors.line),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(
                                            text = "$size",
                                            color = if (selected) Color.White else colors.text,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (matches("auto", "save", "editor")) {
                        SettingSwitchRow(
                            icon = Icons.Default.Save,
                            title = "Auto-save edits",
                            detail = "Write buffer changes immediately to local storage",
                            checked = prefs.autoSaveEnabled,
                            onCheckedChange = onUpdateAutoSave
                        )
                    }
                    if (matches("wrap", "word", "editor")) {
                        SettingSwitchRow(
                            icon = Icons.Default.WrapText,
                            title = "Word wrap",
                            detail = "Wrap long lines instead of horizontal scroll",
                            checked = prefs.wordWrapEnabled,
                            onCheckedChange = onUpdateWordWrap
                        )
                    }
                }
            }

            if (matches("theme", "appearance", "colors", "dark", "light")) {
                SettingsGroup("Appearance") {
                    SettingActionRow(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        detail = prefs.themeName,
                        onClick = onOpenThemes
                    )
                }
            }

            if (matches("privacy", "offline", "sandbox", "crazestudio", "about")) {
                SettingsGroup("Privacy & About") {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, null, tint = StoneOrange, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("CrazeStudio Privacy-First Architecture", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "All projects, terminal sessions, and preferences remain offline on-device. No analytics, accounts, or telemetry.",
                                color = colors.muted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        StoneDialog(
            title = "Workspace Display Name",
            confirmLabel = "Save",
            onDismiss = { showProfileDialog = false },
            onConfirm = {
                onUpdateDisplayName(nameDraft)
                showProfileDialog = false
            }
        ) {
            StoneInput(nameDraft, { nameDraft = it }, "Developer name")
        }
    }

    if (showGitAuthorDialog) {
        StoneDialog(
            title = "Local Git Author",
            confirmLabel = "Save",
            onDismiss = { showGitAuthorDialog = false },
            onConfirm = {
                onUpdateGitAuthor(gitNameDraft, gitEmailDraft)
                showGitAuthorDialog = false
            }
        ) {
            StoneInput(gitNameDraft, { gitNameDraft = it }, "Author name")
            StoneInput(gitEmailDraft, { gitEmailDraft = it }, "Author email")
        }
    }
}

@Composable
fun ThemesScreen(
    activeTheme: String,
    onSelectTheme: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(title = "Themes", subtitle = "Monochrome & Night Palettes", onBack = onBack)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Stone Dark", "Stone Light", "Obsidian", "Midnight").forEach { name ->
                val selected = activeTheme == name
                StoneCard(
                    modifier = Modifier.fillMaxWidth(),
                    highlighted = selected,
                    onClick = { onSelectTheme(name) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val previewBg = when (name) {
                            "Stone Light" -> Color(0xFFECECE8)
                            "Obsidian" -> Color(0xFF050505)
                            "Midnight" -> Color(0xFF101218)
                            else -> Color(0xFF111111)
                        }
                        Surface(
                            color = previewBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(62.dp, 42.dp),
                            border = BorderStroke(1.dp, colors.line)
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth(0.72f).height(3.dp).background(if (name == "Stone Light") Color.DarkGray else Color.LightGray, CircleShape))
                                Box(Modifier.fillMaxWidth(0.45f).height(3.dp).background(StoneOrange, CircleShape))
                                Box(Modifier.fillMaxWidth(0.85f).height(3.dp).background(if (name == "Stone Light") Color.Gray else Color.DarkGray, CircleShape))
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(name, color = colors.text, fontFamily = SpaceGroteskFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (name == "Stone Dark") {
                                Text("Default CrazeStudio monochrome", color = colors.muted, fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = if (selected) "ACTIVE" else "SELECT",
                            color = if (selected) StoneOrange else colors.muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExtensionsScreen(
    enabledProfiles: Set<String>,
    onToggleProfile: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    val catalogue = listOf(
        "HTML / CSS / JavaScript" to "Syntax highlighting & live WebView runner",
        "Kotlin" to "Kotlin keywords, classes, and lambdas",
        "Python" to "Python syntax highlighting profile",
        "Java" to "Java syntax highlighting profile",
        "C / C++" to "C-family syntax highlighting profile",
        "GitLens" to "Local JGit branch, diff & commit history tools"
    )
    var query by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(title = "Language Profiles", subtitle = "${enabledProfiles.size} active", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            StoneInput(query, { query = it }, "Search built-in profiles…")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                catalogue.filter { it.first.contains(query, true) || it.second.contains(query, true) }.forEach { item ->
                    val enabled = item.first in enabledProfiles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = colors.panel2,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Extension, null, tint = if (enabled) StoneOrange else colors.muted)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(item.first, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.second, color = colors.muted, fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { onToggleProfile(item.first) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = if (enabled) "Enabled" else "Enable",
                                color = if (enabled) colors.success else StoneOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider(color = colors.line)
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(
    onTerminal: () -> Unit,
    onWebRunner: () -> Unit,
    onJsRunner: () -> Unit,
    onGit: () -> Unit,
    onDebug: () -> Unit,
    onExtensions: () -> Unit,
    onImportZip: () -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(title = "Tools & Runtime", subtitle = "CrazeStudio Sandbox", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ToolRow(Icons.Default.Terminal, "Terminal Emulator", "Multi-tab persistent /system/bin/sh with ANSI colors", onTerminal)
            ToolRow(Icons.Default.Public, "HTML / WebView Preview", "Local scoped HTML5, CSS3 & DOM preview", onWebRunner)
            ToolRow(Icons.Default.Code, "JS Console Runner", "Execute JavaScript files & interactive REPL", onJsRunner)
            ToolRow(Icons.Default.Code, "Git Source Control", "Local branches, diffs, commit history & HTTPS clone", onGit)
            ToolRow(Icons.Default.BugReport, "Run & Launch Studio", "Configure HTML, JS, or shell launch targets", onDebug)
            ToolRow(Icons.Default.Extension, "Language Profiles", "Manage built-in syntax profiles", onExtensions)
            ToolRow(Icons.Default.UploadFile, "Import ZIP Archive", "Extract project archive with path safety", onImportZip)
            ToolRow(Icons.Default.Settings, "Settings & Privacy", "Offline DataStore preferences & themes", onSettings)
        }
    }
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val colors = LocalStoneColors.current
    StoneCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = StoneOrange)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(title, color = colors.text, fontFamily = SpaceGroteskFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = colors.muted, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.muted)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalStoneColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = colors.text,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        StoneCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
private fun SettingActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val colors = LocalStoneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.muted, modifier = Modifier.size(19.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = colors.muted, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalStoneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.muted, modifier = Modifier.size(19.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = colors.muted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = StoneOrange,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.panel
            )
        )
    }
}
