package mlogix.compiler.analyzer

import mlogix.compiler.type.Type
import mlogix.span.Span

sealed class Constraint(open val pos: Span?) {
    data class Equal(val t1: Type, val t2: Type, override val pos: Span? = null) : Constraint(pos)
    data class Subtype(val sub: Type, val sup: Type, override val pos: Span? = null) : Constraint(pos)
    data class Implicit(val cls: String, val t: Type, override val pos: Span? = null) : Constraint(pos)
}

