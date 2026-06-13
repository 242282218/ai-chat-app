package com.aichat.workbench.ui.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight syntax highlighter using regex-based tokenization.
 * Supports keywords, strings, comments, numbers, and annotations for common languages.
 */

@Composable
fun rememberHighlightedCode(code: String, language: String?): AnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    return remember(code, language, colorScheme) {
        if (language.isNullOrBlank()) {
            return@remember AnnotatedString(code)
        }
        val highlighter = languageToHighlighter(language) ?: return@remember AnnotatedString(code)
        highlighter.highlight(
            code = code,
            keywordColor = colorScheme.primary,
            stringColor = colorScheme.tertiary,
            commentColor = colorScheme.onSurfaceVariant,
            numberColor = colorScheme.secondary,
            annotationColor = colorScheme.error,
        )
    }
}

private fun languageToHighlighter(language: String): SyntaxHighlighter? =
    when (language.lowercase()) {
        "kotlin", "kt" -> KotlinHighlighter
        "java" -> JavaHighlighter
        "python", "py" -> PythonHighlighter
        "javascript", "js", "typescript", "ts" -> JavaScriptHighlighter
        "go" -> GoHighlighter
        "rust", "rs" -> RustHighlighter
        "sql" -> SqlHighlighter
        "shell", "bash", "sh", "zsh" -> ShellHighlighter
        "json" -> JsonHighlighter
        "xml", "html" -> XmlHighlighter
        "css" -> CssHighlighter
        else -> null
    }

private data class Token(
    val start: Int,
    val end: Int,
    val type: TokenType,
)

private enum class TokenType { Keyword, String, Comment, Number, Annotation }

private abstract class SyntaxHighlighter {
    abstract val keywords: Set<String>
    abstract val singleLineComment: String?
    abstract val multiLineCommentStart: String?
    abstract val multiLineCommentEnd: String?
    open val stringDelimiters: List<String> = listOf("\"")
    open val annotationPrefix: String? = null

    fun highlight(
        code: String,
        keywordColor: Color,
        stringColor: Color,
        commentColor: Color,
        numberColor: Color,
        annotationColor: Color,
    ): AnnotatedString {
        val tokens = tokenize(code)
        return buildAnnotatedString {
            var pos = 0
            for (token in tokens) {
                if (token.start > pos) {
                    append(code.substring(pos, token.start))
                }
                val color = when (token.type) {
                    TokenType.Keyword -> keywordColor
                    TokenType.String -> stringColor
                    TokenType.Comment -> commentColor
                    TokenType.Number -> numberColor
                    TokenType.Annotation -> annotationColor
                }
                withStyle(SpanStyle(color = color)) {
                    append(code.substring(token.start, token.end))
                }
                pos = token.end
            }
            if (pos < code.length) {
                append(code.substring(pos))
            }
        }
    }

    private fun tokenize(code: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < code.length) {
            // Multi-line comment
            val mls = multiLineCommentStart
            val mle = multiLineCommentEnd
            if (mls != null && mle != null && code.startsWith(mls, i)) {
                val end = code.indexOf(mle, i + mls.length)
                val tokenEnd = if (end >= 0) end + mle.length else code.length
                tokens.add(Token(i, tokenEnd, TokenType.Comment))
                i = tokenEnd
                continue
            }
            // Single-line comment
            val slc = singleLineComment
            if (slc != null && code.startsWith(slc, i)) {
                val end = code.indexOf('\n', i)
                val tokenEnd = if (end >= 0) end else code.length
                tokens.add(Token(i, tokenEnd, TokenType.Comment))
                i = tokenEnd
                continue
            }
            // Strings
            var matched = false
            for (delim in stringDelimiters) {
                if (code.startsWith(delim, i)) {
                    var j = i + delim.length
                    while (j < code.length) {
                        if (code[j] == '\\') { j += 2; continue }
                        if (code.startsWith(delim, j)) { j += delim.length; break }
                        if (code[j] == '\n' && delim != "\"\"\"" && delim != "'''") break
                        j++
                    }
                    tokens.add(Token(i, j, TokenType.String))
                    i = j
                    matched = true
                    break
                }
            }
            if (matched) continue
            // Annotations
            val ann = annotationPrefix
            if (ann != null && code.startsWith(ann, i)) {
                var j = i + ann.length
                while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                tokens.add(Token(i, j, TokenType.Annotation))
                i = j
                continue
            }
            // Numbers
            if (code[i].isDigit() || (code[i] == '.' && i + 1 < code.length && code[i + 1].isDigit())) {
                var j = i
                if (code[j] == '0' && j + 1 < code.length && (code[j + 1] == 'x' || code[j + 1] == 'X')) {
                    j += 2
                    while (j < code.length && code[j].isHexDigit()) j++
                } else {
                    while (j < code.length && (code[j].isDigit() || code[j] == '.')) j++
                    if (j < code.length && (code[j] == 'e' || code[j] == 'E')) {
                        j++
                        if (j < code.length && (code[j] == '+' || code[j] == '-')) j++
                        while (j < code.length && code[j].isDigit()) j++
                    }
                }
                if (j < code.length && (code[j] == 'L' || code[j] == 'f' || code[j] == 'F' || code[j] == 'u' || code[j] == 'U')) j++
                if (j > i) {
                    tokens.add(Token(i, j, TokenType.Number))
                    i = j
                    continue
                }
            }
            // Keywords
            if (code[i].isLetter() || code[i] == '_') {
                var j = i
                while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                if (word in keywords) {
                    tokens.add(Token(i, j, TokenType.Keyword))
                }
                i = j
                continue
            }
            i++
        }
        return tokens
    }
}

private fun Char.isHexDigit(): Boolean = isDigit() || this in 'a'..'f' || this in 'A'..'F'

