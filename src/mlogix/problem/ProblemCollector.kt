package mlogix.problem

import arc.util.Log
import java.util.function.Consumer

class ProblemCollector {
    val errors = ArrayList<Problem>()
    val warnings = ArrayList<Problem>()

    fun createSnapshot(): ProblemCollectorSnapshot {
        return ProblemCollectorSnapshot(errorNum(), warningNum())
    }

    fun restoreSnapshot(snapshot: ProblemCollectorSnapshot) {
        errors.subList(snapshot.errorNum, errorNum()).clear()
        warnings.subList(snapshot.warningNum, warningNum()).clear()
    }

    fun hasError(): Boolean {
        return !errors.isEmpty()
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

    data class ProblemCollectorSnapshot(val errorNum: Int, val warningNum: Int)
}
