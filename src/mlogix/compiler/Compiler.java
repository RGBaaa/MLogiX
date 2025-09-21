package mlogix.compiler;

import mlogix.compiler.issue.*;
import mlogix.compiler.struct.*;
import mlogix.compiler.struct.SourceMapManager.*;
import mlogix.logix.*;
import mlogix.util.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Compiler {
    private final SourceMapManager manager;
    private final List<Issue> errorList;
    private final List<Issue> warningList;

    public Compiler(Path projectPath) {
        this.manager = new SourceMapManager(projectPath);
        this.errorList = new ArrayList<>();
        this.warningList = new ArrayList<>();
    }

    public boolean compile() {
        PhaseTimer timer = new PhaseTimer();
        // 可复用
        Lexer lexer = new Lexer(errorList, warningList);

        // 遍历项目树
        try {
            manager.walk()
                    .filter(Files::isRegularFile)
                    .filter((Path f) -> f.endsWith("test.lx"))
                    .forEach(file -> {
                        SourceMap sourceMap;
                        try {
                            sourceMap = manager.loadSourceMap(file);
                        } catch(IOException e) {
                            e.printStackTrace();
                            return;
                        }
                        if(sourceMap.source.isEmpty()) return;

                        timer.startPhase("词法分析+语法分析");
                        Parser parser = new Parser(lexer.reset(sourceMap), sourceMap, errorList, warningList);
                        ASTNode ast = parser.parse();
                        timer.endPhase();

                        errorList.forEach(e -> Log.error(e.toString()));
                        warningList.forEach(e -> Log.warning(e.toString()));

                        if(Log.isAllowed(Log.LogType.DEBUG)) {
                            ASTPrinter.print(ast, sourceMap);
                        }

                        /*
                        // 语义分析
                        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
                        StructRegistry structRegistry = new StructRegistry();

                        SemanticResult semanticResult = semanticAnalyzer.analyze(ast, sourceMap);
                        List<Issue.SemanticIssue> semanticErrorList = semanticResult.errorList();
                        List<Issue.SemanticIssue> semanticWarningList = semanticResult.warningList();

                        semanticErrorList.forEach(e -> {
                            Log.error((e.toString()));
                        });
                        semanticWarningList.forEach(e -> {
                            Log.warning((e.toString()));
                        });

                         */
                    });
        } catch(IOException e) {
            e.printStackTrace();
        }

        timer.printPhaseTimes();

        if(!errorList.isEmpty()) {
            Log.info(errorList.size() + " errors");
            Log.info("编译失败");
            return false;
        }

        Log.info("编译成功");
        return true;
    }

    public class PhaseTimer {
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
            endPhase(); // 结束当前阶段

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