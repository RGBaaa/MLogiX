package mlogix.problem

import arc.struct.Seq
import mlogix.compiler.SourceMapManager.SourceMap
import mlogix.span.Spanned
import mlogix.util.Ansi

// 用于表示编译器问题，包含错误和警告
abstract class Problem(
    val sourceMap: SourceMap,   // 这个问题所在文件
    val problemName: String,    // 这个问题的名称
    val level: ProblemLevel     // 问题级别（错误或警告）
) {
    val lineInfos = Seq<LineInfo>()

    // 获取行，若不存在则新建并返回
    private fun getLineInfo(line: Int): LineInfo {
        for (lineInfo in lineInfos) {
            if (lineInfo.line == line) return lineInfo
        }
        val lineInfo = LineInfo(line, sourceMap.getLineString(line))
        lineInfos.add(lineInfo)
        return lineInfo
    }

    fun point(obj: Spanned, text: String): Problem {
        val span = obj.span()
        return point(span.start, span.end, text)
    }

    fun point(start: Int, end: Int, text: String): Problem {
        val lineInfo = getLineInfo(sourceMap.getLine(start))
        lineInfo.point(sourceMap.getCol(start), "^".repeat(end - start), text)
        return this
    }

    fun info(obj: Spanned, text: String): Problem {
        val span = obj.span()
        return info(span.start, span.end, text)
    }

    fun info(start: Int, end: Int, text: String): Problem {
        val lineInfo = getLineInfo(sourceMap.getLine(start))
        lineInfo.info(sourceMap.getCol(start), "-".repeat(end - start), text)
        return this
    }

    override fun toString(): String {
        val color = if (level == ProblemLevel.ERROR) Ansi.RED else Ansi.YELLOW
        val str = StringBuilder("$color${level.name}:$problemName${Ansi.DEFAULT}\n")
        lineInfos.sort(Comparator.comparing { li -> li.line })

        var maxLineDigitLen = 0  // 所有 LineInfo 中最长的行号长度
        for (lineInfo in lineInfos) {
            maxLineDigitLen = maxOf(maxLineDigitLen, lineInfo.line.toString().length)
        }

        for (lineInfo in lineInfos) {
            str.append(lineInfo.format(maxLineDigitLen))
        }
        return str.toString()
    }

    enum class ProblemLevel {
        WARNING, ERROR
    }

    /* Lexer产生的问题 */
    class LexerProblem(sourceMap: SourceMap, problemName: String, level: ProblemLevel) :
        Problem(sourceMap, problemName, level)

    /* Parser产生的问题 */
    class ParserProblem(sourceMap: SourceMap, problemName: String, level: ProblemLevel) :
        Problem(sourceMap, problemName, level)

    /* SemanticAnalyzer产生的问题 */
    class SemanticProblem(sourceMap: SourceMap, problemName: String, level: ProblemLevel) :
        Problem(sourceMap, problemName, level)

    /**
     * @param col 从1开始
     */
    data class Info(val col: Int, val indicator: String, val text: String)

    // 储存一行的错误信息
    inner class LineInfo(
        val line: Int,
        val lineString: String
    ) {
        val infos = mutableListOf<Info>()
        var col: Int = 0

        fun point(startCol: Int, indicator: String, text: String) {
            this.col = startCol
            infos.add(Info(startCol, indicator, text))
        }

        fun info(startCol: Int, indicator: String, text: String) {
            infos.add(Info(startCol, indicator, text))
        }

        /**
         * @param maxLineDigitLen 所有 LineInfo 中最长的行号长度
         */
        fun format(maxLineDigitLen: Int): String {
            val str = StringBuilder()

            // -->Path:line:col
            str.append(" ".repeat(maxLineDigitLen - 1))
                .append("-->")
                .append(sourceMap.relativePath).append(":").append(line).append(":").append(col).append("\n")

            //  ┃
            str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT).append("\n")

            // L┃lineString
            val lineDigitLen = line.toString().length
            str.append(Ansi.CYAN)
                .append(" ".repeat(maxLineDigitLen - lineDigitLen)).append(line).append("┃")
                .append(Ansi.DEFAULT).append(lineString).append("\n")

            if (infos.isEmpty()) return str.toString()  // 为空退出防止越界

            infos.sortBy { it.col }  // 排序，以从前往后输出

            //  ┃ ^ - ^ infos[last]text
            str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT)
            var col = 1  // 标识当前输出列
            for ((col1, indicator) in infos) {
                val count = col1 - col
                if (count < 0) continue  // 与上一个重叠
                str.append(" ".repeat(count))
                str.append(indicator)
                col = col1 + indicator.length
            }
            str.append(" ").append(infos.last().text)
            str.append("\n")

            //  ┃ | |
            //  ┃ | infos[last-1].text
            //  ┃ |
            //  ┃ infos[last-2].text
            for (i in infos.size - 1 downTo 1) {
                val text = infos[i - 1].text
                if (text.isEmpty()) continue

                //  ┃ | |
                str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT)
                col = 1
                for (j in 0 until i) {
                    val count = infos[j].col - col
                    if (count < 0) continue  // 重叠
                    str.append(" ".repeat(count))
                    str.append("|")
                    col = infos[j].col + 1
                }
                str.append("\n")

                //  ┃ | infos[last-1].text
                str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT)
                col = 1
                for (j in 0 until i - 1) {
                    if (infos[j].col == infos[j + 1].col) {  // 与后一个重叠
                        continue  // 跳过此次
                    }
                    val count = infos[j].col - col
                    str.append(" ".repeat(count))
                    str.append("|")
                    col = infos[j].col + 1
                }
                str.append(" ".repeat(maxOf(0, infos[i - 1].col - col)))
                str.append(text)
                str.append("\n")
            }

            return str.toString()
        }
    }
}