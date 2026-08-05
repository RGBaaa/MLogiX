package mlogix.compiler.core

import arc.files.Fi
import arc.func.Cons
import arc.struct.IntSeq
import arc.struct.ObjectMap
import arc.struct.Seq
import java.io.IOException

/**
 * 需要在项目中管理时使用SourceMapManager，否则可以直接使用SourceMap
 */
class SourceMapManager(/* 项目根目录 */val projectPath: Fi) {
    private val sourceMaps = ObjectMap<Fi, SourceMap>()

    /* 以此通过索引获取sourceMap */
    private val sourceMapList = Seq<SourceMap>()

    /**
     * 加载文件并创建 SourceMap
     */
    @Throws(IOException::class)
    fun loadSourceMap(filePath: Fi): SourceMap {
        val sourceMap = SourceMap(filePath, sourceMapList.size, projectPath)
        sourceMaps.put(filePath, sourceMap)
        sourceMapList.add(sourceMap)
        return sourceMap
    }

    /**
     * 获取文件的 SourceMap
     */
    fun getSourceMap(filePath: Fi): SourceMap? {
        return sourceMaps[filePath]
    }

    /**
     * 通过索引获取sourceMap
     */
    fun getSourceMap(index: Int): SourceMap? {
        return sourceMapList.get(index)
    }

    @Throws(IOException::class)
    fun walk(cons: Cons<Fi>) {
        projectPath.findAll { f -> f.extension().equals("mlx") }.forEach { f -> cons.get(f) }
    }

    class SourceMap {
        val filePath: Fi?
        val relativePath: String /* 相对于项目根目录的相对目录 */
        val source: String /* 存储所有字符 */
        val index: Int /* 在SourceMapManager中的索引 */
        private val lineOffsetList: IntSeq /* 每行的起始字符索引 */

        constructor(filePath: Fi, index: Int, projectPath: Fi) {
            this.filePath = filePath
            this.relativePath = projectPath.file().toURI().relativize(filePath.file().toURI()).path
            this.source = loadSource(filePath)
            this.lineOffsetList = buildLineOffsetList()
            this.index = index
        }

        constructor(source: String) {
            this.filePath = null
            this.relativePath = "src"
            this.source = loadSource(source)
            this.lineOffsetList = buildLineOffsetList()
            this.index = 0
        }

        /**
         * 加载文件内容为字符列表（自动处理UTF-8）
         */
        @Throws(IOException::class)
        private fun loadSource(filePath: Fi): String {
            return loadSource(filePath.readString()) // Java 11+ 直接读取为UTF-8字符串
        }

        /**
         * 从字符串加载字符列表
         */
        private fun loadSource(source: String): String {
            return source.replace("\r\n", "\n").replace('\r', '\n')
        }

        /**
         * 构建行号表（记录每行的起始字符索引）
         */
        private fun buildLineOffsetList(): IntSeq {
            val offsetList = IntSeq()
            offsetList.add(0) // 第一行从索引0开始

            for ((i, element) in source.withIndex()) {
                if (element == '\n') {
                    offsetList.add(i + 1) // 下一行起始位置
                }
            }
            return offsetList
        }

        /**
         * 根据字符索引获取行号和列号(从1开始)
         */
        fun getLineAndCol(charIndex: Int): IntArray {
            /*if (charIndex < 0 || charIndex >= source.size()) {
                throw new IllegalArgumentException("无效的字符索引(" + charIndex + " -> [0," + source.size() + "))");
            }*/

            var line = 1
            for (i in lineOffsetList.size - 1 downTo 0) {
                if (charIndex >= lineOffsetList[i]) {
                    line = i + 1
                    break
                }
            }

            val col = charIndex - lineOffsetList[line - 1] + 1
            return intArrayOf(line, col)
        }

        /**
         * 根据字符索引获取行号(从1开始)
         */
        fun getLine(charIndex: Int): Int {
            var line = 1
            for (i in lineOffsetList.size - 1 downTo 0) {
                if (charIndex >= lineOffsetList[i]) {
                    line = i + 1
                    break
                }
            }
            return line
        }

        /**
         * 根据字符索引获取列号(从1开始)
         */
        fun getCol(charIndex: Int): Int {
            return charIndex - lineOffsetList[getLine(charIndex) - 1] + 1
        }

        /** 截取为字符串 */
        fun subString(start: Int, end: Int): String {
            return source.substring(start, end)
        }

        /**
         * 获取一行字符串，不带\n
         */
        fun getLineString(line: Int): String {
            val line = line - 1

            // 最后一行
            if (line == lineOffsetList.size - 1) {
                return subString(lineOffsetList[line], source.length)
            }
            return subString(lineOffsetList[line], lineOffsetList[line + 1] - 1)
        }

        fun length(): Int {
            return source.length
        }

        fun charAt(index: Int): Char {
            return source[index]
        }
    }
}