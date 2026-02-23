package mlogix.compiler;

import arc.struct.Seq;
import mlogix.compiler.SourceMapManager.SourceMap;
import mlogix.mlogix.ast.Expr;
import mlogix.mlogix.ast.Stmt;
import mlogix.mlogix.token.Token;
import mlogix.mlogix.token.TokenType;
import mlogix.problem.Problem;
import mlogix.problem.ProblemCollector;
import mlogix.span.Span;
import mlogix.span.Spanned;

import java.util.Set;

import static mlogix.mlogix.ast.Expr.*;
import static mlogix.mlogix.ast.Stmt.*;
import static mlogix.mlogix.token.TokenType.*;

// TODO isStmtEnd()可能不应该使用，或者需要更改实现，否则可能出现不使用分号作为语句结尾时的歧义
public class Parser {
    private final Lexer lexer;
    private final ProblemCollector collector;
    private SourceMap sourceMap;
    // 作为前瞻缓冲 必须通过工具方法访问
    private Token nextToken;

    /**
     * 一个项目 一次构造
     */
    Parser(Lexer lexer, ProblemCollector collector) {
        this.lexer = lexer;
        this.collector = collector;
    }

    /**
     * 一个文件 一次调用
     * 联动重置lexer
     */
    public Stmt parse(SourceMap sourceMap) {
        this.sourceMap = sourceMap;
        this.lexer.reset(sourceMap);

        nextToken = null;

        return program();
    }

    // ========== 主要解析部分 ==========

    // ---------- Stmt ----------
    private Stmt program() {
        Seq<Stmt> stmts = new Seq<>();

        while(!isAtEnd()) {
            stmts.add(statement());
        }

        return new Program(new Span(sourceMap.index, 0, sourceMap.length()), stmts);
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

        Seq<Stmt> stmts = new Seq<>();
        while(!check(RBRACE)) {
            if(isAtEnd()) {
                error("找不到`块`语句的`}`")
                        .info(lBrace, "开头")
                        .point(lookAhead(), "当前");
                return new Block(between(lBrace, stmts.isEmpty() ? lBrace
                        : stmts.get(stmts.size - 1)), stmts);
            }
            stmts.add(statement());
        }
        Token rbrace = next();

        return new Block(between(lBrace, rbrace), stmts);
    }

    private Stmt ifStmt() {
        Token start = next();

        Expr condition = expression();

        if(expect(LBRACE) instanceof Err) {
            normalRecover();
            return new IfStmt(Span.between(start, condition), condition, null, null);
        }
        Stmt thenBranch = block();

        Stmt elseBranch = null;
        if(match(ELIF)) {
            elseBranch = ifStmt();
        } else if(match(ELSE)) {
            if(expect(LBRACE) instanceof Err) {
                normalRecover();
                return new IfStmt(Span.between(start, condition), condition, thenBranch, null);
            }
            elseBranch = block();
        }

        Stmt end = elseBranch == null ? thenBranch : elseBranch;

        return new IfStmt(between(start, end), condition, thenBranch, elseBranch);
    }

    private Stmt forStmt() {
        Token start = next();

        if(check(IDENTIFIER)) {
            Identifier var = new Identifier(next());
            if(check("in")) {
                next();
                Expr expr = expression();

                if(expect(LBRACE) instanceof Err<?> e) {
                    e.problem.info(between(start, expr), "`for`语句");
                    normalRecover();
                    return new ForStmt(between(start, expr), var, expr, null);
                }
                Stmt body = block();

                return new ForStmt(between(start, body), var, expr, body);
            }
            if(expect(LBRACE) instanceof Err<?> e) {
                e.problem.info(between(start, var), "`for`语句");
                normalRecover();
                return new ForStmt(between(start, var), var, null, null);
            }
            Stmt body = block();

            return new ForStmt(between(start, body), var, null, body);
        } else {
            Expr expr = expression();

            if(expect(LBRACE) instanceof Err<?> e) {
                e.problem.info(between(start, expr), "`for`语句");
                normalRecover();
                return new ForStmt(between(start, expr), null, expr, null);
            }
            Stmt body = block();

            return new ForStmt(between(start, body), null, expr, body);
        }
    }

