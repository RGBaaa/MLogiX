package mlogix.compiler.type

import arc.struct.Seq

/**
 * A simple FunctionType wrapper to represent function signatures.
 * This is intentionally lightweight: parameters and return types are stored as Types.
 */
class FunctionType(val paramTypes: Seq<Type?>, var returnType: Type?) : Type("Fn") {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Fn(")
        for ((i, element) in paramTypes.withIndex()) {
            sb.append(element!!.name)
            if (i < paramTypes.size - 1) sb.append(", ")
        }
        sb.append(") -> ").append(if (returnType == null) "?" else returnType!!.name)
        return sb.toString()
    }
}

