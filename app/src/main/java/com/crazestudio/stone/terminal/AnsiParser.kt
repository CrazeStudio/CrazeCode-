package com.crazestudio.stone.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.crazestudio.stone.ui.theme.AnsiPalette

private data class AnsiStyle(
    val fgIndex: Int? = null,
    val fgBright: Boolean = false,
    val fgCustom: Color? = null,
    val bgIndex: Int? = null,
    val bgBright: Boolean = false,
    val bgCustom: Color? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
)

private data class StyledSegment(
    val text: String,
    val style: AnsiStyle
)

/**
 * Stateful VT100/ANSI stream buffer supporting SGR colors (16/256/24-bit),
 * carriage-return overwrite, backspace, clear screen, and scrollback capping.
 */
class AnsiTerminalBuffer(private val maxLines: Int = 500) {
    private val lines = mutableListOf<MutableList<StyledSegment>>(mutableListOf())
    private var cursorLine = 0
    private var cursorCol = 0
    private var currentStyle = AnsiStyle()
    private val escapeBuffer = StringBuilder()
    private var inEscape = false
    private var escapeMode = '\u0000' // '[' for CSI, ']' for OSC

    @Synchronized
    fun append(chunk: String) {
        for (ch in chunk) {
            if (inEscape) {
                handleEscapeChar(ch)
                continue
            }
            when (ch) {
                '\u001B' -> {
                    inEscape = true
                    escapeMode = '\u0000'
                    escapeBuffer.clear()
                }
                '\r' -> cursorCol = 0
                '\n' -> {
                    cursorLine++
                    cursorCol = 0
                    while (lines.size <= cursorLine) {
                        lines.add(mutableListOf())
                    }
                    trimScrollback()
                }
                '\b' -> {
                    if (cursorCol > 0) cursorCol--
                }
                '\t' -> {
                    val spaces = 4 - (cursorCol % 4)
                    repeat(spaces) { writeChar(' ') }
                }
                else -> {
                    if (ch.code >= 32) {
                        writeChar(ch)
                    }
                }
            }
        }
    }

    @Synchronized
    fun clear() {
        lines.clear()
        lines.add(mutableListOf())
        cursorLine = 0
        cursorCol = 0
        currentStyle = AnsiStyle()
    }

    @Synchronized
    fun toAnnotatedString(palette: AnsiPalette): AnnotatedString {
        return buildAnnotatedString {
            lines.forEachIndexed { idx, segments ->
                for (seg in segments) {
                    val span = resolveSpanStyle(seg.style, palette)
                    if (span != null) {
                        pushStyle(span)
                        append(seg.text)
                        pop()
                    } else {
                        append(seg.text)
                    }
                }
                if (idx < lines.lastIndex) {
                    append('\n')
                }
            }
        }
    }

    @Synchronized
    fun plainText(): String = lines.joinToString("\n") { line ->
        line.joinToString("") { it.text }
    }

    private fun handleEscapeChar(ch: Char) {
        if (escapeMode == '\u0000') {
            when (ch) {
                '[' -> escapeMode = '['
                ']' -> escapeMode = ']'
                else -> inEscape = false
            }
            return
        }
        if (escapeMode == ']') {
            // OSC sequence ends at BEL (\u0007) or ST (\u001B\\)
            if (ch == '\u0007' || (ch == '\\' && escapeBuffer.endsWith('\u001B'))) {
                inEscape = false
                escapeMode = '\u0000'
            } else {
                escapeBuffer.append(ch)
                if (escapeBuffer.length > 256) {
                    inEscape = false
                    escapeMode = '\u0000'
                }
            }
            return
        }
        // CSI sequence ends at 0x40..0x7E
        if (ch.code in 0x40..0x7E) {
            processCsi(escapeBuffer.toString(), ch)
            inEscape = false
            escapeMode = '\u0000'
        } else {
            escapeBuffer.append(ch)
            if (escapeBuffer.length > 128) {
                inEscape = false
                escapeMode = '\u0000'
            }
        }
    }

