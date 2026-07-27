package mlogix.compiler

import arc.graphics.Color
import arc.graphics.Colors
import mlogix.compiler.SourceMapManager.SourceMap
import mlogix.mlogix.token.Token
import mlogix.mlogix.token.TokenType
import mlogix.problem.Problem
import mlogix.problem.Problem.LexerProblem
import mlogix.problem.ProblemCollector
import mlogix.span.Span
import mlogix.util.Ansi
import mlogix.util.Log
import java.util.function.Predicate
import kotlin.math.min

/**
 * 一个项目 构造一次
 */
class Lexer(private val collector: ProblemCollector) {

    private lateinit var sourceMap: SourceMap

    private var length: Int = 0
    private var start: Int = 0
    private var current: Int = 0

    private var lastIsNewline: Boolean = false

    private val recoverTerminators: Set<Char> = setOf(
        ' ', '\n', '\r', ':', ';', ',', '.', '(', ')', '[', ']', '{', '}'
    )

    /**
     * 一个文件 重置一次
     * 重置后才能调用其他方法
     */
    fun reset(sourceMap: SourceMap) {
        this.sourceMap = sourceMap
        this.length = sourceMap.length()

        this.lastIsNewline = false

        this.start = 0
        this.current = 0
    }

    /**
     * 扫描下一个Token
     */
    fun scanToken(): Token {
        while (true) {
            start = current

            if (this.isAtEnd) return eofToken()
            if (lastIsNewline) {
                lastIsNewline = false
                recover { c: Char? -> c != '\n' } // 跳过newline防止重复出现
                if (this.isAtEnd) return eofToken()
                start = current
            }

            when (val c = advance()) {
                '+' -> return if (match('+')) {
                    token(TokenType.PLUS_PLUS)
                } else {
                    token(TokenType.PLUS)
                }

                '-' -> return if (match('-')) {
                    token(TokenType.MINUS_MINUS)
                } else if (match('>')) {
                    token(TokenType.ARROW)
                } else {
                    token(TokenType.MINUS)
                }

                '*' -> return if (match('*')) {
                    token(TokenType.STAR_STAR)
                } else {
                    token(TokenType.STAR)
                }

                '/' -> return if (match('/')) {
                    token(TokenType.SLASH_SLASH)
                } else {
                    token(TokenType.SLASH)
                }

                '^' -> return token(TokenType.CARET)

                '%' -> return if (match('%')) {
                    token(TokenType.PERCENT_PERCENT)
                } else {
                    token(TokenType.PERCENT)
                }

                '&' -> return if (match('&')) {
                    token(TokenType.AND_AND)
                } else {
                    token(TokenType.AND)
                }

                '|' -> return if (match('|')) {
                    token(TokenType.OR_OR)
                } else {
                    token(TokenType.OR)
                }

                '~' -> return token(TokenType.TILDE)

                '!' -> return if (match('=')) {
                    if (match('=')) {
                        token(TokenType.BANG_EQ_EQ)
                    } else {
                        token(TokenType.BANG_EQ)
                    }
                } else {
                    token(TokenType.BANG)
                }

                '<' -> return if (match('<')) {
                    token(TokenType.SHL)
                } else if (match('=')) {
                    token(TokenType.LESS_EQ)
                } else {
                    token(TokenType.LESS)
                }

                '>' -> return if (match('>')) {
                    token(TokenType.SHR)
                } else if (match('=')) {
                    token(TokenType.GREATER_EQ)
                } else {
                    token(TokenType.GREATER)
                }

                '=' -> return if (match('=')) {
                    if (match('=')) {
                        token(TokenType.EQ_EQ_EQ)
                    } else {
                        token(TokenType.EQ_EQ)
                    }
                } else {
                    token(TokenType.ASSIGN)
                }

                '.' -> return token(TokenType.DOT)

                ':' -> return if (match('<')) {
                    token(TokenType.COLON_LESS)
                } else if (match('=')) {
                    token(TokenType.COLON_ASSIGN)
                } else {
                    token(TokenType.COLON)
                }

                ';' -> return token(TokenType.SEMICOLON)
                ',' -> return token(TokenType.COMMA)
                '(' -> return token(TokenType.LPAREN)
                ')' -> return token(TokenType.RPAREN)
                '[' -> return token(TokenType.LBRACKET)
                ']' -> return token(TokenType.RBRACKET)
                '{' -> return token(TokenType.LBRACE)
                '}' -> return token(TokenType.RBRACE)
                '?' -> return token(TokenType.QUESTION_MARK)

                '"' -> return string()

                '“' -> {
                    warning("字符串应该使用英文双引号`\"`")
                        .point(start, start + 1, "\"")
                    return string()
                }

                '#' -> {
                    val r = comment()
                    if (r != null) {
                        return r
                    } else {
                        continue
                    }
                }

                '\n', '\r' -> {
                    lastIsNewline = true
                    return token(TokenType.NEWLINE)
                }

                ' ', '\t' -> continue

                '\u0000' -> return token(TokenType.EOF)

                else -> if (isDigit(c)) {
                    return number()
                } else if (isIdentifierStart(c)) {
                    return identifier()
                } else {
                    error("未知的字符")
                        .point(start, start + 1, Integer.toHexString(c.code))
                    recover { ch: Char -> recoverTerminators.contains(ch) }
                    return token(TokenType.ERROR, subString(start, current))
                }
            }
        }
    }

