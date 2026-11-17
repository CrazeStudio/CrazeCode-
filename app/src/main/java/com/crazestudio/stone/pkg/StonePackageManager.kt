package com.crazestudio.stone.pkg

import android.os.Build
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** One installable package from the index. */
data class PkgEntry(
    val name: String,
    val version: String,
    val description: String,
    /** "bin" = single executable, "tar.gz" or "zip" = archive extracted to $PREFIX/opt/<name> */
    val type: String,
    val strip: Int,
    val urls: Map<String, String>,
    val sha256: Map<String, String>,
    val depends: List<String>,
    /** command name -> path inside the extracted archive (archives only) */
    val bins: Map<String, String>
)

/**
 * apt-style package manager for STONE's terminal.
 *
 * Packages are prebuilt, statically linked Linux binaries (they do not need glibc/musl or a rootfs),
 * installed under $PREFIX (filesDir/usr). $PREFIX/bin is put on PATH by the terminal.
 */
class StonePackageManager(filesDir: File, private val cacheDir: File) {

    val prefix = File(filesDir, "usr")
    val binDir = File(prefix, "bin")
    private val optDir = File(prefix, "opt")
    private val stateDir = File(prefix, "var/lib/stone-pkg")
    private val dbFile = File(stateDir, "installed.json")
    private val indexCache = File(stateDir, "index.json")

    private val nameRegex = Regex("^[a-z0-9][a-z0-9+._-]*$")

    fun ensureDirs() {
        binDir.mkdirs(); optDir.mkdirs(); stateDir.mkdirs(); cacheDir.mkdirs()
    }

    // ---------------------------------------------------------------- commands

    /** Runs `apt <args>`. Returns an exit code. All output goes through [log] (ANSI allowed). */
    fun execute(rawArgs: List<String>, log: (String) -> Unit): Int {
        ensureDirs()
        val args = rawArgs.drop(1).filterNot { it == "-y" || it == "--yes" || it == "-q" || it == "-qq" }
        val sub = args.firstOrNull() ?: "help"
        val rest = args.drop(1)
        return when (sub) {
            "update" -> update(log)
            "install", "add" -> if (rest.isEmpty()) usage(log, 1) else install(rest, log)
            "remove", "purge", "uninstall", "autoremove" -> if (rest.isEmpty()) usage(log, 1) else remove(rest, log)
            "upgrade", "full-upgrade", "dist-upgrade" -> upgrade(log)
            "list" -> list(rest.contains("--installed"), log)
            "search" -> if (rest.isEmpty()) usage(log, 1) else search(rest.joinToString(" "), log)
            "show", "info" -> if (rest.isEmpty()) usage(log, 1) else show(rest[0], log)
            "help", "--help", "-h" -> usage(log, 0)
            else -> { log("\u001B[31mE: Invalid operation $sub\u001B[0m\n"); usage(log, 100) }
        }
    }

    private fun usage(log: (String) -> Unit, code: Int): Int {
        log(
            "\u001B[1mapt (STONE)\u001B[0m - package manager\n" +
                "  apt update              refresh the package index\n" +
                "  apt install <pkg...>    install packages (+ dependencies)\n" +
                "  apt remove <pkg...>     remove packages\n" +
                "  apt upgrade             upgrade installed packages\n" +
                "  apt list [--installed]  list packages\n" +
                "  apt search <text>       search names and descriptions\n" +
                "  apt show <pkg>          show package details\n" +
                "Aliases: pkg, apt-get, stone-pkg\n"
        )
        return code
    }

    fun update(log: (String) -> Unit): Int {
        log("\u001B[2mGet: $INDEX_URL\u001B[0m\n")
        return try {
            val text = httpGetText(INDEX_URL)
            val count = parseIndex(text).size // validates before saving
            indexCache.writeText(text)
            log("Reading package lists... Done ($count packages)\n")
            0
        } catch (e: Exception) {
            log("\u001B[33mW: Could not fetch index (${e.message}); using ${if (indexCache.exists()) "cached" else "built-in"} index.\u001B[0m\n")
            0
        }
    }

    fun install(names: List<String>, log: (String) -> Unit): Int {
        val index = loadIndex()
        val installed = loadDb()
        val order = LinkedHashMap<String, PkgEntry>()

        fun visit(name: String, stack: Set<String>): Boolean {
            if (name in order || installed.has(name)) return true
            if (name in stack) return true
            val entry = index[name]
            if (entry == null) {
                log("\u001B[31mE: Unable to locate package $name\u001B[0m\n")
                return false
            }
            for (dep in entry.depends) if (!visit(dep, stack + name)) return false
            order[name] = entry
            return true
        }
        for (n in names) if (!visit(n, emptySet())) return 100

        if (order.isEmpty()) {
            log("${names.joinToString(" ")} is already the newest version.\n")
            return 0
        }
        log("The following packages will be installed:\n  ${order.keys.joinToString(" ")}\n")
        for (entry in order.values) {
            val rc = installOne(entry, log)
            if (rc != 0) return rc
        }
        return 0
    }

