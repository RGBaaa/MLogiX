package mlogix.mlogix.ast

import arc.struct.Seq
import mlogix.mlogix.token.Token
import mlogix.span.Span

//Expression
abstract class Expr constructor(span: Span) : ASTNode(span) {
    init {
        this.span = span
    }

    /* 字面量 */
    class Literal(val token: Token) : Expr(token.span)

    /* 标识符 */
    class Identifier(val token: Token) : Expr(token.span)

    class Annotation
    /**
     * 需要保证annotations.size > 0
     */(val expr: Expr, val annotations: Seq<Expr>) : Expr(Span.between(expr, annotations.get(annotations.size - 1)))

    /* 一元运算 */
    class Unary(val operator: Token, val expr: Expr) : Expr(Span.between(operator.span, expr.span))

    /* 二元运算 */
    class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr(
        Span.between(
            left.span, right.span
        )
    )

    /* 数组 */
    class Array(span: Span, val elements: Seq<Expr>) : Expr(span)

    /* 索引 */
    class Index(span: Span, val list: Expr, val index: Expr) : Expr(span)

    class Range(span: Span, val left: Expr?, val operator: Token, val right: Expr?) : Expr(span)

    /* 函数调用 func(...) */
    class Call(span: Span, val callee: Expr, val arguments: Seq<Expr>) : Expr(span)

    /* 获取字段 type.field  type.func */
    class Get(val obj: Expr, val field: Expr) : Expr(Span.between(obj.span, field.span))

    /**
     * 错误恢复占位符
     */
    class ErrorExpr(span: Span) : Expr(span)
}