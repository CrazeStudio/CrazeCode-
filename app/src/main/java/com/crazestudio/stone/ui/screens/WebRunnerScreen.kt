package com.crazestudio.stone.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.crazestudio.stone.ui.ConsoleLevel
import com.crazestudio.stone.ui.ConsoleLogEntry
import com.crazestudio.stone.ui.WebRunnerMode
import com.crazestudio.stone.ui.WebRunnerState
import com.crazestudio.stone.ui.components.IconAction
import com.crazestudio.stone.ui.components.PageHeader
import com.crazestudio.stone.ui.theme.JetBrainsMonoFontFamily
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.StoneOrange
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

private const val VIRTUAL_HOST = "stone.local"
private const val VIRTUAL_ORIGIN = "https://$VIRTUAL_HOST/"

private class StoneConsoleBridge(
    private val onLog: (ConsoleLevel, String) -> Unit
) {
    @JavascriptInterface
    fun post(level: String, message: String) {
        val mapped = when (level.uppercase()) {
            "INFO" -> ConsoleLevel.INFO
            "WARN" -> ConsoleLevel.WARN
            "ERROR" -> ConsoleLevel.ERROR
            else -> ConsoleLevel.LOG
        }
        onLog(mapped, message)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebRunnerScreen(
    project: File,
    state: WebRunnerState,
    onBack: (() -> Unit)?,
    onLog: (ConsoleLevel, String) -> Unit,
    onClearLogs: () -> Unit,
    onToggleViewport: () -> Unit,
    onToggleNetwork: () -> Unit,
    onReload: () -> Unit,
    onConsumeSnippet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStoneColors.current
    val context = LocalContext.current
    var replInput by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val targetFile = state.targetFilePath?.let(::File)?.takeIf { it.isFile }
    val title = if (state.mode == WebRunnerMode.JS_CONSOLE) "JS Console Runner" else "WebView Preview"
    val subtitle = targetFile?.name ?: project.name

    fun evalSnippet(code: String) {
        val wv = webViewRef ?: return
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        onLog(ConsoleLevel.INFO, "> $trimmed")
        wv.evaluateJavascript(
            """
            (function() {
              try {
                var __res = (0, eval)(${org.json.JSONObject.quote(trimmed)});
                if (__res !== undefined) {
                  console.log(typeof __res === 'object' ? JSON.stringify(__res) : String(__res));
                }
              } catch (err) {
                console.error(err && err.stack ? err.stack : String(err));
              }
            })();
            """.trimIndent(),
            null
        )
    }

    LaunchedEffect(state.pendingSnippet, webViewRef) {
        val snippet = state.pendingSnippet
        if (!snippet.isNullOrBlank() && webViewRef != null) {
            evalSnippet(snippet)
            onConsumeSnippet()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        PageHeader(
            title = title,
            subtitle = subtitle,
            onBack = onBack
        ) {
            if (state.mode == WebRunnerMode.HTML_PREVIEW) {
                IconAction(
                    icon = Icons.Default.Devices,
                    description = "Toggle mobile viewport",
                    onClick = onToggleViewport,
                    tint = if (state.mobileViewport) StoneOrange else colors.muted
                )
            }
            IconAction(
                icon = if (state.allowExternalNetwork) Icons.Default.Public else Icons.Default.Lock,
                description = if (state.allowExternalNetwork) "External requests allowed" else "Offline sandbox active",
                onClick = onToggleNetwork,
                tint = if (state.allowExternalNetwork) StoneOrange else colors.success
            )
            IconAction(
                icon = Icons.Default.Refresh,
                description = "Reload runner",
                onClick = onReload,
                tint = colors.text,
                testTag = "web_runner_reload"
            )
        }

        // Security & Mode Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.allowExternalNetwork) "Network: Project + External HTTPS" else "Sandbox: Offline Local Files Only",
                color = if (state.allowExternalNetwork) StoneOrange else colors.success,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.mobileViewport) "390dp Mobile" else "Full Width",
                color = colors.muted,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp
            )
        }
        HorizontalDivider(color = colors.line)

        // WebView Viewport (shown larger in HTML_PREVIEW, compact headless host in JS_CONSOLE)
        val previewWeight = if (state.mode == WebRunnerMode.HTML_PREVIEW) 0.56f else 0.01f
        Box(
            modifier = Modifier
                .weight(previewWeight)
                .fillMaxWidth()
                .background(colors.panel2),
            contentAlignment = Alignment.TopCenter
        ) {
            val containerModifier = if (state.mobileViewport && state.mode == WebRunnerMode.HTML_PREVIEW) {
                Modifier
                    .widthIn(max = 390.dp)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }

            AndroidView(
                modifier = containerModifier,
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        addJavascriptInterface(StoneConsoleBridge(onLog), "StoneBridge")
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                                val lvl = when (cm.messageLevel()) {
                                    ConsoleMessage.MessageLevel.ERROR -> ConsoleLevel.ERROR
                                    ConsoleMessage.MessageLevel.WARNING -> ConsoleLevel.WARN
                                    ConsoleMessage.MessageLevel.LOG -> ConsoleLevel.LOG
                                    else -> ConsoleLevel.INFO
                                }
                                onLog(lvl, "${cm.message()} (L${cm.lineNumber()})")
                                return true
                            }
                        }
                        webViewRef = this
                    }
                },
                update = { wv ->
                    val projectRoot = project.canonicalFile
                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val uri: Uri = request.url
                            if (uri.host == VIRTUAL_HOST) {
                                val rel = uri.path.orEmpty().removePrefix("/")
                                if (rel == "__stone_js_runner__.html") {
                                    val jsCode = targetFile?.takeIf { it.isFile }?.readText() ?: ""
                                    val html = buildJsHarnessHtml(targetFile?.name ?: "console.js", jsCode)
                                    return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
                                }
                                val candidate = File(projectRoot, rel).canonicalFile
                                if (candidate.path.startsWith(projectRoot.path + File.separator) && candidate.isFile) {
                                    return WebResourceResponse(
                                        mimeTypeFor(candidate.extension),
                                        "utf-8",
                                        FileInputStream(candidate)
                                    )
                                }
                                return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            if (!state.allowExternalNetwork) {
                                onLog(ConsoleLevel.WARN, "Blocked external request: $uri")
                                return WebResourceResponse("text/plain", "utf-8", 403, "Blocked by Sandbox", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    val tagKey = "${state.mode}|${state.targetFilePath}|${state.reloadNonce}"
                    if (wv.tag != tagKey) {
                        wv.tag = tagKey
                        if (state.mode == WebRunnerMode.JS_CONSOLE) {
                            wv.loadUrl("${VIRTUAL_ORIGIN}__stone_js_runner__.html")
                        } else {
                            val rel = targetFile?.let {
                                runCatching { it.canonicalFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/') }.getOrNull()
                            }
                            if (rel != null) {
                                wv.loadUrl("$VIRTUAL_ORIGIN$rel")
                            } else {
                                val fallbackHtml = "<html><body style='background:#0c0c0c;color:#9a9a9a;font-family:sans-serif;padding:24px;'><h2>No HTML entry file found</h2><p>Create an index.html file in ${project.name} or open the JS Console Runner.</p></body></html>"
                                wv.loadDataWithBaseURL(VIRTUAL_ORIGIN, fallbackHtml, "text/html", "utf-8", null)
                            }
                        }
                    }
                }
            )
        }

        // Console Output Drawer / Pane
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONSOLE (${state.consoleLogs.size})",
                color = colors.text,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconAction(
                icon = Icons.Default.DeleteSweep,
                description = "Clear console",
                onClick = onClearLogs,
                tint = colors.muted,
                iconSize = 17.dp
            )
        }
        HorizontalDivider(color = colors.line)

        LazyColumn(
            modifier = Modifier
                .weight(if (state.mode == WebRunnerMode.JS_CONSOLE) 0.99f else 0.44f)
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.consoleLogs.isEmpty()) {
                item {
                    Text(
                        text = "Console ready. Use console.log(...) in your script or evaluate JS below.",
                        color = colors.muted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp
                    )
                }
            } else {
                items(state.consoleLogs, key = { it.id }) { entry ->
                    ConsoleLine(entry)
                }
            }
        }

        // Interactive JS REPL Bar
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "js›",
                color = StoneOrange,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = replInput,
                onValueChange = { replInput = it },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .testTag("js_repl_input"),
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
                keyboardActions = KeyboardActions(
                    onSend = {
                        val code = replInput
                        replInput = ""
                        evalSnippet(code)
                    }
                ),
                decorationBox = { inner ->
                    Box {
                        if (replInput.isEmpty()) {
                            Text(
                                text = "Evaluate JavaScript expression…",
                                color = colors.muted,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp
                            )
                        }
                        inner()
                    }
                }
            )
            IconAction(
                icon = Icons.Default.PlayArrow,
                description = "Run JavaScript",
                onClick = {
                    val code = replInput
                    replInput = ""
                    evalSnippet(code)
                },
                tint = StoneOrange,
                testTag = "js_repl_run_button"
            )
        }
    }
}

