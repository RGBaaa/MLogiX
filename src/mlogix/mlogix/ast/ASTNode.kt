package mlogix.mlogix.ast

import mlogix.span.Span
import mlogix.span.Spanned

abstract class ASTNode(var span: Span) : Spanned {

    override fun span(): Span {
        return span
    }
}
