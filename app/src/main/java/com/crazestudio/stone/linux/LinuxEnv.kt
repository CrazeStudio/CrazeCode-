package com.crazestudio.stone.linux

import android.system.Os
import com.crazestudio.stone.pkg.StonePackageManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class LaunchSpec(val command: List<String>, val env: Map<String, String>)

/**
 * A real Linux userland inside the app, Acode-terminal style: PRoot (no root needed) running an
 * Alpine Linux root filesystem. Inside it `apk add python3 git nodejs ...` installs real packages.
 *
 * Layout (all under <filesDir>/linux):
 *   proot/   relocatable PRoot build (bin/proot + libexec/proot/loader)
 *   rootfs/  Alpine minirootfs
 */
class LinuxEnv(filesDir: File, private val cacheDir: File) {

    private val base = File(filesDir, "linux")
    private val prootDir = File(base, "proot")
    val rootfs = File(base, "rootfs")
    private val marker = File(base, "installed")
    private val compatFlag = File(base, "no-seccomp")

    // ------------------------------------------------------------ state

    fun isInstalled(): Boolean = marker.exists() && prootBin()?.canExecute() == true && File(rootfs, "bin").isDirectory

    fun installedVersion(): String? = runCatching { marker.readText().trim() }.getOrNull()

    fun compatMode(): Boolean = compatFlag.exists()

    fun setCompatMode(on: Boolean) {
        base.mkdirs()
        if (on) compatFlag.writeText("1") else compatFlag.delete()
    }

    fun reset() {
        base.deleteRecursively()
    }

    private fun prootBin(): File? = findFile(prootDir) { it.name == "proot" && it.parentFile?.name == "bin" }
    private fun prootLoader(): File? = findFile(prootDir) { it.name == "loader" }

    private fun findFile(dir: File, match: (File) -> Boolean): File? {
        val kids = dir.listFiles() ?: return null
        for (k in kids) {
            if (k.isDirectory) findFile(k, match)?.let { return it }
            else if (match(k)) return k
        }
        return null
    }

    // ------------------------------------------------------------ launching

    /**
     * Builds the proot command line. [inner] runs inside Alpine (e.g. listOf("/bin/sh")).
     * [workDir] (an app-private folder) is bind-mounted at /work.
     */
    fun launchSpec(workDir: File, inner: List<String>): LaunchSpec {
        val proot = prootBin() ?: throw IllegalStateException("proot is not installed")
        val tmp = File(cacheDir, "proot-tmp").apply { mkdirs() }
        File(rootfs, "work").mkdirs()

        val cmd = mutableListOf(
            proot.absolutePath,
            "--kill-on-exit", "--link2symlink", "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${workDir.absolutePath}:/work",
            "-w", "/work",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color", "COLORTERM=truecolor", "LANG=C.UTF-8",
            "TMPDIR=/tmp", "STONE_LINUX=1"
        )
        cmd += inner

        val env = mutableMapOf("PROOT_TMP_DIR" to tmp.absolutePath)
        prootLoader()?.let { env["PROOT_LOADER"] = it.absolutePath }
        if (compatMode()) env["PROOT_NO_SECCOMP"] = "1"
        return LaunchSpec(cmd, env)
    }

    /** Runs a quick command inside Alpine and reports whether it worked. */
    fun test(log: (String) -> Unit): Boolean {
        return try {
            val spec = launchSpec(cacheDir, listOf("/bin/sh", "-c", "echo STONE_OK; uname -m; apk --version"))
            val pb = ProcessBuilder(spec.command).directory(cacheDir).redirectErrorStream(true)
            pb.environment().putAll(spec.env)
            val p = pb.start()
            val finished = p.waitFor(40, TimeUnit.SECONDS)
            if (!finished) p.destroyForcibly()
            val out = p.inputStream.bufferedReader().readText()
            log(out.ifBlank { "(no output)\n" }.let { if (it.endsWith("\n")) it else it + "\n" })
            finished && out.contains("STONE_OK")
        } catch (e: Exception) {
            log("\u001B[31mtest failed: ${e.message}\u001B[0m\n")
            false
        }
    }

    // ------------------------------------------------------------ setup

