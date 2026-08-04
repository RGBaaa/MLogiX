package mlogix.compiler

import arc.files.Fi
import arc.struct.ArrayMap
import arc.struct.ObjectMap
import mlogix.compiler.SourceMapManager.SourceMap
import mlogix.mlogix.ast.ASTPrinter
import mlogix.problem.ProblemCollector
import mlogix.util.Log
import java.io.IOException
import kotlin.collections.component1
import kotlin.collections.component2

class Compiler(projectPath: Fi) {
    private val manager: SourceMapManager = SourceMapManager(projectPath)
    private val collector: ProblemCollector = ProblemCollector()

    fun compile(): Boolean {
        val timer = PhaseTimer()
        // 可复用
        val lexer = Lexer(collector)
        val parser = Parser(lexer, collector)

        // 遍历项目树
        try {
            manager.walk { file ->
                if (!file.extension().equals("mlx")) return@walk

                val sourceMap: SourceMap
                try {
                    sourceMap = manager.loadSourceMap(file)
                } catch (e: IOException) {
                    e.printStackTrace()
                    return@walk
                }
                if (sourceMap.source.isEmpty()) return@walk

                // ---------- 词法分析 + 语法分析 ----------
                timer.startPhase("词法分析+语法分析")
                val ast = parser.parse(sourceMap)
                timer.endPhase()


                // ---------- 语义分析 ----------
                //                        timer.startPhase("语义分析");
                //                        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(collector);
                //                        semanticAnalyzer.analyze(ast, sourceMap);
                //                        timer.endPhase();

                // ---------- 输出报告 ----------
                collector.printError()
                collector.printWarning()
                if (Log.isAllowed(Log.LogType.DEBUG)) {
                    ASTPrinter.print(ast, sourceMap)
                }
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }

        timer.printPhaseTimes()

        if (collector.errorNum() != 0) {
            Log.info(collector.errorNum().toString() + " errors")
        }
        if (collector.warningNum() != 0) {
            Log.info(collector.warningNum().toString() + " warnings")
        }
        if (collector.hasError()) {
            Log.info("编译失败")
            return false
        } else {
            Log.info("编译成功")
            return true
        }
    }

    class PhaseTimer {
        private val phaseTimeMap = ArrayMap<String, Long>()
        private var currentPhaseName: String? = null
        private var phaseStart: Long = 0

        fun startPhase(phaseName: String) {
            if (currentPhaseName != null) {
                endPhase()
            }
            currentPhaseName = phaseName
            phaseStart = System.currentTimeMillis()
        }

        fun endPhase() {
            if (currentPhaseName != null) {
                val duration = System.currentTimeMillis() - phaseStart
                phaseTimeMap.put(currentPhaseName, duration)
                currentPhaseName = null
            }
        }

        fun printPhaseTimes() {
            Log.info("=== 编译阶段耗时统计 ===")
            if (Log.isAllowed(Log.LogType.DEBUG)) {
                phaseTimeMap.forEach { entry: ObjectMap.Entry<String, Long> ->
                    System.out.printf("%-10s: %5d ms%n", entry.key, entry.value)
                }
            }

            val total = phaseTimeMap.values.sum()
            System.out.printf("%-10s: %5d ms%n", "总计", total)
        }
    }
}
