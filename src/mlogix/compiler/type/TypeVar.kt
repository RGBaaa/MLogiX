package mlogix.compiler.type

/**
 * Simple type variable for constraint-based inference.
 */
class TypeVar(val id: String) : Type("Var($id)") {
    override fun toString(): String {
        return "Var($id)"
    }
}

