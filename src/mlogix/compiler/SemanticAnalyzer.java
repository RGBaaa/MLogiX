package mlogix.compiler;

import mlogix.logix.*;
import mlogix.logix.Expr.*;
import mlogix.logix.Stmt.*;
import mlogix.compiler.issue.Issue.*;
import mlogix.compiler.struct.SourceMapManager.*;
import mlogix.compiler.struct.*;

import java.util.*;

public class SemanticAnalyzer {
    private Stack<Scope> scopeStack;
    private List<SemanticIssue> errorList;
    private List<SemanticIssue> warningList;
    private SourceMap sourceMap;

    // 语义分析访问者接口
    public interface SemanticVisitor {
        // 语句类型
        void visit(Program node);
        void visit(Block node);
        void visit(ExprStmt node);
        void visit(IfStmt node);
        void visit(ForStmt node);
        void visit(WhileStmt node);
        void visit(BreakStmt node);
        void visit(ContinueStmt node);
        void visit(FnStmt node);
        void visit(ReturnStmt node);
        void visit(AssignStmt node);
        void visit(SetVarStmt node);
        
        // 表达式类型
        Struct visit(Literal node);
        Struct visit(Identifier node);
        Struct visit(Unary node);
        Struct visit(Binary node);
        Struct visit(Array node);
        Struct visit(Index node);
        Struct visit(Range node);
        Struct visit(Call node);
        Struct visit(Get node);
    }

    // 符号表条目
    public static class Symbol {
        public final String name;
        public final Struct type;
        public final Span span;

        public Map<String, Object> values = new HashMap<>();

        public Symbol(String name, Struct type, Span span) {
            this.name = name;
            this.type = type;
            this.span = span;
        }
    }

    // 作用域
    public static class Scope {
        private final Map<String, Symbol> symbols = new HashMap<>();
        public final Scope parent;

        public Scope(Scope parent) {
            this.parent = parent;
        }

        /**
         * @return 符号名是否在“当前”作用域出现
         */
        public boolean contains(String name) {
            return symbols.containsKey(name);
        }

        /**
         * 在这个作用域添加符号
         * @param symbol 添加的符号
         * @return 已定义返回false 未定义且成功定义则返回true
         */
        public boolean addSymbol(Symbol symbol) {
            if (contains(symbol.name)) {
                return false; // 重复定义
            }
            symbols.put(symbol.name, symbol);
            return true;
        }

        /**
         * 从符号名找到作用域及其父作用域的符号
         * @return 没找到则为null
         */
        public Symbol lookup(String name) {
            Symbol symbol = symbols.get(name);
            if (symbol == null && parent != null) {
                return parent.lookup(name);
            }
            return symbol;
        }

    }

