package test.mlogix.compiler;

import java.util.*;

import mlogix.mlogix.*;
import mlogix.compiler.Lexer;
import mlogix.problem.*;
import mlogix.struct.SourceMapManager;
import mlogix.util.*;

public class LexerTest {
    static final SourceMapManager manager = new SourceMapManager();
    static final List<Problem> errorList = new ArrayList<>();
    static final List<Problem> warningList = new ArrayList<>();
    static final Lexer lexer = new Lexer(errorList, warningList);
    static int testNum = 0;
    static int errorNum = 0;

    public static void main(String[] args) {
        test();
    }

    public static void test() {
        Log.info(Ansi.CYAN + "LexerTest: 开始" + Ansi.DEFAULT);

        test("+", token(TokenType.PLUS));
        test("-", token(TokenType.MINUS));
        test("*", token(TokenType.STAR));
        test("/", token(TokenType.SLASH));
        test("//", token(TokenType.SLASH_SLASH));
        test("^", token(TokenType.CARET));
        test("%", token(TokenType.PERCENT));
        test("&", token(TokenType.AND));
        test("&&", token(TokenType.AND_AND));
        test("|", token(TokenType.OR));
        test("||", token(TokenType.OR_OR));
        test("~", token(TokenType.TILDE));
        test("!", token(TokenType.BANG));
        test("!=", token(TokenType.BANG_EQ));
        test("!==", token(TokenType.BANG_EQ_EQ));
        test("<", token(TokenType.LESS));
        test("<<", token(TokenType.SHL));
        test("<=", token(TokenType.LESS_EQ));
        test(">", token(TokenType.GREATER));
        test(">>", token(TokenType.SHR));
        test(">=", token(TokenType.GREATER_EQ));
        test("=", token(TokenType.ASSIGN));
        test("==", token(TokenType.EQ_EQ));
        test("===", token(TokenType.EQ_EQ_EQ));
        test(".", token(TokenType.DOT));
        test("..", token(TokenType.DOT_DOT));
        test("..=", token(TokenType.DOT_DOT_EQ));
        test(":", token(TokenType.COLON));
        test(";", token(TokenType.SEMICOLON));
        test(",", token(TokenType.COMMA));
        test("(", token(TokenType.LPAREN));
        test(")", token(TokenType.RPAREN));
        test("[", token(TokenType.LBRACKET));
        test("]", token(TokenType.RBRACKET));
        test("{", token(TokenType.LBRACE));
        test("}", token(TokenType.RBRACE));

        test("\"hello\"", token(TokenType.STRING, "hello"));
        test("\"hello\\nworld!\"", token(TokenType.STRING, "hello\\nworld!"));

        // 错误测试 - 字符串
        test("\"hello", 1, token(TokenType.STRING, "hello")); // 未闭合的字符串
        test("\"hello\nworld\"", 2, token(TokenType.STRING, "hello")); // 字符串中包含换行符
        
        // 错误测试 - 未知字符
        test("a $ b",
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.ERROR, "$"),
            token(TokenType.IDENTIFIER, "b")); // 包含未知字符 $
        test("a @ b",
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.IDENTIFIER, "b")); // @ 应该被识别为逻辑关键字开始
        
        // 错误测试 - 数字格式
        test("0x12ZG", token(TokenType.ERROR)); // 非法的十六进制数字
        test("0b102", token(TokenType.ERROR)); // 非法的二进制数字

        // 测试普通数字
        test("27_30", token(TokenType.NUM, (double) 2730));
        test("2_7.3_0", token(TokenType.NUM, (double) 27.3));
        test("2_730.", token(TokenType.NUM, (double) 2730));
        test(".27_30", token(TokenType.NUM, (double) 0.273));
        
        // 测试科学计数法
        test("1.23e4", token(TokenType.NUM, 1.23e4));
        test("1.23e+4", token(TokenType.NUM, 1.23e+4));
        test("1.23e-4", token(TokenType.NUM, 1.23e-4));
        test("1e4", token(TokenType.NUM, 1e4));
        
        // 测试十六进制和二进制
        test("0x013_579aBcDeF", token(TokenType.NUM, (double) 0x013579ABCDEFL));
        test("0b10_1100", token(TokenType.NUM, (double) 0b101100));
        
        // 错误测试 - 数字格式
        test("_2730", token(TokenType.ERROR)); // 数字不能以分隔符开头
        test("2730_", token(TokenType.ERROR)); // 数字不能以分隔符结尾
        test("1.2.3"); // 多个小数点
        test("1e2e3"); // 多个指数标记
        
        // 测试关键字
        test("set", token(TokenType.SET));
        test("macro", token(TokenType.MACRO));
        test("const", token(TokenType.CONST));
        test("if", token(TokenType.IF));
        test("elif", token(TokenType.ELIF));
        test("else", token(TokenType.ELSE));
        test("while", token(TokenType.WHILE));
        test("for", token(TokenType.FOR));
        test("break", token(TokenType.BREAK));
        test("continue", token(TokenType.CONTINUE));
        test("struct", token(TokenType.STRUCT));
        test("match", token(TokenType.MATCH));
        test("fn", token(TokenType.FN));
        test("return", token(TokenType.RETURN));
        test("enum", token(TokenType.ENUM));
        
        // 测试标识符
        test("identifier", token(TokenType.IDENTIFIER, "identifier"));
        test("_underscore", token(TokenType.IDENTIFIER, "_underscore"));
        test("camelCase", token(TokenType.IDENTIFIER, "camelCase"));
        test("PascalCase", token(TokenType.IDENTIFIER, "PascalCase"));
        test("snake_case", token(TokenType.IDENTIFIER, "snake_case"));
        
        // 测试布尔值和null
        test("true", token(TokenType.TRUE));
        test("false", token(TokenType.FALSE));
        test("null", token(TokenType.NULL));
        
        // 测试复合表达式
        test("a + b", 
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.PLUS),
            token(TokenType.IDENTIFIER, "b"));
        test("a += 10", 
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.PLUS),
            token(TokenType.ASSIGN),
            token(TokenType.NUM, 10.0));
        test("fn test() { return 42; }", 
            token(TokenType.FN),
            token(TokenType.IDENTIFIER, "test"),
            token(TokenType.LPAREN),
            token(TokenType.RPAREN),
            token(TokenType.LBRACE),
            token(TokenType.RETURN),
            token(TokenType.NUM, 42.0),
            token(TokenType.SEMICOLON),
            token(TokenType.RBRACE));
        
        // 测试注释
        test("a = 5 # 这是注释", 
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.ASSIGN),
            token(TokenType.NUM, 5.0));
            
        // 测试文档注释
        test("#/ 这是多行文档注释 /#", token(TokenType.DOC_COMMENT, " 这是多行文档注释 "));
        test("#| 这是行文档注释" +
                " | a = 1" +
                " | b = a",
                token(TokenType.DOC_COMMENT, " 这是行文档注释" +
                        " a = 1" +
                        " b = a"));
        
        // 测试多行注释（不产生token）
        test("#* 这是多行注释 *#");
        
        // 测试逻辑关键字（以@开头的标识符）
        test("@logic", token(TokenType.IDENTIFIER, "logic"));
        test("@test123", token(TokenType.IDENTIFIER, "test123"));
        
        // 测试更复杂的表达式
        test("a[i] = b[j] + c[k]",
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.LBRACKET),
            token(TokenType.IDENTIFIER, "i"),
            token(TokenType.RBRACKET),
            token(TokenType.ASSIGN),
            token(TokenType.IDENTIFIER, "b"),
            token(TokenType.LBRACKET),
            token(TokenType.IDENTIFIER, "j"),
            token(TokenType.RBRACKET),
            token(TokenType.PLUS),
            token(TokenType.IDENTIFIER, "c"),
            token(TokenType.LBRACKET),
            token(TokenType.IDENTIFIER, "k"),
            token(TokenType.RBRACKET));
            
        test("a.b.c = 100",
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.DOT),
            token(TokenType.IDENTIFIER, "b"),
            token(TokenType.DOT),
            token(TokenType.IDENTIFIER, "c"),
            token(TokenType.ASSIGN),
            token(TokenType.NUM, 100.0));
            
        test("a++; b--;",
            token(TokenType.IDENTIFIER, "a"),
            token(TokenType.PLUS_PLUS),
            token(TokenType.SEMICOLON),
            token(TokenType.IDENTIFIER, "b"),
            token(TokenType.MINUS_MINUS),
            token(TokenType.SEMICOLON));

        if (errorNum != 0) {
            Log.info(Ansi.CYAN + "LexerTest: " + errorNum + "个错误" + Ansi.DEFAULT);
        } else {
            Log.info(Ansi.CYAN + "LexerTest: " + "成功" + Ansi.DEFAULT);
        }
    }

    private static void test(String source, RToken... rTokens) {
        test(source, 0, rTokens);
    }

    private static void test(String source, int problemNum, RToken... rTokens) {
        testNum++;
        List<Token> result = new ArrayList<>();
        lexer.reset(manager.loadSourceMap(source));
        while(true) {
            Token token = lexer.scanToken();
            if(token.type != TokenType.EOF) {
                result.add(token);
            } else {
                break;
            }
        }

        if (errorList.size() + warningList.size() != problemNum) {
            errorNum++;
            Log.error(String.format("%s%s\n错误数量不匹配(%d!=%d)%s\n",
                    Ansi.RED,
                    source,
                    errorList.size() + warningList.size(),
                    problemNum,
                    Ansi.DEFAULT
            ));
        }

        if (result.size() != rTokens.length) {
            errorNum++;
            Log.error(String.format("%s%s\ntoken数量不匹配(%d!=%d)%s\n",
                    Ansi.RED,
                    source,
                    result.size(),
                    rTokens.length,
                    Ansi.DEFAULT
            ));
            return;
        }
        for (int i = 0; i < rTokens.length; i++) {
            if (result.get(i).type != rTokens[i].type ||
                    !Objects.equals(result.get(i).literal, rTokens[i].literal)) {
                errorNum++;
                Log.error(String.format("%s%s\n%s与%s不匹配%s\n",
                        Ansi.RED,
                        source,
                        result.get(i).toString(),
                        rTokens[i].toString(),
                        Ansi.DEFAULT
                ));
            }
        }
        lexer.clearProblem();
    }

    private static RToken token(TokenType type, Object literal) {
        return new RToken(type, literal);
    }

    private static RToken token(TokenType type) {
        return new RToken(type, null);
    }

    // RightToken
    private record RToken(TokenType type, Object literal) {
        public String toString() {
            return String.format("RToken{%s,%s}", type.name(), literal);
        }
    }

}