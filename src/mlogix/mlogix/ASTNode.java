package mlogix.mlogix;

import mlogix.span.Span;
import mlogix.span.Spanned;

public abstract sealed class ASTNode implements Spanned permits Expr, Stmt {
    public Span span;

    public Span span() {
        return span;
    }
}

