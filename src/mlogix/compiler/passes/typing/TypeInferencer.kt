package mlogix.compiler.passes.typing

import arc.struct.Seq
import mlogix.compiler.ast.Expr
import mlogix.compiler.ast.Stmt
import mlogix.compiler.core.SourceMapManager.SourceMap
import mlogix.compiler.core.span.Span
import mlogix.compiler.core.symbol.SymbolTable
import mlogix.compiler.core.token.Token
import mlogix.compiler.core.token.TokenType
import mlogix.compiler.core.type.BuiltinType
import mlogix.compiler.core.type.Type
import mlogix.compiler.diagnostic.Problem
import mlogix.compiler.diagnostic.Problem.SemanticProblem
import mlogix.compiler.diagnostic.ProblemCollector
import mlogix.compiler.ir.ResolutionResult

/**
 * 类型推断：约束生成 + 惰性求解（HM 风格）。
 *
 * - **DefId 中心**：Resolver 已把每个 `Identifier` 解析为 [mlogix.compiler.core.symbol.DefId]，
 *   本 Pass 只通过 [SymbolTable]（Map<DefId, Symbol>）读写符号类型，绝不按名称查表。
 * - **sealed 类型**：所有类型比较用结构相等（`==`），类型变量为 [Type.Var]（Int 索引）。
 *
 * 一个项目 一次构造；一个文件 一次 [analyze]。
 */
class TypeInferencer(val problems: ProblemCollector) {
    private lateinit var sourceMap: SourceMap
    private lateinit var symbolTable: SymbolTable
    private lateinit var solver: TypeSolver
    private val constraints = Seq<Constraint>(0)

    /** 当前函数返回上下文栈：期望返回类型 + 函数声明位置（用于 return 不匹配报错的声明方 info） */
    private val returnContextStack = Seq<ReturnContext>(0)

    // ========== 执行类型推断 ==========
    /**
     * 一个文件 一次分析
     *
     * @param result Resolver 的输出（作用域树 + 符号表），其中 AST 标识符已带 defId
     * @param sourceMap 当前源文件位置映射
     */
    fun analyze(result: ResolutionResult, sourceMap: SourceMap) {
        this.sourceMap = sourceMap
        this.symbolTable = result.symbolTable

        // prepare solver
        solver = TypeSolver(problems, sourceMap)
        constraints.clear()

        // walk AST and collect constraints
        analyzeStmt(result.ast)

        // solve collected constraints
        solver.solveEqualities(constraints)

        // propagate solved inferred types back to symbols
        for (symbol in symbolTable.all()) {
            val inferred = symbol.values.get("inferred") as? Type
            if (inferred != null) {
                val final = solver.read(inferred)
                if (final !is Type.Var) {
                    // update symbol type if previously Unknown
                    if (symbol.type == BuiltinType.Unknown) symbol.type = final
                    symbol.values.put("final", final)
                }
            }
        }
    }

