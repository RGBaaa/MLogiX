package mlogix.compiler.struct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.*;

public class SourceMapManager {
    public final Path projectPath; /* 项目根目录 */
    private final Map<Path, SourceMap> sourceMaps = new HashMap<>();
    private final List<SourceMap> sourceMapList = new ArrayList<>(); /* 以此通过索引获取sourceMap */

    public SourceMapManager(Path projectPath) {
        this.projectPath = projectPath;
    }

    public SourceMapManager() {
        this.projectPath = null;
    }

    /**
     * 加载文件并创建 SourceMap
     */
    public SourceMap loadSourceMap(Path filePath) throws IOException {
        SourceMap sourceMap = new SourceMap(filePath, sourceMapList.size());
        sourceMaps.put(filePath, sourceMap);
        sourceMapList.add(sourceMap);
        return sourceMap;
    }

    /**
     * 从字符串创建 SourceMap
     */
    public SourceMap loadSourceMap(String source) {
        SourceMap sourceMap = new SourceMap(source, sourceMapList.size());
        // sourceMaps.put(null, sourceMap); 临时代码无需
        sourceMapList.add(sourceMap);
        return sourceMap;
    }

    /**
     * 获取文件的 SourceMap
     */
    public SourceMap getSourceMap(Path filePath) {
        return sourceMaps.get(filePath);
    }

    /**
     * 通过索引获取sourceMap
     */
    public SourceMap getSourceMap(int index) {
        return sourceMapList.get(index);
    }

    public Stream<Path> walk() throws IOException {
        if(projectPath != null) {
            return Files.walk(projectPath);
        }
        throw new RuntimeException("无项目路径的SourceMapManager无法使用walk()");
    }

    public class SourceMap {
        public final Path filePath;
        public final Path relativePath; /* 相对于项目根目录的相对目录 */
        public final String source; /* 存储所有字符 */
        public final int index; /* 在SourceMapManager中的索引 */
        private final List<Integer> lineOffsetList; /* 每行的起始字符索引 */

        private SourceMap(Path filePath, int index) throws IOException {
            this.filePath = filePath;
            this.relativePath = projectPath.relativize(filePath);
            this.source = loadSource(filePath);
            this.lineOffsetList = buildLineOffsetList();

            this.index = index;
        }

        private SourceMap(String source, int index) {
            this.filePath = null;
            this.relativePath = null;
            this.source = loadSource(source);
            this.lineOffsetList = buildLineOffsetList();

            this.index = index;
        }

        /**
         * 加载文件内容为字符列表（自动处理UTF-8）
         */
        private String loadSource(Path filePath) throws IOException {
            return loadSource(Files.readString(filePath)); // Java 11+ 直接读取为UTF-8字符串
        }

        /**
         * 从字符串加载字符列表
         */
        private String loadSource(String source) {
            return source.replace("\r\n", "\n").replace('\r', '\n');
        }

        /**
         * 构建行号表（记录每行的起始字符索引）
         */
        private List<Integer> buildLineOffsetList() {
            List<Integer> offsetList = new ArrayList<>();
            offsetList.add(0); // 第一行从索引0开始

            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\n') {
                    offsetList.add(i + 1); // 下一行起始位置
                }
            }
            return offsetList;
        }

        /**
         * 根据字符索引获取行号和列号(从1开始)
         */
        public int[] getLineAndCol(int charIndex) {
            /*if (charIndex < 0 || charIndex >= source.size()) {
                throw new IllegalArgumentException("无效的字符索引(" + charIndex + " -> [0," + source.size() + "))");
            }*/

            int line = 1;
            for (int i = lineOffsetList.size() - 1; i >= 0; i--) {
                if (charIndex >= lineOffsetList.get(i)) {
                    line = i + 1;
                    break;
                }
            }

            int col = charIndex - lineOffsetList.get(line - 1) + 1;
            return new int[]{line, col};
        }

        /**
         * 根据字符索引获取行号(从1开始)
         */
        public int getLine(int charIndex) {
            int line = 1;
            for (int i = lineOffsetList.size() - 1; i >= 0; i--) {
                if (charIndex >= lineOffsetList.get(i)) {
                    line = i + 1;
                    break;
                }
            }
            return line;
        }

        /**
         * 根据字符索引获取列号(从1开始)
         */
        public int getCol(int charIndex) {
            return charIndex - lineOffsetList.get(getLine(charIndex) - 1) + 1;
        }

        /* 截取为字符串 */
        public String subString(int start, int end) {
            return source.substring(start, end);
        }

        /* 获取一行字符串，不带\n */
        public String getLineString(int line) {
            line = line - 1;

            // 最后一行
            if (line == lineOffsetList.size() - 1) {
                return subString(lineOffsetList.get(line), source.length());
            }
            return subString(lineOffsetList.get(line), lineOffsetList.get(line + 1) - 1);
        }

        public int length() {
            return source.length();
        }

        public char charAt(int index) {
            return source.charAt(index);
        }
    }
}