    fun setup(log: (String) -> Unit): Int {
        val arch = StonePackageManager.currentArch()
        val prootPkg = PROOT_PKGS[arch]
        val alpineArch = ALPINE_ARCH[arch]
        if (prootPkg == null || alpineArch == null) {
            log("\u001B[31mE: unsupported CPU architecture: $arch\u001B[0m\n")
            return 100
        }
        base.mkdirs(); cacheDir.mkdirs()
        val tmp = File(cacheDir, "linux-setup.download")
        try {
            marker.delete()

            log("\u001B[1m[1/4]\u001B[0m Downloading PRoot ($arch)\n")
            download("$PROOT_RAW/${prootPkg.file}", tmp, log)
            val blob = gitBlobSha1(tmp)
            if (!blob.equals(prootPkg.blobSha1, ignoreCase = true)) {
                log("\u001B[31mE: PRoot package failed integrity check (git blob sha1 $blob)\u001B[0m\n")
                return 100
            }
            log("\u001B[2mIntegrity verified (pinned commit)\u001B[0m\n")
            prootDir.deleteRecursively(); prootDir.mkdirs()
            TarGz.extract(tmp, prootDir, 0, allowAbsoluteSymlinks = false)
            val proot = prootBin() ?: run {
                log("\u001B[31mE: bin/proot not found in the PRoot package\u001B[0m\n"); return 100
            }
            runCatching { Os.chmod(proot.path, 0b111_101_101) }
            prootLoader()?.let { runCatching { Os.chmod(it.path, 0b111_101_101) } }

            log("\u001B[1m[2/4]\u001B[0m Finding the latest Alpine Linux\n")
            val yaml = httpText("$ALPINE_BASE/$alpineArch/latest-releases.yaml")
            val (file, sha256) = parseMinirootfs(yaml) ?: run {
                log("\u001B[31mE: could not find alpine-minirootfs in the release list\u001B[0m\n"); return 100
            }
            log("Get: $file\n")
            download("$ALPINE_BASE/$alpineArch/$file", tmp, log)
            val got = sha256Hex(tmp)
            if (!got.equals(sha256, ignoreCase = true)) {
                log("\u001B[31mE: Alpine rootfs checksum mismatch (expected $sha256, got $got)\u001B[0m\n")
                return 100
            }
            log("\u001B[2mSHA256 verified\u001B[0m\n")

            log("\u001B[1m[3/4]\u001B[0m Unpacking root filesystem (this takes a moment)\n")
            rootfs.deleteRecursively(); rootfs.mkdirs()
            TarGz.extract(tmp, rootfs, 0, allowAbsoluteSymlinks = true)
            configureRootfs()

            log("\u001B[1m[4/4]\u001B[0m Testing Linux\n")
            if (!test(log)) {
                log(
                    "\u001B[31mE: Linux did not start.\u001B[0m Some devices need compatibility mode:\n" +
                        "  linux compat on\n  linux test\n"
                )
                return 1
            }
            marker.writeText(file)
            log("\u001B[32mDone.\u001B[0m Type \u001B[1mlinux\u001B[0m to enter Alpine, then e.g. \u001B[1mapk add python3 git\u001B[0m\n")
            return 0
        } catch (e: Exception) {
            log("\u001B[31mE: setup failed: ${e.message}\u001B[0m\n")
            return 100
        } finally {
            tmp.delete()
        }
    }

    private fun configureRootfs() {
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/resolv.conf").apply { delete() }.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        // apk repositories: https, with community enabled (python3 is in main; nodejs etc. in community)
        val repoFile = File(rootfs, "etc/apk/repositories")
        repoFile.parentFile?.mkdirs()
        val lines = (if (repoFile.exists()) repoFile.readLines() else emptyList())
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.replace("http://", "https://") }
            .toMutableList()
        if (lines.isEmpty()) {
            lines += "https://dl-cdn.alpinelinux.org/alpine/latest-stable/main"
        }
        if (lines.none { it.endsWith("/community") }) {
            lines.firstOrNull { it.endsWith("/main") }?.let { lines += it.removeSuffix("/main") + "/community" }
        }
        repoFile.delete()
        repoFile.writeText(lines.joinToString("\n") + "\n")

        for (d in listOf("root", "work", "tmp", "usr/local/bin", "proc", "sys", "dev")) File(rootfs, d).mkdirs()
        runCatching { Os.chmod(File(rootfs, "root").path, 0b111_000_000) }
        runCatching { Os.chmod(File(rootfs, "tmp").path, 0x3FF) } // 1777