    private fun analyzeStmt(stmt: Stmt?) {
        if (stmt == null) return
        when (stmt) {
            is Stmt.Program -> {
                // program: process each top-level statement
                for (s in stmt.stmts) analyzeStmt(s)
            }

            is Stmt.UseStmt -> {
                // imports / use ignored by current analyzer
            }

            is Stmt.BlockStmt -> {
                for (s in stmt.stmts) analyzeStmt(s)
            }

            is Stmt.ExprStmt -> {
                val expr = inferExpr(stmt.expr)
                for (c in expr.constraints) constraints.add(c)
            }

            is Stmt.IfStmt -> {
                val cond = inferExpr(stmt.condition)
                for (c in cond.constraints) constraints.add(c)
                constraints.add(Constraint.Equal(cond.type, BuiltinType.Bool, stmt.condition.span))
                analyzeStmt(stmt.thenBranch)
                analyzeStmt(stmt.elseBranch)
            }

            is Stmt.MatchStmt -> {
                val scr = inferExpr(stmt.scrutinee)
                for (c in scr.constraints) constraints.add(c)
                stmt.branches?.let { brs ->
                    for ((_, _, body) in brs) {
                        analyzeStmt(body)
                    }
                }
            }

            is Stmt.ForStmt -> {
                // flag 是循环标签，不是变量，不推断
                stmt.varDecl?.let { val r = inferExpr(it); for (c in r.constraints) constraints.add(c) }
                stmt.expr?.let { val r = inferExpr(it); for (c in r.constraints) constraints.add(c) }
                analyzeStmt(stmt.body)
            }

            is Stmt.WhileStmt -> {
                // flag 是循环标签，不推断
                val cond = inferExpr(stmt.expr)
                for (c in cond.constraints) constraints.add(c)
                constraints.add(Constraint.Equal(cond.type, BuiltinType.Bool, stmt.expr.span))
                analyzeStmt(stmt.body)
            }

            is Stmt.BreakStmt, is Stmt.ContinueStmt -> {
                // nothing
            }

            is Stmt.FnStmt -> {
                analyzeFnStmt(stmt)
            }

            is Stmt.ReturnStmt -> {
                val exprR = stmt.expr?.let { inferExpr(it) }
                exprR?.let { for (c in it.constraints) constraints.add(c) }
                if (!returnContextStack.isEmpty) {
                    val context = returnContextStack.peek()
                    val returnType = exprR?.type ?: BuiltinType.Null
                    // t1=实际返回类型(使用方)，t2=函数声明返回类型(声明方)，declPos=函数声明位置
                    constraints.add(Constraint.Equal(returnType, context.expected, stmt.span, context.declSpan))
                }
            }

            is Stmt.AssignStmt -> {
                // analyze both sides
                val lr = inferExpr(stmt.`var`)
                for (c in lr.constraints) constraints.add(c)
                val valueR = inferExpr(stmt.value)
                for (c in valueR.constraints) constraints.add(c)

                // if left side is a simple identifier, enforce/collect type constraints
                val lhsIdent = unwrapIdentifier(stmt.`var`)
                if (lhsIdent != null) {
                    val defId = lhsIdent.defId
                    if (defId == null) {
                        val lhsName = (lhsIdent.token.literal as? String) ?: lhsIdent.token.type.toString()
                        error("未声明的变量: $lhsName")
                            .point(lhsIdent, "未找到此变量的声明")
                    } else {
                        val symbol = symbolTable.get(defId)
                        if (symbol != null) {
                            // derive a left-side type; if unknown, create a fresh type variable
                            var leftType: Type = symbol.type
                            if (leftType == BuiltinType.Unknown) {
                                leftType = solver.freshVar()
                                symbol.values.put("inferred", leftType)
                            }
                            // 使用方=实际 RHS 类型（point 于 RHS 处），声明方=变量声明类型（info 于符号声明处）
                            constraints.add(Constraint.Equal(valueR.type, leftType, stmt.value.span, symbol.span))
                        }
                    }
                }
                // non-identifier LHS (e.g. indexing, field access): subexpressions already analyzed above.
            }

            is Stmt.SetVarStmt -> {
                // set introduces a new variable in current scope; its symbol was created by Resolver
                val valueType =
                    if (stmt.assignStmt != null) analyzeExpr((stmt.assignStmt as Stmt.AssignStmt).value) else BuiltinType.Unknown
                val varIdent = unwrapIdentifier(stmt.`var`)
                varIdent?.defId?.let { defId ->
                    val symbol = symbolTable.get(defId)
                    if (symbol != null && valueType != BuiltinType.Unknown) {
                        symbol.type = valueType
                    }
                }
                // if there is an assignStmt (a nested AssignStmt) analyze it too
                analyzeStmt(stmt.assignStmt)
            }

            else -> {
                // unhandled statement kinds
            }
        }
    }

