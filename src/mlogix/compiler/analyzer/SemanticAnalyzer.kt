package mlogix.compiler.analyzer

import arc.struct.ObjectMap
import arc.struct.Seq
import mlogix.compiler.SourceMapManager.SourceMap
import mlogix.compiler.ast.Expr
import mlogix.compiler.ast.Stmt
import mlogix.compiler.token.Token
import mlogix.compiler.token.TokenType
import mlogix.compiler.type.BuiltinType
import mlogix.compiler.type.Type
import mlogix.problem.Problem
import mlogix.problem.Problem.SemanticProblem
import mlogix.problem.ProblemCollector
import mlogix.span.Span

/**
 * 一个项目 一次构造
 */
class SemanticAnalyzer(val problems: ProblemCollector) {
    private lateinit var sourceMap: SourceMap
    private lateinit var scopeStack: Seq<Scope>
    private lateinit var solver: TypeSolver
    private val constraints = Seq<Constraint>()
    private val returnTypeStack = Seq<Type>()

    private var anonymousSymbolIndex = 0

    // ========== 执行语义分析 ==========
    /**
     * 一个文件 一次分析
     */
    fun analyze(ast: Stmt, sourceMap: SourceMap) {
        this.sourceMap = sourceMap
        this.scopeStack = Seq<Scope>()
        this.anonymousSymbolIndex = 0

        // enter global scope
        enterScope()
        // prepare solver
        solver = TypeSolver(problems, sourceMap)
        constraints.clear()

        // analyze AST and collect constraints
        analyzeStmt(ast)

        // solve collected constraints
        solver.solveEqualities(constraints)

        // propagate solved inferred types back to symbols
        val symbols = currentScope().collectSymbols()
        for (s in symbols) {
            val inferred = s.values.get("inferred") as? Type
            if (inferred != null) {
                val final = solver.read(inferred)
                if (final !is mlogix.compiler.type.TypeVar) {
                    // update symbol type if previously Unknown
                    if (s.type === BuiltinType.Unknown) s.type = final
                    s.values.put("final", final)
                }
            }
        }

        // exit global scope
        exitScope()
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
                newScope {
                    for (s in stmt.stmts) analyzeStmt(s)
                }
            }

