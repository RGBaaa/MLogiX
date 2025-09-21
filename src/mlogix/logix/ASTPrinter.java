package mlogix.logix;

import mlogix.compiler.struct.SourceMapManager.*;

import java.lang.reflect.*;
import java.util.*;

import static mlogix.util.Ansi.*;

public class ASTPrinter {
    private static final String STMT_COLOR = YELLOW;    // 蓝色 - 语句
    private static final String EXPR_COLOR = BLUE;      // 青色 - 表达式
    private static final String TOKEN_COLOR = CYAN;     // 黄色 - Token
    private static final String VALUE_COLOR = GREEN;    // 绿色 - 值
    private static final String FIELD_COLOR = DEFAULT;  // 白色 - 字段
    private static final String LIST_COLOR = MAGENTA;   // 洋红色 - 列表连接线
    private static final String LINE_COLOR = DEFAULT;   // 白色 - 连接线

    // 连接线样式
    private static final String INDENT_BLANK = "   " + LINE_COLOR;
    private static final String CONNECTOR_LAST = "└──" + LINE_COLOR;
    private static final String CONNECTOR_MID = "├──" + LINE_COLOR;
    private static final String VERTICAL_LINE = "│  " + LINE_COLOR;

    private static boolean indentEnabled;

    private static SourceMap sourceMap;

    public static void print(ASTNode node, SourceMap sourceMap) {
        if(node == null) throw new RuntimeException("空的astNode");
        indentEnabled = false; // Program前不需要缩进
        ASTPrinter.sourceMap = sourceMap;
        print(node, "", true);
    }

    private static void print(Object node, String indent, boolean isLast) {
        if(node == null) {
            printLine(indent, isLast, "null");
            return;
        }

        // 处理不同类型
        if(node instanceof Stmt stmt) {
            printASTNode(stmt, indent, isLast, STMT_COLOR);
        } else if(node instanceof Expr expr) {
            printASTNode(expr, indent, isLast, EXPR_COLOR);
        } else if(node instanceof Token) {
            printLine(indent, isLast, TOKEN_COLOR + ((Token) node).toSimpleString() + DEFAULT);
        } else if(node instanceof List) {
            printList((List<?>) node, indent, isLast);
        } else if(node.getClass().isArray()) {
            printArray((Object[]) node, indent, isLast);
        } else {
            printLine(indent, isLast, node.toString());
        }
    }

    private static void printASTNode(ASTNode node, String indent, boolean isLast, String color) {
        // 打印节点类型名称
        int start = node.span.start();
        int end = node.span.end();
        int line = sourceMap.getLine(start);
        String lineString = sourceMap.getLineString(line);
        char startChar = sourceMap.charAt(start);
        printLine(indent, isLast, color + node.getClass().getSimpleName()
                + VALUE_COLOR + "[" + start + "," + end + ")" + DEFAULT
                + " " + line
                + B_CYAN + lineString.substring(0, Math.max(0, sourceMap.getCol(start) - 1))
                + B_MAGENTA + (startChar == '\n' ? "" : startChar)
                + B_CYAN + lineString.substring(Math.min(sourceMap.getCol(start), lineString.length()))
                + DEFAULT
                + (start == end ? "" : " "
                    + sourceMap.getLine(end - 1)
                    + B_YELLOW + sourceMap.getLineString(sourceMap.getLine(end - 1))
                    + DEFAULT
                )
        );

        indentEnabled = true;

        // 获取所有字段
        Field[] fields = node.getClass().getDeclaredFields();
        if(fields.length == 0) {
            return;
        }

        // 计算新的缩进
        String newIndent = indent + (isLast ? INDENT_BLANK : VERTICAL_LINE + FIELD_COLOR);

        // 打印所有字段
        for(int i = 0; i < fields.length; i++) {
            boolean fieldIsLast = (i == fields.length - 1);
            Field field = fields[i];
            field.setAccessible(true);

            try {
                Object value = field.get(node);
                printField(field.getName(), value, newIndent, fieldIsLast);
            } catch(IllegalAccessException e) {
                printLine(newIndent, fieldIsLast, "ERROR: " + e.getMessage());
            }
        }
    }

    private static void printField(String fieldName, Object value, String indent, boolean isLast) {
        if(value == null) {
            printLine(indent, isLast, FIELD_COLOR + fieldName + ": " + VALUE_COLOR + "null" + DEFAULT);
            return;
        } else if((value instanceof List && ((List<?>) value).isEmpty()) ||
                (value.getClass().isArray() && ((Object[]) value).length == 0)) {
            printLine(indent, isLast, FIELD_COLOR + fieldName + ": " + LIST_COLOR + "[]" + DEFAULT);
            return;
        }

        // 对于简单类型直接打印
        if(value instanceof Boolean || value instanceof Number || value instanceof String || value instanceof Token) {
            printLine(indent, isLast, FIELD_COLOR + fieldName + ": " + VALUE_COLOR + value + DEFAULT);
            return;
        }

        // 对于复杂类型递归打印
        printLine(indent, isLast, fieldName + ":");
        indentEnabled = false;
        print(value, indent + (isLast ? INDENT_BLANK : VERTICAL_LINE + FIELD_COLOR), true);
    }

    private static void printList(List<?> list, String indent, boolean isLast) {
        if(list.isEmpty()) {
            printLine(indent, isLast, "List[]");
            return;
        }

        printLine(indent, isLast, LIST_COLOR + "List" + DEFAULT);
        String newIndent = indent + LIST_COLOR;

        indentEnabled = true;
        for(int i = 0; i < list.size(); i++) {
            boolean itemIsLast = (i == list.size() - 1);
            print(list.get(i), newIndent, itemIsLast);
        }
    }

    private static void printArray(Object[] array, String indent, boolean isLast) {
        if(array.length == 0) {
            printLine(indent, isLast, "Array[]");
            return;
        }

        printLine(indent, isLast, LIST_COLOR + "Array" + DEFAULT);
        String newIndent = indent + LIST_COLOR;

        indentEnabled = true;
        for(int i = 0; i < array.length; i++) {
            boolean itemIsLast = (i == array.length - 1);
            print(array[i], newIndent, itemIsLast);
        }
    }

    private static void printLine(String indent, boolean isLast, String text) {
        System.out.print(indent);
        if(indentEnabled) System.out.print(isLast ? CONNECTOR_LAST : CONNECTOR_MID);
        System.out.println(text);
    }
}