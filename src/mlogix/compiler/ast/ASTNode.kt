package mlogix.compiler.ast

import mlogix.span.Span
import mlogix.span.Spanned

abstract class ASTNode(open val span: Span) : Spanned {

    override fun span(): Span {
        return span
    }
}
