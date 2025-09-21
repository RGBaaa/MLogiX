package mlogix.logix;

import mlogix.logix.Expr.*;
import mlogix.compiler.*;
import mlogix.compiler.struct.*;

import java.util.*;

//Statement
public abstract non-sealed class Stmt extends ASTNode {
    protected Stmt(Span span) {
        this.span = span;
    }

    public abstract void accept(SemanticAnalyzer.SemanticVisitor visitor);


    public static class Program extends Stmt {
        public final List<Stmt> stmts;

        public Program(Span span, List<Stmt> stmts) {
            super(span);
            this.stmts = stmts;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class Block extends Stmt {
        public final List<Stmt> stmts;

        public Block(Span span, List<Stmt> stmts) {
            super(span);
            this.stmts = stmts;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class ExprStmt extends Stmt {
        public final Expr expr;

        public ExprStmt(Span span, Expr expr) {
            super(span);
            this.expr = expr;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class IfStmt extends Stmt {
        public final Expr condition;
        public final Stmt thenBranch;
        public final Stmt elseBranch;

        public IfStmt(Span span, Expr condition, Stmt thenBranch, Stmt elseBranch) {
            super(span);
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class ForStmt extends Stmt {
        public final Identifier varDecl;
        public final Expr expr;
        public final Stmt body;

        public ForStmt(Span span, Identifier varDecl, Expr expr, Stmt body) {
            super(span);
            this.varDecl = varDecl;
            this.expr = expr;
            this.body = body;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class WhileStmt extends Stmt {
        public final Expr expr;
        public final Stmt body;

        public WhileStmt(Span span, Expr expr, Stmt body) {
            super(span);
            this.expr = expr;
            this.body = body;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class BreakStmt extends Stmt {
        public BreakStmt(Span span) {
            super(span);
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class ContinueStmt extends Stmt {
        public ContinueStmt(Span span) {
            super(span);
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class FnStmt extends Stmt {
        public final Token name;
        public final List<Expr> parameters;
        public final List<Expr> results;
        public final Stmt body;

        public FnStmt(Span span, Token name, List<Expr> parameters, List<Expr> results, Stmt body) {
            super(span);
            this.name = name;
            this.parameters = parameters;
            this.results = results;
            this.body = body;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class ReturnStmt extends Stmt {
        public final Expr expr;

        public ReturnStmt(Span span, Expr expr) {
            super(span);
            this.expr = expr;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class AssignStmt extends Stmt {
        public final Expr var;
        public final Token operator;
        public final Expr value;

        public AssignStmt(Span span, Expr var, Token operator, Expr value) {
            super(span);
            this.var = var;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class SetVarStmt extends Stmt {
        public final Expr var;
        public final Stmt assignStmt;

        public SetVarStmt(Span span, Expr var, Stmt assignStmt) {
            super(span);
            this.var = var;
            this.assignStmt = assignStmt;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }
}