    fun remove(names: List<String>, log: (String) -> Unit): Int {
        val db = loadDb()
        val index = loadIndex()
        for (name in names) {
            if (!db.has(name)) {
                log("\u001B[33mPackage $name is not installed, so not removed\u001B[0m\n")
                continue
            }
            val blockers = db.keys().asSequence()
                .filter { it != name && it !in names && index[it]?.depends?.contains(name) == true }
                .toList()
            if (blockers.isNotEmpty()) {
                log("\u001B[31mE: $name is required by ${blockers.joinToString(", ")}\u001B[0m\n")
                return 100
            }
            val files = db.getJSONObject(name).optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    val f = safeUnderPrefix(files.getString(i)) ?: continue
                    f.deleteRecursively()
                }
            }
            db.remove(name)
            saveDb(db)
            log("Removing $name... Done\n")
        }
        return 0
    }

    fun upgrade(log: (String) -> Unit): Int {
        val index = loadIndex()
        val db = loadDb()
        val outdated = db.keys().asSequence().mapNotNull { n ->
            val e = index[n]
            if (e != null && e.version != db.getJSONObject(n).optString("version")) e else null
        }.toList()
        if (outdated.isEmpty()) { log("All packages are up to date.\n"); return 0 }
        log("Upgrading: ${outdated.joinToString(" ") { it.name }}\n")
        for (e in outdated) {
            val rc = installOne(e, log)
            if (rc != 0) return rc
        }
        return 0
    }

    fun list(installedOnly: Boolean, log: (String) -> Unit): Int {
        val index = loadIndex()
        val db = loadDb()
        val arch = currentArch()
        for (e in index.values.sortedBy { it.name }) {
            val inst = db.has(e.name)
            if (installedOnly && !inst) continue
            val avail = if (e.urls.containsKey(arch)) "" else " \u001B[31m(no $arch build)\u001B[0m"
            log("\u001B[32m${e.name}\u001B[0m/stable ${e.version} $arch${if (inst) " [installed]" else ""}$avail\n")
        }
        return 0
    }

    fun search(q: String, log: (String) -> Unit): Int {
        val needle = q.lowercase()
        val hits = loadIndex().values.filter { it.name.contains(needle) || it.description.lowercase().contains(needle) }
        if (hits.isEmpty()) log("No packages found for \"$q\"\n")
        for (e in hits.sortedBy { it.name }) log("\u001B[32m${e.name}\u001B[0m/stable ${e.version}\n  ${e.description}\n")
        return 0
    }

    fun show(name: String, log: (String) -> Unit): Int {
        val e = loadIndex()[name]
        if (e == null) { log("\u001B[31mE: No packages found: $name\u001B[0m\n"); return 100 }
        log(
            "Package: ${e.name}\nVersion: ${e.version}\nType: ${e.type}\n" +
                "Depends: ${e.depends.joinToString(", ").ifBlank { "-" }}\n" +
                "Architectures: ${e.urls.keys.joinToString(", ")}\n" +
                "Description: ${e.description}\n" +
                "Installed: ${if (loadDb().has(name)) "yes" else "no"}\n"
        )
        return 0
    }

    // ---------------------------------------------------------------- install

    private fun installOne(e: PkgEntry, log: (String) -> Unit): Int {
        val arch = currentArch()
        val url = e.urls[arch]
        if (url == null) {
            log("\u001B[31mE: ${e.name} has no build for $arch\u001B[0m\n")
            return 100
        }
        if (!url.startsWith("https://")) {
            log("\u001B[31mE: refusing non-HTTPS download for ${e.name}\u001B[0m\n")
            return 100
        }
        val tmp = File(cacheDir, "pkg-${e.name}.download")
        try {
            log("Get: ${e.name} ${e.version} [$arch]\n")
            val sha = httpDownload(url, tmp, log)
            val expected = e.sha256[arch]
            if (expected != null) {
                if (!expected.equals(sha, ignoreCase = true)) {
                    log("\u001B[31mE: checksum mismatch for ${e.name} (expected $expected, got $sha)\u001B[0m\n")
                    return 100
                }
                log("\u001B[2mSHA256 verified\u001B[0m\n")
            } else {
                log("\u001B[33mW: no checksum in index for ${e.name}; downloaded over HTTPS only\u001B[0m\n")
            }

            val files = mutableListOf<String>()
            log("Unpacking ${e.name}...\n")
            when (e.type) {
                "bin" -> {
                    val dest = File(binDir, e.name)
                    tmp.copyTo(dest, overwrite = true)
                    dest.setExecutable(true, false)
                    files += "bin/${e.name}"
                }
                "tar.gz", "zip" -> {
                    val dir = File(optDir, e.name)
                    dir.deleteRecursively()
                    dir.mkdirs()
                    if (e.type == "zip") extractZip(tmp, dir, e.strip) else extractTarGz(tmp, dir, e.strip)
                    files += "opt/${e.name}"
                    for ((cmd, rel) in e.bins) {
                        if (!nameRegex.matches(cmd) || rel.contains("..")) continue
                        val target = File(dir, rel)
                        if (!target.isFile) {
                            log("\u001B[33mW: ${e.name}: $rel not found in archive\u001B[0m\n")
                            continue
                        }
                        target.setExecutable(true, false)
                        val wrapper = File(binDir, cmd)
                        wrapper.writeText("#!/system/bin/sh\nexec \"${target.absolutePath}\" \"\$@\"\n")
                        wrapper.setExecutable(true, false)
                        files += "bin/$cmd"
                    }
                }
                else -> { log("\u001B[31mE: unknown package type ${e.type}\u001B[0m\n"); return 100 }
            }

            val db = loadDb()
            db.put(e.name, JSONObject().put("version", e.version).put("files", org.json.JSONArray(files)))
            saveDb(db)
            log("\u001B[32mSetting up ${e.name} (${e.version})... Done\u001B[0m\n")
            return 0
        } catch (ex: Exception) {
            log("\u001B[31mE: failed to install ${e.name}: ${ex.message}\u001B[0m\n")
            return 100
        } finally {
            tmp.delete()
        }
    }

    // ---------------------------------------------------------------- index + db

    private fun loadIndex(): Map<String, PkgEntry> {
        val text = runCatching { if (indexCache.exists()) indexCache.readText() else null }.getOrNull()
        val parsed = text?.let { runCatching { parseIndex(it) }.getOrNull() }
        return parsed ?: parseIndex(BuiltinIndex.JSON)
    }

    private fun parseIndex(text: String): Map<String, PkgEntry> {
        val arr = JSONObject(text).getJSONArray("packages")
        val out = LinkedHashMap<String, PkgEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("name")
            if (!nameRegex.matches(name)) continue
            fun strMap(key: String): Map<String, String> {
                val m = o.optJSONObject(key) ?: return emptyMap()
                return m.keys().asSequence().associateWith { m.getString(it) }
            }
            val deps = o.optJSONArray("depends")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            out[name] = PkgEntry(
                name = name,
                version = o.getString("version"),
                description = o.optString("description"),
                type = o.optString("type", "bin"),
                strip = o.optInt("strip", 0),
                urls = strMap("urls"),
                sha256 = strMap("sha256"),
                depends = deps,
                bins = strMap("bins")
            )
        }
        return out
    }

    private fun loadDb(): JSONObject =
        runCatching { JSONObject(dbFile.readText()) }.getOrDefault(JSONObject())

    private fun saveDb(db: JSONObject) {
        stateDir.mkdirs()
        dbFile.writeText(db.toString())
    }

    private fun safeUnderPrefix(rel: String): File? {
        val f = File(prefix, rel).canonicalFile
        val root = prefix.canonicalPath + File.separator
        return if (f.path.startsWith(root)) f else null
    }

    // ---------------------------------------------------------------- network

    private fun httpGetText(url: String): String {
        val c = open(url)
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "HTTPS required" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "STONE-apt/1.0")
        if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode} for $url")
        return c
    }

    /** Downloads [url] to [dest]; returns the SHA-256 (hex) of the downloaded bytes. */
    private fun httpDownload(url: String, dest: File, log: (String) -> Unit): String {
        val c = open(url)
        val total = c.contentLengthLong
        val md = MessageDigest.getInstance("SHA-256")
        var read = 0L
        var nextMark = 25
        c.inputStream.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    read += n
                    if (total > 0) {
                        val pct = (read * 100 / total).toInt()
                        if (pct >= nextMark) { log("  $pct% (${read / 1024} KB)\n"); nextMark += 25 }
                    }
                }
            }
        }
        log("Fetched ${read / 1024} KB\n")
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------------------------------------------------------------- archives

    private fun stripPath(name: String, strip: Int): String? {
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.size <= strip) return null
        return parts.drop(strip).joinToString("/")
    }

    private fun resolveInside(dest: File, rel: String): File {
        val root = dest.canonicalPath
        val f = File(dest, rel).canonicalFile
        if (f.path != root && !f.path.startsWith(root + File.separator)) {
            throw SecurityException("archive entry escapes install dir: $rel")
        }
        return f
    }

    private fun extractZip(file: File, dest: File, strip: Int) {
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val rel = stripPath(entry.name, strip) ?: continue
                val target = resolveInside(dest, rel)
                if (entry.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { zin.copyTo(it) }
            }
        }
    }

    private fun extractTarGz(file: File, dest: File, strip: Int) {
        GZIPInputStream(BufferedInputStream(FileInputStream(file))).use { extractTar(it, dest, strip) }
    }

    private fun extractTar(input: InputStream, dest: File, strip: Int) {
        val header = ByteArray(512)
        var longName: String? = null
        var paxPath: String? = null
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break
            var name = cString(header, 0, 100)
            val mode = octal(header, 100, 8).toInt()
            val size = octal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val linkName = cString(header, 157, 100)
            if (cString(header, 257, 5) == "ustar") {
                val p = cString(header, 345, 155)
                if (p.isNotEmpty()) name = "$p/$name"
            }
            val pad = ((512 - size % 512) % 512)

            when (type) {
                'L' -> {
                    longName = String(readN(input, size)).trimEnd('\u0000', '\n')
                    skipN(input, pad); continue
                }
                'x' -> {
                    val rec = String(readN(input, size))
                    Regex("\\d+ path=([^\\n]*)\\n").find(rec)?.let { paxPath = it.groupValues[1] }
                    skipN(input, pad); continue
                }
                'g' -> { skipN(input, size + pad); continue }
            }

            val fullName = paxPath ?: longName ?: name
            paxPath = null; longName = null
            val rel = stripPath(fullName, strip)
            if (rel == null) { skipN(input, size + pad); continue }
            val target = resolveInside(dest, rel)

            when (type) {
                '5' -> { target.mkdirs(); skipN(input, size + pad) }
                '0', '\u0000', '7' -> {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out ->
                        var left = size
                        val buf = ByteArray(64 * 1024)
                        while (left > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) throw java.io.EOFException("truncated tar")
                            out.write(buf, 0, n); left -= n
                        }
                    }
                    skipN(input, pad)
                    if (mode and 0b001001001 != 0) target.setExecutable(true, false)
                }
                '2' -> {
                    // symlink: only allow relative links that stay inside the install dir
                    val resolved = File(target.parentFile, linkName).let { runCatching { it.canonicalFile }.getOrNull() }
                    val root = dest.canonicalPath
                    if (!linkName.startsWith("/") && resolved != null &&
                        (resolved.path == root || resolved.path.startsWith(root + File.separator))
                    ) {
                        target.parentFile?.mkdirs()
                        target.delete()
                        Files.createSymbolicLink(target.toPath(), Paths.get(linkName))
                    }
                    skipN(input, size + pad)
                }
                '1' -> {
                    val srcRel = stripPath(linkName, strip)
                    if (srcRel != null) {
                        val src = resolveInside(dest, srcRel)
                        if (src.isFile) { target.parentFile?.mkdirs(); src.copyTo(target, overwrite = true) }
                    }
                    skipN(input, size + pad)
                }
                else -> skipN(input, size + pad)
            }
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off != 0 && throw java.io.EOFException("truncated tar header")
            off += n
        }
        return true
    }

    private fun readN(input: InputStream, n: Long): ByteArray {
        require(n in 0..1_000_000) { "unreasonable tar header size" }
        val out = ByteArray(n.toInt())
        var off = 0
        while (off < out.size) {
            val r = input.read(out, off, out.size - off)
            if (r < 0) throw java.io.EOFException("truncated tar")
            off += r
        }
        return out
    }

    private fun skipN(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw java.io.EOFException("truncated tar")
            left -= r
        }
    }

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        // GNU base-256 encoding for very large sizes
        if (b[off].toInt() and 0x80 != 0) {
            var v = 0L
            for (i in 1 until len) v = (v shl 8) or (b[off + i].toLong() and 0xff)
            return v
        }
        val s = cString(b, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    companion object {
        const val INDEX_URL = "https://raw.githubusercontent.com/CrazeStudio/CrazeCode-/main/repo/packages.json"

        val COMMAND_NAMES = setOf("apt", "apt-get", "pkg", "stone-pkg")

        fun currentArch(): String {
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            return when {
                abi.startsWith("arm64") -> "aarch64"
                abi.startsWith("armeabi") -> "arm"
                abi == "x86_64" -> "x86_64"
                abi == "x86" -> "i686"
                else -> abi
            }
        }
    }
}
