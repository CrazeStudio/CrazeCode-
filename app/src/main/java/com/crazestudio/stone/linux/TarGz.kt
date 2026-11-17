package com.crazestudio.stone.linux

import android.system.Os
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Minimal .tar.gz extractor that keeps Unix modes and symlinks (needed for a Linux root filesystem).
 *
 * Safety: every entry path is normalised lexically and no existing ancestor may be a symlink, so an
 * archive cannot write outside [dest] by chaining symlinks. Symlink *targets* are stored verbatim
 * when [allowAbsoluteSymlinks] is true (rootfs use: they only mean something inside proot).
 */
object TarGz {

    fun extract(file: File, dest: File, strip: Int = 0, allowAbsoluteSymlinks: Boolean) {
        GZIPInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use {
            extractTar(it, dest, strip, allowAbsoluteSymlinks)
        }
    }

    private fun extractTar(input: InputStream, dest: File, strip: Int, allowAbsLinks: Boolean) {
        val header = ByteArray(512)
        var longName: String? = null
        var paxPath: String? = null
        while (true) {
            if (!readHeader(input, header)) break
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
            val pad = (512 - size % 512) % 512

            when (type) {
                'L' -> { longName = String(readN(input, size)).trimEnd('\u0000', '\n'); skipN(input, pad); continue }
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
            val target = safeTarget(dest, rel)

            when (type) {
                '5' -> {
                    target.mkdirs()
                    chmod(target, mode or 0x1C0) // keep owner rwx so we can keep extracting
                    skipN(input, size + pad)
                }
                '0', '\u0000', '7' -> {
                    target.parentFile?.mkdirs()
                    if (target.exists() || Files.isSymbolicLink(target.toPath())) target.delete()
                    FileOutputStream(target).use { out ->
                        var left = size
                        val buf = ByteArray(64 * 1024)
                        while (left > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) throw EOFException("truncated tar")
                            out.write(buf, 0, n); left -= n
                        }
                    }
                    skipN(input, pad)
                    chmod(target, mode or 0x180)
                }
                '2' -> {
                    if (linkName.isNotEmpty() && (allowAbsLinks || !linkName.startsWith("/"))) {
                        target.parentFile?.mkdirs()
                        if (target.exists() || Files.isSymbolicLink(target.toPath())) target.delete()
                        Os.symlink(linkName, target.path)
                    }
                    skipN(input, size + pad)
                }
                '1' -> {
                    val srcRel = stripPath(linkName, strip)
                    if (srcRel != null) {
                        val src = safeTarget(dest, srcRel)
                        target.parentFile?.mkdirs()
                        if (target.exists() || Files.isSymbolicLink(target.toPath())) target.delete()
                        if (src.isFile) {
                            src.copyTo(target, overwrite = true)
                        } else if (Files.isSymbolicLink(src.toPath())) {
                            Os.symlink(Os.readlink(src.path), target.path)
                        }
                    }
                    skipN(input, size + pad)
                }
                else -> skipN(input, size + pad) // devices, fifos: not needed (proot binds /dev)
            }
        }
    }

    private fun stripPath(name: String, strip: Int): String? {
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.size <= strip) return null
        return parts.drop(strip).joinToString("/")
    }

    /** Lexically resolves [rel] under [dest]; rejects escapes and paths that go through symlinks. */
    private fun safeTarget(dest: File, rel: String): File {
        val root = dest.toPath().toAbsolutePath().normalize()
        val p = root.resolve(rel).normalize()
        if (!p.startsWith(root)) throw SecurityException("archive entry escapes install dir: $rel")
        val parent = p.parent
        if (parent != null && parent != root) {
            var cur = root
            for (part in root.relativize(parent)) {
                cur = cur.resolve(part)
                if (Files.isSymbolicLink(cur)) throw SecurityException("archive path crosses a symlink: $rel")
            }
        }
        return p.toFile()
    }

    private fun chmod(f: File, mode: Int) {
        runCatching { Os.chmod(f.path, mode and 0xFFF) }
    }

    private fun readHeader(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) {
                if (off == 0) return false
                throw EOFException("truncated tar header")
            }
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
            if (r < 0) throw EOFException("truncated tar")
            off += r
        }
        return out
    }

    private fun skipN(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw EOFException("truncated tar")
            left -= r
        }
    }

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) { // GNU base-256
            var v = 0L
            for (i in 1 until len) v = (v shl 8) or (b[off + i].toLong() and 0xff)
            return v
        }
        val s = cString(b, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }
}
