package com.crazestudio.stone.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.FileTreeIterator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

enum class ProjectTemplate(val label: String, val subtitle: String) {
    WEB_APP("Web App", "HTML, CSS & live JS runner"),
    JS_CONSOLE("JS Script", "Standalone JS console sandbox"),
    SHELL_KIT("Shell Script", "POSIX sh workspace script"),
    BLANK("Blank", "Empty README workspace")
}

data class GitChangeItem(
    val code: String,
    val path: String
)

data class GitCommitSummary(
    val shortId: String,
    val message: String,
    val author: String,
    val timeLabel: String
)

data class GitWorkspaceInfo(
    val isRepo: Boolean = false,
    val branchName: String = "main",
    val branches: List<String> = emptyList(),
    val changes: List<GitChangeItem> = emptyList(),
    val recentCommits: List<GitCommitSummary> = emptyList(),
    val error: String? = null
)

class ProjectRepository(private val filesDir: File, private val contentResolver: ContentResolver) {
    val projectsRoot: File = File(filesDir, "projects").apply { mkdirs() }

    fun listProjects(): List<File> =
        projectsRoot.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun ensureDefaultProject(): File {
        val existing = listProjects().firstOrNull()
        if (existing != null) return existing
        val folder = File(projectsRoot, "StoneWebStarter")
        if (!folder.exists()) {
            folder.mkdirs()
            writeWebTemplate(folder, "Stone Web Starter")
        }
        return folder
    }

    fun createProject(rawName: String, template: ProjectTemplate): File {
        val name = rawName.trim().replace(Regex("[^A-Za-z0-9._ -]"), "").take(48).ifBlank { "Untitled Project" }
        val folder = uniqueFolder(File(projectsRoot, name)).apply { mkdirs() }
        when (template) {
            ProjectTemplate.WEB_APP -> writeWebTemplate(folder, name)
            ProjectTemplate.JS_CONSOLE -> {
                File(folder, "main.js").writeText(
                    """
                    // $name — Run directly in STONE JS Console
                    const startedAt = new Date().toISOString();
                    console.log("STONE JS Runtime active:", startedAt);

                    const metrics = [12, 18, 27, 42];
                    const total = metrics.reduce((acc, n) => acc + n, 0);
                    console.info("Metrics sum =", total, "| avg =", total / metrics.length);
                    """.trimIndent() + "\n"
                )
                File(folder, "README.md").writeText("# $name\n\nStandalone JavaScript project for STONE.\n")
            }
            ProjectTemplate.SHELL_KIT -> {
                File(folder, "info.sh").writeText(
                    """
                    #!/system/bin/sh
                    echo "Project: $name"
                    echo "Working Dir: $(pwd)"
                    echo "Kernel: $(uname -sr)"
                    ls -la
                    """.trimIndent() + "\n"
                )
                File(folder, "README.md").writeText("# $name\n\nAndroid sandbox shell script workspace.\n")
            }
            ProjectTemplate.BLANK -> {
                File(folder, "README.md").writeText("# $name\n\nCreated privately on-device with STONE.\n")
            }
        }
        return folder
    }