    /* 标识符 关键字 */
    private fun identifier(): Token {
        while (!this.isAtEnd && isIdentifierPart(peek())) advance()
        val text = subString(start, current)
        if (text.startsWith("__")) {
            error("非法的标识符")
                .point(start, start + 2, "不能以`__`开头，将被替换为`_`")
            return token(TokenType.IDENTIFIER, text.substring(1))
        }
        val type = TokenType.KEYWORDS_MAP[text]
        return if (type == null) {
            token(TokenType.IDENTIFIER, text)
        } else {
            token(type)
        }
    }

    private fun string(): Token {
        while (!match('"')) {
            if (this.isAtEnd || check('\n')) {
                error("未匹配到字符串末尾`\"`")
                    .info(start, start + 1, "字符串头部")
                    .point(current, current + 1, "匹配末尾")
                return token(TokenType.STR, subString(start + 1, current))
            }
            if (match('”')) {
                warning("字符串应该使用英文双引号`\"`")
                    .point(start, start + 1, "\"")
                break
            }
            advance()
        }
        return token(TokenType.STR, subString(start + 1, current - 1))
    }

    private fun number(): Token {
        // 16进制整数 _分隔符
        if (match('x')) {
            if (peek(-2) != '0') {
                error("16进制数字应以`0x`开头")
                    .point(start, start + 2, "")
                recover { c: Char? -> !isDigit(c!!) && !isAlpha(c) }
                return token(TokenType.ERROR)
            }
            val builder = StringBuilder()
            while (!this.isAtEnd) {
                if (isHexDigit(peek())) {
                    builder.append(peek())
                } else if (check('_')) {
                    // 忽略数字分隔符
                } else if (isAlpha(peek())) {
                    // ['g' ~ 'z'] | ['G' ~ 'Z']
                    error("16进制数字不包括的字符")
                        .point(current, current + 1, Integer.toHexString(peek().code))
                    recover { c: Char? -> !isDigit(c!!) && !isAlpha(c) }
                    return token(TokenType.ERROR)
                } else {
                    break
                }
                advance()
            }
            return token(TokenType.INT, builder.toString().toLong(16).toDouble())

            // 2进制整数 _分隔符
        } else if (match('b')) {
            if (peek(-2) != '0') {
                error("2进制数字应以`0b`开头")
                    .point(start, start + 2, "")
                recover { c: Char? -> !isDigit(c!!) && !isAlpha(c) }
                return token(TokenType.ERROR)
            }
            val builder = StringBuilder()
            while (!this.isAtEnd) {
                if (isBinDigit(peek())) {
                    builder.append(peek())
                } else if (check('_')) {
                    // 忽略数字分隔符
                } else if (isAlpha(peek())) {
                    error("2进制数字不包括的字符")
                        .point(current, current + 1, Integer.toHexString(peek().code))
                    recover { c: Char? -> !isDigit(c!!) && !isAlpha(c) }
                    return token(TokenType.ERROR)
                } else {
                    break
                }
                advance()
            }
            return token(TokenType.INT, builder.toString().toLong(2).toDouble())

        // 颜色值
        } else if (match('c')) {
            if (peek(-2) != '0') {
                error("颜色值应以`0c`开头")
                    .point(start, start + 2, "")
                recover { c: Char -> !isDigit(c) && !isAlpha(c) }
                return token(TokenType.ERROR)
            }

            var notColorName = false
            var notColorValue = false
            var hasLowerCaseLetter = false
            val text = buildString {
                while (!this@Lexer.isAtEnd) {
                    val c = peek()
                    when (c) {
                        '_' -> {
                            notColorName = true
                            // 忽略数字分隔符
                        }
                        in '0'..'9' -> {
                            notColorName = true
                            append(peek())
                        }
                        in 'a'..'f' -> {
//                            notColorName = true
                            hasLowerCaseLetter = true
                            append(peek())
                        }
                        in 'A'..'F' -> {
                            append(peek())
                        }
                        in 'g'..'z' -> {
//                            notColorName = true
                            hasLowerCaseLetter = true
                            notColorValue = true
                            append(peek())
                        }
                        in 'G'..'Z' -> {
                            notColorValue = true
                            append(peek())
                        }
                        else -> {
                            break
                        }
                    }
                    advance()
                }
            }

            if (!notColorName) {
                if (!hasLowerCaseLetter) {
                    Colors.get(text)?.also {
                        return token(TokenType.COL, Color.toDoubleBits(it.r, it.g, it.b, it.a))
                    }
                } else {
                    Colors.get(text.uppercase())?.also {
                        error("颜色名称应为大写")
                            .point(start + 2, current, "")
                        return token(TokenType.COL, Color.toDoubleBits(it.r, it.g, it.b, it.a))
                    }
                }
            }
            if (!notColorValue) {
                when (text.length) {
                    6 -> { // 0cRR_GG_BB
                        val r = text.substring(0, 2).toInt(16)
                        val g = text.substring(2, 4).toInt(16)
                        val b = text.substring(4, 6).toInt(16)
                        val a = 0xFF
                        return token(TokenType.COL, Color.toDoubleBits(r, g, b, a))
                    }

                    8 -> { // 0cRR_GG_BB_AA
                        val r = text.substring(0, 2).toInt(16)
                        val g = text.substring(2, 4).toInt(16)
                        val b = text.substring(4, 6).toInt(16)
                        val a = text.substring(6, 8).toInt(16)
                        return token(TokenType.COL, Color.toDoubleBits(r, g, b, a))
                    }

                    else -> {
                        error("`颜色值`十六进制部分长度应为6或8")
                            .point(start + 2, current, "长度 = " + (text.length))
                        return token(TokenType.ERROR)
                    }
                }
            }
            error("颜色值应为内置颜色名称或16进制数字")
                .point(start, current, "")
            return token(TokenType.ERROR)

        // 普通浮点数 科学计数法 _分隔符
        } else {
            var isInt = true

            val builder = StringBuilder()
            builder.append(peek(-1))

            while (!this.isAtEnd && peek() == '_') advance() //防止normalNumber报错

            numberFragment(builder)

            if (match('.')) {
                isInt = false

                builder.append('.')
                numberFragment(builder)
            }

            if (match('e') || match('E')) {
                isInt = false

                builder.append('e')
                if (match('+')) {
                    builder.append('+')
                } else if (match('-')) {
                    builder.append('-')
                }

                numberFragment(builder)

                if (match('.')) {
                    builder.append('.')
                    numberFragment(builder)
                }
            }

            return token(if (isInt) TokenType.INT else TokenType.NUM, builder.toString().toDouble())
        }
    }

