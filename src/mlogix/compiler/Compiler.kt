package mlogix.compiler

import mlogix.compiler.SourceMapManager.SourceMap
import mlogix.mlogix.ast.ASTPrinter
import mlogix.problem.ProblemCollector
import mlogix.util.Log
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class Compiler(projectPath: Path) {
    private val manager: SourceMapManager = SourceMapManager(projectPath)
    private val collector: ProblemCollector = ProblemCollector()

    fun compile(): Boolean {
        val timer = PhaseTimer()
        // 可复用
        val lexer = Lexer(collector)
        val parser = Parser(lexer, collector)

        // 遍历项目树
        try {
            manager.walk()
                .filter { path: Path -> Files.isRegularFile(path) }
                .filter { f: Path -> f.fileName.toString().endsWith(".mlx") }
                .forEach { file: Path ->
                    val sourceMap: SourceMap
                    try {
                        sourceMap = manager.loadSourceMap(file)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        return@forEach
                    }
                    if (sourceMap.source.isEmpty()) return@forEach

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
        private val phaseTimeMap = HashMap<String, Long>()
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
                phaseTimeMap.merge(currentPhaseName!!, duration) { a: Long, b: Long -> a + b }
                currentPhaseName = null
            }
        }

        fun printPhaseTimes() {
            Log.info("=== 编译阶段耗时统计 ===")
            if (Log.isAllowed(Log.LogType.DEBUG)) {
                phaseTimeMap.forEach { (phaseName: String, time: Long) ->
                    System.out.printf("%-10s: %5d ms%n", phaseName, time)
                }
            }

            val total = phaseTimeMap.values.stream().mapToLong { obj: Long -> obj }.sum()
            System.out.printf("%-10s: %5d ms%n", "总计", total)
        }
    }
}
