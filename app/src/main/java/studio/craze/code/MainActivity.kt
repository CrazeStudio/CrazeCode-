package studio.craze.code

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pendingSaveText = ""
    private var pendingSaveName = "untitled.txt"
    private var selectedFolder: Uri? = null
    private val openCode = 2101
    private val saveCode = 2102
    private val folderCode = 2103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedFolder = getPreferences(MODE_PRIVATE).getString("workspace_tree_uri", null)?.let { Uri.parse(it) }
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                    return true
                }
                return false
            }
        }
        web.addJavascriptInterface(Bridge(), "CrazeNative")
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class Bridge {
        @JavascriptInterface fun openFile() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, openCode)
            }
        }

        @JavascriptInterface fun chooseFolder() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, folderCode)
            }
        }

        @JavascriptInterface fun saveFile(text: String, suggestedName: String) {
            runOnUiThread {
                pendingSaveText = text
                pendingSaveName = suggestedName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "untitled.txt" }
                val folder = selectedFolder
                if (folder != null) {
                    try {
                        val mime = when (pendingSaveName.substringAfterLast('.', "").lowercase()) {
                            "html", "htm" -> "text/html"
                            "js", "mjs", "ts", "css", "json", "xml", "md", "py", "kt", "java", "c", "cpp", "h", "sql", "sh" -> "text/plain"
                            "svg" -> "image/svg+xml"
                            else -> "text/plain"
                        }
                        val doc = DocumentsContract.createDocument(contentResolver, folder, mime, pendingSaveName)
                            ?: throw IllegalStateException("Could not create file in selected folder")
                        contentResolver.openOutputStream(doc, "wt")?.bufferedWriter()?.use { it.write(pendingSaveText) }
                            ?: throw IllegalStateException("Could not open destination")
                        Toast.makeText(this@MainActivity, "Saved to selected folder", Toast.LENGTH_SHORT).show()
                        web.evaluateJavascript("window.nativeSaveComplete && window.nativeSaveComplete(true)", null)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Folder save failed: ${e.message}", Toast.LENGTH_LONG).show()
                        web.evaluateJavascript("window.nativeSaveComplete && window.nativeSaveComplete(false)", null)
                    }
                } else {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, pendingSaveName)
                    }
                    startActivityForResult(intent, saveCode)
                }
            }
        }
    }

    @Deprecated("Deprecated in Android SDK but retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            openCode -> try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "opened-file.txt"
                web.evaluateJavascript("window.receiveNativeFile(${org.json.JSONObject.quote(name)}, ${org.json.JSONObject.quote(text)});", null)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open file: ${e.message}", Toast.LENGTH_LONG).show()
            }
            saveCode -> try {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingSaveText) }
                Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show()
                web.evaluateJavascript("window.nativeSaveComplete && window.nativeSaveComplete(true)", null)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not save file: ${e.message}", Toast.LENGTH_LONG).show()
                web.evaluateJavascript("window.nativeSaveComplete && window.nativeSaveComplete(false)", null)
            }
            folderCode -> {
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
                selectedFolder = uri
                getPreferences(MODE_PRIVATE).edit().putString("workspace_tree_uri", uri.toString()).apply()
                web.evaluateJavascript("window.folderSelected && window.folderSelected(${org.json.JSONObject.quote(uri.lastPathSegment ?: "Selected folder")})", null)
                Toast.makeText(this, "CrazeCode save folder selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
