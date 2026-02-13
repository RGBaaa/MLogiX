package mlogix.mlogix;

import mlogix.span.Span;
import mlogix.span.Spanned;

public class Token implements Spanned {
    public final TokenType type;
    public final Span span;
    public final Object literal;

    public Token(TokenType type, Span span) {
        this.type = type;
        this.span = span;
        this.literal = null;
    }

    public Token(TokenType type, Span span, Object literal) {
        this.type = type;
        this.span = span;
        this.literal = literal;
    }

    public String toString() {
        return String.format("Token{%s,%s,%s}", type.name(), span.toString(), literal);
    }

    public String toSimpleString() {
        return String.format("Token(%s,%s)", type.name(), literal);
    }

    public Span span() {
        return span;
    }
}