    private Stmt whileStmt() {
        Token start = next();

        Expr expr = expression();

        if(expect(LBRACE) instanceof Err<?> e) {
            e.problem.info(between(start, expr), "`while`语句");
            normalRecover();
            return new WhileStmt(between(start, expr), expr, null);
        } else {
            Stmt body = block();
            return new WhileStmt(between(start, body), expr, body);
        }
    }

    private Stmt functionStmt() {
        Token start = next();

        Res<Token> varRes = consume(IDENTIFIER);
        Token name;
        if(varRes instanceof Err<?> e) {
            name = null;
            if(consume(LPAREN) instanceof Err) {
                normalRecover();
                return new FnStmt(start.span, null, null, null, null);
            }
        } else {
            name = ((Ok<Token>) varRes).obj;
            if(consume(LPAREN) instanceof Err) {
                normalRecover();
                return new FnStmt(start.span, name, null, null, null);
            }
        }

        Seq<Expr> parameters = new Seq<>();

        while(!match(RPAREN)) {
            if(isAtEnd()) {
                error("无法结束的`函数形参声明`")
                        .info(start, "函数声明开头")
                        .point(lookAhead(), "末尾");
                return new FnStmt(between(start, lookAhead()), name, null, null, null);
            }
            // TODO var : type
            parameters.add(expression());
            if(!match(COMMA)) {
                consume(RPAREN); // 若未找到则当成找到了
                break;
            }
        }

        Seq<Expr> results = new Seq<>();
        if(check(ARROW)) {
            Token arrow = next();
            while(!check(LBRACE)) {
                if(isAtEnd()) {
                    if(results.isEmpty()) {
                        error("无法找到`函数返回值声明`")
                                .info(start, "函数开头")
                                .point(lookAhead(), "期望`标识符`");
                        return new FnStmt(between(start, lookAhead()), name, parameters, null, null);
                    } else {
                        error("无法找到函数体")
                                .info(start, "函数开头")
                                .point(lookAhead(), "期望`{`");
                        return new FnStmt(between(start, lookAhead()), name, parameters, results, null);
                    }
                }
//                try {
                results.add(expression());
//                } catch(Problem.ParserProblem e) {
//                    e.info(arrow, "解析`函数返回值声明`时出现错误");
//                    normalRecover();
//                    break;
//                }
                match(COMMA); // 可选逗号
            }
        }
        if(expect(LBRACE) instanceof Err<?> e) {
            Spanned end = results.isEmpty() ? start : results.get(results.size);
            e.problem.info(between(start, end), "`fn`语句");
            normalRecover();
            return new FnStmt(between(start, end), name, parameters, results, null);
        } else {
            Stmt body = block();
            return new FnStmt(between(start, body), name, parameters, results, body);
        }
    }

    private Stmt exprStmt() {
        if(check(BREAK)) {
            Token start = next();
            consumeStmtEnd();
            return new BreakStmt(start.span);

        } else if(check(CONTINUE)) {
            Token start = next();
            consumeStmtEnd();
            return new ContinueStmt(start.span);

        } else if(check(RETURN)) {
            Token start = next();
            if(matchStmtEnd()) {
                return new ReturnStmt(start.span, null);
            }
            Expr expr = expression();
            consumeStmtEnd();
            return new ReturnStmt(between(start, expr), expr);

        } else if(check(SET)) {
            Token start = next();

            Expr var = expression();

            Stmt assignStmt;
            assignStmt = assignStmt(var);
            if(assignStmt == null) {
                consumeStmtEnd();
                return new SetVarStmt(between(start, var), var, null);
            } else {
                // assignStmt(_)消耗了StmtEnd
                return new SetVarStmt(between(start, assignStmt), var, assignStmt);
            }

        } else {
            Expr var = expression();

            Stmt assignStmt = assignStmt(var);

            if(assignStmt == null) {
                consumeStmtEnd();
                return new ExprStmt(var.span, var);
            } else {
                return assignStmt;
            }
        }
    }