    // 语义分析主访问者
    /*public class AnalysisVisitor implements SemanticVisitor {
        @Override
        public void visit(Program node) {
            enterScope(); // 全局作用域
            for (Stmt stmt : node.stmts) {
                stmt.accept(this);
            }
            exitScope();
        }

        @Override
        public void visit(Block node) {
            enterScope();
            for (Stmt stmt : node.stmts) {
                stmt.accept(this);
            }
            exitScope();
        }

        @Override
        public void visit(ExprStmt node) {
            node.expr.accept(this);
        }

        @Override
        public void visit(IfStmt node) {
            node.condition.accept(this);
            enterScope();
            node.thenBranch.accept(this);
            exitScope();
            if (node.elseBranch != null) {
                enterScope();
                node.elseBranch.accept(this);
                exitScope();
            }
        }

        @Override
        public void visit(ForStmt node) {
            Struct type = node.expr.accept(this);
            if(node.varDecl != null) {
                currentScope().addSymbol(new Symbol((String)node.varDecl.token.literal,
                        type,
                        Span.between(node.varDecl.span, node.expr.span);
            }

            enterScope();
            node.body.accept(this);
            exitScope();
        }

        @Override
        public void visit(WhileStmt node) {
            node.expr.accept(this);
            node.body.accept(this);
        }

        @Override
        public void visit(BreakStmt node) {
            // 检查是否在循环内部
        }

        @Override
        public void visit(ContinueStmt node) {
            // 检查是否在循环内部
        }

        @Override
        public void visit(FnStmt node) {
            String functionName = (String) node.name.literal;
            
            // 检查函数是否已定义
            if (functionTable.containsKey(functionName)) {
                error("函数 '" + functionName + "' 已定义", node.name);
                return;
            }
            
            // 解析参数类型
            List<Struct> paramTypes = new ArrayList<>();
            List<Symbol> paramSymbols = new ArrayList<>();
            
            for (Expr param : node.parameters) {
                if (param instanceof Identifier) {
                    Identifier id = (Identifier) param;
                    String paramName = (String) id.token.literal;
                    
                    // 检查参数名是否重复
                    for (Symbol existingParam : paramSymbols) {
                        if (existingParam.name.equals(paramName)) {
                            error("参数名 '" + paramName + "' 重复", id.token);
                            break;
                        }
                    }
                    
                    // 解析参数类型
                    Struct paramType = BuiltinStruct.Unknown;
                    if (id.type != null) {
                        String typeName = (String) id.type.literal;
                        paramType = resolveType(typeName);
                        if (paramType == BuiltinStruct.Unknown) {
                            error("未知参数类型: " + typeName, id.type);
                        }
                    } else {
                        error("参数 '" + paramName + "' 缺少类型声明", id.token);
                    }
                    
                    paramTypes.add(paramType);
                    paramSymbols.add(new Symbol(paramName, paramType, new Span(sourceMap.index(), id.token.start, id.token.end)));
                } else {
                    error("无效的参数声明", param.token);
                }
            }
            
            // 解析返回类型（如果有）
            Struct returnType = BuiltinStruct.Unknown; // 默认返回类型
            
            // 创建函数符号并添加到函数表
            FunctionSymbol functionSymbol = new FunctionSymbol(
                functionName,
                returnType,
                paramTypes,
                new Span(sourceMap.index(), node.name.start, node.name.end)
            );
            functionTable.put(functionName, functionSymbol);
            
            // 分析函数体
            enterScope();
            
            // 将参数添加到函数作用域
            for (Symbol paramSymbol : paramSymbols) {
                currentScope().put(paramSymbol.name, paramSymbol);
            }
            
            node.body.accept(this);
            exitScope();
        }

        @Override
        public void visit(ReturnStmt node) {
            if (node.expr != null) {
                node.expr.accept(this);
            }
            
            // 检查返回类型是否匹配函数声明
            // 这里需要知道当前所在的函数，可以通过维护一个当前函数栈来实现
            // 简化版实现：检查返回值类型是否有效
            if (node.expr != null) {
                Struct returnType = analyzeExpression(node.expr);
                if (returnType == BuiltinStruct.Unknown) {
                    error("无法确定返回值类型", node.expr.token);
                }
                
                // 完整实现应该检查返回类型是否与函数声明的返回类型匹配
                // 这需要知道当前所在的函数
            }
        }

        @Override
        public void visit(SetVarStmt node) {
            // 处理变量声明
            if (node.var instanceof Identifier identifier) {
                String name = (String) identifier.token.literal;
                
                // 检查变量是否已在当前作用域中定义
                Map<String, Symbol> currentScope = currentScope();
                if (currentScope.containsKey(name)) {
                    error("变量 '" + name + "' 已在当前作用域中定义", id.token);
                    return;
                }
                
                // 确定变量类型
                Struct type = null;
                if (id.type != null) {
                    // 如果显式指定了类型
                    String typeName = (String) id.type.literal;
                    type = resolveType(typeName);
                    if (type == BuiltinStruct.Unknown) {
                        error("未知类型: " + typeName, id.type);
                    }
                }
                
                // 如果有初始化表达式，检查类型兼容性
                if (node.assignStmt != null && node.assignStmt instanceof AssignStmt) {
                    AssignStmt assignStmt = (AssignStmt) node.assignStmt;
                    assignStmt.accept(this);
                    
                    Struct initType = analyzeExpression(assignStmt.value);
                    
                    if (type == null) {
                        // 如果没有显式指定类型，使用初始化表达式的类型
                        type = initType;
                    } else if (initType != type && !isAssignable(type, initType)) {
                        error("类型不匹配: 无法将类型 '" + initType.name + "' 赋值给类型 '" + type.name + "'", assignStmt.value.token);
                    }
                }
                
                // 如果类型仍然未确定，使用默认类型
                if (type == null) {
                    type = BuiltinStruct.Unknown;
                    error("无法推断变量 '" + name + "' 的类型", id.token);
                }
                
                // 添加变量到当前作用域
                Symbol symbol = new Symbol(name, type, new Span(sourceMap.index(), id.token.start, id.token.end));
                currentScope.put(name, symbol);
            } else {
                // 如果不是简单的标识符，可能是数组或对象属性赋值
                node.var.accept(this);
                if (node.assignStmt != null) {
                    node.assignStmt.accept(this);
                }
            }
        }

        @Override
        public void visit(ConstVarStmt node) {
            node.var.accept(this);
            if (node.assignStmt != null) {
                node.assignStmt.accept(this);
            }
            // 标记为常量，不可修改
        }

        @Override
        public void visit(MacroVarStmt node) {
            node.var.accept(this);
            if (node.assignStmt != null) {
                node.assignStmt.accept(this);
            }
            // 处理宏定义
        }

        @Override
        public void visit(AssignStmt node) {
            node.var.accept(this);
            node.value.accept(this);
            
            // 检查赋值类型是否匹配
            Struct varType = analyzeExpression(node.var);
            Struct valueType = analyzeExpression(node.value);
            
            if (varType == BuiltinStruct.Unknown) {
                // 如果变量类型未知，可能是未定义的变量
                if (node.var instanceof Identifier) {
                    String name = (String) ((Identifier) node.var).token.literal;
                    error("未定义的变量: " + name, ((Identifier) node.var).token);
                }
                return;
            }
            
            // 检查赋值操作符
            if (node.operator.type != TokenType.EQ) {
                // 复合赋值操作符 (+=, -=, *=, /=)
                TokenType baseOperator = null;
                switch (node.operator.type) {
                    case PLUS_ASSIGN -> baseOperator = TokenType.PLUS;
                    case MINUS_ASSIGN -> baseOperator = TokenType.MINUS;
                    case STAR_ASSIGN -> baseOperator = TokenType.STAR;
                    case SLASH_ASSIGN -> baseOperator = TokenType.SLASH;
                }
                
                if (baseOperator != null && !isTypeCompatible(baseOperator, varType, valueType)) {
                    error("类型不兼容: 无法对类型 '" + varType.name + "' 和 '" + valueType.name + 
                          "' 执行操作 '" + node.operator.lexeme + "'", node.operator);
                    return;
                }
            } else if (valueType != varType && !isAssignable(varType, valueType)) {
                // 简单赋值操作符 (=)
                error("类型不匹配: 无法将类型 '" + valueType.name + "' 赋值给类型 '" + varType.name + "'", node.value.token);
            }
        }

        @Override
        public void visit(Literal node) {
            // 处理字面量
        }

        @Override
        public void visit(Identifier node) {
            // 检查标识符是否已定义
            String name = (String) node.token.literal;
            Symbol symbol = lookupSymbol(name);
            if (symbol == null) {
                error("未定义的标识符: " + name, node.token);
            }
        }

        @Override
        public void visit(Unary node) {
            node.expr.accept(this);
            // 检查一元运算符类型
        }

        @Override
        public void visit(Binary node) {
            node.left.accept(this);
            node.right.accept(this);
            
            // 检查二元运算符类型
            Struct leftType = analyzeExpression(node.left);
            Struct rightType = analyzeExpression(node.right);
            
            // 检查类型兼容性
            if (!isTypeCompatible(node.operator.type, leftType, rightType)) {
                error("类型不兼容: 无法对类型 '" + leftType.name + "' 和 '" + rightType.name + "' 执行操作 '" + node.operator.lexeme + "'", node.operator);
            }
        }
        
        // 检查类型兼容性
        private boolean isTypeCompatible(TokenType operator, Struct leftType, Struct rightType) {
            // 根据操作符和类型进行兼容性检查
            switch (operator) {
                case PLUS, MINUS, STAR, SLASH:
                    return (leftType == BuiltinStruct.Int || leftType == BuiltinStruct.Num) &&
                           (rightType == BuiltinStruct.Int || rightType == BuiltinStruct.Num);
                case EQ_EQ, BANG_EQ:
                    return true; // 所有类型都可以进行相等性比较
                case GREATER, GREATER_EQ, LESS, LESS_EQ:
                    return (leftType == BuiltinStruct.Int || leftType == BuiltinStruct.Num) &&
                           (rightType == BuiltinStruct.Int || rightType == BuiltinStruct.Num);
                default:
                    return false;
            }
        }

        @Override
        public void visit(Array node) {
            for (Expr element : node.elements) {
                element.accept(this);
            }
            // 检查数组元素类型
        }

        @Override
        public void visit(Index node) {
            node.list.accept(this);
            node.index.accept(this);
            // 检查索引类型
        }

        @Override
        public void visit(Slice node) {
            if (node.list != null) {
                node.list.accept(this);
            }
            if (node.left != null) {
                node.left.accept(this);
            }
            if (node.right != null) {
                node.right.accept(this);
            }
            // 检查切片类型
        }

        @Override
        public void visit(Call node) {
            node.callee.accept(this);
            for (Expr arg : node.arguments) {
                arg.accept(this);
            }
            
            // 检查函数调用
            if (node.callee instanceof Identifier) {
                String functionName = (String) ((Identifier) node.callee).token.literal;
                FunctionSymbol function = functionTable.get(functionName);
                
                if (function == null) {
                    error("未定义的函数: " + functionName, ((Identifier) node.callee).token);
                    return;
                }
                
                // 检查参数数量
                if (function.parameters.size() != node.arguments.size()) {
                    error("函数 '" + functionName + "' 需要 " + function.parameters.size() +
                          " 个参数，但提供了 " + node.arguments.size() + " 个", 
                          new Span(sourceMap.index(), node.arguments.isEmpty() ? 
                                  node.paren.start : node.arguments.get(0).token.start,
                                  node.arguments.isEmpty() ? 
                                  node.paren.end : node.arguments.get(node.arguments.size() - 1).token.end));
                    return;
                }
                
                // 检查参数类型
                for (int i = 0; i < node.arguments.size(); i++) {
                    Struct argType = analyzeExpression(node.arguments.get(i));
                    Struct paramType = function.parameters.get(i).type;
                    
                    if (argType != paramType && !isAssignable(paramType, argType)) {
                        error("参数类型不匹配: 函数 '" + functionName + "' 的第 " + (i + 1) + 
                              " 个参数需要类型 '" + paramType.name + "'，但提供了类型 '" + 
                              argType.name + "'", node.arguments.get(i).token);
                    }
                }
            } else if (node.callee instanceof Get) {
                // 处理方法调用
                Get get = (Get) node.callee;
                Struct objectType = analyzeExpression(get.object);
                String methodName = (String) get.name.literal;
                
                // 检查对象是否有该方法
                if (objectType.methods == null || !objectType.methods.containsKey(methodName)) {
                    error("类型 '" + objectType.name + "' 没有方法 '" + methodName + "'", get.name);
                }
                // 这里可以进一步检查方法参数类型
            }
        }
        
        // 检查类型是否可赋值
        private boolean isAssignable(Struct target, Struct source) {
            // 相同类型可以赋值
            if (target == source) return true;
            
            // 特殊类型转换规则
            if (target == BuiltinStruct.Num && source == BuiltinStruct.Int) return true;
            
            // 其他类型转换规则可以在这里添加
            
            return false;
        }

        @Override
        public Struct visit(Get node) {
            node.object.accept(this);
            node.field.accept(this);
            // TODO 检查对象字段访问
        }
    }*/

