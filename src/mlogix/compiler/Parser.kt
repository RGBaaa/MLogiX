package mlogix.compiler

import arc.func.Cons
import arc.struct.Queue
import arc.struct.Seq
import mlogix.compiler.SourceMapManager.SourceMap
import mlogix.mlogix.ast.Expr
import mlogix.mlogix.ast.Expr.ErrorExpr
import mlogix.mlogix.ast.Expr.Get
import mlogix.mlogix.ast.Stmt
import mlogix.mlogix.ast.Stmt.*
import mlogix.mlogix.token.Token
import mlogix.mlogix.token.TokenType
import mlogix.problem.Problem
import mlogix.problem.Problem.ParserProblem
import mlogix.problem.ProblemCollector
import mlogix.span.Span
import mlogix.span.Spanned
import java.util.*

/**
 * 一个项目 一次构造
 */
class Parser(
    private val lexer: Lexer,
    private val collector: ProblemCollector,
) {
    private lateinit var sourceMap: SourceMap
    private lateinit var input: LookAheadWindow
    private lateinit var prevToken: Token

    /**
     * 一个文件 一次调用
     * 联动重置lexer
     */
    fun parse(sourceMap: SourceMap): Stmt {
        this.sourceMap = sourceMap
        this.lexer.reset(sourceMap)
        this.input = LookAheadWindow()
        this.prevToken = lookAhead(0)

        return program()
    }

    // ========== 主要解析部分 ==========
    // ---------- Stmt ----------
    private fun program(): Stmt {
        val stmts = Seq<Stmt>()

        while (!this.isAtEnd) {
            val stmt = statement()
            if (stmt != null) {
                stmts.add(stmt)
            }
        }

        return Program(Span(sourceMap.index, 0, sourceMap.length()), stmts)
    }

    private fun statement(): Stmt? = when {
        check(TokenType.USE) -> useStmt()
        check(TokenType.IF) -> ifStmt()
        check(TokenType.MATCH) -> matchStmt()
        check(TokenType.FOR) -> forStmt()
        check(TokenType.WHILE) -> whileStmt()
        check(TokenType.FN) -> functionStmt()
        check(TokenType.LBRACE) -> block()
        else -> exprStmt()
    }

    private fun useStmt(): Stmt? {
        val start = next() // consume 'use'
        val item = useItem() ?: return null
        return UseStmt(between(start, item), item)
    }

    private fun useItem(): UseStmt.UseItem? {
        val path = Seq<Expr.Identifier>()
        while (true) {
            if (check(TokenType.IDENTIFIER)) {
                path.add(Expr.Identifier(next()))
            } else if (check(TokenType.STAR)) {
                return UseStmt.All(if (path.isEmpty) next().span else between(path.get(0), next()), path)
            } else if (check(TokenType.STAR_STAR)) {
                return UseStmt.Recursion(if (path.isEmpty) next().span else between(path.get(0), next()), path)
            } else if (check(TokenType.LBRACE)) {
                val lbrace = next()

                val items = Seq<UseStmt.UseItem>()
                while (true) {
                    if (match(TokenType.RBRACE)) {
                        break
                    }
                    val item = useItem()
                    if (item == null) {
                        break
                    } else {
                        items.add(item)
                    }
                    match(TokenType.COMMA)
                }
                return UseStmt.Multi(between((if (path.isEmpty) lbrace else path.get(0)), prevToken), path, items)
            } else {
                error("期望标识符、* 或 **")
                    .point(lookAhead(0), "")
                normalRecover()
                return null
            }
            if (!match(TokenType.DOT)) {
                return UseStmt.Single(between(path.get(0), prevToken), path)
            }
        }
    }

    private fun block(): Stmt {
        val lBrace = next()

        val stmts = Seq<Stmt>()
        while (!check(TokenType.RBRACE)) {
            if (this.isAtEnd) {
                error("期望`块`语句的`}`")
                    .point(lBrace, "开头")
                return BlockStmt(between(lBrace, prevToken), stmts)
            }
            stmts.add(statement())
        }
        val rbrace = next()

        return BlockStmt(between(lBrace, rbrace), stmts)
    }

    private fun ifStmt(): Stmt {
        val start = next()

        val condition = expression()

        if (expect(TokenType.LBRACE) == null) {
            return IfStmt(between(start, condition), condition, null, null)
        }
        val thenBranch = block()

        var elseBranch: Stmt? = null
        if (match(TokenType.ELIF)) {
            elseBranch = ifStmt()
        } else if (match(TokenType.ELSE)) {
            if (expect(TokenType.LBRACE) == null) {
                return IfStmt(between(start, condition), condition, thenBranch, null)
            }
            elseBranch = block()
        }

        return IfStmt(between(start, prevToken), condition, thenBranch, elseBranch)
    }

    private fun matchStmt(): Stmt {
        val start = next()

        val scrutinee = expression()

        if (consume(TokenType.LBRACE) == null) {
            error("期望`match`语句的`{`")
                .info(start, "语句开头")
                .point(lookAhead(0), "当前")
            return MatchStmt(between(start, scrutinee), scrutinee, null)
        }

        val branches = Seq<MatchStmt.MatchBranch>()
        while (!match(TokenType.RBRACE)) {
            if (this.isAtEnd) {
                error("期望`match`语句的`}`")
                    .info(start, "语句开头")
                    .point(lookAhead(0), "当前")
                return MatchStmt(
                    between(start, (if (branches.isEmpty) start else branches.get(branches.size - 1))),
                    scrutinee,
                    branches,
                )
            }
            val pattern = expression()
            if (consume(TokenType.ARROW) == null || consume(TokenType.LBRACE) == null) {
                return MatchStmt(
                    between(start, (if (branches.isEmpty) start else branches.get(branches.size - 1))),
                    scrutinee,
                    branches,
                )
            }
            val body = block()
            branches.add(MatchStmt.MatchBranch(between(pattern, prevToken), pattern, body))
        }
        return MatchStmt(
            between(start, prevToken),
            scrutinee,
            branches,
        )
    }

    private fun forStmt(): Stmt {
        val start = next()

        // for id
        if (check(TokenType.IDENTIFIER)) {
            val `var` = Expr.Identifier(next())
            val expr: Expr? = if (match("in")) {
                // for id in expr
                expression()
            } else {
                // for id
                null
            }

            if (expect(
                    TokenType.LBRACE,
                ) { e: Problem -> e.info(between(start, prevToken), "`for`语句") }
                == null
            ) {
                return ForStmt(between(start, prevToken), `var`, expr, null)
            }
            val body = block()

            return ForStmt(between(start, body), `var`, expr, body)

            // for repeatNum
        } else {
            val expr = expression()

            if (expect(
                    TokenType.LBRACE,
                ) { e: Problem -> e.info(between(start, expr), "`for`语句") } == null
            ) {
                return ForStmt(between(start, expr), null, expr, null)
            }
            val body = block()

            return ForStmt(between(start, body), null, expr, body)
        }
    }

    private fun whileStmt(): Stmt {
        val start = next()

        val expr = expression()

        if (expect(
                TokenType.LBRACE,
            ) { e: Problem -> e.info(between(start, expr), "`while`语句") } == null
        ) {
            return WhileStmt(between(start, expr), expr, null)
        } else {
            val body = block()
            return WhileStmt(between(start, body), expr, body)
        }
    }

    private fun functionStmt(): Stmt {
        val start = next()

        val name = consume(TokenType.IDENTIFIER)
        val lparen: Token?
        if (name == null) {
            if (!check(TokenType.LPAREN)) {
                return FnStmt(start.span, null, null, null, null)
            }
            lparen = next()
        } else {
            lparen = consume(TokenType.LPAREN)
            if (lparen == null) {
                return FnStmt(between(start, name), name, null, null, null)
            }
        }

        val parameters = Seq<Expr>()

        while (!match(TokenType.RPAREN)) {
            if (this.isAtEnd) {
                error("无法结束的`函数形参声明`")
                    .info(start, "函数声明开头")
                    .point(lookAhead(0), "末尾")
                return FnStmt(between(start, prevToken), name, null, null, null)
            }
            if (!check(TokenType.IDENTIFIER)) {
                error("期望`)`")
                    .info(lparen, "开头")
                    .point(lookAhead(0), "末尾")
                break
            }
            val parameter = annotation()
            parameters.add(parameter)
            match(TokenType.COMMA)
        }

        val results = Seq<Expr>()
        if (match(TokenType.ARROW)) {
            while (!check(TokenType.LBRACE)) {
                if (this.isAtEnd) {
                    if (results.isEmpty) {
                        error("无法找到`函数返回值声明`")
                            .info(start, "函数开头")
                            .point(lookAhead(0), "期望`标识符`或`_`")
                        return FnStmt(between(start, prevToken), name, parameters, null, null)
                    } else {
                        error("无法找到函数体")
                            .info(start, "函数开头")
                            .point(lookAhead(0), "期望`{`")
                        return FnStmt(between(start, prevToken), name, parameters, results, null)
                    }
                }
                if (!check(TokenType.IDENTIFIER)) {
                    break
                }
                results.add(annotation())
                match(TokenType.COMMA)
            }
        }

        if (expect(
                TokenType.LBRACE,
            ) { e: Problem -> e.info(between(start, prevToken), "`fn`语句") } == null
        ) {
            return FnStmt(between(start, prevToken), name, parameters, results, null)
        } else {
            val body = block()
            return FnStmt(between(start, body), name, parameters, results, body)
        }
    }

    private fun exprStmt(): Stmt? {
        if (check(TokenType.BREAK)) {
            val start = next()
            consumeStmtEnd()
            return BreakStmt(start.span)
        } else if (check(TokenType.CONTINUE)) {
            val start = next()
            consumeStmtEnd()
            return ContinueStmt(start.span)
        } else if (check(TokenType.RETURN)) {
            val start = next()
            if (matchStmtEnd()) {
                return ReturnStmt(start.span, null)
            }
            val expr = expression()
            consumeStmtEnd()
            return ReturnStmt(between(start, expr), expr)
        } else if (check(TokenType.SET)) {
            val start = next()

            val expr = expression()
            val assignStmt = assignStmt(expr)
            if (assignStmt == null) {
                consumeStmtEnd()
                return SetVarStmt(between(start, expr), expr, null)
            } else {
                // assignStmt(_)消耗了StmtEnd
                return SetVarStmt(between(start, assignStmt), expr, assignStmt)
            }
        } else {
            val expr = expression()
            if (expr is ErrorExpr) {
                normalRecover()
                return null
            }

            val assignStmt = assignStmt(expr)

            if (assignStmt == null) {
                consumeStmtEnd()
                return ExprStmt(expr.span, expr)
            } else {
                return assignStmt
            }
        }
    }

    /**
     * `=` ...
     * 复合赋值运算符`+=` ...
     * @param expr `=`或复合赋值运算符前的表达式
     * @return 没有`=`或者复合赋值运算符时返回null;有时返回AssignStmt
     */
    private fun assignStmt(expr: Expr): Stmt? {
        if (check(TokenType.ASSIGN)) {
            val operator = next()
            val value = expression()

            consumeStmtEnd()
            return AssignStmt(between(expr, value), expr, operator, value)
        } else if (check(TokenType.BINARY_OPERATORS)) {
            val operator = next()
            if (match(TokenType.ASSIGN)) {
                val value = expression()

                consumeStmtEnd()
                return AssignStmt(between(expr, value), expr, operator, value)
            }
            val right = expression()
            return ExprStmt(between(expr, right), Expr.Binary(expr, operator, right))
        }
        return null
    }

    // ---------- Expr ----------
    private fun expression(): Expr = or()

    private fun or(): Expr {
        var expr = and()

        while (check(TokenType.OR_OR)) {
            val operator = next()

            val right = or()
            if (right is Expr.Binary && right.operator.type == TokenType.AND_AND) {
                error("不明确关系的逻辑运算表达式，请添加括号")
                    .point(expr.span.start, right.span.end, "")
                // 此处遵循优先级and > or
            }
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        while (check(TokenType.AND_AND)) {
            val operator = next()

            val right = or()
            if (right is Expr.Binary && right.operator.type == TokenType.OR_OR) {
                error("不明确关系的逻辑运算表达式，请添加括号")
                    .point(expr.span.start, right.span.end, "")
                // expr && right.left || right.right
                // 按照优先级and > or进行重构
                // (expr && right.left) || right.right
                expr = Expr.Binary(
                    Expr.Binary(expr, operator, right.left),
                    right.operator,
                    right.right,
                )
            } else {
                expr = Expr.Binary(expr, operator, right)
            }
        }
        return expr
    }

    /**
     * == != === !==
     */
    private fun equality(): Expr {
        var expr = comparison()

        if (check(TokenType.EQ_EQ, TokenType.BANG_EQ, TokenType.EQ_EQ_EQ, TokenType.BANG_EQ_EQ)) {
            val operator = next()
            val right = comparison()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    /**
     * > >= < <=
     */
    private fun comparison(): Expr {
        var expr = range()

        if (check(TokenType.GREATER, TokenType.GREATER_EQ, TokenType.LESS, TokenType.LESS_EQ)) {
            val operator = next()
            val right = range()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun range(): Expr {
        // :< ...    := ...
        if (check(TokenType.COLON_LESS, TokenType.COLON_ASSIGN)) {
            val operator = next()

            // :<    :=
            if (!check(TokenType.LITERALS) && !check(TokenType.IDENTIFIER) && !check(TokenType.LPAREN)) {
                Expr.Range(operator.span, null, operator, null)
            }

            // :< expr    := expr
            val right = addAndSub()
            Expr.Range(between(operator, right), null, operator, right)
        }

        // expr
        var expr = addAndSub()

        // expr :< ...    expr := ...
        if (!this.isStmtEnd && check(TokenType.COLON_LESS, TokenType.COLON_ASSIGN)) {
            val operator = next()

            // expr :<    expr :=
            if (!check(TokenType.LITERALS) && !check(TokenType.IDENTIFIER) && !check(TokenType.LPAREN)) {
                Expr.Range(between(operator, operator), null, operator, null)
            }

            // expr :< expr    expr := expr
            val right = addAndSub()
            expr = Expr.Range(between(expr, right), expr, operator, right)
        }

        return expr
    }

    private fun addAndSub(): Expr {
        var expr = mulAndDiv()

        while (check(TokenType.PLUS, TokenType.MINUS)) {
            val operator = next()
            val right = mulAndDiv()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun mulAndDiv(): Expr {
        var expr = pow()

        while (check(TokenType.STAR, TokenType.SLASH)) {
            val operator = next()
            val right = pow()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun pow(): Expr {
        var expr = unary()

        if (check(TokenType.STAR_STAR)) {
            val operator = next()
            val right = pow() // 右结合
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun unary(): Expr {
        if (check(TokenType.BANG, TokenType.MINUS)) {
            val operator = next()
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return suffixExpr()
    }

    private fun suffixExpr(): Expr {
        var expr = primary()

        while (true) {
//            if(isStmtEnd()) return expr;

            if (check(TokenType.LBRACKET)) { // 对列表的索引或切片
                val lBracket = next()

                val index = expression()
                val rBracket =
                    consume(TokenType.RBRACKET) { e: Problem -> e.info(lBracket, "解析`数组索引`时出现错误") }

                expr = if (rBracket != null) {
                    Expr.Index(between(lBracket, rBracket), expr, index)
                } else {
                    Expr.Index(between(lBracket, index), expr, index)
                }
                continue
            } else if (check(TokenType.LPAREN)) { // 函数调用
                val lParen = next()

                val arguments = Seq<Expr>()
                while (true) {
                    if (this.isAtEnd) {
                        error("无法结束的`函数传参`")
                            .info(lParen, "参数开头")
                            .point(lookAhead(0), "末尾")
                        expr = if (arguments.isEmpty) {
                            Expr.Call(expr.span, expr, arguments)
                        } else {
                            Expr.Call(
                                between(expr, arguments.get(arguments.size - 1)),
                                expr,
                                arguments,
                            )
                        }
                        break
                    }
                    if (check(TokenType.RPAREN)) {
                        expr = Expr.Call(between(expr, next()), expr, arguments)
                        break
                    }

                    arguments.add(expression())
                    match(TokenType.COMMA) // 可选逗号
                }
                continue
            } else if (check(TokenType.DOT)) { // 访问类的元素
                val dot = next()

                val id =
                    consume(TokenType.IDENTIFIER) { e: Problem -> e.info(dot, "解析`类元素访问`时出现错误") }
                if (id == null) return expr
                val field: Expr = Expr.Identifier(id)

                expr = Get(expr, field)
                continue
            }

            return expr
        }
    }

    private fun primary(): Expr {
        if (check(TokenType.LITERALS)) {
            val literal = next()

            // lit :
            if (!this.isStmtEnd && match(TokenType.COLON)) {
                val annotations = Seq<Expr>()

                // lit : ?
                if (check(TokenType.QUESTION_MARK)) {
                    annotations.add(Expr.Literal(Token(next().span, TokenType.NULL)))
                }

                // lit : anno1 | anno2 ...
                while (!this.isAtEnd) {
                    annotations.add(unary())
                    if (!match(TokenType.OR)) break
                }
                if (annotations.isEmpty) return Expr.Annotation(Expr.Literal(literal), annotations)
                // 如果标注数量为0，视作Literal
            }
            return Expr.Literal(literal)
        } else if (check(TokenType.IDENTIFIER)) {
            val id = next()

            // id :
            if (!this.isStmtEnd && match(TokenType.COLON)) {
                val annotations = Seq<Expr>()

                // id : ?
                if (check(TokenType.QUESTION_MARK)) {
                    annotations.add(Expr.Literal(Token(next().span, TokenType.NULL)))
                }

                // id : anno1 | anno2 ...
                while (!this.isAtEnd) {
                    annotations.add(unary())
                    if (!match(TokenType.OR)) break
                }
                if (annotations.isEmpty) return Expr.Annotation(Expr.Identifier(id), annotations)
                // 如果标注数量为0，视作Identifier
            }
            return Expr.Identifier(id)
        } else if (match(TokenType.LPAREN)) {
            val expr = expression()
            consume(TokenType.RPAREN)
            return expr
        } else if (check(TokenType.LBRACE)) {
            val lBrace = next()
            val elements = Seq<Expr>()
            while (!check(TokenType.RBRACE)) {
                if (this.isAtEnd) {
                    error("无法结束的数组")
                        .info(lBrace, "数组开头")
                        .point(lookAhead(0), "末尾")
                }
                //                try {
                elements.add(expression())
                //                } catch(Problem.ParserProblem e) {
//                    e.info(lBrace, "解析`数组`时出现错误");
//                    elements.add(new Literal(token(ERROR, lookAhead())));
//                }
                match(TokenType.COMMA) // 可选逗号
            }
            val rBrace = next()
            return Expr.Array(between(lBrace, rBrace), elements)
        }

        error("期望表达式").point(lookAhead(0), "")
        return ErrorExpr(prevToken.span)
    }

    /**
     * 比较特殊，专门给FnStmt用的，只可能返回Identifier或Annotation
     */
    private fun annotation(): Expr {
        val id = next()

        // id :
        if (!this.isStmtEnd && match(TokenType.COLON)) {
            val annotations = Seq<Expr>()

            // id : ?
            if (check(TokenType.QUESTION_MARK)) {
                annotations.add(Expr.Literal(Token(next().span, TokenType.NULL)))
            }

            // id : anno1 | anno2 ...
            while (!this.isAtEnd) {
                annotations.add(unary())
                if (!match(TokenType.OR)) break
            }
            if (!annotations.isEmpty) return Expr.Annotation(Expr.Identifier(id), annotations)
            // 如果标注数量为0，视作Identifier
        }
        return Expr.Identifier(id)
    }

    // ========== 工具方法 ==========
    // ---------- Token基础方法 ----------

    /**
     * 向前推进一个token
     */
    private fun next(): Token {
        prevToken = input.next()
        return prevToken
    }

    /**
     * 前瞻token
     * @param index 0表示当前token，1表示下一个token，2表示下下个token，依次类推
     */
    private fun lookAhead(index: Int): Token =
        input.lookAhead(index)

    /**
     * 检查是否为文件结尾
     * @return 下一个Token为`EOF`时返回true，自动消耗NEWLINE
     */
    private val isAtEnd: Boolean
        get() {
            val nextType = lookAhead(0).type
            if (nextType == TokenType.NEWLINE) {
                // 第二个不会是NEWLINE，由Lexer.scanToken()保证
                if (lookAhead(1).type == TokenType.EOF) {
                    next() // 跳过NEWLINE
                    return true
                }
                return false
            }
            return nextType == TokenType.EOF
        }

    /**
     * 检查是否为语句结尾
     * @return 下一个Token为`NEWLINE`或`SEMICOLON`或`EOF`时返回true
     */
    private val isStmtEnd: Boolean
        get() {
            val peekType = lookAhead(0).type
            return peekType == TokenType.NEWLINE || peekType == TokenType.SEMICOLON || peekType == TokenType.EOF
        }

    /**
     * 不支持NEWLINE
     */
    private fun check(type: TokenType): Boolean {
        val nextType = lookAhead(0).type
        if (nextType == TokenType.NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()保证
            next()
            nextType = lookAhead(0).type
        }
        return nextType == type
    }

    /**
     * 下一个Token的类型在参数中，则返回true
     * 不支持NEWLINE
     */
    private fun check(types: MutableSet<TokenType>): Boolean {
        val nextType = lookAhead(0).type
        if (nextType == TokenType.NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()保证
            next()
            nextType = lookAhead(0).type
        }
        return types.contains(nextType)
    }

    /**
     * 下一个Token.literal等于参数，则返回true
     * 不支持NEWLINE
     */
    private fun check(text: String): Boolean {
        val next = lookAhead(0)
        if (next.type == TokenType.NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()保证
            next()
            next = lookAhead(0)
        }
        return text == next.literal
    }

    /**
     * 下一个Token类型是参数之一时，则返回true
     * 不支持NEWLINE
     */
    private fun check(vararg types: TokenType): Boolean {
        val nextType = lookAhead(0).type
        if (nextType == TokenType.NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()保证
            next()
            nextType = lookAhead(0).type
        }
        for (expected in types) {
            if (nextType == expected) return true
        }
        return false
    }

    private fun checkLoopFlag(): Identifier? {
        val id = lookAhead(0)
        if (id.type == NEWLINE) {
            // 第二个不会是NEWLINE，由Lexer.scanToken()保证
            next()
            id = lookAhead(0)
        }
        if (id.type != IDENTIFIER) return null

        var i = 1

        val colon = lookAhead(i)
        if (colon.type == NEWLINE) {
            i++
            colon = lookAhead(i)
        }
        if (colon.type != COLON) return null
        i++

        val loopHead = lookAhead(i)
        if (loopHead.type == NEWLINE) {
            loopHead = lookAhead(i + 1)
        }
        if (loopHead.type != FOR || 
            loopHead.type != WHILE
        ) return null

        return id
    }

    private fun match(type: TokenType): Boolean {
        if (check(type)) {
            next()
            return true
        }
        return false
    }

    private fun match(types: MutableSet<TokenType>): Boolean {
        if (check(types)) {
            next()
            return true
        }
        return false
    }

    private fun match(vararg types: TokenType): Boolean {
        if (check(*types)) {
            next()
            return true
        }
        return false
    }

    private fun match(text: String): Boolean {
        if (check(text)) {
            next()
            return true
        }
        return false
    }

    private fun match(vararg texts: String): Boolean {
        for (text in texts) {
            if (check(text)) {
                next()
                return true
            }
        }
        return false
    }

    private fun match(vararg types: TokenType): Boolean {

    }

    // ---------- Token高级方法 ----------

    /**
     * 若下一个token不是指定类型的则返回null并报告错误，否则返回该token。
     */
    private fun expect(type: TokenType): Token? {
        if (check(type)) return lookAhead(0)

        error("期望$type")
            .point(lookAhead(0), type.toString())
        return null
    }

    /**
     * 若下一个token不是指定类型的则返回null并报告错误并将错误输入Cons，否则返回该token。
     */
    private fun expect(type: TokenType, cons: Cons<Problem>): Token? {
        if (check(type)) return lookAhead(0)

        cons.get(
            error("期望$type")
                .point(lookAhead(0), type.toString()),
        )
        return null
    }

    /**
     * 检查 ; \n EOF 作为语句结束符，若有则推进；
     * 检查 { } 作为语句结束符，不推进；
     * 不报错。
     */
    private fun matchStmtEnd(): Boolean {
        val peekType = lookAhead(0).type
        if (peekType == TokenType.NEWLINE || peekType == TokenType.SEMICOLON || peekType == TokenType.EOF) {
            next()
            return true
        }
        // 检查 { } 作为语句结束符，不推进
        return peekType == TokenType.LBRACE || peekType == TokenType.RBRACE
    }

    /**
     * 检查 ; \n EOF 作为语句结束符，推进；
     * 检查 { } 作为语句结束符，不推进；
     * 都没有则报错。
     */
    private fun consumeStmtEnd() {
        val peekType = lookAhead(0).type
        if (peekType == TokenType.NEWLINE || peekType == TokenType.SEMICOLON || peekType == TokenType.EOF) {
            next()
            return
        }
        if (peekType == TokenType.LBRACE || peekType == TokenType.RBRACE) {
            return
        }
        // 如果没有找到，报告错误
        error("缺少换行或分号作为语句结束符")
            .point(lookAhead(0), "")
    }

    /**
     * 若下一个token不是指定类型的则报告错误并返回null，否则返回该token并推进
     */
    private fun consume(type: TokenType): Token? {
        if (check(type)) return next()

        error("期望$type")
            .point(lookAhead(0), type.toString())
        return null
    }

    /**
     * 若下一个token不是指定类型的则报告错误并返回null，否则返回该token并推进
     */
    private fun consume(type: TokenType, cons: Cons<Problem>): Token? {
        if (check(type)) return next()

        cons.get(
            error("期望$type")
                .point(lookAhead(0), type.toString()),
        )
        return null
    }

    //    private Res<Token> consume(Set<TokenType> types) {
    //        if(check(types)) return new Ok<> (next());
    //        StringBuilder sbd = new StringBuilder();
    //        for(TokenType type : types) {
    //            sbd.append(type.toString()).append(" ");
    //        }
    //        return new Err<>(error("期望TokenType")
    //                .point(lookAhead(), sbd.toString())
    //        );
    //    }
    //
    //    private Res<Token> consume(TokenType type, Runnable r) {
    //        if(check(type)) return new Ok<>(next());
    //        Problem.ParserProblem e = (Problem.ParserProblem) error("期望字符").point(lookAhead(), type.toString());
    //        r.run();
    //        return new Err<>(e);
    //    }
    // ---------- 错误恢复方法 ----------

    /**
     * 错误恢复，扫描直到期望的TokenType
     */
    private fun recover(expected: TokenType) {
        while (!this.isAtEnd) {
            if (check(expected)) {
                return
            }
            next()
        }
    }

    /**
     * 错误恢复，扫描直到期望的TokenType
     */
    private fun recover(expecteds: MutableSet<TokenType>) {
        while (!this.isAtEnd) {
            if (check(expecteds)) {
                return
            }
            next()
        }
    }

    /**
     * 等价于recover(EnumSet.of(USE, IF, MATCH, FOR, WHILE, FN, LBRACE))
     */
    private fun normalRecover() {
     * 等价于recover(EnumSet.of(USE, IF, MATCH, FOR, WHILE, FN, LBRACE))
     */
    private fun normalRecover() {
     * 等价于recover(EnumSet.of(USE, IF, MATCH, FOR, WHILE, FN, LBRACE))
     */
    private fun normalRecover() {
        recover(
            EnumSet.of(
                TokenType.USE,
     * 等价于recover(EnumSet.of(USE, IF, MATCH, FOR, WHILE, FN, LBRACE))
     */
    private fun normalRecover() {
        recover(
            EnumSet.of(
                TokenType.USE,
                TokenType.IF,
                TokenType.MATCH,
                TokenType.FOR,
                TokenType.WHILE,
                TokenType.FN,
                TokenType.LBRACE,
            )
        )
    }

    // ---------- 类生成方法 ----------
    private fun token(type: TokenType, from: Token): Token {
        return Token(from.span, type)
    }

    /**
     * 生成当前文件的span
     * @param from 起始
     * @param to 末尾
     */
    private fun between(from: Spanned, to: Spanned): Span {
        return Span(sourceMap.index, from.span().start, to.span().end)
    }

    private fun error(text: String): ParserProblem {
        val e = ParserProblem(sourceMap, text, Problem.ProblemLevel.ERROR)
        collector.addError(e)
        return e
    }

    private fun warning(text: String): ParserProblem {
        val w = ParserProblem(sourceMap, text, Problem.ProblemLevel.WARNING)
        collector.addWarning(w)
        return w
    }

    // ========== 工具类 ==========
    private inner class LookAheadWindow {
        private val capacity: Int = 5
        private val buffer = Queue<Token>(capacity)

        fun next(): Token {
            if (buffer.size < 1) return lexer.scanToken()
            return buffer.removeFirst()
        }

        fun lookAhead(index: Int): Token {
            require(index in 0 until capacity) { "index must be between 0 and ${capacity - 1}" }
            for (i in 0..index - buffer.size) {
                buffer.addLast(lexer.scanToken())
            }
            return buffer.get(index)
        }
    }
}
