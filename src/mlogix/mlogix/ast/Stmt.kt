package mlogix.mlogix.ast

import arc.struct.Seq
import mlogix.mlogix.token.Token
import mlogix.span.Span
import mlogix.span.Spanned

//Statement
abstract class Stmt(span: Span) : ASTNode(span) {
    init {
        this.span = span
    }

    class Program(span: Span, val stmts: Seq<Stmt>) : Stmt(span)

    class UseStmt(span: Span, val item: UseItem) : Stmt(span) {

        abstract class UseItem(val span: Span) : Spanned {
            override fun span(): Span {
                return this.span
            }

        }

        class Single(span: Span, val path: Seq<Expr.Identifier>) : UseItem(span)

        // *
        class All(span: Span, val path: Seq<Expr.Identifier>) : UseItem(span)

        // **
        class Recursion(span: Span, val path: Seq<Expr.Identifier>) : UseItem(span)

        // {...}
        class Multi(span: Span, val path: Seq<Expr.Identifier>, val items: Seq<UseItem>) : UseItem(span)
    }

    class BlockStmt(span: Span, val stmts: Seq<Stmt>) : Stmt(span)

    class ExprStmt(span: Span, val expr: Expr) : Stmt(span)

    class IfStmt(span: Span, val condition: Expr, val thenBranch: Stmt?, val elseBranch: Stmt?) : Stmt(span)

    class MatchStmt(span: Span, val scrutinee: Expr, val branches: Seq<MatchBranch>?) : Stmt(span) {
        class MatchBranch(val span: Span, val pattern: Expr, val body: Stmt) : Spanned {
            override fun span(): Span {
                return this.span
            }
        }
    }

    class ForStmt(
        span: Span,
        val flag: Expr.Identifier?,
        val varDecl: Expr.Identifier?,
        val body: Stmt?,
        val expr: Expr?
    ) : Stmt(span)

    class WhileStmt(span: Span, val flag: Expr.Identifier?, val body: Stmt?, val expr: Expr) : Stmt(span)

    class BreakStmt(span: Span, flag: Expr.Identifier?) : Stmt(span)

    class ContinueStmt(span: Span, flag: Expr.Identifier?) : Stmt(span)

    class FnStmt(
        span: Span,
        val name: Token?,
        val parameters: Seq<Expr>?,
        val results: Seq<Expr>?,
        val body: Stmt?
    ) : Stmt(span)

    class ReturnStmt(span: Span, val expr: Expr?) : Stmt(span)

    class AssignStmt(span: Span, val `var`: Expr, val operator: Token, val value: Expr) : Stmt(span)

    class SetVarStmt(span: Span, val `var`: Expr, val assignStmt: Stmt?) : Stmt(span)
}