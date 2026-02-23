package mlogix.mlogix.type;

import mlogix.mlogix.token.TokenType;

public class BuiltinType {
    public static final Type
            Num, Int, Str, Bool, Null, Array, Fn, Ref, Unknown;

    static {
        Num = new Type("Num");
        Int = new Type("Int");
        Bool = new Type("Bool");
        Null = new Type("Null");

        Str = new Type("Str");
        Array = new Type("Array")
                .addFields("length", Int);

        Fn = new Type("Fn");

        Ref = new Type("Ref");

        Unknown = new Type("Unknown");
    }

    /**
     * 将给定的 TokenType 转换为对应的Type
     *
     * @param tokenType 要转换的词法标记类型
     * @return 对应的Type
     * @throws IllegalArgumentException 当遇到未知的 tokenType 时抛出
     */
    public static Type toType(TokenType tokenType) {
        return switch(tokenType) {
            case INT -> Int;
            case TRUE, FALSE -> Bool;
            case NULL -> Null;
            case STR -> Str;
            case FN -> Fn;
            case UNKNOWN -> Unknown;
            default -> throw new IllegalArgumentException("Unknown token type: " + tokenType);
        };
    }
}
