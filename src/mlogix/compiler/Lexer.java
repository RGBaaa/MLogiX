package mlogix.compiler;

import arc.graphics.Color;
import mlogix.mlogix.token.Token;
import mlogix.mlogix.token.TokenType;
import mlogix.problem.Problem;
import mlogix.problem.ProblemCollector;
import mlogix.span.Span;
import mlogix.compiler.SourceMapManager.SourceMap;
import mlogix.util.Ansi;
import mlogix.util.Log;

import java.util.Set;
import java.util.function.Predicate;

import static mlogix.mlogix.token.TokenType.*;
import static mlogix.problem.Problem.LexerProblem;

public class Lexer {
    private final ProblemCollector collector;
    private SourceMap sourceMap;
    private int length;
    private int start;
    private int current;

    private boolean lastIsNewline;

    /**
     * 一个项目 构造一次
     */
    public Lexer(ProblemCollector collector) {
        this.collector = collector;
    }

    /**
     * 一个文件 重置一次
     * 重置后才能调用其他方法
     */
    public void reset(SourceMap sourceMap) {
        this.sourceMap = sourceMap;
        this.length = sourceMap.length();

        this.lastIsNewline = false;

        this.start = 0;
        this.current = 0;
    }

    /**
     * 扫描下一个Token
     */
    public Token scanToken() {
        while(true) {
            start = current;

            if(isAtEnd()) return eofToken();
            if(lastIsNewline) {
                lastIsNewline = false;
                recover(c -> c != '\n'); // 跳过newline防止重复出现
                if(isAtEnd()) return eofToken();
                start = current;
            }

            char c = advance();
            switch(c) {
                case '+':
                    if(match('+')) {
                        return token(PLUS_PLUS);
                    } else {
                        return token(PLUS);
                    }
                case '-':
                    if(match('-')) {
                        return token(MINUS_MINUS);
                    } else if(match('>')) {
                        return token(ARROW);
                    } else {
                        return token(MINUS);
                    }
                case '*':
                    if(match('*')) {
                        return token(STAR_STAR);
                    } else {
                        return token(STAR);
                    }
                case '/':
                    if(match('/')) {
                        return token(SLASH_SLASH);
                    } else {
                        return token(SLASH);
                    }
                case '^':
                    return token(CARET);
                case '%':
                    if(match('%')) {
                        return token(PERCENT_PERCENT);
                    } else {
                        return token(PERCENT);
                    }
                case '&':
                    if(match('&')) {
                        return token(AND_AND);
                    } else {
                        return token(AND);
                    }
                case '|':
                    if(match('|')) {
                        return token(OR_OR);
                    } else {
                        return token(OR);
                    }
                case '~':
                    return token(TILDE);
                case '!':
                    if(match('=')) {
                        if(match('=')) {
                            return token(BANG_EQ_EQ);
                        } else {
                            return token(BANG_EQ);
                        }
                    } else {
                        return token(BANG);
                    }
                case '<':
                    if(match('<')) {
                        return token(SHL);
                    } else if(match('=')) {
                        return token(LESS_EQ);
                    } else {
                        return token(LESS);
                    }
                case '>':
                    if(match('>')) {
                        return token(SHR);
                    } else if(match('=')) {
                        return token(GREATER_EQ);
                    } else {
                        return token(GREATER);
                    }
                case '=':
                    if(match('=')) {
                        if(match('=')) {
                            return token(EQ_EQ_EQ);
                        } else {
                            return token(EQ_EQ);
                        }
                    } else {
                        return token(ASSIGN);
                    }
                case '.':
                    return token(DOT);
                case ':':
                    if(match('<')) {
                        return token(COLON_LESS);
                    } else if(match('=')) {
                        return token(COLON_ASSIGN);
                    } else {
                        return token(COLON);
                    }
                case ';':
                    return token(SEMICOLON);
                case ',':
                    return token(COMMA);
                case '(':
                    return token(LPAREN);
                case ')':
                    return token(RPAREN);
                case '[':
                    return token(LBRACKET);
                case ']':
                    return token(RBRACKET);
                case '{':
                    return token(LBRACE);
                case '}':
                    return token(RBRACE);
                case '?':
                    return token(QUESTION_MARK);

                case '"':
                    return string();

                case '“': // 中文引号
                    warning("字符串应该使用英文双引号`\"`")
                            .point(start, start + 1, "\"");
                    return string();

                case '#': {
                    Token r = comment();
                    if(r != null) {
                        return r;
                    } else {
                        continue;
                    }
                }

                case '@':
                    return logicKeyword();

                case '\n':
                case '\r': {
                    lastIsNewline = true;
                    return token(NEWLINE);
                }

                case ' ':
                case '\t':
                    continue;

                case '\0':
                    return token(EOF);

                default:
                    if(isDigit(c)) {
                        return number();
                    } else if(isIdentifierStart(c)) {
                        return identifier();
                    } else {
                        error("未知的字符")
                                .point(start, start + 1, Integer.toHexString(c));
                        recover(ch -> Set.of(':', ';', ',', '.', ' ', '\n',
                                '(', ')',
                                '[', ']',
                                '{', '}'
                        ).contains(ch));
                        return token(ERROR, subString(start, current));
                    }
            }
        }
    }

