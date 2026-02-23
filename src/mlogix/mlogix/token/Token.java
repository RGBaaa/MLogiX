package mlogix.mlogix.token;

import mlogix.span.Span;
import mlogix.span.Spanned;

public class Token implements Spanned {
    public final Span span;
    public final TokenType type;
    public final Object literal;

    public Token(Span span, TokenType type) {
        this.span = span;
        this.type = type;
        this.literal = null;
    }

    public Token(Span span, TokenType type, Object literal) {
        this.span = span;
        this.type = type;
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
