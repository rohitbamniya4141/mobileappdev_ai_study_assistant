package com.studyassistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Block types recognized by the markdown parser.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class BulletItem(val indent: Int, val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String, val indent: Int) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    object Divider : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

/**
 * Lightweight, high-performance Markdown parser for Jetpack Compose.
 * Handles headings, code blocks, bullet/numbered lists, quotes, dividers,
 * tables, bold, italic, bold-italic, inline code, links, and strikethroughs.
 */
object MarkdownParser {

    /**
     * Splits raw markdown text into a sequence of structured blocks.
     */
    fun parseBlocks(markdown: String): List<MarkdownBlock> {
        val lines = markdown.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0
        val n = lines.size

        while (i < n) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Blank line -> skip
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 2. Fenced code block: ```[language] ... ```
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < n) {
                    if (lines[i].trim().startsWith("```")) {
                        i++
                        break
                    }
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            // 3. Thematic break / Divider: ---, ***, ___
            if (trimmed.length >= 3 && (trimmed.all { it == '-' } || trimmed.all { it == '*' } || trimmed.all { it == '_' })) {
                blocks.add(MarkdownBlock.Divider)
                i++
                continue
            }

            // 4. Headings: # ... ######
            val headingMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2]
                blocks.add(MarkdownBlock.Heading(level, text))
                i++
                continue
            }

            // 5. Blockquote: > ...
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < n && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                blocks.add(MarkdownBlock.BlockQuote(quoteLines.joinToString("\n")))
                continue
            }

            // 6. Table: | ... |
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2) {
                val tableLines = mutableListOf<String>()
                while (i < n && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.size >= 2) {
                    val headers = tableLines[0].trim('|').split('|').map { it.trim() }
                    val rows = mutableListOf<List<String>>()
                    // Index 1 is typically the delimiter row (|---|---|)
                    val dataStart = if (tableLines.size > 1 && tableLines[1].contains('-')) 2 else 1
                    for (r in dataStart until tableLines.size) {
                        rows.add(tableLines[r].trim('|').split('|').map { it.trim() })
                    }
                    blocks.add(MarkdownBlock.Table(headers, rows))
                    continue
                } else {
                    for (tl in tableLines) {
                        blocks.add(MarkdownBlock.Paragraph(tl))
                    }
                    continue
                }
            }

            // 7. Bullet list: * , - , +
            val bulletMatch = Regex("^(\\s*)([*\\-+])\\s+(.*)$").find(line)
            if (bulletMatch != null) {
                val indent = bulletMatch.groupValues[1].length / 2
                val text = bulletMatch.groupValues[3]
                blocks.add(MarkdownBlock.BulletItem(indent, text))
                i++
                continue
            }

            // 8. Numbered list: 1. or 1)
            val numberedMatch = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$").find(line)
            if (numberedMatch != null) {
                val indent = numberedMatch.groupValues[1].length / 2
                val number = numberedMatch.groupValues[2]
                val text = numberedMatch.groupValues[3]
                blocks.add(MarkdownBlock.NumberedItem(number, text, indent))
                i++
                continue
            }

            // 9. Standard Paragraph (accumulates consecutive lines until next block)
            val paraLines = mutableListOf(trimmed)
            i++
            while (i < n) {
                val nextTrimmed = lines[i].trim()
                if (nextTrimmed.isEmpty()) break
                if (nextTrimmed.startsWith("```") || nextTrimmed.startsWith("#") ||
                    nextTrimmed.startsWith(">") || nextTrimmed.startsWith("|")
                ) break
                if (Regex("^(\\s*)([*\\-+]|\\d+[.)])\\s+").containsMatchIn(lines[i])) break
                if (nextTrimmed.length >= 3 && (nextTrimmed.all { it == '-' } || nextTrimmed.all { it == '*' } || nextTrimmed.all { it == '_' })) break
                paraLines.add(nextTrimmed)
                i++
            }
            blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
        }

        return blocks
    }

    /**
     * Parses inline markdown markers (bold, italic, code, etc.) into a Compose [AnnotatedString].
     */
    fun parseInline(
        text: String,
        isUser: Boolean,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color
    ): AnnotatedString {
        return buildAnnotatedString {
            appendInlineSpans(
                text = text,
                isUser = isUser,
                primaryColor = primaryColor,
                codeBgColor = codeBgColor,
                codeTextColor = codeTextColor
            )
        }
    }

    private fun AnnotatedString.Builder.appendInlineSpans(
        text: String,
        isUser: Boolean,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color
    ) {
        var i = 0
        val n = text.length
        val buf = StringBuilder()

        fun flushBuf() {
            if (buf.isNotEmpty()) {
                append(buf.toString())
                buf.clear()
            }
        }

        while (i < n) {
            // 1. Inline code: `...`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1 && !text.substring(i + 1, end).contains('\n')) {
                    flushBuf()
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = codeTextColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                    continue
                }
            }

            // 2. Bold Italic: ***...***
            if (text.startsWith("***", i)) {
                val end = text.indexOf("***", i + 3)
                if (end != -1 && !text.substring(i + 3, end).contains('\n')) {
                    flushBuf()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        appendInlineSpans(text.substring(i + 3, end), isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    i = end + 3
                    continue
                }
            }

            // 3. Bold: **...**
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1 && !text.substring(i + 2, end).contains('\n')) {
                    flushBuf()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineSpans(text.substring(i + 2, end), isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    i = end + 2
                    continue
                }
            }

            // 4. Italic: *...*
            if (text[i] == '*' && (i == 0 || text[i - 1] != '*') && (i + 1 < n && text[i + 1] != '*' && text[i + 1] != ' ')) {
                var end = -1
                var j = i + 1
                while (j < n && text[j] != '\n') {
                    if (text[j] == '*' && (j + 1 >= n || text[j + 1] != '*') && text[j - 1] != ' ') {
                        end = j
                        break
                    }
                    j++
                }
                if (end != -1) {
                    flushBuf()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineSpans(text.substring(i + 1, end), isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    i = end + 1
                    continue
                }
            }

            // 5. Bold Italic: ___...___
            if (text.startsWith("___", i)) {
                val end = text.indexOf("___", i + 3)
                if (end != -1 && !text.substring(i + 3, end).contains('\n')) {
                    flushBuf()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        appendInlineSpans(text.substring(i + 3, end), isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    i = end + 3
                    continue
                }
            }

            // 6. Bold: __...__
            if (text.startsWith("__", i)) {
                val end = text.indexOf("__", i + 2)
                if (end != -1 && !text.substring(i + 2, end).contains('\n')) {
                    flushBuf()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineSpans(text.substring(i + 2, end), isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    i = end + 2
                    continue
                }
            }

            // 7. Italic: _..._ (only when not inside a word like user_id)
            if (text[i] == '_' && (i == 0 || !text[i - 1].isLetterOrDigit()) && (i + 1 < n && text[i + 1] != '_' && text[i + 1] != ' ')) {
                var end = -1
                var j = i + 1
                while (j < n && text[j] != '\n') {
                    if (text[j] == '_' && (j + 1 >= n || !text[j + 1].isLetterOrDigit()) && text[j - 1] != ' ') {
                        end = j
                        break
                    }
                    j++
                }
                if (end != -1) {
                    flushBuf()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineSpans(text.substring(i + 1, end), isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    i = end + 1
                    continue
                }
            }

            // 8. Strikethrough: ~~...~~
            if (text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end != -1 && !text.substring(i + 2, end).contains('\n')) {
                    flushBuf()
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInlineSpans(text.substring(i + 2, end), isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    i = end + 2
                    continue
                }
            }

            // 9. Links: [label](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1 && !text.substring(i, closeParen).contains('\n')) {
                        val label = text.substring(i + 1, closeBracket)
                        flushBuf()
                        withStyle(
                            SpanStyle(
                                color = if (isUser) Color.White else primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(label)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            buf.append(text[i])
            i++
        }

        flushBuf()
    }
}

/**
 * A production-quality Markdown text composable designed for Gemini / AI chat responses.
 * Renders full markdown formatting: Headings, Code Blocks with syntax header & copy button,
 * Bullet/Numbered lists with hanging indents, Blockquotes, Tables, Bold, Italic, and Inline Code.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false,
    color: Color = Color.Unspecified
) {
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = if (color != Color.Unspecified) color
    else if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    val codeBgColor = if (isUser) Color.White.copy(alpha = 0.22f)
    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)

    val codeTextColor = if (isUser) Color.White
    else MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    MarkdownHeading(block, isUser, textColor, primaryColor, codeBgColor, codeTextColor)
                }
                is MarkdownBlock.CodeBlock -> {
                    MarkdownCodeBlock(block)
                }
                is MarkdownBlock.BulletItem -> {
                    MarkdownBulletItem(block, isUser, textColor, primaryColor, codeBgColor, codeTextColor)
                }
                is MarkdownBlock.NumberedItem -> {
                    MarkdownNumberedItem(block, isUser, textColor, primaryColor, codeBgColor, codeTextColor)
                }
                is MarkdownBlock.BlockQuote -> {
                    MarkdownBlockQuote(block, isUser, textColor, primaryColor, codeBgColor, codeTextColor)
                }
                is MarkdownBlock.Table -> {
                    MarkdownTable(block, isUser, textColor)
                }
                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = if (isUser) Color.White.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text, isUser, primaryColor) {
                        MarkdownParser.parseInline(block.text, isUser, primaryColor, codeBgColor, codeTextColor)
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownHeading(
    block: MarkdownBlock.Heading,
    isUser: Boolean,
    textColor: Color,
    primaryColor: Color,
    codeBgColor: Color,
    codeTextColor: Color
) {
    val annotated = remember(block.text, isUser) {
        MarkdownParser.parseInline(block.text, isUser, primaryColor, codeBgColor, codeTextColor)
    }
    val (style, topPadding) = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp) to 10.dp
        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp) to 8.dp
        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp) to 6.dp
    }

    Text(
        text = annotated,
        style = style,
        color = textColor,
        modifier = Modifier.padding(top = topPadding, bottom = 2.dp)
    )
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock.CodeBlock) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val containerBg = Color(0xFF1E1E2E) // Dark slate for developer code aesthetic
    val headerBg = Color(0xFF181825)
    val codeTextColor = Color(0xFFCDD6F4)
    val mutedColor = Color(0xFFA6ADC8)
    val successColor = Color(0xFFA6E3A1)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = containerBg,
        shadowElevation = 2.dp
    ) {
        Column {
            // Top Header Bar: Language label + Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (block.language.isNotBlank()) block.language else "code").uppercase(),
                    color = mutedColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(block.code))
                            copied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        tint = if (copied) successColor else mutedColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (copied) "Copied!" else "Copy",
                        color = if (copied) successColor else mutedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF313244), thickness = 0.8.dp)

            // Horizontally Scrollable Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = block.code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = codeTextColor
                )
            }
        }
    }
}

@Composable
private fun MarkdownBulletItem(
    block: MarkdownBlock.BulletItem,
    isUser: Boolean,
    textColor: Color,
    primaryColor: Color,
    codeBgColor: Color,
    codeTextColor: Color
) {
    val annotated = remember(block.text, isUser) {
        MarkdownParser.parseInline(block.text, isUser, primaryColor, codeBgColor, codeTextColor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.indent * 16).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = if (isUser) Color.White else primaryColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MarkdownNumberedItem(
    block: MarkdownBlock.NumberedItem,
    isUser: Boolean,
    textColor: Color,
    primaryColor: Color,
    codeBgColor: Color,
    codeTextColor: Color
) {
    val annotated = remember(block.text, isUser) {
        MarkdownParser.parseInline(block.text, isUser, primaryColor, codeBgColor, codeTextColor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.indent * 16).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${block.number}.",
            color = if (isUser) Color.White else primaryColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.width(22.dp)
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MarkdownBlockQuote(
    block: MarkdownBlock.BlockQuote,
    isUser: Boolean,
    textColor: Color,
    primaryColor: Color,
    codeBgColor: Color,
    codeTextColor: Color
) {
    val annotated = remember(block.text, isUser) {
        MarkdownParser.parseInline(block.text, isUser, primaryColor, codeBgColor, codeTextColor)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
        color = if (isUser) Color.White.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else primaryColor,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = textColor,
                lineHeight = 20.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun MarkdownTable(
    block: MarkdownBlock.Table,
    isUser: Boolean,
    textColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isUser) Color.White.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Column {
                // Table Header
                Row(
                    modifier = Modifier
                        .background(
                            color = if (isUser) Color.White.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    block.headers.forEach { header ->
                        Text(
                            text = header,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            modifier = Modifier
                                .widthIn(min = 90.dp)
                                .padding(end = 12.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Table Rows
                block.rows.forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (idx % 2 == 1) {
                                    if (isUser) Color.White.copy(alpha = 0.05f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                } else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                modifier = Modifier
                                    .widthIn(min = 90.dp)
                                    .padding(end = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
