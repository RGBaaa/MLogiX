package mlogix.compiler;

import arc.func.Cons;
import arc.struct.Queue;
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

public class Parser {
    private final Lexer lexer;
    private final ProblemCollector collector;
    private SourceMap sourceMap;
    private LookAheadWindow input;

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

        this.input = new LookAheadWindow();

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
                        .point(lookAhead(0), "当前");
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

        if(expect(LBRACE) == null) {
            normalRecover();
            return new IfStmt(Span.between(start, condition), condition, null, null);
        }
        Stmt thenBranch = block();

        Stmt elseBranch = null;
        if(match(ELIF)) {
            elseBranch = ifStmt();
        } else if(match(ELSE)) {
            if(expect(LBRACE) == null) {
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

        // for id
        if(check(IDENTIFIER)) {
            Identifier var = new Identifier(next());

            Expr expr;
            // for id in expr
            if(match("in")) {
                expr = expression();

                // for id
            } else {
                expr = null;
            }

            if(expect(LBRACE,
                    e -> e.info(between(start, expr != null ? expr : var), "`for`语句")
            ) == null) {
                normalRecover();
                return new ForStmt(between(start, expr != null ? expr : var), var, expr, null);
            }
            Stmt body = block();

            return new ForStmt(between(start, body), var, expr, body);

            //for repeatNum
        } else {
            Expr expr = expression();

            if(expect(LBRACE,
                    e -> e.info(between(start, expr), "`for`语句")
            ) == null) {
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

        if(expect(LBRACE,
                e -> e.info(between(start, expr), "`while`语句")
        ) == null) {
            normalRecover();
            return new WhileStmt(between(start, expr), expr, null);
        } else {
            Stmt body = block();
            return new WhileStmt(between(start, body), expr, body);
        }
    }

    private Stmt functionStmt() {
        Token start = next();
        Spanned end;

        Token name = consume(IDENTIFIER);
        if(name == null) {
            if(!check(LPAREN)) {
                normalRecover();
                return new FnStmt(start.span, null, null, null, null);
            }
            end = next();
        } else {
            Token lparen = consume(LPAREN);
            if(lparen == null) {
                normalRecover();
                return new FnStmt(between(start, name), name, null, null, null);
            }
            end = lparen;
        }

        Seq<Expr> parameters = new Seq<>();

        while(!match(RPAREN)) {
            if(isAtEnd()) {
                error("无法结束的`函数形参声明`")
                        .info(start, "函数声明开头")
                        .point(lookAhead(0), "末尾");
                return new FnStmt(between(start, lookAhead(0)), name, null, null, null);
            }
            if(!check(IDENTIFIER)) {
                recover(RPAREN);
                break;
            }
            Expr parameter = annotation();
            end = parameter;
            parameters.add(parameter);
            match(COMMA);
        }

        Seq<Expr> results = new Seq<>();
        if(check(ARROW)) {
            end = next();
            while(!check(LBRACE)) {
                if(isAtEnd()) {
                    if(results.isEmpty()) {
                        error("无法找到`函数返回值声明`")
                                .info(start, "函数开头")
                                .point(lookAhead(0), "期望`标识符`");
                        return new FnStmt(between(start, end), name, parameters, null, null);
                    } else {
                        error("无法找到函数体")
                                .info(start, "函数开头")
                                .point(lookAhead(0), "期望`{`");
                        return new FnStmt(between(start, end), name, parameters, results, null);
                    }
                }
                if(!check(IDENTIFIER)) {
                    recover(LBRACE);
                    break;
                }
                Expr result = annotation();
                end = result;
                results.add(result);
                match(COMMA);
            }
        }
        Spanned finalEnd = end;
        if(expect(LBRACE, e ->
                e.info(between(start, finalEnd), "`fn`语句")
        ) == null) {
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
            if(match(ASSIGN)) {
                Expr value = expression();

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
    // Expr解析函数只能返回Expr，不能是null，错误请用ErrorExpr
    private Expr expression() {
        return or();
    }

    private Expr or() {
        Expr expr = and();

        while(check(OR_OR)) {
            Token operator = next();

            Expr right = or();
            if(right instanceof Binary && ((Binary) right).operator.type == AND_AND) {
                error("不明确关系的逻辑运算表达式，请添加括号")
                        .point(expr.span.start, right.span.end, "");
                // 此处遵循优先级and > or
            }
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while(check(AND_AND)) {
            Token operator = next();

            Expr right = or();
            if(right instanceof Binary rightBin && rightBin.operator.type == OR_OR) {
                error("不明确关系的逻辑运算表达式，请添加括号")
                        .point(expr.span.start, right.span.end, "");
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
        return expr;
    }

    /**
     *  == != === !==
     */
    private Expr equality() {
        Expr expr = comparison();

        if(check(EQ_EQ, BANG_EQ, EQ_EQ_EQ, BANG_EQ_EQ)) {
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

        if(check(GREATER, GREATER_EQ, LESS, LESS_EQ)) {
            Token operator = next();
            Expr right = range();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr range() {
        // :< ...    := ...
        if(check(COLON_LESS, COLON_ASSIGN)) {
            Token operator = next();

            // :<    :=
            if(!check(LITERALS) && !check(IDENTIFIER) && !check(LPAREN)) {
                new Range(operator.span, null, operator, null);
            }

            // :< expr    := expr
            Expr right;
//            try {
            right = addAndSub();
//            } catch(Problem.ParserProblem e) {
//                e.info(operator, "解析`范围表达式`时出现错误");
//                right = new Literal(token(ERROR, lookAhead()));
//            }
            new Range(between(operator, right), null, operator, right);
        }

        // expr
        Expr expr = addAndSub();

        // expr :< ...    expr := ...
        if(!isStmtEnd() && check(COLON_LESS, COLON_ASSIGN)) {
            Token operator = next();

            // expr :<    expr :=
            if(!check(LITERALS) && !check(IDENTIFIER) && !check(LPAREN)) {
                new Range(between(operator, operator), null, operator, null);
            }

            // expr :< expr    expr := expr
            Expr right;
//            try {
            right = addAndSub();
//            } catch(Problem.ParserProblem e) {
//                e.info(operator, "解析`范围表达式`时出现错误");
//                right = new Literal(token(ERROR, lookAhead()));
//            }
            expr = new Range(Span.between(expr, right), expr, operator, right);
        }

        return expr;
    }

    private Expr addAndSub() {
        Expr expr = mulAndDiv();

        while(check(PLUS, MINUS)) {
            Token operator = next();
            Expr right = mulAndDiv();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr mulAndDiv() {
        Expr expr = pow();

        while(check(STAR, SLASH)) {
            Token operator = next();
            Expr right = pow();
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr pow() {
        Expr expr = unary();

        if(check(STAR_STAR)) {
            Token operator = next();
            Expr right = pow(); // 右结合
            expr = new Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if(check(BANG, MINUS)) {
            Token operator = next();
            Expr right = unary();
            return new Unary(operator, right);
        }

        return suffixExpr();
    }

    private Expr suffixExpr() {
        Expr expr = primary();

        while(true) {
//            if(isStmtEnd()) return expr;

            if(check(LBRACKET)) {//对列表的索引或切片
                Token lBracket = next();

                Expr index;
//                try {
                index = expression();
//                } catch(Problem.ParserProblem e) {
//                    e.info(lBracket, "解析`数组索引`时出现错误");
//                    index = new Literal(token(ERROR, lookAhead()));
//                }

                Token rBracket = consume(RBRACKET, e -> e.info(lBracket, "解析`数组索引`时出现错误"));

                if(rBracket != null) {
                    expr = new Index(between(lBracket, rBracket), expr, index);
                } else {
                    expr = new Index(between(lBracket, index), expr, index);
                }
                continue;

            } else if(check(LPAREN)) {//函数调用
                Token lParen = next();

                Seq<Expr> arguments = new Seq<>();
                while(true) {
                    if(isAtEnd()) {
                        error("无法结束的`函数传参`")
                                .info(lParen, "参数开头")
                                .point(lookAhead(0), "末尾");
                        if(arguments.isEmpty()) {
                            expr = new Call(expr.span, expr, arguments);
                        } else {
                            expr = new Call(between(expr, arguments.get(arguments.size - 1)),
                                    expr, arguments);
                        }
                        break;
                    }
                    if(check(RPAREN)) {
                        expr = new Call(between(expr, next()), expr, arguments);
                        break;
                    }

//                    try {
                    arguments.add(expression());
//                    } catch(Problem.ParserProblem e) {
//                        e.info(lParen, "解析`函数调用`时出现错误");
//                    }
                    match(COMMA); // 可选逗号
                }
                continue;

            } else if(check(DOT)) {//访问类的元素
                Token dot = next();

                Token id = consume(IDENTIFIER, e -> e.info(dot, "解析`类元素访问`时出现错误"));
                if(id == null) return expr;
                Expr field = new Identifier(id);

                expr = new Get(expr, field);
                continue;
            }

            return expr;
        }
    }

    private Expr primary() {
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
                while(!isAtEnd()) {
                    annotations.add(unary());
                    if(!match(OR)) break;
                }
                if(annotations.isEmpty())
                    return new Annotation(new Literal(literal), annotations);
                // 如果标注数量为0，视作Literal
            }
            return new Literal(literal);

        } else if(check(IDENTIFIER)) {
            Token id = next();

            // id :
            if(!isStmtEnd() && match(COLON)) {
                Seq<Expr> annotations = new Seq<>();

                // id : ?
                if(check(QUESTION_MARK)) {
                    annotations.add(new Literal(new Token(next().span, NULL)));
                }

                // id : anno1 | anno2 ...
                while(!isAtEnd()) {
                    annotations.add(unary());
                    if(!match(OR)) break;
                }
                if(annotations.isEmpty())
                    return new Annotation(new Identifier(id), annotations);
                // 如果标注数量为0，视作Identifier
            }
            return new Identifier(id);

        } else if(match(LPAREN)) {
            Expr expr = expression();
            consume(RPAREN);
            return expr;

        } else if(check(LBRACE)) {
            Token lBrace = next();
            Seq<Expr> elements = new Seq<>();
            while(!check(RBRACE)) {
                if(isAtEnd()) {
                    error("无法结束的数组")
                            .info(lBrace, "数组开头")
                            .point(lookAhead(0), "末尾");
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

        error("期望表达式").point(lookAhead(0), "");
        return new ErrorExpr(next().span);
    }

    /**
     * 比较特殊，专门给FnStmt用的吗，只可能返回Identifier或Annotation
     */
    private Expr annotation() {
        Token id = next();

        // id :
        if(!isStmtEnd() && match(COLON)) {
            Seq<Expr> annotations = new Seq<>();

            // id : ?
            if(check(QUESTION_MARK)) {
                annotations.add(new Literal(new Token(next().span, NULL)));
            }

            // id : anno1 | anno2 ...
            while(!isAtEnd()) {
                annotations.add(unary());
                if(!match(OR)) break;
            }
            if(annotations.isEmpty())
                return new Annotation(new Identifier(id), annotations);
            // 如果标注数量为0，视作Identifier
        }
        return new Identifier(id);
    }

    // ========== 工具方法 ==========

    // ---------- Token基础方法 ----------

    /**
     * 向前推进一个token
     */
    private Token next() {
        return input.next();
    }

    /**
     * 前瞻下一个token
     */
    private Token lookAhead(int index) {
        return input.lookAhead(index);
    }

    /**
     * 检查是否为文件结尾
     * @return 下一个Token为`EOF`时返回true，自动消耗NEWLINE
     */
    private boolean isAtEnd() {
        TokenType nextType = lookAhead(0).type;
        if(nextType == NEWLINE) {
            next();
            nextType = lookAhead(0).type; // 第二个不会是NEWLINE，由Lexer.scanToken()证明
        }
        return nextType == EOF;
    }

    /**
     * 检查是否为语句结尾
     * @return 下一个Token为`NEWLINE`或`SEMICOLON`或`EOF`时返回true
     */
    private boolean isStmtEnd() {
        TokenType peekType = lookAhead(0).type;
        return peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF;
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(TokenType type) {
        TokenType nextType = lookAhead(0).type;
        if(nextType == NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()证明
            if(lookAhead(1).type == type) {
                next(); // 跳过NEWLINE
                return true;
            }
            return false;
        }
        return nextType == type;
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(Set<TokenType> types) {
        TokenType nextType = lookAhead(0).type;
        if(nextType == NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()证明
            if(types.contains(lookAhead(1).type)) {
                next(); // 跳过NEWLINE
                return true;
            }
            return false;
        }
        return types.contains(nextType);
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(String text) {
        Token next = lookAhead(0);
        if(next.type == NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()证明
            if(text.equals(lookAhead(1).literal)) {
                next(); // 跳过NEWLINE
                return true;
            }
            return false;
        }
        return text.equals(next.literal);
    }

    /**
     * 不支持NEWLINE
     */
    private boolean check(TokenType... types) {
        TokenType nextType0 = lookAhead(0).type;
        if(nextType0 == NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()证明
            TokenType nextType1 = lookAhead(1).type;
            for(TokenType expected : types) {
                if(nextType1 == expected) {
                    next(); // 跳过NEWLINE
                    return true;
                }
            }
            return false;
        }
        for(TokenType expected : types) {
            if(nextType0 == expected) return true;
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

    private boolean match(String text) {
        if(check(text)) {
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
     * 若下一个token不是指定类型的则返回null并报告错误，否则返回该token。
     */
    private Token expect(TokenType type) {
        if(check(type)) return lookAhead(0);

        error("未找到期望TokenType").point(lookAhead(0), type.toString());
        return null;
    }

    /**
     * 若下一个token不是指定类型的则返回null并报告错误并将错误输入Cons，否则返回该token。
     */
    private Token expect(TokenType type, Cons<Problem> cons) {
        if(check(type)) return lookAhead(0);

        cons.get(error("未找到期望TokenType").point(lookAhead(0), type.toString()));
        return null;
    }

    /**
     * 检查 ; \n EOF 作为语句结束符，若有则推进；
     * 检查 { } 作为语句结束符，不推进；
     * 不报错。
     */
    private boolean matchStmtEnd() {
        TokenType peekType = lookAhead(0).type;
        if(peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF) {
            next();
            return true;
        }
        // 检查 { } 作为语句结束符，不推进
        return peekType == LBRACE || peekType == RBRACE;
    }

    /**
     * 检查 ; \n EOF 作为语句结束符，推进；
     * 检查 { } 作为语句结束符，不推进；
     * 都没有则报错。
     */
    private void consumeStmtEnd() {
        TokenType peekType = lookAhead(0).type;
        if(peekType == NEWLINE || peekType == SEMICOLON || peekType == EOF) {
            next();
            return;
        }
        if(peekType == LBRACE || peekType == RBRACE) {
            return;
        }
        // 如果没有找到，报告错误
        error("缺少换行或分号作为语句结束符")
                .point(lookAhead(0), "");
    }

    /**
     * 若下一个token不是指定类型的则抛出错误，否则返回该token并推进
     */
    private Token consume(TokenType type) {
        if(check(type)) return next();

        error("未找到期望TokenType")
                .point(lookAhead(0), type.toString());
        return null;
    }

    /**
     * 若下一个token不是指定类型的则抛出错误，否则返回该token并推进
     */
    private Token consume(TokenType type, Cons<Problem> cons) {
        if(check(type)) return next();

        cons.get(error("未找到期望TokenType")
                .point(lookAhead(0), type.toString())
        );
        return null;
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
        return new Span(sourceMap.index, from.span().start, to.span().end);
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

    private class LookAheadWindow {
        private final byte capacity = 2;
        private final Queue<Token> buffer = new Queue<>(capacity);

        public LookAheadWindow() {
        }

        public Token next() {
            if(buffer.size < 1)
                return lexer.scanToken();
            return buffer.removeFirst();
        }

        public Token lookAhead(int index) {
            for(int i = 0; i <= index - buffer.size; i++) {
                buffer.addLast(lexer.scanToken());
            }
            return buffer.get(index);
        }

    }
}