private object KotlinHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "in", "interface", "is", "null", "object", "package", "return",
        "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
        "var", "when", "while", "by", "catch", "constructor", "delegate",
        "dynamic", "field", "file", "finally", "get", "import", "init",
        "inline", "inner", "internal", "lateinit", "noinline", "open",
        "operator", "out", "override", "private", "protected", "public",
        "reified", "sealed", "suspend", "tailrec", "vararg", "it",
        "abstract", "actual", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline",
        "internal", "lazy", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend",
        "tailrec", "value", "vararg", "where",
    )
    override val singleLineComment = "//"
    override val multiLineCommentStart = "/*"
    override val multiLineCommentEnd = "*/"
    override val stringDelimiters = listOf("\"\"\"", "\"")
    override val annotationPrefix = "@"
}

private object JavaHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "continue", "default", "do", "double", "else", "enum", "extends",
        "final", "finally", "float", "for", "if", "implements", "import", "instanceof",
        "int", "interface", "long", "native", "new", "null", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super",
        "switch", "synchronized", "this", "throw", "throws", "transient", "true",
        "false", "try", "void", "volatile", "while",
    )
    override val singleLineComment = "//"
    override val multiLineCommentStart = "/*"
    override val multiLineCommentEnd = "*/"
    override val stringDelimiters = listOf("\"")
    override val annotationPrefix = "@"
}

private object PythonHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from",
        "global", "if", "import", "in", "is", "lambda", "nonlocal", "not",
        "or", "pass", "raise", "return", "try", "while", "with", "yield",
        "True", "False", "None",
    )
    override val singleLineComment = "#"
    override val multiLineCommentStart = null
    override val multiLineCommentEnd = null
    override val stringDelimiters = listOf("\"\"\"", "'''", "\"", "'")
}

private object JavaScriptHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue",
        "debugger", "default", "delete", "do", "else", "enum", "export", "extends",
        "finally", "for", "from", "function", "if", "import", "in", "instanceof",
        "let", "new", "of", "return", "static", "super", "switch", "this",
        "throw", "try", "typeof", "undefined", "var", "void", "while", "with",
        "yield", "true", "false", "null", "NaN", "Infinity",
    )
    override val singleLineComment = "//"
    override val multiLineCommentStart = "/*"
    override val multiLineCommentEnd = "*/"
    override val stringDelimiters = listOf("`", "\"", "'")
}

private object GoHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "do",
        "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
        "interface", "map", "package", "range", "return", "select", "struct",
        "switch", "type", "var", "true", "false", "nil", "iota",
    )
    override val singleLineComment = "//"
    override val multiLineCommentStart = "/*"
    override val multiLineCommentEnd = "*/"
    override val stringDelimiters = listOf("`", "\"")
}

private object RustHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "as", "async", "await", "break", "const", "continue", "crate", "dyn",
        "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
        "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
        "self", "Self", "static", "struct", "super", "trait", "true", "type",
        "unsafe", "use", "where", "while", "abstract", "become", "box", "do",
        "final", "macro", "override", "priv", "try", "typeof", "unsized",
        "virtual", "yield",
    )
    override val singleLineComment = "//"
    override val multiLineCommentStart = "/*"
    override val multiLineCommentEnd = "*/"
    override val stringDelimiters = listOf("\"")
    override val annotationPrefix = "#"
}

private object SqlHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "select", "from", "where", "and", "or", "not", "in", "between", "like",
        "is", "null", "as", "on", "join", "inner", "left", "right", "outer",
        "cross", "group", "by", "order", "having", "limit", "offset", "insert",
        "into", "values", "update", "set", "delete", "create", "table", "alter",
        "drop", "index", "view", "trigger", "procedure", "function", "if",
        "else", "then", "end", "case", "when", "exists", "any", "all", "union",
        "except", "intersect", "distinct", "top", "with", "recursive", "primary",
        "key", "foreign", "references", "constraint", "unique", "check", "default",
        "null", "not", "auto_increment", "integer", "text", "varchar", "boolean",
        "true", "false", "asc", "desc", "count", "sum", "avg", "min", "max",
        "coalesce", "cast", "convert", "using",
    )
    override val singleLineComment = "--"
    override val multiLineCommentStart = "/*"
    override val multiLineCommentEnd = "*/"
    override val stringDelimiters = listOf("'")
}

private object ShellHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
        "until", "do", "done", "in", "function", "select", "time", "coproc",
        "export", "readonly", "declare", "typeset", "local", "unset", "shift",
        "source", "return", "exit", "exec", "eval", "trap", "wait", "kill",
        "cd", "pwd", "echo", "printf", "read", "test", "true", "false",
    )
    override val singleLineComment = "#"
    override val multiLineCommentStart = null
    override val multiLineCommentEnd = null
    override val stringDelimiters = listOf("\"", "'")
}

private object JsonHighlighter : SyntaxHighlighter() {
    override val keywords = setOf("true", "false", "null")
    override val singleLineComment = null
    override val multiLineCommentStart = null
    override val multiLineCommentEnd = null
    override val stringDelimiters = listOf("\"")
}

private object XmlHighlighter : SyntaxHighlighter() {
    override val keywords = emptySet<String>()
    override val singleLineComment = null
    override val multiLineCommentStart = "<!--"
    override val multiLineCommentEnd = "-->"
    override val stringDelimiters = listOf("\"", "'")
}

private object CssHighlighter : SyntaxHighlighter() {
    override val keywords = setOf(
        "import", "media", "keyframes", "font-face", "supports", "layer",
        "charset", "namespace", "page", "property", "container",
    )
    override val singleLineComment = null
    override val multiLineCommentStart = "/*"
    override val multiLineCommentEnd = "*/"
    override val stringDelimiters = listOf("\"", "'")
}

