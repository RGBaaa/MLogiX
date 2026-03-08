package mlogix.mlogix.ast;

import arc.struct.Seq;
import mlogix.compiler.SemanticAnalyzer;
import mlogix.mlogix.ast.Expr.Identifier;
import mlogix.mlogix.token.Token;
import mlogix.span.Span;

//Statement
public abstract non-sealed class Stmt extends ASTNode {
    protected Stmt(Span span) {
        this.span = span;
    }

    public abstract void accept(SemanticAnalyzer.SemanticVisitor visitor);

    public static class Program extends Stmt {
        public final Seq<Stmt> stmts;

        public Program(Span span, Seq<Stmt> stmts) {
            super(span);
            this.stmts = stmts;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class UseStmt extends Stmt {
        public final UseItem item;

        public UseStmt(Span span, UseItem item) {
            super(span);
            this.item = item;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static abstract sealed class UseItem extends Stmt
            permits UseItem.Single, UseItem.All, UseItem.Multi, UseItem.Recursion {

        public final Seq<Identifier> path;

        public UseItem(Span span, Seq<Identifier> path) {
            super(span);
            this.path = path;
        }

        @Override
        public void accept(SemanticAnalyzer.SemanticVisitor visitor) {
            visitor.visit(this);
        }

        public static final class Single extends UseItem {
            public Single(Span span, Seq<Identifier> path) {
                super(span, path);
            }
        }

        // *
        public static final class All extends UseItem {
            public All(Span span, Seq<Identifier> path) {
                super(span, path);
            }
        }

        // **
        public static final class Recursion extends UseItem {
            public Recursion(Span span, Seq<Identifier> path) {
                super(span, path);
            }
        }

        // {...}
        public static final class Multi extends UseItem {
            public final Seq<UseItem> items;

            public Multi(Span span, Seq<Identifier> path, Seq<UseItem> items) {
                super(span, path);
                this.items = items;
            }
        }
    }


    public static class BlockStmt extends Stmt {
        public final Seq<Stmt> stmts;

        public BlockStmt(Span span, Seq<Stmt> stmts) {
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
        public final Seq<Expr> parameters;
        public final Seq<Expr> results;
        public final Stmt body;

        public FnStmt(Span span, Token name, Seq<Expr> parameters, Seq<Expr> results, Stmt body) {
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