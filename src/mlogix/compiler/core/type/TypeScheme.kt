package mlogix.compiler.core.type

import arc.struct.IntSet
import arc.struct.ObjectMap
import arc.struct.Seq

/**
 * 类型方案（Type Scheme），用于表示带"全称量化"的多态类型。
 *
 * 在 HM（Hindley-Milner）类型系统中，
 * 一个泛型函数（如 `fn id<T>(x: T) -> T`）的类型不能简单地用普通类型表示，
 * 而必须明确记录"哪些类型变量是被量化的（属于这个函数自己的）"。
 *
 * 本类本质上对应数学形式：**∀ α₁ α₂ ... αₙ. Body**
 * - 其中 `∀` 表示"对于所有类型"；
 * - `α₁..αₙ` 是泛型参数（如 `<T, E>`）；
 * - `Body` 是去掉了量词后的类型结构（如 `fn(T) -> T`）。
 *
 * @property typeVars 被 `∀` 绑定的类型变量列表（如 `[T, E]`）。这些变量在 `body` 中会被引用。
 * @property body 主体类型，即去掉了 `∀` 前缀后的类型表达式。
 *                  注意：`body` 中出现的自由变量（指未在 `typeVars` 中声明的变量）
 *                  必须在更大的上下文（如外层函数或结构体）中定义。
 *
 * @see instantiate 用于在调用泛型函数时生成具体类型
 * @see generalize 用于从普通类型推导出泛型类型方案
 */
class TypeScheme(
    val typeVars: Seq<Type.Var>,
    val body: Type,
) {

    /**
     * 实例化（Instantiation）：将当前的泛型类型方案转换为一个**具体的**、可使用的类型。
     *
     * 这是泛型多态最核心的操作。
     * 每次调用泛型函数（如调用 `foo<Int>(42)`），编译器都必须执行此操作。
     *
     * **为什么需要这个方法？**
     * 因为在类型方案中，`typeVars` 是共享的（属于函数定义本身）。
     * 如果我们在函数被调用时不进行处理，所有调用点会共用同一组类型变量，导致类型冲突。
     * 例如 `set a = id(42); set b = id("hello")`，如果两次调用共用同一个变量 `T`，
     * 那么第一次将 `T` 固定为 `i32` 后，第二次传入 `String` 就会报错。
     *
     * **解决方式：**
     * 本方法会为 `typeVars` 中的**每一个**量化变量生成一个**全新的、独一无二的**类型变量，
     * 然后将 `body` 中的所有旧变量替换为新变量。这样，每次调用都会获得完全独立的一套类型参数。
     *
     * @param freshVar 一个高阶函数（工厂），用于生成一个新的类型变量（`Type.Var`）。
     *                 通常由 `TypeSolver` 提供，确保每次生成的变量索引（`index`）是全局唯一的。
     *                 之所以设计为函数参数而非直接 `new`，是为了将"如何生成变量"的逻辑与"如何替换"的逻辑解耦。
     *
     * @return 替换后的具体类型（`Type`）。
     *         此时返回的类型中不再包含量化变量，所有占位符都已被替换为全新的具体变量，
     *         可以立即与参数类型进行统一（Unification）。
     *
     * @see substitute 本方法依赖的底层替换逻辑
     */
    fun instantiate(freshVar: () -> Type.Var): Type {
        val subst = ObjectMap<Int, Type.Var>()
        for ((index) in typeVars) subst.put(index, freshVar())
        return substitute(body, subst)
    }

    /**
     * 泛化（Generalization）：将当前类型（`body`）中**未受约束**的自由类型变量提升为量化变量。
     *
     * 这是 set 多态（Set-Polymorphism）的基础。
     * 通常在处理 set 绑定（如`set x = ...` 或函数定义）时，
     * 如果推断出的类型中包含一些尚未确定的类型变量，且这些变量没有受到外部环境的限制，
     * 我们就可以把它们"装进盒子"（量化），形成一个泛型类型方案。
     *
     * **必须理解 `envFreeVars`（环境自由变量）的作用：**
     * - 假设我们有嵌套作用域：外层定义了一个泛型结构体 `struct Wrapper<T> { ... }`，
     *   内层定义了一个闭包 `\|x\| x`。
     * - 内层闭包的 `freeTypeVars()` 可能包含外层的 `T`。
     * - 但是，`T` 是属于外层结构体的，内层闭包**不能**把外层的 `T` 抢过来作为自己的泛型参数（否则类型会混乱）。
     * - 因此，`generalize` 会检查 `envFreeVars`，**跳过**那些已经在环境中被占用的变量，只量化"真正自由"的本地变量。
     *
     * **操作步骤：**
     * 1. 调用 `freeTypeVars()` 获取 `body` 中出现的所有类型变量（去重）。
     * 2. 过滤掉那些索引存在于 `envFreeVars` 中的变量（即外部环境已经声明的）。
     * 3. 将剩余的变量作为新的 `typeVars`，构造一个新的 `TypeScheme` 实例返回。
     *
     * @param envFreeVars 一个整数集合（`IntSet`），包含当前类型环境（`TypeEnv`）中
     *                    所有已被占用的类型变量的索引。
     *                    这些变量虽然出现在 `body` 中，但属于外部上下文，禁止在此处被量化。
     *
     * @return 一个新的 `TypeScheme` 实例。其 `typeVars` 包含刚刚量化的新变量，
     *         而 `body` 保持不变（因为变量本身就是指向 `body` 内部的引用）。
     *
     * @see freeTypeVars 用于收集 `body` 中的所有候选变量
     */
    fun generalize(envFreeVars: IntSet): TypeScheme {
        val quantified = Seq<Type.Var>(0)
        val seen = ObjectMap<Int, Boolean>()
        for (v in freeTypeVars()) {
            if (!envFreeVars.contains(v.index) && !seen.containsKey(v.index)) {
                quantified.add(v)
                seen.put(v.index, true)
            }
        }
        return TypeScheme(quantified, body)
    }

    /**
     * 收集 [body] 中出现的全部类型变量（按 index 去重）。
     */
    fun freeTypeVars(): Seq<Type.Var> {
        val result = Seq<Type.Var>(0)
        val seen = ObjectMap<Int, Boolean>()
        body.accept(object : TypeVisitor {
            override fun visitVar(type: Type.Var) {
                if (!seen.containsKey(type.index)) {
                    seen.put(type.index, true)
                    result.add(type)
                }
            }
        })
        return result
    }

    private fun substitute(t: Type, subst: ObjectMap<Int, Type.Var>): Type {
        return when (t) {
            is Type.Var -> subst.get(t.index) ?: t
            is Type.Con -> t

            is Type.Func -> {
                val params = Seq<Type>(0)
                for (p in t.params) params.add(substitute(p, subst))
                Type.Func(params, substitute(t.result, subst))
            }

            is Type.Arr -> Type.Arr(substitute(t.element, subst))

            is Type.TupleType -> {
                val elements = Seq<Type>(0)
                for (e in t.elements) elements.add(substitute(e, subst))
                Type.TupleType(elements)
            }

            Type.Unknown, Type.Error -> t
        }
    }
}