@Composable
private fun ConsoleLine(entry: ConsoleLogEntry) {
    val colors = LocalStoneColors.current
    val accent = when (entry.level) {
        ConsoleLevel.ERROR -> colors.error
        ConsoleLevel.WARN -> colors.warning
        ConsoleLevel.INFO -> StoneOrange
        ConsoleLevel.LOG -> colors.success
    }
    Surface(
        color = colors.panel2,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.line.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = entry.timestamp,
                color = colors.muted,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.message,
                color = accent,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun buildJsHarnessHtml(fileName: String, scriptText: String): String {
    val escapedScript = scriptText.replace("</script>", "<\\/script>")
    return """
        <!doctype html>
        <html>
        <head><meta charset="utf-8"><title>$fileName</title></head>
        <body style="background:#0c0c0c;color:#f4f4f4;font-family:monospace;padding:12px;">
          <div style="font-size:12px;color:#9a9a9a;">STONE JS Runtime · $fileName</div>
          <script>
            (function() {
              function fmt(args) {
                return Array.prototype.slice.call(args).map(function(a) {
                  try { return typeof a === 'object' ? JSON.stringify(a) : String(a); }
                  catch (e) { return String(a); }
                }).join(' ');
              }
              console.log = function() { StoneBridge.post('LOG', fmt(arguments)); };
              console.info = function() { StoneBridge.post('INFO', fmt(arguments)); };
              console.warn = function() { StoneBridge.post('WARN', fmt(arguments)); };
              console.error = function() { StoneBridge.post('ERROR', fmt(arguments)); };
              window.onerror = function(msg, src, line) {
                StoneBridge.post('ERROR', msg + ' (L' + line + ')');
              };
            })();
          </script>
          <script>
            try {
              $escapedScript
            } catch (e) {
              console.error(e && e.stack ? e.stack : String(e));
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js", "mjs" -> "application/javascript"
    "json" -> "application/json"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "txt", "md" -> "text/plain"
    else -> "application/octet-stream"
}
