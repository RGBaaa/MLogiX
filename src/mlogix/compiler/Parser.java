package mlogix.compiler;

import mlogix.compiler.issue.*;
import mlogix.compiler.issue.Issue.*;
import mlogix.compiler.struct.SourceMapManager.*;
import mlogix.compiler.struct.*;
import mlogix.logix.*;

import java.util.*;

import static mlogix.logix.Expr.*;
import static mlogix.logix.Stmt.*;
import static mlogix.logix.TokenType.*;

public class Parser {
    private final Lexer lexer;
    private final SourceMap sourceMap;

    private final List<Issue> errorList;
    private final List<Issue> warningList;

    // 作为前瞻缓冲 必须通过工具方法访问
    private Token nextToken = null;

    Parser(Lexer lexer, SourceMap sourceMap, List<Issue> errorList, List<Issue> warningList) {
        this.lexer = lexer;
        this.sourceMap = sourceMap;
        this.errorList = errorList;
        this.warningList = warningList;
    }

    public ASTNode parse() {
        Stmt program = program();

        return program;
    }

    //########################################
    private Stmt program() {
        List<Stmt> stmts = new ArrayList<>();

        while(!isAtEnd()) {
            try {
                stmts.add(statement());
            } catch(ParserIssue e) {
                normalRecover(); // 有用吗
            }
        }

        return new Program(span(0, sourceMap.length()), stmts);
    }

    private Stmt statement() {
        if(check(IF)) return ifStmt();

        if(check(FOR)) return forStmt();
        if(check(WHILE)) return whileStmt();

        if(check(FN)) return functionStmt();

        //if (match(MATCH)) return matchStmt();
        if(check(LBRACE)) return block();
        return exprStmt();
    }

    private Stmt block() {
        Token lBrace = next();

        List<Stmt> stmts = new ArrayList<>();
        while(!check(RBRACE)) {
            if(isAtEnd()) {
                if(expect(RBRACE) == null) {
                    return new Block(span(lBrace, stmts.isEmpty() ? lBrace.span.end()
                            : stmts.get(stmts.size() - 1).span.end()), stmts);
                }
                break;
            }
            stmts.add(statement());
        }
        Token rbrace = next();

        return new Block(span(lBrace, rbrace), stmts);
    }

    private Stmt ifStmt() {
        Token start = next();

        Expr condition = expression();

        expect(LBRACE);
        Stmt thenBranch = block();

        Stmt elseBranch = null;
        if(match(ELIF)) {
            elseBranch = ifStmt();
        } else if(match(ELSE)) {
            expect(LBRACE);
            elseBranch = block();
        }

        int end = elseBranch == null ? thenBranch.span.end() : elseBranch.span.end();

        return new IfStmt(span(start, end), condition, thenBranch, elseBranch);
    }

    private Stmt forStmt() {
        Token start = next();

        Identifier var = null;

        if(check(IDENTIFIER)) {
            var = new Identifier(next());
            if(check("in")) {
                next();
                Expr expr = expression();

                expect(LBRACE);
                Stmt body = block();

                return new ForStmt(span(start, body.span.end()), var, expr, body);
            }
            expect(LBRACE);
            Stmt body = block();

            return new ForStmt(span(start, body.span.end()), var, null, body);
        }
        Expr expr = expression();

        expect(LBRACE);
        Stmt body = block();

        return new ForStmt(span(start, body.span.end()), var, expr, body);
    }

    private Stmt whileStmt() {
        Token start = next();

        Expr expr = expression();

        expect(LBRACE);
        Stmt body = block();

        int end = body.span.end();

        return new WhileStmt(span(start, end), expr, body);
    }

    private Stmt functionStmt() {
        Token start = next();

        Token name = consume(IDENTIFIER);
        consume(LPAREN);

        List<Expr> parameters = new ArrayList<>();
        while(!match(RPAREN)) {
            if(isAtEnd()) {
                throw error("无法结束的`函数形参声明`")
                        .info(start, "函数声明开头")
                        .point(lookAhead(), "末尾");
            }
            // TODO var : type
            parameters.add(expression());
            if(!match(COMMA)) {
                consume(RPAREN);
                break;
            }
        }

        List<Expr> results = new ArrayList<>();
        if(check(ARROW)) {
            Token arrow = next();
            while(!check(LBRACE)) {
                if(isAtEnd()) {
                    if(results.isEmpty()) {
                        throw error("无法找到`函数返回值声明`")
                                .info(start, "函数开头")
                                .point(lookAhead(), "期望`标识符`");
                    } else {
                        throw error("无法找到函数体")
                                .info(start, "函数开头")
                                .point(lookAhead(), "期望`{`");
                    }
                }
                try {
                    results.add(expression());
                } catch(ParserIssue e) {
                    e.info(arrow, "解析`函数返回值声明`时出现错误");
                    normalRecover();
                    break;
                }
                match(COMMA); // 可选逗号
            }
        }
        expect(LBRACE);
        Stmt body = block();

        int end = body.span.end();

        return new FnStmt(span(start, end), name, parameters, results, body);
    }