    /**
     * `=` ...
     * 复合赋值运算符`+=` ...
     * @param expr `=`或复合赋值运算符前的表达式
     * @return 没有`=`或者复合赋值运算符时返回null;有时返回AssignStmt
     */
    private Stmt assignStmt(Expr expr) {
        if(check(ASSIGN)) {
            Token operator = next();
            Expr value;
            value = expression();

            consumeStmtEnd();
            return new AssignStmt(between(expr, value), expr, operator, value);

        } else if(check(BINARY_OPERATORS)) {
            Token operator = next();
            if(check(ASSIGN)) {
                Token assignOp = next();
                Expr value;
                value = expression();

                consumeStmtEnd();
                return new AssignStmt(between(expr, value), expr, operator, value);
            }
            Expr right;
            right = expression();
            return new ExprStmt(between(expr, right), new Binary(expr, operator, right));
        }
        return null;
    }

    // ---------- Expr ----------
    private Res<Expr> expression() {
        return or();
    }

    private Res<Expr> or() {
        Expr expr;
        var res = and();
        if(res instanceof Err<Expr> e)
            return e;
        else
            expr = ((Ok<Expr>) res).obj;

        while(!isStmtEnd() && check(OR_OR)) {
            Token operator = next();

            Expr right;
            var rightRes = or();
            if(rightRes instanceof Err<Expr> e)
                return e;
            else
                right = ((Ok<Expr>) rightRes).obj;

            if(right instanceof Binary && ((Binary) right).operator.type == AND_AND) {
                error("不明确关系的逻辑运算表达式，请添加括号")
                        .point(expr.span.start(), right.span.end(), "");
                // 此处遵循优先级and > or
            }
            expr = new Binary(expr, operator, right);
        }

        return new Ok<>(expr);
    }

    private Res<Expr> and() {
//        Expr expr = equality();
        Expr expr;
        var res = equality();
        if(res instanceof Err<Expr> e)
            return e;
        else
            expr = ((Ok<Expr>) res).obj;

        while(!isStmtEnd() && check(AND_AND)) {
            Token operator = next();

            Expr right;
            var rightRes = or();
            if(rightRes instanceof Err<Expr> e)
                return e;
            else
                right = ((Ok<Expr>) rightRes).obj;

            if(right instanceof Binary rightBin && rightBin.operator.type == OR_OR) {
                error("不明确关系的逻辑运算表达式，请添加括号")
                        .point(expr.span.start(), right.span.end(), "");
                // expr && right.left || right.right
                // 按照优先级and > or进行重构
                // (expr && right.left) || right.right
                expr = new Binary(
                        new Binary(expr, operator, rightBin.left),
                        rightBin.operator,
                        rightBin.right
                );
            } else {
                expr = new Binary(expr, operator, right);
            }
        }
        return new Ok<>(expr);
    }

