package com.crazestudio.stone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazestudio.stone.terminal.TerminalSessionManager
import com.crazestudio.stone.terminal.TerminalTabState
import com.crazestudio.stone.ui.components.IconAction
import com.crazestudio.stone.ui.components.PageHeader
import com.crazestudio.stone.ui.theme.JetBrainsMonoFontFamily
import com.crazestudio.stone.ui.theme.LocalAnsiPalette
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.StoneOrange
import java.io.File

@Composable
fun TerminalScreen(
    project: File,
    tabs: List<TerminalTabState>,
    activeTabId: String?,
    terminalManager: TerminalSessionManager,
    fontSizeSp: Float,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    val ansiPalette = LocalAnsiPalette.current
    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()
    var commandInput by rememberSaveable { mutableStateOf("") }
    var tabCompletions by remember { mutableStateOf<List<String>>(emptyList()) }
    val scrollState = rememberScrollState()

    val renderedOutput = remember(activeTab?.id, activeTab?.revision, ansiPalette) {
        activeTab?.let { terminalManager.renderBuffer(it.id, ansiPalette) }
    }

    LaunchedEffect(activeTab?.id, activeTab?.revision) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    fun submitCommand() {
        val tab = activeTab ?: return
        val cmd = commandInput
        commandInput = ""
        tabCompletions = emptyList()
        terminalManager.sendCommand(tab.id, cmd)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(
            title = "Terminal",
            subtitle = activeTab?.let { "~/${it.workingDir.name}" } ?: project.name,
            onBack = onBack
        ) {
            if (activeTab != null) {
                IconAction(
                    icon = Icons.Default.Stop,
                    description = "Interrupt process (Ctrl+C)",
                    onClick = { terminalManager.interruptSession(activeTab.id) },
                    tint = if (activeTab.isRunning) StoneOrange else colors.muted,
                    testTag = "terminal_interrupt_button"
                )
                IconAction(
                    icon = Icons.Default.DeleteSweep,
                    description = "Clear terminal output",
                    onClick = { terminalManager.clearSession(activeTab.id) },
                    tint = colors.muted,
                    testTag = "terminal_clear_button"
                )
            }
            IconAction(
                icon = Icons.Default.Add,
                description = "New shell session",
                onClick = { terminalManager.createSession(project) },
                tint = StoneOrange,
                testTag = "terminal_new_tab_button"
            )
        }

        // Session Tab Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(colors.panel)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == activeTab?.id
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (selected) colors.panel2 else colors.panel)
                        .clickable { terminalManager.selectTab(tab.id) }
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tab.isRunning) "● ${tab.title}" else tab.title,
                        color = if (selected) StoneOrange else colors.muted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    IconAction(
                        icon = Icons.Default.Close,
                        description = "Close ${tab.title}",
                        onClick = { terminalManager.closeSession(tab.id, project) },
                        tint = colors.muted,
                        iconSize = 14.dp
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            activeTab?.lastExitCode?.let { code ->
                Text(
                    text = "exit $code",
                    color = if (code == 0) colors.success else colors.error,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
        HorizontalDivider(color = colors.line)

        // Quick-Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ls -la", "pwd", "stone-env", "env", "preview", "runjs").forEach { quick ->
                Surface(
                    onClick = {
                        activeTab?.let { terminalManager.sendCommand(it.id, quick) }
                    },
                    color = colors.panel2,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.line)
                ) {
                    Text(
                        text = quick,
                        color = colors.text,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = colors.line)

        // ANSI Output Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.bg)
        ) {
            SelectionContainer {
                Text(
                    text = renderedOutput ?: androidx.compose.ui.text.AnnotatedString(""),
                    color = ansiPalette.defaultFg,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = (fontSizeSp - 1f).coerceAtLeast(11f).sp,
                    lineHeight = (fontSizeSp + 5f).sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(14.dp)
                        .testTag("terminal_output_text")
                )
            }
        }

        // Tab Completion Candidates
        AnimatedVisibility(visible = tabCompletions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel2)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabCompletions.forEach { candidate ->
                    Surface(
                        onClick = {
                            val prefix = commandInput.substringBeforeLast(' ', "")
                            commandInput = if (prefix.isEmpty() && !commandInput.contains(' ')) {
                                candidate
                            } else {
                                "$prefix $candidate".trimStart()
                            }
                            tabCompletions = emptyList()
                        },
                        color = colors.panel,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, StoneOrange)
                    ) {
                        Text(
                            text = candidate,
                            color = StoneOrange,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Extra-Keys Modifier Strip
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel2)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ExtraKeyButton(
                label = "CTRL",
                active = activeTab?.ctrlActive == true,
                onClick = { activeTab?.let { terminalManager.toggleCtrl(it.id) } }
            )
            ExtraKeyButton(
                label = "TAB",
                onClick = {
                    val tab = activeTab ?: return@ExtraKeyButton
                    val (updated, matches) = terminalManager.completeInput(tab.id, commandInput)
                    commandInput = updated
                    tabCompletions = matches
                }
            )
            ExtraKeyButton(
                label = "↑",
                onClick = {
                    activeTab?.let {
                        terminalManager.stepHistory(it.id, older = true)?.let { hist -> commandInput = hist }
                    }
                }
            )
            ExtraKeyButton(
                label = "↓",
                onClick = {
                    activeTab?.let {
                        terminalManager.stepHistory(it.id, older = false)?.let { hist -> commandInput = hist }
                    }
                }
            )
            listOf("|", "/", "~", "-", ">", "&&", "\"", "./").forEach { sym ->
                ExtraKeyButton(label = sym, onClick = { commandInput += sym })
            }
            ExtraKeyButton(
                label = "^C",
                onClick = { activeTab?.let { terminalManager.interruptSession(it.id) } }
            )
        }

        // Command Input Row
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (activeTab?.ctrlActive == true) "^" else "›",
                color = if (activeTab?.ctrlActive == true) StoneOrange else colors.success,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = commandInput,
                onValueChange = {
                    commandInput = it
                    if (tabCompletions.isNotEmpty()) tabCompletions = emptyList()
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .testTag("terminal_command_input"),
                textStyle = TextStyle(
                    color = colors.text,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(StoneOrange),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii
                ),
                keyboardActions = KeyboardActions(onSend = { submitCommand() }),
                decorationBox = { inner ->
                    Box {
                        if (commandInput.isEmpty()) {
                            Text(
                                text = "Enter shell command…",
                                color = colors.muted,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp
                            )
                        }
                        inner()
                    }
                }
            )
            IconAction(
                icon = Icons.Default.PlayArrow,
                description = "Execute command",
                onClick = { submitCommand() },
                tint = StoneOrange,
                testTag = "terminal_run_button"
            )
        }
    }
}

@Composable
private fun ExtraKeyButton(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalStoneColors.current
    Surface(
        onClick = onClick,
        color = if (active) StoneOrange else colors.panel,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, if (active) StoneOrange else colors.line),
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (active) androidx.compose.ui.graphics.Color.White else colors.text,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
