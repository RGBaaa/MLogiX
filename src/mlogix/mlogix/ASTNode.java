package mlogix.mlogix;

import mlogix.struct.*;

public abstract sealed class ASTNode permits Expr, Stmt {
    public Span span;
    // public abstract void accept(SemanticAnalyzer.SemanticVisitor visitor);
}