    private fun processCsi(params: String, command: Char) {
        when (command) {
            'm' -> applySgr(params)
            'J' -> {
                val mode = params.toIntOrNull() ?: 0
                if (mode == 2 || mode == 3) {
                    clear()
                }
            }
            'K' -> {
                val line = lines.getOrNull(cursorLine) ?: return
                val full = line.joinToString("") { it.text }
                if (cursorCol < full.length) {
                    replaceLineText(cursorLine, full.take(cursorCol))
                }
            }
            'H', 'f' -> {
                if (params.isBlank() || params == "1;1") {
                    cursorCol = 0
                }
            }
            'G' -> {
                val col = (params.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                cursorCol = col
            }
        }
    }

    private fun applySgr(params: String) {
        if (params.isBlank()) {
            currentStyle = AnsiStyle()
            return
        }
        val codes = params.split(';').mapNotNull { it.toIntOrNull() }
        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> currentStyle = AnsiStyle()
                1 -> currentStyle = currentStyle.copy(bold = true)
                2 -> currentStyle = currentStyle.copy(dim = true)
                3 -> currentStyle = currentStyle.copy(italic = true)
                4 -> currentStyle = currentStyle.copy(underline = true)
                7 -> currentStyle = currentStyle.copy(inverse = true)
                22 -> currentStyle = currentStyle.copy(bold = false, dim = false)
                23 -> currentStyle = currentStyle.copy(italic = false)
                24 -> currentStyle = currentStyle.copy(underline = false)
                27 -> currentStyle = currentStyle.copy(inverse = false)
                in 30..37 -> currentStyle = currentStyle.copy(fgIndex = code - 30, fgBright = false, fgCustom = null)
                39 -> currentStyle = currentStyle.copy(fgIndex = null, fgBright = false, fgCustom = null)
                in 40..47 -> currentStyle = currentStyle.copy(bgIndex = code - 40, bgBright = false, bgCustom = null)
                49 -> currentStyle = currentStyle.copy(bgIndex = null, bgBright = false, bgCustom = null)
                in 90..97 -> currentStyle = currentStyle.copy(fgIndex = code - 90, fgBright = true, fgCustom = null)
                in 100..107 -> currentStyle = currentStyle.copy(bgIndex = code - 100, bgBright = true, bgCustom = null)
                38, 48 -> {
                    val isFg = code == 38
                    val mode = codes.getOrNull(i + 1)
                    if (mode == 5 && i + 2 < codes.size) {
                        val color = xterm256Color(codes[i + 2])
                        currentStyle = if (isFg) {
                            currentStyle.copy(fgIndex = null, fgCustom = color)
                        } else {
                            currentStyle.copy(bgIndex = null, bgCustom = color)
                        }
                        i += 2
                    } else if (mode == 2 && i + 4 < codes.size) {
                        val r = codes[i + 2].coerceIn(0, 255)
                        val g = codes[i + 3].coerceIn(0, 255)
                        val b = codes[i + 4].coerceIn(0, 255)
                        val color = Color(r, g, b)
                        currentStyle = if (isFg) {
                            currentStyle.copy(fgIndex = null, fgCustom = color)
                        } else {
                            currentStyle.copy(bgIndex = null, bgCustom = color)
                        }
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun writeChar(ch: Char) {
        while (lines.size <= cursorLine) lines.add(mutableListOf())
        val line = lines[cursorLine]
        val lineLen = line.sumOf { it.text.length }
        if (cursorCol == lineLen) {
            val last = line.lastOrNull()
            if (last != null && last.style == currentStyle) {
                line[line.lastIndex] = last.copy(text = last.text + ch)
            } else {
                line.add(StyledSegment(ch.toString(), currentStyle))
            }
        } else {
            // Overwrite character at cursorCol
            val chars = MutableList(maxOf(lineLen, cursorCol + 1)) { ' ' to AnsiStyle() }
            var pos = 0
            for (seg in line) {
                for (c in seg.text) {
                    chars[pos++] = c to seg.style
                }
            }
            chars[cursorCol] = ch to currentStyle
            line.clear()
            for ((c, st) in chars) {
                val last = line.lastOrNull()
                if (last != null && last.style == st) {
                    line[line.lastIndex] = last.copy(text = last.text + c)
                } else {
                    line.add(StyledSegment(c.toString(), st))
                }
            }
        }
        cursorCol++
    }

    private fun replaceLineText(lineIndex: Int, text: String) {
        val line = lines[lineIndex]
        line.clear()
        if (text.isNotEmpty()) {
            line.add(StyledSegment(text, AnsiStyle()))
        }
    }

    private fun trimScrollback() {
        while (lines.size > maxLines) {
            lines.removeAt(0)
            if (cursorLine > 0) cursorLine--
        }
    }

    private fun resolveSpanStyle(style: AnsiStyle, palette: AnsiPalette): SpanStyle? {
        if (style == AnsiStyle()) return null
        var fg: Color? = style.fgCustom ?: style.fgIndex?.let { idx ->
            if (style.fgBright) palette.bright.getOrNull(idx) else palette.standard.getOrNull(idx)
        }
        var bg: Color? = style.bgCustom ?: style.bgIndex?.let { idx ->
            if (style.bgBright) palette.bright.getOrNull(idx) else palette.standard.getOrNull(idx)
        }
        if (style.inverse) {
            val origFg = fg ?: palette.defaultFg
            val origBg = bg ?: palette.defaultBg
            fg = origBg
            bg = origFg
        }
        if (style.dim && fg != null) {
            fg = fg.copy(alpha = 0.65f)
        } else if (style.dim) {
            fg = palette.defaultFg.copy(alpha = 0.65f)
        }
        return SpanStyle(
            color = fg ?: Color.Unspecified,
            background = bg ?: Color.Unspecified,
            fontWeight = if (style.bold) FontWeight.Bold else null,
            fontStyle = if (style.italic) FontStyle.Italic else null,
            textDecoration = if (style.underline) TextDecoration.Underline else null
        )
    }

    private fun xterm256Color(index: Int): Color {
        val idx = index.coerceIn(0, 255)
        if (idx < 16) {
            val base = listOf(
                0x000000, 0x800000, 0x008000, 0x808000,
                0x000080, 0x800080, 0x008080, 0xC0C0C0,
                0x808080, 0xFF0000, 0x00FF00, 0xFFFF00,
                0x0000FF, 0xFF00FF, 0x00FFFF, 0xFFFFFF
            )
            return Color(base[idx] or (0xFF shl 24))
        }
        if (idx < 232) {
            val c = idx - 16
            val r = (c / 36) * 51
            val g = ((c / 6) % 6) * 51
            val b = (c % 6) * 51
            return Color(r, g, b)
        }
        val gray = 8 + (idx - 232) * 10
        return Color(gray, gray, gray)
    }
}