    /* 标识符 关键字 */
    private Token identifier() {
        while(!isAtEnd() && isIdentifierPart(peek())) advance();
        String text = subString(start, current);
        if(text.startsWith("__")) {
            error("非法的标识符")
                    .point(start, start + 2, "不能以`__`开头，将被替换为`_`");
            return token(IDENTIFIER, text.substring(1));
        }
        TokenType type = KEYWORDS_MAP.get(text);
        if(type == null) {
            return token(IDENTIFIER, text);
        } else {
            return token(type);
        }
    }

    /* 逻辑关键字 */
    // TODO 舍弃
    private Token logicKeyword() {
        while(!isAtEnd() && (isAlpha(peek()) || isDigit(peek()))) advance();
        String text = subString(start + 1, current);
        return token(IDENTIFIER, text);
    }

    private Token string() {
        while(!match('"')) {
            if(isAtEnd() || check('\n')) {
                error("未匹配到字符串末尾`\"`")
                        .info(start, start + 1, "字符串头部")
                        .point(current, current + 1, "匹配末尾");
                return token(STR, subString(start + 1, current));
            }
            if(match('”')) {
                warning("字符串应该使用英文双引号`\"`")
                        .point(start, start + 1, "\"");
                break;
            }
            advance();
        }
        return token(STR, subString(start + 1, current - 1));
    }