            is Stmt.ExprStmt -> {
                val r = inferExpr(stmt.expr)
                for (c in r.constraints) constraints.add(c)
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
                        newScope {
                            analyzeStmt(body)
                        }
                    }
                }
            }

            is Stmt.ForStmt -> {
                newScope {
                    stmt.flag?.let { val r = inferExpr(it); for (c in r.constraints) constraints.add(c) }
                    stmt.varDecl?.let { val r = inferExpr(it); for (c in r.constraints) constraints.add(c) }
                    stmt.expr?.let { val r = inferExpr(it); for (c in r.constraints) constraints.add(c) }
                    analyzeStmt(stmt.body)
                }
            }

            is Stmt.WhileStmt -> {
                newScope {
                    stmt.flag?.let { val r = inferExpr(it); for (c in r.constraints) constraints.add(c) }
                    val cond = inferExpr(stmt.expr)
                    for (c in cond.constraints) constraints.add(c)
                    constraints.add(Constraint.Equal(cond.type, BuiltinType.Bool, stmt.expr.span))
                    analyzeStmt(stmt.body)
                }
            }

            is Stmt.BreakStmt, is Stmt.ContinueStmt -> {
                // nothing
            }

            is Stmt.FnStmt -> {
                // register function symbol in current scope
                val fnNameToken = stmt.name
                val fnType = BuiltinType.Fn
                if (fnNameToken != null) {
                    val symName = (fnNameToken.literal as? String) ?: fnNameToken.type.toString()
                    val sym = Symbol(symName, fnType, fnNameToken.span)
                    addSymbol(sym)
                }

                // prepare function type: param type variables + result type variable
                val paramTypes = Seq<Type>()
                stmt.parameters?.let { params ->
                    for (p in params) {
                        val tv = solver.freshVar()
                        paramTypes.add(tv)
                    }
                }
                val resultType = solver.freshVar()
                // set function symbol type
                if (fnNameToken != null) {
                    val symName = (fnNameToken.literal as? String) ?: fnNameToken.type.toString()
                    val fnSym = lookupSymbol(symName)
                    if (fnSym != null) {
                        fnSym.type = mlogix.compiler.type.FunctionType(paramTypes, resultType)
                    }
                }

                // analyze body in new scope with parameters bound
                newScope {
                    // push current function return type for ReturnStmt handling
                    returnTypeStack.add(resultType)
                    stmt.parameters?.let { params ->
                        for ((i, p) in params.withIndex()) {
                            if (p is Expr.Identifier) {
                                val name = (p.token.literal as? String) ?: p.token.type.toString()
                                val s = Symbol(name, paramTypes.get(i), p.span)
                                addSymbol(s)
                            }
                        }
                    }
                    analyzeStmt(stmt.body)
                    // pop return type
                    returnTypeStack.pop()
                }
            }

            is Stmt.ReturnStmt -> {
                val exprR = stmt.expr?.let { inferExpr(it) }
                exprR?.let { for (c in it.constraints) constraints.add(c) }
                if (!returnTypeStack.isEmpty) {
                    val expected = returnTypeStack.peek()
                    val retType = exprR?.type ?: BuiltinType.Null
                    constraints.add(Constraint.Equal(retType, expected, stmt.span))
                }
            }

            is Stmt.AssignStmt -> {
                // analyze both sides
                analyzeExpr(stmt.`var`)
                val valueType = analyzeExpr(stmt.value)

                // if left side is a simple identifier, enforce/collect type constraints
                if (stmt.`var` is Expr.Identifier) {
                    val name = (stmt.`var`.token.literal as? String) ?: stmt.`var`.token.type.toString()
                    val existing = lookupSymbol(name)
                    if (existing == null) {
                        error("未声明的变量: $name").point(stmt.`var`, "未找到此变量的声明")
                    } else {
                        // derive a left-side type; if unknown, create a fresh type variable
                        var leftType: Type = existing.type
                        if (leftType === BuiltinType.Unknown) {
                            leftType = solver.freshVar()
                            existing.values.put("inferred", leftType)
                        }
                        // if we have a RHS type, add an equality constraint
                        constraints.add(Constraint.Equal(leftType, valueType, stmt.span))
                    }
                } else {
                    // Non-identifier LHS (e.g. indexing, field access): we've already analyzed subexpressions above.
                    // For now, do not create symbol-level constraints here; future: generate constraints for field/index writes.
                }
            }

            is Stmt.SetVarStmt -> {
                // set introduces a new variable in current scope
                if (stmt.`var` is Expr.Identifier) {
                    val name = (stmt.`var`.token.literal as? String) ?: stmt.`var`.token.type.toString()
                    val declaredType =
                        if (stmt.assignStmt != null) analyzeExpr((stmt.assignStmt as Stmt.AssignStmt).value) else BuiltinType.Unknown
                    val sym = Symbol(name, declaredType ?: BuiltinType.Unknown, stmt.`var`.span)
                    addSymbol(sym)
                }
                // if there is an assignStmt (a nested AssignStmt) analyze it too
                analyzeStmt(stmt.assignStmt)
            }

            else -> {
                // unhandled statement kinds
            }
        }
    }

    private fun analyzeExpr(expr: Expr?): Type {
        // keep compatibility wrapper: call inferExpr and return type
        return inferExpr(expr).type
    }

    private fun inferExpr(expr: Expr?): InferResult {
        if (expr == null) return InferResult(BuiltinType.Unknown, Seq<Constraint>())

        return when (expr) {
            is Expr.Literal -> InferResult(BuiltinType.toType(expr.token.type), Seq())

            is Expr.Identifier -> {
                val name = (expr.token.literal as? String) ?: expr.token.type.toString()
                val sym = lookupSymbol(name)
                if (sym == null) {
                    error("未声明的标识符: $name").point(expr, "未找到此标识符的定义")
                    InferResult(BuiltinType.Unknown, Seq())
                } else {
                    var ty = sym.type
                    if (ty === BuiltinType.Unknown) {
                        // create type variable to be inferred
                        ty = solver.freshVar()
                        sym.values.put("inferred", ty)
                    }
                    InferResult(ty, Seq())
                }
            }

            is Expr.Tuple -> {
                InferResult(BuiltinType.Unknown, Seq())
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
                val combined = Seq<Constraint>()
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
                } else if (l.type !== BuiltinType.Unknown && r.type !== BuiltinType.Unknown) {
                    getResultType(expr.operator, l.type, r.type)
                } else {
                    solver.freshVar()
                }

                InferResult(resultType, combined)
            }

            is Expr.Array -> {
                val combined = Seq<Constraint>()
                for (e in expr.elements) {
                    val r = inferExpr(e)
                    for (c in r.constraints) combined.add(c)
                }
                InferResult(BuiltinType.Array, combined)
            }

            is Expr.Index -> {
                val l = inferExpr(expr.list)
                val idx = inferExpr(expr.index)
                val combined = Seq<Constraint>()
                for (c in l.constraints) combined.add(c)
                for (c in idx.constraints) combined.add(c)
                InferResult(solver.freshVar(), combined)
            }

            is Expr.Range -> {
                val combined = Seq<Constraint>()
                expr.left?.let { for (c in inferExpr(it).constraints) combined.add(c) }
                expr.right?.let { for (c in inferExpr(it).constraints) combined.add(c) }
                InferResult(BuiltinType.Unknown, combined)
            }

            is Expr.Call -> {
                val calleeR = inferExpr(expr.callee)
                val combined = Seq<Constraint>()
                for (c in calleeR.constraints) combined.add(c)

                val paramTypes = Seq<Type>()
                for (a in expr.arguments) {
                    val ar = inferExpr(a)
                    for (c in ar.constraints) combined.add(c)
                    paramTypes.add(ar.type)
                }

                val resVar = solver.freshVar()
                // represent function type and constrain callee to it
                val fnType = mlogix.compiler.type.FunctionType(paramTypes, resVar)
                combined.add(Constraint.Equal(calleeR.type, fnType, expr.span))
                InferResult(resVar, combined)
            }

            is Expr.Get -> {
                val ot = inferExpr(expr.obj)
                val combined = Seq<Constraint>()
                combined.addAll(ot.constraints)
                val fieldName = if (expr.field is Expr.Identifier) (expr.field.token.literal as? String) else null
                if (ot.type === BuiltinType.Array && fieldName == "length") {
                    InferResult(BuiltinType.Int, combined)
                } else {
                    InferResult(BuiltinType.Unknown, combined)
                }
            }

            is Expr.ErrorExpr -> InferResult(BuiltinType.Unknown, Seq())
            else -> InferResult(BuiltinType.Unknown, Seq())
        }
    }

    // region tools

    private fun currentScope(): Scope {
        return scopeStack.peek()
    }

    /**
     * 建议优先使用
     */
    private fun newScope(cons: Runnable) {
        enterScope()
        cons.run()
        exitScope()
    }

    private fun enterScope() {
        val parent = if (scopeStack.isEmpty) null else currentScope()
        scopeStack.add(Scope(parent))
    }

    private fun exitScope() {
        scopeStack.pop()
    }


    private fun containsSymbol(name: String): Boolean {
        return currentScope().contains(name)
    }

    private fun lookupSymbol(name: String): Symbol? {
        return currentScope().lookup(name)
    }

    private fun addSymbol(symbol: Symbol) {
        currentScope().addSymbol(symbol)
    }

    // 根据操作符和操作数类型确定结果类型
    private fun getResultType(operator: Token, leftType: Type?, rightType: Type?): Type {
        when (operator.type) {
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH -> {
                if (leftType === BuiltinType.Num || rightType === BuiltinType.Num) {
                    return BuiltinType.Num
                }
                return BuiltinType.Int
            }

            TokenType.GREATER, TokenType.GREATER_EQ, TokenType.LESS, TokenType.LESS_EQ, TokenType.EQ_EQ, TokenType.BANG_EQ -> return BuiltinType.Bool
            else -> return BuiltinType.Unknown
        }
    }

    /**
     * 生成匿名符号，格式($INDEX$ = 匿名符号序号)：
     * __$INDEX$
     */
    private fun genAnonymousSymbol(type: Type, span: Span): Symbol {
        val symbol = Symbol("__$anonymousSymbolIndex", type, span)
        anonymousSymbolIndex++
        return symbol
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

    // endregion

    // region classes

    class Symbol(
        val name: String,
        var type: Type,
        /** 符号定义处  */
        val span: Span
    ) {
        var values = ObjectMap<String?, Any?>()
    }

    // 作用域
    inner class Scope(val parent: Scope?) {
        private val symbols = ObjectMap<String?, Symbol?>()

        fun collectSymbols(): Seq<Symbol> {
            val seq = Seq<Symbol>()
            val keys = symbols.keys()
            for (k in keys) {
                val s = symbols.get(k)
                if (s != null) seq.add(s)
            }
            parent?.let { p ->
                val pseq = p.collectSymbols()
                for (ss in pseq) seq.add(ss)
            }
            return seq
        }

        /**
         * 检查符号表中是否包含指定的符号名称。
         * 该方法会首先检查当前符号表中是否存在指定的符号名称，
         * 如果不存在，则递归检查其父符号表。
         *
         * @param name 要检查的符号名称
         * @return 如果符号表中包含该名称则返回 true，否则返回 false
         */
        fun contains(name: String): Boolean {
            return !(!symbols.containsKey(name) && parent != null) || parent.contains(name)
        }

        /**
         * 根据名称查找符号。
         *
         * @param name 要查找的符号名称
         * @return 如果找到则返回对应的Symbol对象；如果当前作用域未找到且存在父作用域，则递归在父作用域中查找；
         * 如果所有作用域都未找到，则返回null
         */
        fun lookup(name: String): Symbol? {
            val symbol = symbols[name]
            if (symbol == null && parent != null) {
                return parent.lookup(name)
            }
            return symbol
        }

        /**
         * 在这个作用域添加符号
         * @param symbol 添加的符号
         */
        fun addSymbol(symbol: Symbol) {
            if (contains(symbol.name)) {
                // 重复定义
                error("重复定义变量: " + symbol.name)
                    .point(symbol.span, "")
            } else {
                symbols.put(symbol.name, symbol)
            }
        }
    }

    // endregion
}