    /**
     * 函数声明：为函数符号构造 [Type.Func]，绑定形参类型，分析函数体。
     */
    private fun analyzeFnStmt(stmt: Stmt.FnStmt) {
        val fnSymbol = stmt.defId?.let { symbolTable.get(it) }
        if (fnSymbol == null) {
            // 无名函数或解析失败：仍尝试分析函数体
            analyzeStmt(stmt.body)
            return
        }

        // prepare function type: param type variables + result type variable
        val paramTypes = Seq<Type>(0)
        stmt.parameters?.let { params ->
            repeat(params.size) {
                val tv = solver.freshVar()
                paramTypes.add(tv)
            }
        }
        val resultType = solver.freshVar()
        // set function symbol type
        fnSymbol.type = Type.Func(paramTypes, resultType)

        // analyze body with parameters bound
        returnContextStack.add(ReturnContext(resultType, stmt.name?.span ?: stmt.span))
        stmt.parameters?.let { params ->
            for ((i, p) in params.withIndex()) {
                val ident = unwrapIdentifier(p)
                ident?.defId?.let { defId ->
                    symbolTable.get(defId)?.let { paramSymbol ->
                        paramSymbol.type = paramTypes.get(i)
                    }
                }
            }
        }
        analyzeStmt(stmt.body)
        // pop return context
        returnContextStack.pop()
    }

    private fun analyzeExpr(expr: Expr?): Type {
        // keep compatibility wrapper: call inferExpr and return type
        return inferExpr(expr).type
    }

    private fun inferExpr(expr: Expr?): InferResult {
        if (expr == null) return InferResult(BuiltinType.Unknown, Seq<Constraint>(0))

        return when (expr) {
            is Expr.Literal -> InferResult(BuiltinType.toType(expr.token.type), Seq(0))

            is Expr.Identifier -> {
                val defId = expr.defId
                if (defId == null) {
                    val name = (expr.token.literal as? String) ?: expr.token.type.toString()
                    error("未声明的标识符: $name").point(expr, "未找到此标识符的定义")
                    InferResult(BuiltinType.Unknown, Seq(0))
                } else {
                    val symbol = symbolTable.get(defId)
                    if (symbol == null) {
                        InferResult(BuiltinType.Unknown, Seq(0))
                    } else {
                        var ty = symbol.type
                        if (ty == BuiltinType.Unknown) {
                            // create type variable to be inferred
                            ty = solver.freshVar()
                            symbol.values.put("inferred", ty)
                        }
                        InferResult(ty, Seq(0))
                    }
                }
            }

            is Expr.Tuple -> {
                // 元组：逐元素推断，产出 Type.TupleType
                val combined = Seq<Constraint>(0)
                val elementTypes = Seq<Type>(0)
                for (e in expr.elements) {
                    val r = inferExpr(e)
                    for (c in r.constraints) combined.add(c)
                    elementTypes.add(r.type)
                }
                InferResult(Type.TupleType(elementTypes), combined)
            }

            is Expr.Annotation -> {
                val r = inferExpr(expr.expr)
                InferResult(r.type, r.constraints)
            }

            is Expr.Unary -> {
                val r = inferExpr(expr.expr)
                InferResult(BuiltinType.Unknown, r.constraints)
            }

            is Expr.Binary -> {
                val l = inferExpr(expr.left)
                val r = inferExpr(expr.right)
                val combined = Seq<Constraint>(0)
                for (c in l.constraints) combined.add(c)
                for (c in r.constraints) combined.add(c)

                val resultType = if (expr.operator.type in setOf(
                        TokenType.GREATER,
                        TokenType.GREATER_EQ,
                        TokenType.LESS,
                        TokenType.LESS_EQ,
                        TokenType.EQ_EQ,
                        TokenType.BANG_EQ
                    )
                ) {
                    BuiltinType.Bool
                } else if (l.type != BuiltinType.Unknown && r.type != BuiltinType.Unknown) {
                    getResultType(expr.operator, l.type, r.type)
                } else {
                    solver.freshVar()
                }

                InferResult(resultType, combined)
            }

            is Expr.Array -> {
                // 数组字面量：所有元素统一为一个元素类型，产出 Type.Arr(elementType)
                val combined = Seq<Constraint>(0)
                val elemVar = solver.freshVar()
                for (e in expr.elements) {
                    val r = inferExpr(e)
                    for (c in r.constraints) combined.add(c)
                    // 使用方=元素实际类型，声明方=统一的元素类型变量
                    combined.add(Constraint.Equal(r.type, elemVar, e.span))
                }
                InferResult(Type.Arr(elemVar), combined)
            }

            is Expr.Index -> {
                // 索引：list 必须是 Array<result>，index 必须是 Int
                val l = inferExpr(expr.list)
                val index = inferExpr(expr.index)
                val combined = Seq<Constraint>(0)
                for (c in l.constraints) combined.add(c)
                for (c in index.constraints) combined.add(c)
                val elemVar = solver.freshVar()
                combined.add(Constraint.Equal(l.type, Type.Arr(elemVar), expr.list.span))
                combined.add(Constraint.Equal(index.type, BuiltinType.Int, expr.index.span))
                InferResult(elemVar, combined)
            }

            is Expr.Range -> {
                val combined = Seq<Constraint>(0)
                expr.left?.let { for (c in inferExpr(it).constraints) combined.add(c) }
                expr.right?.let { for (c in inferExpr(it).constraints) combined.add(c) }
                InferResult(BuiltinType.Unknown, combined)
            }

            is Expr.Call -> {
                val callee = inferExpr(expr.callee)
                val combined = Seq<Constraint>(0)
                for (c in callee.constraints) combined.add(c)

                val paramTypes = Seq<Type>(0)
                for (a in expr.arguments) {
                    val ar = inferExpr(a)
                    for (c in ar.constraints) combined.add(c)
                    paramTypes.add(ar.type)
                }

                val resVar = solver.freshVar()
                // represent function type and constrain callee to it
                val fnType = Type.Func(paramTypes, resVar)
                combined.add(Constraint.Equal(callee.type, fnType, expr.span))
                InferResult(resVar, combined)
            }

            is Expr.Get -> {
                val ot = inferExpr(expr.obj)
                val combined = Seq<Constraint>(0)
                combined.addAll(ot.constraints)
                val fieldName = if (expr.field is Expr.Identifier) (expr.field.token.literal as? String) else null
                if (ot.type is Type.Arr && fieldName == "length") {
                    InferResult(BuiltinType.Int, combined)
                } else {
                    InferResult(BuiltinType.Unknown, combined)
                }
            }

            is Expr.ErrorExpr -> InferResult(BuiltinType.Unknown, Seq(0))
            else -> InferResult(BuiltinType.Unknown, Seq(0))
        }
    }