    private Stmt exprStmt() {
        if(check(BREAK)) {
            Token start = next();
            Token end;
            try {
                end = consumeStmtEnd();
            } catch(ParserIssue e) {
                e.info(start, "解析`break`时出错");
                end = start;
            }
            return new BreakStmt(span(start, end));

        } else if(check(CONTINUE)) {
            Token start = next();
            Token end;
            try {
                end = consumeStmtEnd();
            } catch(ParserIssue e) {
                e.info(start, "解析`continue`时出错");
                end = start;
            }
            return new ContinueStmt(span(start, end));
        } else if(check(RETURN)) {
            Token start = next();
            Token end;
            if(check(SEMICOLON)) {
                end = next();
                return new ReturnStmt(span(start, end), null);
            }

            Expr expr;
            try {
                expr = expression();
            } catch(ParserIssue e) {
                e.info(start, "解析`return`语句时出错");
                expr = new Literal(token(ERROR, lookAhead()));
            }

            try {
                end = consumeStmtEnd();
            } catch(ParserIssue e) {
                e.info(start, "解析`return`语句时出错");
                end = lookAhead();
            }
            return new ReturnStmt(span(start, end), expr);
        } else if(check(SET)) {
            Token start = next();
            Expr var;
            try {
                var = expression();
            } catch(ParserIssue e) {
                e.info(start, "解析`set`变量时出错");
                var = new Identifier(token(ERROR, lookAhead()));
            }

            Stmt assignStmt;
            try {
                assignStmt = assignStmt(var);
            } catch(ParserIssue e) {
                e.info(start, "解析`set`赋值语句时出错");
                assignStmt = null;
            }

            if(assignStmt == null) {
                int end;
                try {
                    end = consumeStmtEnd().span.end();
                } catch(ParserIssue e) {
                    e.info(start, "解析`break`时出错");
                    end = var.span.end();
                }
                return new SetVarStmt(span(start, end), var, null);
            } else {
                return new SetVarStmt(span(start, assignStmt.span.end()), var, assignStmt);
            }
        } else {
            Token start = lookAhead();
            Expr var;
            try {
                var = expression();
            } catch(ParserIssue e) {
                e.info(start, "解析表达式时出错");
                var = new Literal(token(ERROR, lookAhead()));
            }

            Stmt assignStmt;
            try {
                assignStmt = assignStmt(var);
            } catch(ParserIssue e) {
                e.info(start, "解析`赋值语句`时出错");
                assignStmt = null;
            }

            if(assignStmt == null) {
                int end;
                try {
                    end = consumeStmtEnd().span.end();
                } catch(ParserIssue e) {
                    e.info(start, "解析`赋值语句`时出错");
                    end = var.span.end();
                }
                return new ExprStmt(span(start, end), var);
            } else {
                return assignStmt;
            }
        }
    }

    private Stmt assignStmt(Expr expr) {
        if(check(ASSIGN)) {
            Token operator = next();
            Expr value;
            try {
                value = expression();
            } catch(ParserIssue e) {
                e.info(operator, "解析赋值表达式时出错");
                value = new Literal(token(ERROR, lookAhead()));
            }

            Token end;
            try {
                end = consumeStmtEnd();
            } catch(ParserIssue e) {
                e.info(operator, "解析赋值语句时出错");
                end = lookAhead();
            }
            return new AssignStmt(span(expr.span.start(), end.span.end()), expr, operator, value);

        } else if(check(BINARY_OPERATORS)) {
            Token operator = next();
            if(check(ASSIGN)) {
                Token assignOp = next();
                Expr value;
                try {
                    value = expression();
                } catch(ParserIssue e) {
                    e.info(operator, "解析复合赋值表达式时出错");
                    value = new Literal(token(ERROR, lookAhead()));
                }

                Token end;
                try {
                    end = consumeStmtEnd();
                } catch(ParserIssue e) {
                    e.info(operator, "解析复合赋值语句时出错");
                    end = lookAhead();
                }
                return new AssignStmt(span(expr.span.start(), end.span.end()), expr, operator, value);
            }
            Expr right;
            try {
                right = expression();
            } catch(ParserIssue e) {
                e.info(operator, "解析二元表达式时出错");
                right = new Literal(token(ERROR, lookAhead()));
            }
            return new ExprStmt(span(expr.span.start(), right.span.end()), new Binary(expr, operator, right));
        }
        return null;
    }

