package mlogix.logix;

public class BuiltinStruct {
    public static final Struct
            Num, Int, String, Bool, Null, Array, Fn, Ref, Unknown;

    static {
        Num = new Struct("Num");
        Int = new Struct("Int");
        Bool = new Struct("Bool");
        Null = new Struct("Null");

        String = new Struct("String");
        Array = new Struct("Array")
                .addFields("length", Int);

        Fn = new Struct("Fn");

        Ref = new Struct("Ref");

        Unknown = new Struct("Unknown");
    }

    public static Struct toStruct(TokenType tokenType) {
        return switch(tokenType) {
            case INT -> Int;
            case TRUE, FALSE -> Bool;
            case NULL -> Null;
            case STRING -> String;
            case FN -> Fn;
            case UNKNOWN -> Unknown;
            default -> throw new IllegalArgumentException("Unknown token type: " + tokenType);
        };
    }
}
