package mlogix.compiler.issue;

import mlogix.compiler.struct.SourceMapManager.*;
import mlogix.logix.*;
import mlogix.util.*;

import java.util.*;

// 用于表示编译器问题，包含错误和警告
public abstract class Issue extends RuntimeException {
    private final SourceMap sourceMap; // 这个问题所在文件
    private final String issueName; // 这个问题的名称
    private final List<LineInfo> lineList = new ArrayList<>();
    private final IssueLevel level; // 问题级别（错误或警告）

    public Issue(SourceMap sourceMap, String issueName, IssueLevel level) {
        this.sourceMap = sourceMap;
        this.issueName = issueName;
        this.level = level;
    }

    // 获取行，若不存在则新建并返回
    private LineInfo getLineInfo(int line) {
        for(LineInfo lineInfo : lineList) {
            if(lineInfo.line == line) {
                return lineInfo;
            }
        }
        LineInfo lineInfo = new LineInfo(line, sourceMap.getLineString(line));
        lineList.add(lineInfo);
        return lineInfo;
    }

    public Issue point(int start, int end, String text) {
        LineInfo lineInfo = getLineInfo(sourceMap.getLine(start));
        lineInfo.point(sourceMap.getCol(start), "^".repeat(end - start), text);
        return this;
    }

    public Issue point(Token token, String text) {
        return point(token.span.start(), token.span.end(), text);
    }

    public Issue info(int start, int end, String text) {
        LineInfo lineInfo = getLineInfo(sourceMap.getLine(start));
        lineInfo.info(sourceMap.getCol(start), "-".repeat(end - start), text);
        return this;
    }

    public Issue info(Token token, String text) {
        return info(token.span.start(), token.span.end(), text);
    }

    public String toString() {
        String color = level == IssueLevel.ERROR ? Ansi.RED : Ansi.YELLOW;
        StringBuilder str = new StringBuilder(color + level.name() + ":" + issueName + Ansi.DEFAULT + "\n");
        lineList.sort(Comparator.comparing(li -> li.line));

        int maxLineDigitLen = 0; // 所有LineInfo中最长的行号长度
        for(LineInfo lineInfo : lineList) {
            maxLineDigitLen = Math.max(maxLineDigitLen, Integer.toString(lineInfo.line).length());
        }

        for(LineInfo lineInfo : lineList) {
            str.append(lineInfo.toString(maxLineDigitLen));
        }
        return str.toString();
    }

    public enum IssueLevel {
        WARNING,
        ERROR
    }

    /* Lexer产生的问题 */
    public static class LexerIssue extends Issue {
        public LexerIssue(SourceMap sourceMap, String issueName, IssueLevel level) {
            super(sourceMap, issueName, level);
        }
    }

    /* Parser产生的问题 */
    public static class ParserIssue extends Issue {
        public ParserIssue(SourceMap sourceMap, String issueName, IssueLevel level) {
            super(sourceMap, issueName, level);
        }
    }

    /* SemanticAnalyzer产生的问题 */
    public static class SemanticIssue extends Issue {
        public SemanticIssue(SourceMap sourceMap, String issueName, IssueLevel level) {
            super(sourceMap, issueName, level);
        }
    }

    // 储存一行的错误信息
    class LineInfo {
        final int line;
        final String lineString;
        final List<Info> infos = new ArrayList<>();
        int col;

        LineInfo(int line, String lineString) {
            this.line = line;
            this.lineString = lineString;
        }

        void point(int startCol, String indicator, String text) {
            this.col = startCol;
            Info info = new Info(startCol, indicator, text);
            infos.add(info);
        }

        void info(int startCol, String indicator, String text) {
            Info info = new Info(startCol, indicator, text);
            infos.add(info);
        }

        /**
         * @param maxLineDigitLen 所有LineInfo中最长的行号长度
         */
        public String toString(int maxLineDigitLen) {
            StringBuilder str = new StringBuilder();

            // -->Path:line:col
            str.append(" ".repeat(maxLineDigitLen - 1)).append("-->")
                    .append(sourceMap.relativePath).append(":").append(line).append(":").append(col).append("\n");

            //  ┃
            str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT).append("\n");

            // L┃lineString
            int lineDigitLen = Integer.toString(line).length();
            str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen - lineDigitLen)).append(line).append("┃")
                    .append(Ansi.DEFAULT).append(lineString).append("\n");

            if(infos.isEmpty()) return str.toString();// 为空退出防止越界

            infos.sort(Comparator.comparing(i -> i.col));// 排序，以从前往后输出

            //  ┃ ^ - ^ infos[last]text
            str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT);
            int col = 1;// 标识当前输出列
            for(Info info : infos) {
                int count = info.col - col;
                if(count < 0) continue; // 与上一个重叠
                str.append(" ".repeat(count));
                str.append(info.indicator);
                col = info.col + info.indicator.length();
            }
            str.append(" ").append(infos.get(infos.size() - 1).text);
            str.append("\n");

            //  ┃ | |
            //  ┃ | infos[last-1].text
            //  ┃ |
            //  ┃ infos[last-2].text
            for(int i = infos.size() - 1; i > 0; i--) {
                String text = infos.get(i - 1).text;
                if(text.isEmpty()) continue;

                //  ┃ | |
                str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT);
                col = 1;
                for(int j = 0; j < i; j++) {
                    int count = infos.get(j).col - col;
                    if(count < 0) continue; // 重叠
                    str.append(" ".repeat(count));
                    str.append("|");
                    col = infos.get(j).col + 1;
                }
                str.append("\n");

                //  ┃ | infos[last-1].text
                str.append(Ansi.CYAN).append(" ".repeat(maxLineDigitLen)).append("┃").append(Ansi.DEFAULT);
                col = 1;
                for(int j = 0; j < i - 1; j++) {
                    if(infos.get(j).col == infos.get(j + 1).col) { // 与后一个重叠
                        continue; // 跳过此次
                    }
                    int count = infos.get(j).col - col;
                    str.append(" ".repeat(count));
                    str.append("|");
                    col = infos.get(j).col + 1;
                }
                str.append(" ".repeat(Math.max(0, infos.get(i - 1).col - col)));
                str.append(text);
                str.append("\n");
            }

            return str.toString();
        }

        /**
         * @param col 从1开始
         */
        private record Info(int col, String indicator, String text) {
        }
    }
}