    private Expr expression() {
        return or();
    }

    private Expr or() {
        Expr expr = and();

        while(!isStmtEnd() && check(OR_OR)) {
            Token operator = next();
            Token lparen = lookAhead();
            Expr right = or();
            if(lparen.type != LPAREN && right instanceof Binary && ((Binary) right).operator.type == AND_AND) {
                throw error("不明确关系的逻辑运算表达式，请添加括号")
                        .point(expr.span.start(), right.span.end(), "");
            }
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while(!isStmtEnd() && check(AND_AND)) {
            Token operator = next();
            Token lparen = lookAhead();
            Expr right = or();
            if(lparen.type != LPAREN && right instanceof Binary && ((Binary) right).operator.type == OR_OR) {
                throw error("不明确关系的逻辑运算表达式，请添加括号")
                        .point(expr.span.start(), right.span.end(), "");
            }
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     *  == !=
     */
    private Expr equality() {
        Expr expr = comparison();

        while(!isStmtEnd() && check(EQ_EQ, BANG_EQ)) {
            Token operator = next();
            Expr right = comparison();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }


    /**
     *  > >= < <=
     */
    private Expr comparison() {
        Expr expr = range();

        while(!isStmtEnd() && check(GREATER, GREATER_EQ, LESS, LESS_EQ)) {
            Token operator = next();
            Expr right = range();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr range() {
        if(!isStmtEnd() && check(DOT_DOT, DOT_DOT_EQ)) { // .. expr?
            Token operator = next();

            if(!check(LITERALS) && !check(IDENTIFIER) && !check(LPAREN)) { // ..
                new Range(span(operator, operator), null, operator, null);
            }

            Expr right;
            try {
                right = addAndSub();
            } catch(ParserIssue e) {
                e.info(operator, "解析`范围表达式`时出现错误");
                right = new Literal(token(ERROR, lookAhead()));
            }
            // .. expr
            new Range(span(operator, right.span.end()), null, operator, right);
        }

        Expr expr = addAndSub();

        if(!isStmtEnd() && check(DOT_DOT, DOT_DOT_EQ)) { // expr .. expr?
            Token operator = next();

            if(!check(LITERALS) && !check(IDENTIFIER) && !check(LPAREN)) { // expr ..
                new Range(span(operator, operator), null, operator, null);
            }

            Expr right;
            try {
                right = addAndSub();
            } catch(ParserIssue e) {
                e.info(operator, "解析`范围表达式`时出现错误");
                right = new Literal(token(ERROR, lookAhead()));
            }
            // expr .. expr
            expr = new Range(Span.between(expr.span, right.span), expr, operator, right);
        }

        return expr;
    }

    private Expr addAndSub() {
        Expr expr = mulAndDiv();

        while(!isStmtEnd() && check(PLUS, MINUS)) {
            Token operator = next();
            Expr right = mulAndDiv();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr mulAndDiv() {
        Expr expr = unary();

        while(!isStmtEnd() && check(STAR, SLASH)) {
            Token operator = next();
            Expr right = unary();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if(!isStmtEnd() && check(BANG, MINUS)) {
            Token operator = next();
            Expr right = unary();
            return new Unary(operator, right);
        }

        return listAndClassStmt();
    }

    private Expr listAndClassStmt() {
        Expr expr = primary();

        while(true) {
            if(isStmtEnd()) return expr;

            List<Expr> arguments = new ArrayList<>();

            if(check(LBRACKET)) {//对列表的索引或切片
                Token lBracket = next();

                Expr index;
                try {
                    index = expression();
                } catch(ParserIssue e) {
                    e.info(lBracket, "解析`数组索引`时出现错误");
                    index = new Literal(token(ERROR, lookAhead()));
                    ;
                }

                Token rBracket;
                try {
                    rBracket = consume(RBRACKET);
                } catch(ParserIssue e) {
                    e.info(lBracket, "解析`数组索引`时出现错误");
                    rBracket = null;
                }
                if(rBracket != null) {
                    expr = new Index(span(lBracket, rBracket), expr, index);
                } else {
                    expr = new Index(span(lBracket, index.span.end()), expr, index);
                }
                continue;

            } else if(check(LPAREN)) {//函数调用
                Token lParen = next();
                while(!check(RPAREN)) {
                    if(isAtEnd()) {
                        throw error("无法结束的`函数传参`")
                                .info(lParen, "参数开头")
                                .point(lookAhead(), "末尾");
                    }
                    try {
                        arguments.add(expression());
                    } catch(ParserIssue e) {
                        e.info(lParen, "解析`函数调用`时出现错误");
                    }
                }
                Token rParen = next();
                expr = new Call(span(lParen, rParen), expr, arguments);
                continue;

            } else if(check(DOT)) {//访问类的元素
                Token dot = next();
                Expr field;
                try {
                    field = new Identifier(consume(IDENTIFIER));
                } catch(ParserIssue e) {
                    e.info(dot, "解析`类元素访问`时出现错误");
                    field = new Identifier(token(ERROR, lookAhead()));
                }
                expr = new Get(expr, field);
                continue;
            }

            return expr;
        }
    }

    // TODO 添加新语法 var:type1 type2
    private Expr primary() {
        if(check(LITERALS)) {
            Token literal = next();
            List<Expr> type = new ArrayList<>();
            if(!isStmtEnd() && check(COLON)) {
                Token colon = next();
                while(!isAtEnd() && !isStmtEnd()) {
                    try {
                        type.add(primary());
                    } catch(ParserIssue e) {
                        e.info(colon, "解析`类型声明`时出现错误");
                    }
                }
                return new Literal(literal);
            }
            return new Literal(literal);

        } else if(check(IDENTIFIER)) {
            Token id = next();
            List<Expr> type = new ArrayList<>();
            if(!isStmtEnd() && check(COLON)) {
                Token colon = next();
                while(!isAtEnd()) {
                    try {
                        type.add(primary());
                    } catch(ParserIssue e) {
                        e.info(colon, "解析`类型声明`时出现错误");
                    }
                }
                return new Identifier(id);
            }
            return new Identifier(id);

        } else if(check(LPAREN)) {
            Token lParen = next();
            Expr expr = null;
            try {
                expr = expression();
            } catch(ParserIssue e) {
                e.info(lParen, "找不到括号内的表达式");
                expr = new Literal(token(ERROR, lookAhead()));
            }
            try {
                consume(RPAREN);
            } catch(ParserIssue e) {
                e.info(lParen, "解析`括号内表达式`时出现错误");
            }
            return expr;
        } else if(check(LBRACE)) {
            Token lBrace = next();
            List<Expr> elements = new ArrayList<>();
            while(!check(RBRACE)) {
                if(isAtEnd()) {
                    throw error("无法结束的数组")
                            .info(lBrace, "数组开头")
                            .point(lookAhead(), "末尾");
                }
                try {
                    elements.add(expression());
                } catch(ParserIssue e) {
                    e.info(lBrace, "解析`数组`时出现错误");
                    elements.add(new Literal(token(ERROR, lookAhead())));
                }
                match(COMMA); // 可选逗号
            }
            Token rBrace = next();
            return new Array(span(lBrace, rBrace), elements);
        }

        var error = error("期望表达式").point(lookAhead(), "");
        normalRecover();
        throw error;
    }

    //########################################
    private boolean isAtEnd() {
        TokenType nextType = lookAhead().type;
        if(nextType == NEWLINE) {
            next();
            nextType = lookAhead().type; // 第二个不会是NEWLINE
        }
        return nextType == EOF;
    }

    /**
     * 向前推进一个token
     */
    private Token next() {
        if(nextToken != null) {
            Token temp = nextToken;
            nextToken = null;
            return temp;
        }
        return lexer.scanToken();
    }

    /**
     * 前瞻下一个token
     */
    private Token lookAhead() {
        if(nextToken != null) {
            return nextToken;
        }
        nextToken = lexer.scanToken();
        return nextToken;
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(TokenType type) {
        TokenType nextType = lookAhead().type;
        if(nextType == ERROR) return true; // 忽略Lexer传来的错误Token
        if(nextType == NEWLINE) {
            next();
            nextType = lookAhead().type; // 第二个不会是NEWLINE
        }
        return nextType == type;
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(Set<TokenType> types) {
        TokenType nextType = lookAhead().type;
        if(nextType == ERROR) return true; // 忽略Lexer传来的错误Token
        if(nextType == NEWLINE) {
            next();
            nextType = lookAhead().type; // 第二个不会是NEWLINE
        }
        return types.contains(nextType);
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(String text) {
        Token next = lookAhead();
        if(next.type == ERROR) return true; // 忽略Lexer传来的错误Token
        if(next.type == NEWLINE) {
            next();
            next = lookAhead(); // 第二个不会是NEWLINE
        }
        return text.equals(next.literal);
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(TokenType... types) {
        TokenType nextType = lookAhead().type;
        if(nextType == ERROR) return true; // 忽略Lexer传来的错误Token
        if(nextType == NEWLINE) {
            next();
            // 第二个不会是NEWLINE
            nextType = lookAhead().type;
        }
        for(TokenType expected : types) {
            if(nextType == expected) return true;
        }
        return false;
    }

    private boolean match(TokenType type) {
        if(check(type)) {
            next();
            return true;
        }
        return false;
    }

    private boolean match(Set<TokenType> types) {
        if(check(types)) {
            next();
            return true;
        }
        return false;
    }

    private boolean match(TokenType... types) {
        if(check(types)) {
            next();
            return true;
        }
        return false;
    }

    private boolean match(String... texts) {
        for(String text : texts) {
            if(check(text)) {
                next();
                return true;
            }
        }
        return false;
    }

    //########################################
    private Token consumeStmtEnd() {
        // 检查 ; \n EOF 作为语句结束符
        TokenType peekType = lookAhead().type;
        if(peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF) {
            return next();
        }
        // 检查 { } 作为语句结束符
        if(peekType == LBRACE || peekType == RBRACE) {
            return lookAhead();
        }
        // 如果没有找到，抛出错误
        throw error("缺少换行或分号作为语句结束符")
                .point(lookAhead(), "");
    }

    private boolean isStmtEnd() {
        TokenType peekType = lookAhead().type;
        return peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF;
    }

    private Token token(TokenType type, Token from) {
        return new Token(type, from.span);
    }

    /**
     * 生成当前文件的span
     * @param from 起始
     * @param to 末尾
     */
    private Span span(Token from, Token to) {
        return new Span(sourceMap.index, from.span.start(), to.span.end());
    }

    /**
     * 生成当前文件的span
     * @param from 起始
     * @param to 末尾
     */
    private Span span(int from, Token to) {
        return new Span(sourceMap.index, from, to.span.end());
    }

    /**
     * 生成当前文件的span
     * @param from 起始
     * @param to 末尾
     */
    private Span span(Token from, int to) {
        return new Span(sourceMap.index, from.span.start(), to);
    }

    /**
     * 生成当前文件的span
     * @param from 起始
     * @param to 末尾
     */
    private Span span(int from, int to) {
        return new Span(sourceMap.index, from, to);
    }

    /**
     * 若下一个token不是指定类型的则打印错误并返回null，否则返回该token并不推进
     */
    private Token expect(TokenType type) {
        if(check(type)) return lookAhead();
        error("未找到期望TokenType").point(lookAhead(), type.toString());
        return null;
    }

    /**
     * 若下一个token不是指定类型的则抛出错误，否则返回该token并推进
     */
    private Token consume(TokenType type) {
        if(check(type)) return next();
        throw error("未找到期望TokenType").point(lookAhead(), type.toString());
    }

    private Token consume(Set<TokenType> types) {
        if(check(types)) return next();
        StringBuilder sbd = new StringBuilder();
        for(TokenType type : types) {
            sbd.append(type.toString()).append(" ");
        }
        throw error("未找到期望TokenType").point(lookAhead(), sbd.toString());
    }

    private Token consume(TokenType type, Runnable r) {
        if(check(type)) return next();
        ParserIssue e = (ParserIssue) error("未找到期望字符").point(lookAhead(), type.toString());
        r.run();
        throw e;
    }


    /**
     * 错误恢复，扫描直到期望的TokenType
     */
    private void recover(TokenType... expectedTokenTypes) {
        while(!isAtEnd()) {
            for(TokenType expected : expectedTokenTypes) {
                if(check(expected)) {
                    return;
                }
            }
            next();
        }
    }

    private void normalRecover() {
        recover(IF, FOR, WHILE, FN, LBRACE);
    }

    /**
     * 报告错误并错误恢复，扫描直到期望的TokenType
     */
    private ParserIssue error(String text, TokenType... expectedTokenTypes) {
        recover(expectedTokenTypes);
        ParserIssue e = new ParserIssue(sourceMap, text, IssueLevel.ERROR);
        errorList.add(e);
        return e;
    }

    private ParserIssue error(String text) {
        ParserIssue e = new ParserIssue(sourceMap, text, IssueLevel.ERROR);
        errorList.add(e);
        return e;
    }

    private ParserIssue warning(String text) {
        ParserIssue e = new ParserIssue(sourceMap, text, IssueLevel.WARNING);
        warningList.add(e);
        return e;
    }
}