    private fun writeWebTemplate(folder: File, name: String) {
        File(folder, "README.md").writeText("# $name\n\nCreated locally with STONE by CrazeStudio.\n")
        File(folder, "index.html").writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>$name</title>
              <link rel="stylesheet" href="style.css" />
            </head>
            <body>
              <main class="card">
                <span class="badge">STONE SANDBOX</span>
                <h1>$name</h1>
                <p>Live local HTML, CSS, and JavaScript inside your offline workspace.</p>
                <button id="ping-btn">Run Interactive Test</button>
                <pre id="output">Ready.</pre>
              </main>
              <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent() + "\n"
        )
        File(folder, "style.css").writeText(
            """
            :root {
              color-scheme: dark;
              font-family: system-ui, -apple-system, sans-serif;
            }
            body {
              margin: 0;
              min-height: 100vh;
              display: grid;
              place-items: center;
              background: #0c0c0c;
              color: #f4f4f4;
              padding: 20px;
            }
            .card {
              width: min(100%, 420px);
              background: #171717;
              border: 1px solid #303030;
              border-radius: 16px;
              padding: 24px;
            }
            .badge {
              display: inline-block;
              font-size: 11px;
              font-weight: 700;
              letter-spacing: 0.08em;
              color: #ff7900;
              margin-bottom: 10px;
            }
            h1 { margin: 0 0 8px; font-size: 24px; }
            p { color: #9a9a9a; line-height: 1.5; margin: 0 0 18px; }
            button {
              width: 100%;
              padding: 12px 16px;
              border: 0;
              border-radius: 10px;
              background: #ff7900;
              color: #fff;
              font-weight: 600;
              cursor: pointer;
            }
            pre {
              margin: 16px 0 0;
              padding: 12px;
              background: #101010;
              border-radius: 10px;
              color: #8bd8a5;
              font-size: 12px;
              overflow-x: auto;
            }
            """.trimIndent() + "\n"
        )
        File(folder, "app.js").writeText(
            """
            const btn = document.getElementById("ping-btn");
            const out = document.getElementById("output");
            let count = 0;

            console.log("Loaded $name preview script");

            btn?.addEventListener("click", () => {
              count += 1;
              const stamp = new Date().toLocaleTimeString();
              const msg = `Click #${'$'}{count} at ${'$'}{stamp}`;
              out.textContent = msg;
              console.log("Button pressed:", msg);
            });
            """.trimIndent() + "\n"
        )
    }

    private fun uniqueFolder(folder: File): File {
        if (!folder.exists()) return folder
        var index = 2
        while (File(folder.parentFile, "${folder.name} ($index)").exists()) index++
        return File(folder.parentFile, "${folder.name} ($index)")
    }

    suspend fun importFolder(tree: Uri): File = withContext(Dispatchers.IO) {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val name = contentResolver.query(rootUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "Imported Project"
        } ?: "Imported Project"
        val safeName = name.replace(Regex("[^A-Za-z0-9._ -]"), "").take(48).ifBlank { "Imported Project" }
        val target = uniqueFolder(File(projectsRoot, safeName)).apply { mkdirs() }
        copySafChildren(tree, id, target)
        target
    }

    private fun copySafChildren(tree: Uri, parentId: String, target: File) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
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
        val target = uniqueFolder(File(projectsRoot, "Imported ${System.currentTimeMillis().toString().takeLast(5)}")).apply { mkdirs() }
        val root = target.canonicalPath + File.separator
        contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = File(target, entry.name)
                    require(output.canonicalPath.startsWith(root) || output.canonicalPath == target.canonicalPath) {
                        "Unsafe ZIP entry rejected: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
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

    fun resolveSafeChild(project: File, relativePath: String): File? {
        val clean = relativePath.trim().removePrefix("/")
        if (clean.isBlank()) return null
        val root = project.canonicalFile
        val target = File(root, clean).canonicalFile
        return if (target.path.startsWith(root.path + File.separator)) target else null
    }

    fun calculateProjectsBytes(): Long {
        return projectsRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun cloneRepository(url: String, rawName: String): File = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://", ignoreCase = true)) { "Only HTTPS Git URLs are accepted for security." }
        val fallback = trimmed.substringAfterLast('/').removeSuffix(".git")
        val name = rawName.trim().ifBlank { fallback }.replace(Regex("[^A-Za-z0-9._ -]"), "").take(48).ifBlank { "Cloned Project" }
        val target = uniqueFolder(File(projectsRoot, name))
        Git.cloneRepository().setURI(trimmed).setDirectory(target).call().close()
        target
    }

    suspend fun inspectGit(folder: File): GitWorkspaceInfo = withContext(Dispatchers.IO) {
        if (!File(folder, ".git").exists()) {
            return@withContext GitWorkspaceInfo(isRepo = false)
        }
        try {
            Git.open(folder).use { git ->
                val branch = runCatching { git.repository.branch }.getOrDefault("main")
                val branches = runCatching {
                    git.branchList().call().map { it.name.removePrefix("refs/heads/") }
                }.getOrDefault(listOf(branch))
                val status = git.status().call()
                val changes = buildList {
                    status.untracked.sorted().forEach { add(GitChangeItem("U", it)) }
                    status.added.sorted().forEach { add(GitChangeItem("A", it)) }
                    status.changed.sorted().forEach { add(GitChangeItem("M", it)) }
                    status.modified.sorted().forEach { add(GitChangeItem("M", it)) }
                    status.missing.sorted().forEach { add(GitChangeItem("D", it)) }
                    status.removed.sorted().forEach { add(GitChangeItem("D", it)) }
                    status.conflicting.sorted().forEach { add(GitChangeItem("!", it)) }
                }
                val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                val commits = runCatching {
                    git.log().setMaxCount(15).call().map { c ->
                        GitCommitSummary(
                            shortId = c.id.abbreviate(7).name(),
                            message = c.shortMessage,
                            author = c.authorIdent.name,
                            timeLabel = formatter.format(Date(c.commitTime.toLong() * 1000L))
                        )
                    }
                }.getOrDefault(emptyList())

                GitWorkspaceInfo(
                    isRepo = true,
                    branchName = branch,
                    branches = branches,
                    changes = changes,
                    recentCommits = commits
                )
            }
        } catch (e: Exception) {
            GitWorkspaceInfo(isRepo = false, error = e.message ?: "Unable to read Git repository.")
        }
    }

    suspend fun readFileDiff(folder: File, relativePath: String): String = withContext(Dispatchers.IO) {
        try {
            Git.open(folder).use { git ->
                val headId = git.repository.resolve("HEAD^{tree}")
                val out = ByteArrayOutputStream()
                DiffFormatter(out).use { df ->
                    df.setRepository(git.repository)
                    if (headId != null) {
                        val reader = git.repository.newObjectReader()
                        val oldTree = CanonicalTreeParser().apply { reset(reader, headId) }
                        val newTree = FileTreeIterator(git.repository)
                        val diffs = df.scan(oldTree, newTree).filter {
                            it.newPath == relativePath || it.oldPath == relativePath
                        }
                        if (diffs.isNotEmpty()) {
                            df.format(diffs)
                            return@withContext out.toString(Charsets.UTF_8.name()).ifBlank { "(Binary or mode-only change)" }
                        }
                    }
                }
                val target = resolveSafeChild(folder, relativePath)
                if (target != null && target.isFile) {
                    val preview = target.readText().lines().take(60).joinToString("\n") { "+ $it" }
                    "New / Untracked file ($relativePath):\n$preview"
                } else {
                    "No diff available for $relativePath"
                }
            }
        } catch (e: Exception) {
            "Could not compute diff: ${e.message}"
        }
    }

    suspend fun initGit(folder: File): String = withContext(Dispatchers.IO) {
        try {
            if (File(folder, ".git").exists()) {
                "Git is already initialized."
            } else {
                Git.init().setInitialBranch("main").setDirectory(folder).call().close()
                "Initialized Git repository on main."
            }
        } catch (e: Exception) {
            e.message ?: "Could not initialize Git."
        }
    }

    suspend fun checkoutBranch(folder: File, branchName: String, createNew: Boolean): String = withContext(Dispatchers.IO) {
        val clean = branchName.trim().replace(Regex("[^A-Za-z0-9._/-]"), "")
        if (clean.isBlank()) return@withContext "Enter a valid branch name."
        try {
            Git.open(folder).use { git ->
                git.checkout().setCreateBranch(createNew).setName(clean).call()
                if (createNew) "Created and switched to $clean" else "Switched to $clean"
            }
        } catch (e: Exception) {
            e.message ?: "Branch checkout failed."
        }
    }

    suspend fun commit(folder: File, message: String, authorName: String, authorEmail: String): String = withContext(Dispatchers.IO) {
        try {
            Git.open(folder).use { git ->
                git.add().addFilepattern(".").call()
                git.add().setUpdate(true).addFilepattern(".").call()
                val commit = git.commit()
                    .setMessage(message.ifBlank { "Update from STONE" })
                    .setAuthor(authorName.ifBlank { "Stone User" }, authorEmail.ifBlank { "user@stone.local" })
                    .call()
                "Committed ${commit.id.abbreviate(7).name()}"
            }
        } catch (e: Exception) {
            e.message ?: "Commit failed."
        }
    }

    suspend fun discardChanges(folder: File, relativePath: String): String = withContext(Dispatchers.IO) {
        try {
            Git.open(folder).use { git ->
                git.checkout().addPath(relativePath).call()
                "Restored $relativePath"
            }
        } catch (e: Exception) {
            e.message ?: "Could not restore file."
        }
    }
}