        // apt-style front end so `apt install x` also works inside Alpine
        val script = APT_WRAPPER.replace('\u00A7', '$')
        for (name in listOf("apt", "apt-get", "pkg")) {
            val f = File(rootfs, "usr/local/bin/$name")
            f.writeText(script)
            runCatching { Os.chmod(f.path, 0b111_101_101) }
        }
    }

    // ------------------------------------------------------------ helpers

    private fun parseMinirootfs(yaml: String): Pair<String, String>? {
        val blocks = ArrayList<MutableMap<String, String>>()
        var cur = mutableMapOf<String, String>()
        for (raw in yaml.lines()) {
            val line = raw.trim()
            if (line == "-") { cur = mutableMapOf(); blocks += cur; continue }
            val i = line.indexOf(':')
            if (i > 0) cur[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('"')
        }
        val b = blocks.firstOrNull { it["flavor"] == "alpine-minirootfs" } ?: return null
        val file = b["file"] ?: return null
        val sha = b["sha256"] ?: return null
        if (!Regex("^[A-Za-z0-9._-]+$").matches(file)) return null
        return file to sha
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "HTTPS required" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "STONE-linux/1.0")
        if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode} for $url")
        return c
    }

    private fun httpText(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun download(url: String, dest: File, log: (String) -> Unit) {
        val c = open(url)
        val total = c.contentLengthLong
        var read = 0L
        var nextMark = 25
        c.inputStream.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) {
                        val pct = (read * 100 / total).toInt()
                        if (pct >= nextMark) { log("  $pct% (${read / 1024} KB)\n"); nextMark += 25 }
                    }
                }
            }
        }
        log("Fetched ${read / 1024} KB\n")
    }

    private fun sha256Hex(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Git blob id: sha1("blob <size>\0" + bytes). Lets us verify files from a pinned git commit. */
    private fun gitBlobSha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${f.length()}\u0000".toByteArray())
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private data class ProotPkg(val file: String, val blobSha1: String)

    companion object {
        private const val PROOT_COMMIT = "01f83b8841358450c78333d1b33ab30d4943bec4"
        private const val PROOT_RAW =
            "https://raw.githubusercontent.com/green-green-avk/build-proot-android/$PROOT_COMMIT/packages"
        private const val ALPINE_BASE = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases"

        // git blob ids of the prebuilt packages at PROOT_COMMIT
        private val PROOT_PKGS = mapOf(
            "aarch64" to ProotPkg("proot-android-aarch64.tar.gz", "45158586e587e1adf9a75db687dfdb08baec83c5"),
            "arm" to ProotPkg("proot-android-armv7a.tar.gz", "d2fbd7fb494d937f35242c822573ac3db1318850"),
            "x86_64" to ProotPkg("proot-android-x86_64.tar.gz", "9b9ce4dc7ecdb09a24b8a2a1a2640ccd0bb1c1cb"),
            "i686" to ProotPkg("proot-android-i686.tar.gz", "d4e8d34ead404a422e87114f16cb87cd701c59b9")
        )
        private val ALPINE_ARCH = mapOf("aarch64" to "aarch64", "arm" to "armv7", "x86_64" to "x86_64", "i686" to "x86")

        // '\u00A7' is replaced by '$' (keeps Kotlin from interpolating the shell variables)
        private const val APT_WRAPPER = """#!/bin/sh
# STONE: apt-style front end for Alpine's apk
cmd="§1"
[ §# -gt 0 ] && shift
args=""
for a in "§@"; do
  case "§a" in -y|--yes|-q|-qq|--no-install-recommends) ;; *) args="§args §a" ;; esac
done
case "§cmd" in
  update) exec apk update ;;
  install|add) exec apk add §args ;;
  remove|purge|uninstall) exec apk del §args ;;
  upgrade|full-upgrade|dist-upgrade) apk update && exec apk upgrade ;;
  search) exec apk search -v §args ;;
  list) exec apk list §args ;;
  show|info) exec apk info -a §args ;;
  autoremove) echo "nothing to do"; exit 0 ;;
  ""|help|-h|--help) echo "apt (STONE/Alpine): update | install | remove | upgrade | search | list | show  (backed by apk)"; exit 0 ;;
  *) echo "apt: unsupported command '§cmd' (this is Alpine Linux; see: apk help)"; exit 1 ;;
esac
"""
    }
}