    /* 给number()用的，扫描下一段最简单的数字片段，123_456，不允许两侧分隔符 */
    private fun numberFragment(builder: StringBuilder) {
        if (this.isAtEnd) return
        val c = peek()
        if (c == '_') {
            error("数字两端不允许分隔符`_`")
                .point(current, current + 1, Integer.toHexString(c.code))
            advance()
        }

        while (!this.isAtEnd) {
            if (isDigit(c)) {
                builder.append(c)
            } else if (c == '_') {
                //忽略分隔符
            } else if (c == 'e' || c == 'E') {
                break
            } else if (isAlpha(c)) {
                error("数字中不期望的字符")
                    .point(current, current + 1, Integer.toHexString(c.code))
                recover { c: Char -> !isDigit(c) && !isAlpha(c) }
                break
            } else {
                break
            }
            advance()
        }

        if (builder[builder.length - 1] == '_') {
            error("数字两端不允许分隔符`_`")
                .point(current, current + 1, Integer.toHexString(c.code))
        }
    }

    private fun comment(): Token? {
        val docComment = StringBuilder()

        if (match('/')) { // 多行文档注释开始 #/
            while (!this.isAtEnd) {
                if (match("/#")) { // 多行文档注释结束 #/ ... /#
                    return token(TokenType.DOC_COMMENT, docComment.toString())
                }
                docComment.append(advance())
            }
            return token(TokenType.DOC_COMMENT, docComment.toString())
        } else if (match('|')) { // 文档注释开始 #|
            // int col = sourceMap.getCol(current - 1) - 1;
            while (!this.isAtEnd) {
                if (match('\n')) {
                    while (!this.isAtEnd && isWhitespace(peek())) {
                        advance()
                    }
                    if (!match('|')) { // 文档注释结束 #| ...\n |
                        return token(TokenType.DOC_COMMENT, docComment.toString())
                    }
                    docComment.append('\n')
                }
                docComment.append(advance())
            }
            return token(TokenType.DOC_COMMENT, docComment.toString())
        } else if (match('*')) { // 多行注释开始 #*
            while (!this.isAtEnd && !match("*#")) { // #* ... *#
                advance()
            }
            return null
        } else { // 单行注释 # ...
            while (!this.isAtEnd && !match('\n')) {
                advance()
            }
            return null
        }
    }