    /**
     *  == !=
     */
    private Res<Expr> equality() {
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
    private Res<Expr> comparison() {
        Expr expr = range();

        while(!isStmtEnd() && check(GREATER, GREATER_EQ, LESS, LESS_EQ)) {
            Token operator = next();
            Expr right = range();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Res<Expr> range() {
        if(!isStmtEnd() && check(COLON_LESS, COLON_ASSIGN)) {
            Token operator = next();

            if(!check(LITERALS) && !check(IDENTIFIER) && !check(LPAREN)) { // ..
                new Range(between(operator, operator), null, operator, null);
            }

            Expr right;
//            try {
            right = addAndSub();
//            } catch(Problem.ParserProblem e) {
//                e.info(operator, "解析`范围表达式`时出现错误");
//                right = new Literal(token(ERROR, lookAhead()));
//            }
            // .. expr
            new Range(between(operator, right), null, operator, right);
        }

        Expr expr = addAndSub();

        if(!isStmtEnd() && check(COLON_LESS, COLON_ASSIGN)) { // expr .. expr?
            Token operator = next();

            if(!check(LITERALS) && !check(IDENTIFIER) && !check(LPAREN)) { // expr ..
                new Range(between(operator, operator), null, operator, null);
            }

            Expr right;
//            try {
            right = addAndSub();
//            } catch(Problem.ParserProblem e) {
//                e.info(operator, "解析`范围表达式`时出现错误");
//                right = new Literal(token(ERROR, lookAhead()));
//            }
            // expr .. expr
            expr = new Range(Span.between(expr, right), expr, operator, right);
        }

        return expr;
    }

    private Res<Expr> addAndSub() {
        Expr expr = mulAndDiv();

        while(!isStmtEnd() && check(PLUS, MINUS)) {
            Token operator = next();
            Expr right = mulAndDiv();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Res<Expr> mulAndDiv() {
        Expr expr = unary();

        while(!isStmtEnd() && check(STAR, SLASH)) {
            Token operator = next();
            Expr right = unary();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Res<Expr> unary() {
        if(!isStmtEnd() && check(BANG, MINUS)) {
            Token operator = next();
            Expr right = unary();
            return new Unary(operator, right);
        }

        return suffixExpr();
    }

    private Res<Expr> suffixExpr() {
        Expr expr = primary();

        while(true) {
            if(isStmtEnd()) return expr;

            Seq<Expr> arguments = new Seq<>();

            if(check(LBRACKET)) {//对列表的索引或切片
                Token lBracket = next();

                Expr index;
//                try {
                index = expression();
//                } catch(Problem.ParserProblem e) {
//                    e.info(lBracket, "解析`数组索引`时出现错误");
//                    index = new Literal(token(ERROR, lookAhead()));
//                }

                Token rBracket;
//                try {
                rBracket = consume(RBRACKET);
//                } catch(Problem.ParserProblem e) {
//                    e.info(lBracket, "解析`数组索引`时出现错误");
//                    rBracket = null;
//                }
                if(rBracket != null) {
                    expr = new Index(between(lBracket, rBracket), expr, index);
                } else {
                    expr = new Index(between(lBracket, index), expr, index);
                }
                continue;

            } else if(check(LPAREN)) {//函数调用
                Token lParen = next();
                while(!check(RPAREN)) {
                    if(isStmtEnd()) {
                        throw error("无法结束的`函数传参`")
                                .info(lParen, "参数开头")
                                .point(lookAhead(), "末尾");
                    }
//                    try {
                    arguments.add(expression());
//                    } catch(Problem.ParserProblem e) {
//                        e.info(lParen, "解析`函数调用`时出现错误");
//                    }
                    match(COMMA); // 可选逗号
                }
                Token rParen = next();
                expr = new Call(between(lParen, rParen), expr, arguments);
                continue;

            } else if(check(DOT)) {//访问类的元素
                Token dot = next();
                Expr field;
//                try {
                field = new Identifier(consume(IDENTIFIER));
//                } catch(Problem.ParserProblem e) {
//                    e.info(dot, "解析`类元素访问`时出现错误");
//                    field = new Identifier(token(ERROR, lookAhead()));
//                }
                expr = new Get(expr, field);
                continue;
            }

            return expr;
        }
    }

    // TODO 添加新语法 var : ? type1 | type2
    private Res<Expr> primary() {
        if(check(LITERALS)) {
            Token literal = next();

            // lit :
            if(!isStmtEnd() && match(COLON)) {
                Seq<Expr> annotations = new Seq<>();

                // lit : ?
                if(check(QUESTION_MARK)) {
                    annotations.add(new Literal(new Token(next().span, NULL)));
                }

                // lit : anno1 | anno2 ...
                Res<Expr> res;
                while(!isAtEnd()) {
                    res = unary();
                    if(res instanceof Ok<Expr> ok) {
                        annotations.add(ok.obj);
                    }
                    if(!match(OR)) break;
                }
                if(annotations.size != 0)
                    return new Ok<>(new Annotation(new Literal(literal), annotations));
                // 如果标注数量为0，视作Literal
            }
            return new Ok<>(new Literal(literal));

        } else if(check(IDENTIFIER)) {
            Token id = next();
            Seq<Expr> type = new Seq<>();
            if(!isStmtEnd() && check(COLON)) {
                Token colon = next();
                while(!isAtEnd()) {
//                    try {
                    type.add(primary());
//                    } catch(Problem.ParserProblem e) {
//                        e.info(colon, "解析`类型声明`时出现错误");
//                    }
                }
                return new Identifier(id);
            }
            return new Identifier(id);

        } else if(check(LPAREN)) {
            Token lParen = next();
            Expr expr = null;
//            try {
            expr = expression();
//            } catch(Problem.ParserProblem e) {
//                e.info(lParen, "找不到括号内的表达式");
//                expr = new Literal(token(ERROR, lookAhead()));
//            }
//            try {
            consume(RPAREN);
//            } catch(Problem.ParserProblem e) {
//                e.info(lParen, "解析`括号内表达式`时出现错误");
//            }
            return expr;
        } else if(check(LBRACE)) {
            Token lBrace = next();
            Seq<Expr> elements = new Seq<>();
            while(!check(RBRACE)) {
                if(isAtEnd()) {
                    throw error("无法结束的数组")
                            .info(lBrace, "数组开头")
                            .point(lookAhead(), "末尾");
                }
//                try {
                elements.add(expression());
//                } catch(Problem.ParserProblem e) {
//                    e.info(lBrace, "解析`数组`时出现错误");
//                    elements.add(new Literal(token(ERROR, lookAhead())));
//                }
                match(COMMA); // 可选逗号
            }
            Token rBrace = next();
            return new Array(between(lBrace, rBrace), elements);
        }

        Problem e = error("期望表达式").point(lookAhead(), "");
        return new Err<>(e);
    }

    // ========== 工具方法 ==========

    // ---------- Token基础方法 ----------

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
     * 检查是否为文件结尾
     * @return 下一个Token为`EOF`时返回true，自动消耗NEWLINE
     */
    private boolean isAtEnd() {
        TokenType nextType = lookAhead().type;
        if(nextType == NEWLINE) {
            next();
            nextType = lookAhead().type; // 第二个不会是NEWLINE，由Lexer.scanToken()证明
        }
        return nextType == EOF;
    }

    /**
     * 检查是否为语句结尾
     * @return 下一个Token为`NEWLINE`或`SEMICOLON`或`EOF`时返回true
     */
    private boolean isStmtEnd() {
        TokenType peekType = lookAhead().type;
        return peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF;
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(TokenType type) {
        TokenType nextType = lookAhead().type;
        if(nextType == ERROR) return true; // 忽略Lexer传来的错误Token
        if(nextType == NEWLINE) {
            next();
            nextType = lookAhead().type; // 第二个不会是NEWLINE，由Lexer.scanToken()证明
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
            nextType = lookAhead().type; // 第二个不会是NEWLINE，由Lexer.scanToken()证明
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
            next = lookAhead(); // 第二个不会是NEWLINE，由Lexer.scanToken()证明
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
            nextType = lookAhead().type;// 第二个不会是NEWLINE，由Lexer.scanToken()证明
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


    // ---------- Token高级方法 ----------

    /**
     * 不报错
     */
    private boolean matchStmtEnd() {
        // 检查 ; \n EOF 作为语句结束符，若有则推进
        TokenType peekType = lookAhead().type;
        if(peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF) {
            next();
            return true;
        }
        // 检查 { } 作为语句结束符，不推进
        return peekType == LBRACE || peekType == RBRACE;
    }

    private Res<Token> consumeStmtEnd() {
        // 检查 ; \n EOF 作为语句结束符
        TokenType peekType = lookAhead().type;
        if(peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF) {
            return new Ok<>(next());
        }
        // 检查 { } 作为语句结束符
        if(peekType == LBRACE || peekType == RBRACE) {
            return new Ok<>(lookAhead());
        }
        // 如果没有找到，输出错误
        return new Err<>(error("缺少换行或分号作为语句结束符")
                .point(lookAhead(), "")
        );
    }

    /**
     * 若下一个token不是指定类型的则报告错误并返回Err，否则返回含该token的Ok并不推进
     */
    private Res<Token> expect(TokenType type) {
        if(check(type)) return new Ok<>(lookAhead());
        Problem e = error("未找到期望TokenType").point(lookAhead(), type.toString());
        return new Err<>(e);
    }

    /**
     * 若下一个token不是指定类型的则抛出错误，否则返回该token并推进
     */
    private Res<Token> consume(TokenType type) {
        if(check(type)) return new Ok<>(next());
        return new Err<>(error("未找到期望TokenType")
                .point(lookAhead(), type.toString())
        );
    }

//    private Res<Token> consume(Set<TokenType> types) {
//        if(check(types)) return new Ok<> (next());
//        StringBuilder sbd = new StringBuilder();
//        for(TokenType type : types) {
//            sbd.append(type.toString()).append(" ");
//        }
//        return new Err<>(error("未找到期望TokenType")
//                .point(lookAhead(), sbd.toString())
//        );
//    }
//
//    private Res<Token> consume(TokenType type, Runnable r) {
//        if(check(type)) return new Ok<>(next());
//        Problem.ParserProblem e = (Problem.ParserProblem) error("未找到期望字符").point(lookAhead(), type.toString());
//        r.run();
//        return new Err<>(e);
//    }

    // ---------- 错误恢复方法 ----------

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

    /**
     * 等价于recover(IF, FOR, WHILE, FN, LBRACE)
     */
    private void normalRecover() {
        recover(IF, FOR, WHILE, FN, LBRACE);
    }

//    /**
//     * 报告错误并错误恢复，扫描直到期望的TokenType
//     */
//    private Problem.ParserProblem error(Str text, TokenType... expectedTokenTypes) {
//        recover(expectedTokenTypes);
//        Problem.ParserProblem e = new Problem.ParserProblem(sourceMap, text, Problem.ProblemLevel.ERROR);
//        errorList.add(e);
//        return e;
//    }

    // ---------- 类生成方法 ----------
    private Token token(TokenType type, Token from) {
        return new Token(from.span, type);
    }

    /**
     * 生成当前文件的span
     * @param from 起始
     * @param to 末尾
     */
    private Span between(Spanned from, Spanned to) {
        return new Span(sourceMap.index, from.span().start(), to.span().end());
    }

    private Problem.ParserProblem error(String text) {
        Problem.ParserProblem e = new Problem.ParserProblem(sourceMap, text, Problem.ProblemLevel.ERROR);
        collector.addError(e);
        return e;
    }

    private Problem.ParserProblem warning(String text) {
        Problem.ParserProblem w = new Problem.ParserProblem(sourceMap, text, Problem.ProblemLevel.WARNING);
        collector.addWarning(w);
        return w;
    }

    // ========== 工具类 ==========

    /**
     *只为Expr
     */
    private abstract static class Res<T> {
        public Object getValue() {
            if(this instanceof Ok<T> ok) {
                return ok.obj;
            } else {
                return new Expr.ErrorExpr();
            }
        }
    }

    /**
     * 表示结果正确，过程可能出错且对调用者无影响
     */
    static class Ok<T> extends Parser.Res<T> {
        public final T obj;

        Ok(T obj) {
            this.obj = obj;
        }
    }

    /**
     * 表示结果错误、无法处理，过程出错
     */
    static class Err<T> extends Parser.Res<T> {
        public final Problem problem;

        Err(Problem problem) {
            this.problem = problem;
        }
    }
}