package mlogix.compiler;

import mlogix.mlogix.*;
import mlogix.problem.*;
import mlogix.struct.*;
import mlogix.struct.SourceMapManager.*;
import mlogix.util.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Compiler {
    private final SourceMapManager manager;
    private final ProblemCollector collector;

    public Compiler(Path projectPath) {
        this.manager = new SourceMapManager(projectPath);
        this.collector = new ProblemCollector();
    }

    public boolean compile() {
        PhaseTimer timer = new PhaseTimer();
        // 可复用
        Lexer lexer = new Lexer(collector);
        Parser parser = new Parser(lexer, collector);

        // 遍历项目树
        try {
            manager.walk()
                    .filter(Files::isRegularFile)
                    .filter((Path f) -> f.getFileName().toString().endsWith(".mlx"))
                    .forEach(file -> {
                        SourceMap sourceMap;
                        try {
                            sourceMap = manager.loadSourceMap(file);
                        } catch(IOException e) {
                            e.printStackTrace();
                            return;
                        }
                        if(sourceMap.source.isEmpty()) return;

                        // ---------- 词法分析 + 语法分析 ----------
                        timer.startPhase("词法分析+语法分析");
                        Stmt ast = parser.parse(sourceMap);
                        timer.endPhase();
                        
                        // ---------- 语义分析 ----------
//                        timer.startPhase("语义分析");
//                        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(collector);
//                        semanticAnalyzer.analyze(ast, sourceMap);
//                        timer.endPhase();

                        // ---------- 输出报告 ----------
                        collector.printError();
                        collector.printWarning();

                        if(Log.isAllowed(Log.LogType.DEBUG)) {
                            ASTPrinter.print(ast, sourceMap);
                        }
                    });
        } catch(IOException e) {
            e.printStackTrace();
        }

        timer.printPhaseTimes();

        if(collector.errorNum() != 0) {
            Log.info(collector.errorNum() + " errors");
        }
        if(collector.warningNum()!=0){
            Log.info(collector.warningNum() + " warnings");
        }
        if(collector.hasError()) {
            Log.info("编译失败");
            return false;
        } else {
            Log.info("编译成功");
            return true;
        }
    }

    public static class PhaseTimer {
        private final Map<String, Long> phaseTimeMap = new HashMap<>();
        private String currentPhaseName;
        private long phaseStart;

        public void startPhase(String phaseName) {
            if(currentPhaseName != null) {
                endPhase();
            }
            currentPhaseName = phaseName;
            phaseStart = System.currentTimeMillis();
        }

        public void endPhase() {
            if(currentPhaseName != null) {
                long duration = System.currentTimeMillis() - phaseStart;
                phaseTimeMap.merge(currentPhaseName, duration, Long::sum);
                currentPhaseName = null;
            }
        }

        public void printPhaseTimes() {
            System.out.println("=== 编译阶段耗时统计 ===");
            if(Log.isAllowed(Log.LogType.DEBUG)) {
                phaseTimeMap.forEach((phaseName, time) -> {
                    System.out.printf("%-10s: %5d ms%n", phaseName, time);
                });
            }

            long total = phaseTimeMap.values().stream().mapToLong(Long::longValue).sum();
            System.out.printf("%-10s: %5d ms%n", "总计", total);
        }
    }
}