    // region tools

    // 根据操作符和操作数类型确定结果类型
    private fun getResultType(operator: Token, leftType: Type?, rightType: Type?): Type {
        when (operator.type) {
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH -> {
                if (leftType == BuiltinType.Num || rightType == BuiltinType.Num) {
                    return BuiltinType.Num
                }
                return BuiltinType.Int
            }

            TokenType.GREATER, TokenType.GREATER_EQ, TokenType.LESS, TokenType.LESS_EQ, TokenType.EQ_EQ, TokenType.BANG_EQ -> return BuiltinType.Bool
            else -> return BuiltinType.Unknown
        }
    }

    // 从 `Identifier` 或 `Annotation(Identifier, ...)` 中取出标识符
    private fun unwrapIdentifier(expr: Expr): Expr.Identifier? {
        return when (expr) {
            is Expr.Identifier -> expr
            is Expr.Annotation -> expr.expr as? Expr.Identifier
            else -> null
        }
    }

    // 错误
    private fun error(name: String): SemanticProblem {
        val e = SemanticProblem(sourceMap, name, Problem.ProblemLevel.ERROR)
        problems.addError(e)
        return e
    }

    // 警告
    private fun warning(name: String): SemanticProblem {
        val w = SemanticProblem(sourceMap, name, Problem.ProblemLevel.WARNING)
        problems.addWarning(w)
        return w
    }

    // 当前函数返回上下文：期望返回类型 + 函数声明位置（不匹配报错的声明方 info 用）
    private data class ReturnContext(val expected: Type, val declSpan: Span)

    // endregion
}