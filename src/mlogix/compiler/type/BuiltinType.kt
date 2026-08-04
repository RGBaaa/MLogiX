package mlogix.compiler.type

import mlogix.compiler.token.TokenType

object BuiltinType {
    val Num: Type = Type("Num")
    val Int: Type = Type("Int")
    val Str: Type = Type("Str")
    val Bool: Type = Type("Bool")
    val Null: Type = Type("Null")
    val Array: Type = Type("Array")
        .addField("length", Int)
    val Fn: Type = Type("Fn")
    val Ref: Type = Type("Ref")
    val Unknown: Type = Type("Unknown")

    /**
     * 将给定的 TokenType 转换为对应的Type
     * 
     * @param tokenType 要转换的词法标记类型
     * @return 对应的Type
     * @throws IllegalArgumentException 当遇到未知的 tokenType 时抛出
     */
    fun toType(tokenType: TokenType): Type? {
        return when (tokenType) {
            TokenType.INT -> Int
            TokenType.TRUE, TokenType.FALSE -> Bool
            TokenType.NULL -> Null
            TokenType.STR -> Str
            TokenType.FN -> Fn
            TokenType.UNKNOWN -> Unknown
            else -> throw IllegalArgumentException("Unknown token type: $tokenType")
        }
    }
}
