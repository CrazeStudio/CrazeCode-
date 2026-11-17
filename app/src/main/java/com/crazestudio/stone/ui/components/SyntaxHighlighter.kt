package com.crazestudio.stone.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.crazestudio.stone.ui.theme.StoneColors
import com.crazestudio.stone.ui.theme.StoneOrange

fun buildCodeVisualTransformation(
    fileExtension: String,
    colors: StoneColors
): VisualTransformation {
    val ext = fileExtension.lowercase()
    return VisualTransformation { source ->
        val text = source.text
        if (text.length > 40_000) {
            return@VisualTransformation TransformedText(source, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder(text)
        val keywordColor = StoneOrange
        val stringColor = if (colors.isLight) Color(0xFF2E6B40) else Color(0xFF8BD8A5)
        val numberColor = if (colors.isLight) Color(0xFF1565C0) else Color(0xFF7DCFFF)
        val commentColor = colors.muted
        val tagColor = if (colors.isLight) Color(0xFF7B1FA2) else Color(0xFFBB9AF7)

        when (ext) {
            "html", "htm", "xml", "svg" -> {
                Regex("</?[A-Za-z0-9:_-]+|/?\\s*>").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1)
                }
                Regex("\\b[a-zA-Z:-]+(?==)").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = tagColor), it.range.first, it.range.last + 1)
                }
                Regex("<!--([\\s\\S]*?)-->").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
                }
            }
            "css" -> {
                Regex("[.#]?[A-Za-z_-][A-Za-z0-9_-]*(?=\\s*\\{)").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1)
                }
                Regex("\\b[a-z-]+(?=\\s*:)").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
                }
                Regex("/\\*[\\s\\S]*?\\*/").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
                }
            }
            "json" -> {
                Regex("\"([^\"\\\\]|\\\\.)*\"(?=\\s*:)").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
                }
                Regex("\\b(true|false|null)\\b").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1)
                }
            }
            "md", "markdown" -> {
                Regex("(?m)^#{1,6}\\s+.*$").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
                }
                Regex("`[^`\\n]+`").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
                }
            }
            else -> {
                val keywords = Regex(
                    "\\b(fun|class|object|interface|data|sealed|enum|val|var|const|let|return|if|else|for|while|when|switch|case|break|continue|import|export|from|package|def|print|public|private|protected|internal|static|void|int|float|double|boolean|String|new|true|false|null|undefined|try|catch|finally|throw|async|await|function|echo|fi|then|do|done|export)\\b"
                )
                keywords.findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1)
                }
                Regex("\\b\\d+(\\.\\d+)?\\b").findAll(text).forEach {
                    builder.addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
                }
            }
        }

        // Strings & line comments last so they override inner tokens cleanly
        Regex("\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*'|`([^`\\\\\\n]|\\\\.)*`").findAll(text).forEach {
            builder.addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
        }
        Regex("//[^\\n]*|(?<!&)#[^\\n]*").findAll(text).forEach {
            builder.addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
        }

        TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
