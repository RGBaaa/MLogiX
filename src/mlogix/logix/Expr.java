package mlogix.logix;

import mlogix.compiler.*;
import mlogix.compiler.struct.*;

import java.util.*;

//Expression
public abstract non-sealed class Expr extends ASTNode {
    protected Expr(Span span) {
        this.span = span;
    }

    public abstract Struct accept(SemanticAnalyzer.SemanticVisitor visitor);

    /* 字面量 */
    public static class Literal extends Expr {
        public final Token token;

        public Literal(Token token) {
            super(token.span);
            this.token = token;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    /* 标识符 */
    public static class Identifier extends Expr {
        public final Token token;

        public Identifier(Token token) {
            super(token.span);
            this.token = token;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    /* 一元运算 */
    public static class Unary extends Expr {
        public final Token operator;
        public final Expr expr;

        public Unary(Token operator, Expr expr) {
            super(Span.between(operator.span, expr.span));
            this.operator = operator;
            this.expr = expr;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    /* 二元运算 */
    public static class Binary extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public Binary(Expr left, Token operator, Expr right) {
            super(Span.between(left.span, right.span));
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    /* 数组 */
    public static class Array extends Expr {
        public final List<Expr> elements;

        public Array(Span span, List<Expr> elements) {
            super(span);
            this.elements = elements;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    /* 索引 */
    public static class Index extends Expr {
        public final Expr list;
        public final Expr index;

        public Index(Span span, Expr list, Expr index) {
            super(span);
            this.list = list;
            this.index = index;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    public static class Range extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public Range(Span span, Expr left, Token operator, Expr right) {
            super(span);
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    /* 函数调用 func(...) */
    public static class Call extends Expr {
        public final Expr callee;
        public final List<Expr> arguments;

        public Call(Span span, Expr callee, List<Expr> arguments) {
            super(span);
            this.callee = callee;
            this.arguments = arguments;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }

    /* 获取字段 struct.field  struct.func */
    public static class Get extends Expr {
        public final Expr object;
        public final Expr field;

        public Get(Expr object, Expr field) {
            super(Span.between(object.span, field.span));
            this.object = object;
            this.field = field;
        }

        @Override
        public Struct accept(SemanticAnalyzer.SemanticVisitor visitor) {
            return visitor.visit(this);
        }
    }
}