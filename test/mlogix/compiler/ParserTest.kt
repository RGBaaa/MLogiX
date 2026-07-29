package mlogix.compiler

import mlogix.mlogix.ast.Expr
import mlogix.mlogix.ast.Stmt
import mlogix.mlogix.token.Token
import mlogix.mlogix.token.TokenType
import mlogix.problem.ProblemCollector
import mlogix.span.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParserTest {
    val problems = ProblemCollector()
    val lexer = Lexer(problems)
    val parser = Parser(lexer, problems)

    val span = Span(0, 0, 0)

    @Test
    fun `parse correct AST for addition`() {
        val ast = parser.parse("2 + 3")
        assertEquals(
            ast,
            Stmt.ExprStmt(
                span,
                Expr.Binary(
                    Expr.Literal(token(TokenType.INT, 2.0)),
                    token(TokenType.PLUS),
                    Expr.Literal(token(TokenType.INT, 3.0))
                )
            )
        )

    }

    @Test
    fun `parse set assignment`() {
        val ast = parser.parse("set a = 1")

        val varExpr = Expr.Identifier(token(TokenType.IDENTIFIER, "a"))
        val assign = Stmt.AssignStmt(
            span,
            varExpr,
            token(TokenType.ASSIGN),
            Expr.Literal(token(TokenType.INT, 1.0))
        )
        val expected = Stmt.SetVarStmt(span, varExpr, assign)
        assertEquals(ast, expected)
    }

    @Test
    fun `parse if else`() {
        val ast = parser.parse("if 1 { 2 } else { 3 }")

        val condition = Expr.Literal(token(TokenType.INT, 1.0))
        val thenBranch = Stmt.BlockStmt(span, listOf(Stmt.ExprStmt(span, Expr.Literal(token(TokenType.INT, 2.0)))))
        val elseBranch = Stmt.BlockStmt(span, listOf(Stmt.ExprStmt(span, Expr.Literal(token(TokenType.INT, 3.0)))))

        val expected = Stmt.IfStmt(span, condition, thenBranch, elseBranch)
        assertEquals(ast, expected)
    }

    @Test
    fun `parse for repeat loop`() {
        val ast = parser.parse("for 3 { 1 }")

        val body = Stmt.BlockStmt(span, listOf(Stmt.ExprStmt(span, Expr.Literal(token(TokenType.INT, 1.0)))))
        val expected = Stmt.ForStmt(span, null, null, body, Expr.Literal(token(TokenType.INT, 3.0)))
        assertEquals(ast, expected)
    }

    @Test
    fun `parse function declaration`() {
        val ast = parser.parse("fn add(a b) -> ? Num|Int { return a + b }")

        val name = token(TokenType.IDENTIFIER, "add")
        val aParam = Expr.Identifier(token(TokenType.IDENTIFIER, "a"))
        val bParam = Expr.Identifier(token(TokenType.IDENTIFIER, "b"))
        val results = listOf(
            Expr.Literal(token(TokenType.NULL)),
            Expr.Identifier(token(TokenType.IDENTIFIER, "Num")),
            Expr.Identifier(token(TokenType.IDENTIFIER, "Int"))
        )
        val body = Stmt.BlockStmt(
            span, listOf(
                Stmt.ReturnStmt(
                    span, Expr.Binary(
                        Expr.Identifier(token(TokenType.IDENTIFIER, "a")),
                        token(TokenType.PLUS),
                        Expr.Identifier(token(TokenType.IDENTIFIER, "b"))
                    )
                )
            )
        )

        val expectedFn = Stmt.FnStmt(span, name, listOf(aParam, bParam), results, body)
        assertEquals(ast, expectedFn)
    }

    @Test
    fun `parse function call`() {
        val ast = parser.parse("add(1, 2)")
        val call = Expr.Call(
            span, Expr.Identifier(token(TokenType.IDENTIFIER, "add")), listOf(
                Expr.Literal(token(TokenType.INT, 1.0)), Expr.Literal(token(TokenType.INT, 2.0))
            )
        )
        assertEquals(ast, Stmt.ExprStmt(span, call))
    }

    @Test
    fun `parse set with annotation and array literal`() {
        val ast = parser.parse("set a : Int = {1, 2}")

        val varExpr = Expr.Annotation(
            Expr.Identifier(token(TokenType.IDENTIFIER, "a")),
            listOf(Expr.Identifier(token(TokenType.IDENTIFIER, "Int")))
        )
        val array = Expr.Array(
            span, listOf(
                Expr.Literal(token(TokenType.INT, 1.0)), Expr.Literal(token(TokenType.INT, 2.0))
            )
        )
        val assign = Stmt.AssignStmt(span, varExpr, token(TokenType.ASSIGN), array)
        val expected = Stmt.SetVarStmt(span, varExpr, assign)
        assertEquals(ast, expected)
    }

    @Test
    fun `parse match`() {
        val ast = parser.parse("match 1 { 1 -> { break } }")
        val matchBranchBody = Stmt.BlockStmt(span, listOf(Stmt.BreakStmt(span, null)))
        val branch = Stmt.MatchStmt.MatchBranch(span, Expr.Literal(token(TokenType.INT, 1.0)), matchBranchBody)
        val expectedMatch = Stmt.MatchStmt(span, Expr.Literal(token(TokenType.INT, 1.0)), listOf(branch))
        assertEquals(ast, expectedMatch)
    }

    @Test
    fun `parse while`() {
        val ast = parser.parse("while true { continue }")
        val expectedWhile = Stmt.WhileStmt(
            span,
            null,
            Stmt.BlockStmt(span, listOf(Stmt.ContinueStmt(span, null))),
            Expr.Literal(token(TokenType.INT, 0.0))
        )
        assertEquals(ast, expectedWhile)
    }

    private fun token(type: TokenType, literal: Any? = null): Token {
        return Token(Span(0, 0, 0), type, literal)
    }

    private fun assertEquals(actual: Stmt, vararg stmts: Stmt) {
        assertEquals(Stmt.Program(span, listOf(*stmts)), actual, "AST 结构必须匹配")
    }
}
