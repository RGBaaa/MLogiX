package mlogix.test.compiler;

import java.util.*;

import mlogix.logix.*;
import mlogix.compiler.Lexer;
import mlogix.compiler.struct.SourceMapManager;
import mlogix.logix.Token;
import mlogix.util.*;

import static mlogix.logix.TokenType.*;

public class LexerTest {
    final SourceMapManager manager = new SourceMapManager();
    final Lexer lexer = new Lexer(new ArrayList<>(), new ArrayList<>());
    int testNum = 0;
    int errorNum = 0;

    public void test() {
        Log.info(Ansi.CYAN + "LexerTest: 开始" + Ansi.DEFAULT);

        test("+", token(PLUS));
        test("-", token(MINUS));
        test("*", token(STAR));
        test("/", token(SLASH));
        test("//", token(SLASH_SLASH));
        test("^", token(CARET));
        test("%", token(PERCENT));
        test("&", token(AND));
        test("&&", token(AND_AND));
        test("|", token(OR));
        test("||", token(OR_OR));
        test("~", token(TILDE));
        test("!", token(BANG));
        test("!=", token(BANG_EQ));
        test("!==", token(BANG_EQ_EQ));
        test("<", token(LESS));
        test("<<", token(SHL));
        test("<=", token(LESS_EQ));
        test(">", token(GREATER));
        test(">>", token(SHR));
        test(">=", token(GREATER_EQ));
        test("=", token(ASSIGN));
        test("==", token(EQ_EQ));
        test("===", token(EQ_EQ_EQ));
        test(".", token(DOT));
        test("..", token(DOT_DOT));
        test("..=", token(DOT_DOT_EQ));
        test(":", token(COLON));
        test(";", token(SEMICOLON));
        test(",", token(COMMA));
        test("(", token(LPAREN));
        test(")", token(RPAREN));
        test("[", token(LBRACKET));
        test("]", token(RBRACKET));
        test("{", token(LBRACE));
        test("}", token(RBRACE));

        test("\"hello\"", token(STRING, "hello"));
        test("\"hello\\nworld!\"", token(STRING, "hello\\nworld!"));

        // 错误测试 - 字符串
        test("\"hello"); // 未闭合的字符串
        test("\"hello\nworld\""); // 字符串中包含换行符
        
        // 错误测试 - 未知字符
        test("a $ b"); // 包含未知字符 $
        test("a @ b"); // @ 应该被识别为逻辑关键字开始
        
        // 错误测试 - 数字格式
        test("0x12ZG"); // 非法的十六进制数字
        test("0b102"); // 非法的二进制数字

        // 测试普通数字
        test("27_30", token(NUM, (double) 2730));
        test("2_7.3_0", token(NUM, (double) 27.3));
        test("2_730.", token(NUM, (double) 2730));
        test(".27_30", token(NUM, (double) 0.273));
        
        // 测试科学计数法
        test("1.23e4", token(NUM, 1.23e4));
        test("1.23e+4", token(NUM, 1.23e+4));
        test("1.23e-4", token(NUM, 1.23e-4));
        test("1e4", token(NUM, 1e4));
        
        // 测试十六进制和二进制
        test("0x013_579aBcDeF", token(NUM, (double) 0x013579ABCDEFL));
        test("0b10_1100", token(NUM, (double) 0b101100));
        
        // 错误测试 - 数字格式
        test("_2730"); // 数字不能以分隔符开头
        test("2730_"); // 数字不能以分隔符结尾
        test("1.2.3"); // 多个小数点
        test("1e2e3"); // 多个指数标记
        
        // 测试关键字
        test("set", token(SET));
        test("macro", token(MACRO));
        test("const", token(CONST));
        test("if", token(IF));
        test("elif", token(ELIF));
        test("else", token(ELSE));
        test("while", token(WHILE));
        test("for", token(FOR));
        test("break", token(BREAK));
        test("continue", token(CONTINUE));
        test("struct", token(STRUCT));
        test("match", token(MATCH));
        test("fn", token(FN));
        test("return", token(RETURN));
        test("enum", token(ENUM));
        
        // 测试标识符
        test("identifier", token(IDENTIFIER, "identifier"));
        test("_underscore", token(IDENTIFIER, "_underscore"));
        test("camelCase", token(IDENTIFIER, "camelCase"));
        test("PascalCase", token(IDENTIFIER, "PascalCase"));
        test("snake_case", token(IDENTIFIER, "snake_case"));
        
        // 测试布尔值和null
        test("true", token(TRUE));
        test("false", token(FALSE));
        test("null", token(NULL));
        
        // 测试复合表达式
        test("a + b", 
            token(IDENTIFIER, "a"), 
            token(PLUS), 
            token(IDENTIFIER, "b"));
        test("a += 10", 
            token(IDENTIFIER, "a"), 
            token(PLUS),
            token(ASSIGN),
            token(NUM, 10.0));
        test("fn test() { return 42; }", 
            token(FN),
            token(IDENTIFIER, "test"),
            token(LPAREN),
            token(RPAREN),
            token(LBRACE),
            token(RETURN),
            token(NUM, 42.0),
            token(SEMICOLON),
            token(RBRACE));
        
        // 测试注释
        test("a = 5 # 这是注释", 
            token(IDENTIFIER, "a"), 
            token(ASSIGN), 
            token(NUM, 5.0));
            
        // 测试文档注释
        test("#/ 这是多行文档注释 /#", token(DOC_COMMENT, " 这是多行文档注释 "));
        test("#| 这是行文档注释" +
                " | a = 1" +
                " | b = a",
                token(DOC_COMMENT, " 这是行文档注释" +
                        " a = 1" +
                        " b = a"));
        
        // 测试多行注释（不产生token）
        test("#* 这是多行注释 *#");
        
        // 测试逻辑关键字（以@开头的标识符）
        test("@logic", token(IDENTIFIER, "logic"));
        test("@test123", token(IDENTIFIER, "test123"));
        
        // 测试更复杂的表达式
        test("a[i] = b[j] + c[k]",
            token(IDENTIFIER, "a"),
            token(LBRACKET),
            token(IDENTIFIER, "i"),
            token(RBRACKET),
            token(ASSIGN),
            token(IDENTIFIER, "b"),
            token(LBRACKET),
            token(IDENTIFIER, "j"),
            token(RBRACKET),
            token(PLUS),
            token(IDENTIFIER, "c"),
            token(LBRACKET),
            token(IDENTIFIER, "k"),
            token(RBRACKET));
            
        test("a.b.c = 100",
            token(IDENTIFIER, "a"),
            token(DOT),
            token(IDENTIFIER, "b"),
            token(DOT),
            token(IDENTIFIER, "c"),
            token(ASSIGN),
            token(NUM, 100.0));
            
        test("a++; b--;",
            token(IDENTIFIER, "a"),
            token(PLUS_PLUS),
            token(SEMICOLON),
            token(IDENTIFIER, "b"),
            token(MINUS_MINUS),
            token(SEMICOLON));

        if (errorNum != 0) {
            Log.info(Ansi.CYAN + "LexerTest: " + errorNum + "个错误" + Ansi.DEFAULT);
        } else {
            Log.info(Ansi.CYAN + "LexerTest: " + "成功" + Ansi.DEFAULT);
        }
    }

    private void test(String source, RToken... rTokens) {
        testNum++;
        List<Token> result = new ArrayList<>();
        lexer.reset(manager.loadSourceMap(source));
        while(true) {
            Token token = lexer.scanToken();
            if(token.type != EOF) {
                result.add(token);
            } else {
                break;
            }
        }
        lexer.clearIssue();

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
    }

    private RToken token(TokenType type, Object literal) {
        return new RToken(type, literal);
    }

    private RToken token(TokenType type) {
        return new RToken(type, null);
    }

    // RightToken
    private record RToken(TokenType type, Object literal) {
        public String toString() {
            return String.format("RToken{%s,%s}", type.name(), literal);
        }
    }

}