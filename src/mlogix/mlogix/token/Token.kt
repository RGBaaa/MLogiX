package mlogix.mlogix.token

import mlogix.span.Span
import mlogix.span.Spanned

class Token(
    val span: Span,
    val type: TokenType,
    val literal: Any? = null
) : Spanned {

    override fun toString(): String =
        "Token{${type.name},${span},$literal}"

    fun toSimpleString(): String =
        "Token(${type.name},$literal)"

    override fun span(): Span {
        return span
    }
}