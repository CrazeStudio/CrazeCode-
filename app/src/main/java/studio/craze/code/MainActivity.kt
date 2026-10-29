package studio.craze.code

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private val openCode = 2101
    private val saveCode = 2102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        @JavascriptInterface fun saveFile(text: String, suggestedName: String) {
            runOnUiThread {
                pendingSaveText = text
                pendingSaveName = suggestedName.ifBlank { "untitled.txt" }
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, pendingSaveName)
                }
                startActivityForResult(intent, saveCode)
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
            } catch (e: Exception) {
                Toast.makeText(this, "Could not save file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}