    private Token number() {
        // 16进制整数 _分隔符
        if(match('x')) {
            if(peek(-2) != '0') {
                error("16进制数字应以`0x`开头")
                        .point(start, start + 2, "");
                recover(c -> !isDigit(c) && !isAlpha(c));
                return token(ERROR);
            }
            StringBuilder builder = new StringBuilder();
            while(!isAtEnd()) {
                if(isHexDigit(peek())) {
                    builder.append(peek());
                } else if(check('_')) {
                    // 忽略数字分隔符
                } else if(isAlpha(peek())) {
                    // ['g' ~ 'z'] | ['G' ~ 'Z']
                    error("16进制数字不包括的字符")
                            .point(current, current + 1, Integer.toHexString(peek()));
                    recover(c -> !isDigit(c) && !isAlpha(c));
                    return token(ERROR);
                } else {
                    break;
                }
                advance();
            }
            return token(INT, (double) Long.parseLong(builder.toString(), 16));

            // 2进制整数 _分隔符
        } else if(match('b')) {
            if(peek(-2) != '0') {
                error("2进制数字应以`0b`开头")
                        .point(start, start + 2, "");
                recover(c -> !isDigit(c) && !isAlpha(c));
                return token(ERROR);
            }
            StringBuilder builder = new StringBuilder();
            while(!isAtEnd()) {
                if(isBinDigit(peek())) {
                    builder.append(peek());
                } else if(check('_')) {
                    // 忽略数字分隔符
                } else if(isAlpha(peek())) {
                    error("2进制数字不包括的字符")
                            .point(current, current + 1, Integer.toHexString(peek()));
                    recover(c -> !isDigit(c) && !isAlpha(c));
                    return token(ERROR);
                } else {
                    break;
                }
                advance();
            }
            return token(INT, (double) Long.parseLong(builder.toString(), 2));

            // 颜色值
            // TODO 以内置颜色名称索引 0cRED
        } else if(match('c')) {
            if(peek(-2) != '0') {
                error("颜色值应以`0c`开头")
                        .point(start, start + 2, "");
                recover(c -> !isDigit(c) && !isAlpha(c));
                return token(ERROR);
            }
            StringBuilder builder = new StringBuilder();
            while(!isAtEnd()) {
                if(isHexDigit(peek())) {
                    builder.append(peek());
                } else if(check('_')) {
                    // 忽略数字分隔符
                } else if(isAlpha(peek())) {
                    // ['g' ~ 'z'] | ['G' ~ 'Z']
                    error("16进制数字不包括的字符")
                            .point(current, current + 1, Integer.toHexString(peek()));
                    recover(c -> !isDigit(c) && !isAlpha(c));
                    return token(ERROR);
                } else {
                    break;
                }
                advance();
            }

            if(builder.length() == 6) { // 0cRR_GG_BB
                int r = Integer.valueOf(builder.substring(2, 4), 16);
                int g = Integer.valueOf(builder.substring(4, 6), 16);
                int b = Integer.valueOf(builder.substring(6, 8), 16);
                int a = 0xFF;
                return token(COL, Color.toDoubleBits(r, g, b, a));
            } else if(builder.length() == 8) { // 0cRR_GG_BB_AA
                int r = Integer.valueOf(builder.substring(2, 4), 16);
                int g = Integer.valueOf(builder.substring(4, 6), 16);
                int b = Integer.valueOf(builder.substring(6, 8), 16);
                int a = Integer.valueOf(builder.substring(8, 10), 16);
                return token(COL, Color.toDoubleBits(r, g, b, a));
            } else {
                error("`颜色值`长度应为6或8")
                        .point(start, current, "长度 = " + (builder.length() - 2));
                return token(ERROR);
            }


            // 普通浮点数 科学计数法 _分隔符
        } else {
            boolean isInt = true;

            StringBuilder builder = new StringBuilder();
            builder.append(peek(-1));

            while(!isAtEnd() && peek() == '_') advance();//防止normalNumber报错
            normalNumber(builder);

            if(match('.')) {
                isInt = false;

                builder.append('.');
                normalNumber(builder);
            }

            if(match('e') || match('E')) {
                isInt = false;

                builder.append('e');
                if(match('+')) {
                    builder.append('+');
                } else if(match('-')) {
                    builder.append('-');
                }

                normalNumber(builder);

                //TODO 是否移除
                if(match('.')) {
                    builder.append('.');
                    normalNumber(builder);
                }
            }

            return token(isInt ? INT : NUM, Double.parseDouble(builder.toString()));
        }
    }

    /* 给number()用的，扫描下一段最普通的数字，123_456，不允许两侧分隔符 */
    private void normalNumber(StringBuilder builder) {
        if(isAtEnd()) return;
        if(peek() == '_') {
            error("数字两端不允许分隔符`_`")
                    .point(current, current + 1, Integer.toHexString(peek()));
        }

        while(!isAtEnd()) {
            if(isDigit(peek())) {
                builder.append(peek());
            } else if(check('_')) {
                //忽略分隔符
            } else if(isAlpha(peek())) {
                error("不期望的字符")
                        .point(current, current + 1, Integer.toHexString(peek()));
                recover(c -> !isDigit(c) && !isAlpha(c));
                break;
            } else {
                break;
            }
            advance();
        }

        if(builder.charAt(builder.length() - 1) == '_') {
            error("数字两端不允许分隔符`_`")
                    .point(current, current + 1, Integer.toHexString(peek()));
        }
    }

    private Token comment() {
        StringBuilder docComment = new StringBuilder();

        if(match('/')) { // 多行文档注释开始 #/
            while(!isAtEnd()) {
                if(match("/#")) { // 多行文档注释结束 #/ ... /#
                    return token(DOC_COMMENT, docComment.toString());
                }
                docComment.append(advance());
            }
            return token(DOC_COMMENT, docComment.toString());
        } else if(match('|')) { // 文档注释开始 #|
            // int col = sourceMap.getCol(current - 1) - 1;
            while(!isAtEnd()) {
                if(match('\n')) {
                    while(!isAtEnd() && isWhitespace(peek())) {
                        advance();
                    }
                    if(!match('|')) { // 文档注释结束 #| ...\n |
                        return token(DOC_COMMENT, docComment.toString());
                    }
                    docComment.append('\n');
                }
                docComment.append(advance());
            }
            return token(DOC_COMMENT, docComment.toString());
        } else if(match('*')) { // 多行注释开始 #*
            while(!isAtEnd() && !match("*#")) { // #* ... *#
                advance();
            }
            return null;
        } else { // 单行注释 # ...
            while(!isAtEnd() && !match('\n')) {
                advance();
            }
            return null;
        }
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c == '_');
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isHexDigit(char c) {
        return isDigit(c)
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private boolean isBinDigit(char c) {
        return c == '0' || c == '1';
    }

    // 标识符开头
    private boolean isIdentifierStart(char c) {
        return isAlpha(c)
                || Character.isLetter(c);
    }

    // 标识符中间和末尾
    private boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }

    private boolean isWhitespace(char c) {
        return Character.isWhitespace(c);
    }

    private char charAt(int index) {
        return sourceMap.charAt(index);
    }

    private boolean match(char expected) {
        if(isAtEnd()) return false;
        if(charAt(current) != expected) return false;
        current++;
        return true;
    }

    /* 检查当前往后的字符串是否是期望字符串，是则推进至该字符串后1 */
    private boolean match(String expected) {
        if(isAtEnd()) return false;
        if(expected.equals(subString(current, Math.min(current + expected.length(), length)))) {
            advance(expected.length());
            return true;
        }
        return false;
    }


    private boolean check(char expected) {
        if(isAtEnd()) return false;
        return charAt(current) == expected;
    }

    private boolean isAtEnd() {
        return current >= length;
    }

    private char advance() {
        current++;
        return charAt(current - 1);
    }

    private char advance(int step) {
        current += step;
        return charAt(current - step);
    }

    private char peek() {
        return charAt(current);
    }

    private char peek(int step) {
        return charAt(current + step);
    }

    private String subString(int start, int end) {
        return sourceMap.subString(start, end);
    }

    private Token token(TokenType type) {
        return token(type, null);
    }

    private Token token(TokenType type, Object literal) {
        Span span = new Span(sourceMap.index, start, current);

        if(Log.isAllowed(Log.LogType.DEBUG)) {
            int[] lineAndCol = sourceMap.getLineAndCol(start);
            Log.debug(start + Ansi.CYAN + "┃"
                    + Ansi.DEFAULT + "(" + lineAndCol[0] + "," + lineAndCol[1] + ")" + Ansi.CYAN + "┃"
                    + Ansi.DEFAULT + type.name() + Ansi.CYAN + "┃"
                    + Ansi.DEFAULT + sourceMap.subString(start, current) + ((literal == null) ? "" : Ansi.CYAN + "┃"
                    + Ansi.DEFAULT + literal));
        }

        return new Token(span, type, literal);
    }

    // EOF特化
    private Token eofToken() {
        // 特化部分:current -> start + 1
        Span span = new Span(sourceMap.index, start, start + 1);

        if(Log.isAllowed(Log.LogType.DEBUG)) {
            int[] lineAndCol = sourceMap.getLineAndCol(start);
            Log.debug(start + Ansi.CYAN + "┃"
                    + Ansi.DEFAULT + "(" + lineAndCol[0] + "," + lineAndCol[1] + ")" + Ansi.CYAN + "┃"
                    + Ansi.DEFAULT + EOF.name() + Ansi.CYAN + "┃"
                    + Ansi.DEFAULT);
        }

        return new Token(span, EOF, null);
    }

    /**
     * 错误恢复，扫描直到期望的字符
     */
    private void recover(char... expectedChars) {
        while(!isAtEnd()) {
            for(char expected : expectedChars) {
                if(check(expected)) {
                    return;
                }
            }
            advance();
        }
    }

    /**
     * 错误恢复，扫描直到期望的字符
     * @param predicate 满足该条件则退出
     */
    private void recover(Predicate<Character> predicate) {
        while(!isAtEnd()) {
            if(predicate.test(peek())) {
                return;
            }
            advance();
        }
    }

    private LexerProblem error(String text) {
        LexerProblem e = new LexerProblem(sourceMap, text, Problem.ProblemLevel.ERROR);
        collector.addError(e);
        return e;
    }

    private LexerProblem warning(String text) {
        LexerProblem w = new LexerProblem(sourceMap, text, Problem.ProblemLevel.WARNING);
        collector.addWarning(w);
        return w;
    }

//    public record LexerResult(List<LexerProblem> errorList, List<LexerProblem> warningList) {}
}