    private fun isAlpha(c: Char): Boolean {
        return (c in 'a'..'z')
                || (c in 'A'..'Z')
                || (c == '_')
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun isHexDigit(c: Char): Boolean {
        return isDigit(c)
                || (c in 'a'..'f')
                || (c in 'A'..'F')
    }

    private fun isBinDigit(c: Char): Boolean {
        return c == '0' || c == '1'
    }

    // 标识符开头
    private fun isIdentifierStart(c: Char): Boolean {
        return isAlpha(c)
                || Character.isLetter(c)
    }

    // 标识符中间和末尾
    private fun isIdentifierPart(c: Char): Boolean {
        return isIdentifierStart(c) || isDigit(c)
    }

    private fun isWhitespace(c: Char): Boolean {
        return Character.isWhitespace(c)
    }

    private fun charAt(index: Int): Char {
        return sourceMap.charAt(index)
    }

    private fun match(expected: Char): Boolean {
        if (this.isAtEnd) return false
        if (charAt(current) != expected) return false
        current++
        return true
    }

    /* 检查当前往后的字符串是否是期望字符串，是则推进至该字符串后1 */
    private fun match(expected: String): Boolean {
        if (this.isAtEnd) return false
        if (expected == subString(current, min(current + expected.length, length))) {
            advance(expected.length)
            return true
        }
        return false
    }


    private fun check(expected: Char): Boolean {
        return !this.isAtEnd && charAt(current) == expected
    }

    private val isAtEnd: Boolean
        get() = current >= length

    private fun advance(): Char {
        current++
        return charAt(current - 1)
    }

    private fun advance(step: Int): Char {
        current += step
        return charAt(current - step)
    }

    private fun peek(): Char {
        return charAt(current)
    }

    private fun peek(step: Int): Char {
        return charAt(current + step)
    }

    private fun subString(start: Int, end: Int): String {
        return sourceMap.subString(start, end)
    }

    private fun token(type: TokenType, literal: Any? = null): Token {
        val span = Span(sourceMap.index, start, current)

//        if (Log.isAllowed(Log.LogType.DEBUG)) {
//            val lineAndCol: IntArray? = sourceMap.getLineAndCol(start)
//            Log.debug(
//                start.toString() + Ansi.CYAN + "┃"
//                        + Ansi.DEFAULT + "(" + lineAndCol!![0] + "," + lineAndCol[1] + ")" + Ansi.CYAN + "┃"
//                        + Ansi.DEFAULT + type.toString() + Ansi.CYAN + "┃"
//                        + Ansi.DEFAULT + sourceMap.subString(
//                    start,
//                    current
//                ) + (if (literal == null) "" else (Ansi.CYAN + "┃"
//                        + Ansi.DEFAULT + literal))
//            )
//        }

        return Token(span, type, literal)
    }

    // EOF特化
    private fun eofToken(): Token {
        // 特化部分:current -> start + 1
        val span = Span(sourceMap.index, start, start + 1)

        if (Log.isAllowed(Log.LogType.DEBUG)) {
            val lineAndCol = sourceMap.getLineAndCol(start)
            Log.debug(
                (start.toString() + Ansi.CYAN + "┃"
                        + Ansi.DEFAULT + "(" + lineAndCol[0] + "," + lineAndCol[1] + ")" + Ansi.CYAN + "┃"
                        + Ansi.DEFAULT + TokenType.EOF.name + Ansi.CYAN + "┃"
                        + Ansi.DEFAULT)
            )
        }

        return Token(span, TokenType.EOF, null)
    }

    /**
     * 错误恢复，扫描直到期望的字符
     */
    private fun recover(vararg expectedChars: Char) {
        while (!this.isAtEnd) {
            for (expected in expectedChars) {
                if (check(expected)) {
                    return
                }
            }
            advance()
        }
    }

    /**
     * 错误恢复，扫描直到期望的字符
     * @param predicate 满足该条件则退出
     */
    private fun recover(predicate: Predicate<Char>) {
        while (!this.isAtEnd) {
            if (predicate.test(peek())) {
                return
            }
            advance()
        }
    }

    private fun error(text: String): LexerProblem {
        val e = LexerProblem(sourceMap, text, Problem.ProblemLevel.ERROR)
        collector.addError(e)
        return e
    }

    private fun warning(text: String): LexerProblem {
        val w = LexerProblem(sourceMap, text, Problem.ProblemLevel.WARNING)
        collector.addWarning(w)
        return w
    } //    public record LexerResult(List<LexerProblem> errorList, List<LexerProblem> warningList) {}
}