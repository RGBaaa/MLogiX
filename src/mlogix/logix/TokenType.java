package mlogix.logix;

import java.util.*;

public enum TokenType {
    // 关键字
    SET("set"), MACRO("macro"), CONST("const"),
    IF("if"), ELIF("elif"), ELSE("else"),
    WHILE("while"), FOR("for"), BREAK("break"), CONTINUE("continue"),
    STRUCT("struct"),
    MATCH("match"),
    FN("fn"), RETURN("return"),
    ENUM("enum"),

    // 标识符
    // xxx      'xxx
    IDENTIFIER, FLAG,

    // 字面量
    NUM, INT, COL, STRING, TRUE("true"), FALSE("false"), NULL("null"),

    // 运算符
    // +  -      *     /      **         %        %%               //
    PLUS, MINUS, STAR, SLASH, STAR_STAR, PERCENT, PERCENT_PERCENT, SLASH_SLASH,
    //&  |   ^      <<   >>   ~
    AND, OR, CARET, SHL, SHR, TILDE,
    // ++      --
    PLUS_PLUS, MINUS_MINUS,
    // =
    ASSIGN,
    // ==  !=       ===       !==
    EQ_EQ, BANG_EQ, EQ_EQ_EQ, BANG_EQ_EQ,
    // <  >        <=       >=
    LESS, GREATER, LESS_EQ, GREATER_EQ,
    // &&    ||     !
    AND_AND, OR_OR, BANG,
    // ..    ..=
    DOT_DOT, DOT_DOT_EQ,

    // 分隔符
    // ->
    ARROW,
    // :   ;          ,      .
    COLON, SEMICOLON, COMMA, DOT,
    // (    )
    LPAREN, RPAREN,
    // [      ]
    LBRACKET, RBRACKET,
    // {    }
    LBRACE, RBRACE,

    // 文档注释
    DOC_COMMENT,

    // 其他
    NEWLINE, // 换行符
    UNKNOWN,//未知
    ERROR,//错误
    EOF; // 标识源码结尾

    // 方便Lexer判断是否为关键字
    public static final Map<String, TokenType> KEYWORDS_MAP;

    public static final Set<TokenType> LITERALS = EnumSet.of(
            NUM, INT, COL, STRING, TRUE, FALSE, NULL
    );

    public static final Set<TokenType> BINARY_OPERATORS = EnumSet.of(
            PLUS, MINUS, STAR, SLASH, STAR_STAR, PERCENT, PERCENT_PERCENT, SLASH_SLASH,
            AND, OR, CARET, SHL, SHR,
            AND_AND, OR_OR
    );

    public static final Set<TokenType> SEPARATORS = EnumSet.of(
            COLON, SEMICOLON, COMMA, DOT,
            LPAREN, RPAREN,
            LBRACKET, RBRACKET,
            LBRACE, RBRACE
    );

    static {
        Map<String, TokenType> tempMap = new HashMap<>();
        for (TokenType type : TokenType.values()) {
            if (type.keyword != null) {
                tempMap.put(type.keyword, type);
            }
        }
        KEYWORDS_MAP = Collections.unmodifiableMap(tempMap); // 设置为不可修改的 Map
    }

    // 关键字字段
    private final String keyword;

    // 带参数的构造函数（用于关键字）
    TokenType(String keyword) {
        this.keyword = keyword;
    }

    // 无参构造函数
    TokenType() {
        this.keyword = null; // 没有关键字
    }

    public String toString() {
        // 如果有对应的关键字，直接返回
        if (this.keyword != null) {
            return this.keyword;
        }

        // 否则根据枚举值返回对应的符号
        return switch(this) {
            // 运算符
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case STAR_STAR -> "**";
            case PERCENT -> "%";
            case PERCENT_PERCENT -> "%%";
            case SLASH_SLASH -> "//";
            case AND -> "&";
            case OR -> "|";
            case CARET -> "^";
            case SHL -> "<<";
            case SHR -> ">>";
            case TILDE -> "~";
            case PLUS_PLUS -> "++";
            case MINUS_MINUS -> "--";
            case ASSIGN -> "=";
            case EQ_EQ -> "==";
            case BANG_EQ -> "!=";
            case EQ_EQ_EQ -> "===";
            case BANG_EQ_EQ -> "!==";
            case LESS -> "<";
            case GREATER -> ">";
            case LESS_EQ -> "<=";
            case GREATER_EQ -> ">=";
            case AND_AND -> "&&";
            case OR_OR -> "||";
            case BANG -> "!";
            case DOT_DOT -> "..";
            case DOT_DOT_EQ -> "..=";

            // 分隔符
            case ARROW -> "->";
            case COLON -> ":";
            case SEMICOLON -> ";";
            case COMMA -> ",";
            case DOT -> ".";
            case LPAREN -> "(";
            case RPAREN -> ")";
            case LBRACKET -> "[";
            case RBRACKET -> "]";
            case LBRACE -> "{";
            case RBRACE -> "}";

            // 其他
            case NEWLINE -> "\\n";
            case EOF -> "\\0";
            default -> this.name().toLowerCase();
        };
    }
}