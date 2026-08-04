package mlogix.compiler.analyzer

import arc.struct.ObjectMap
import arc.struct.Seq
import mlogix.compiler.SourceMapManager.SourceMap
import mlogix.compiler.type.Type
import mlogix.compiler.type.TypeVar
import mlogix.compiler.type.FunctionType
import mlogix.problem.ProblemCollector
import mlogix.problem.Problem
import mlogix.span.Span

/**
 * A small union-find based TypeSolver to solve Equal constraints.
 * This is intentionally minimal: supports TypeVar binding and decomposition for FunctionType
 */
class TypeSolver(private val problems: ProblemCollector, private val sourceMap: SourceMap) {
    private val parent = ObjectMap<String, String>()
    private val rootType = ObjectMap<String, Type>()
    private var counter = 0

    fun freshVar(): TypeVar {
        val id = "t${counter++}"
        return TypeVar(id)
    }

    private fun find(v: String): String {
        val p = parent.get(v) ?: return v
        if (p == v) return v
        val r = find(p)
        parent.put(v, r)
        return r
    }

    private fun unionVars(v1: String, v2: String) {
        val r1 = find(v1)
        val r2 = find(v2)
        if (r1 == r2) return
        // attach r2 -> r1
        parent.put(r2, r1)
        val t1 = rootType.get(r1)
        if (t1 == null) rootType.put(r1, rootType.get(r2))
        rootType.remove(r2)
    }

    fun bind(varName: String, ty: Type) {
        val root = find(varName)
        rootType.put(root, ty)
    }

    fun read(t: Type): Type {
        if (t is TypeVar) {
            val root = find(t.id)
            val rt = rootType.get(root)
            return rt ?: t
        }
        return t
    }

    private fun walk(t: Type): Type {
        if (t is TypeVar) {
            val root = find(t.id)
            val rt = rootType.get(root)
            return if (rt == null) TypeVar(root) else walk(rt)
        }
        return t
    }

    fun solveEqualities(constraints: Seq<Constraint>) {
        for (c in constraints) {
            if (c is Constraint.Equal) {
                unify(c.t1, c.t2, c.pos)
            }
        }
    }

    private fun unify(a: Type, b: Type, pos: Span?) {
        val t1 = walk(a)
        val t2 = walk(b)
        if (t1 is TypeVar && t2 is TypeVar) {
            unionVars(t1.id, t2.id)
            return
        }
        if (t1 is TypeVar) {
            // occurs check naive: avoid binding var to a type containing itself by string match
            if (t2.toString().contains(t1.id)) {
                val e =
                    Problem.SemanticProblem(sourceMap, "Occurs check failed for ${t1.id}", Problem.ProblemLevel.ERROR)
                problems.addError(e)
                e.point(pos ?: Span(sourceMap.index, 0, 0), "")
                return
            }
            bind(t1.id, t2)
            return
        }
        if (t2 is TypeVar) {
            if (t1.toString().contains(t2.id)) {
                val e =
                    Problem.SemanticProblem(sourceMap, "Occurs check failed for ${t2.id}", Problem.ProblemLevel.ERROR)
                problems.addError(e)
                e.point(pos ?: Span(sourceMap.index, 0, 0), "")
                return
            }
            bind(t2.id, t1)
            return
        }

        // both are concrete types: handle function types specially, otherwise accept if names match
        if (t1 is FunctionType && t2 is FunctionType) {
            // parameter count must match
            if (t1.paramTypes.size != t2.paramTypes.size) {
                val e = Problem.SemanticProblem(
                    sourceMap,
                    "函数参数数量不匹配: ${t1.paramTypes.size} != ${t2.paramTypes.size}",
                    Problem.ProblemLevel.ERROR
                )
                problems.addError(e)
                e.point(pos ?: Span(sourceMap.index, 0, 0), "")
                return
            }
            // unify each corresponding parameter and the return type
            for (i in 0 until t1.paramTypes.size) {
                unify(t1.paramTypes.get(i), t2.paramTypes.get(i), pos)
            }
            unify(t1.returnType, t2.returnType, pos)
            return
        }

        if (t1.name != t2.name) {
            val e =
                Problem.SemanticProblem(sourceMap, "类型不匹配: ${t1.name} != ${t2.name}", Problem.ProblemLevel.ERROR)
            problems.addError(e)
            e.point(pos ?: Span(sourceMap.index, 0, 0), "")
        }
    }
}