    // 辅助方法
    private void enterScope() {
        Scope parent = scopeStack.isEmpty() ? null : scopeStack.peek();
        scopeStack.push(new Scope(parent));
    }

    private void exitScope() {
        scopeStack.pop();
    }

    private Scope currentScope() {
        return scopeStack.peek();
    }

    private Symbol lookupSymbol(String name) {
        return currentScope().lookup(name);
    }

    // 根据操作符和操作数类型确定结果类型
    private Struct getResultType(Token operator, Struct leftType, Struct rightType) {
        
        switch (operator.type) {
            case PLUS, MINUS, STAR, SLASH:
                if (leftType == BuiltinStruct.Num || rightType == BuiltinStruct.Num) {
                    return BuiltinStruct.Num;
                }
                return BuiltinStruct.Int;
            case GREATER, GREATER_EQ, LESS, LESS_EQ, EQ_EQ, BANG_EQ:
                return BuiltinStruct.Bool;
            default:
                return BuiltinStruct.Unknown;
        }
    }

    // 错误
    private SemanticIssue error(String text) {
        SemanticIssue e = new SemanticIssue(sourceMap, text, IssueLevel.ERROR);
        errorList.add(e);
        return e;
    }

    // 警告
    private SemanticIssue warning(String text) {
        SemanticIssue e = new SemanticIssue(sourceMap, text, IssueLevel.WARNING);
        warningList.add(e);
        return e;
    }

    // 执行语义分析
    public SemanticResult analyze(ASTNode ast, SourceMap sourceMap) {
        this.sourceMap = sourceMap;
        this.scopeStack = new Stack<>();
        this.errorList = new ArrayList<>();
        this.warningList = new ArrayList<>();

        //AnalysisVisitor visitor = new AnalysisVisitor();
        //ast.accept(visitor);

        return new SemanticResult(errorList, warningList);
    }
    
    // 语义分析结果
    public record SemanticResult(List<SemanticIssue> errorList, List<SemanticIssue> warningList) {
    }

}