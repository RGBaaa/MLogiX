package mlogix.problem

import arc.struct.Seq
import arc.util.Log
import java.util.function.Consumer

class ProblemCollector {
    val errors: Seq<Problem> = Seq<Problem>()
    val warnings: Seq<Problem> = Seq<Problem>()

    fun hasError(): Boolean {
        return !errors.isEmpty
    }

    fun errorNum(): Int {
        return errors.size
    }

    fun warningNum(): Int {
        return warnings.size
    }

    fun addError(error: Problem) {
        errors.add(error)
    }

    fun addWarning(warning: Problem) {
        warnings.add(warning)
    }

    fun printError() {
        errors.forEach(Consumer { e: Problem -> Log.err(e.toString()) })
    }

    fun printWarning() {
        warnings.forEach(Consumer { w: Problem -> Log.warn(w.toString()) })
    }

    fun clear() {
        errors.clear()
        warnings.clear()
    }
}
