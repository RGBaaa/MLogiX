package mlogix.compiler;

import mlogix.mlogix.ast.Expr;
import mlogix.mlogix.ast.Stmt;
import mlogix.mlogix.token.TokenType;
import mlogix.problem.ProblemCollector;
import mlogix.util.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParserTest {
    private SourceMapManager sourceMapManager;
    private ProblemCollector problemCollector;

    public static void main(String[] args) {
        ParserTest test = new ParserTest();
        test.setUp();

        Log.info("Running MLogiX Parser Structure Tests...");

        try {
            test.testEmptyProgram();
            test.testSingleExpressionStatement();
            test.testVariableAssignment();
            test.testIfStatementStructure();
            test.testWhileLoopStructure();
            test.testForLoopStructure();
            test.testFunctionDeclarationStructure();
            test.testUnaryExpressionStructure();
            test.testBinaryExpressionStructure();
            test.testArrayExpressionStructure();
            test.testIndexExpressionStructure();
            test.testNestedBlockStructure();
            test.testComplexMixedStatements();
            test.testErrorRecoveryStructure();
            test.testOperatorPrecedenceStructure();
            test.testComparisonOperatorsStructure();
        } catch(Exception e) {
            Log.error("Test execution failed: " + e.getMessage());
            e.printStackTrace();
        }

        Log.info("All parser structure tests completed.");
    }

    @BeforeEach
    void setUp() {
        sourceMapManager = new SourceMapManager();
        problemCollector = new ProblemCollector();
        Log.setLevel(Log.LogType.INFO);
    }

    @Test
    void testEmptyProgram() {
        Log.info("Testing empty program parsing...");
        Stmt stmt = parse("");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertNotNull(program.stmts);
        assertEquals(0, program.stmts.size);
        Log.info("Empty program test passed.");
    }

    @Test
    void testSingleExpressionStatement() {
        Log.info("Testing single expression statement...");
        Stmt stmt = parse("42");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, program.stmts.first());

        Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) program.stmts.first();
        assertInstanceOf(Expr.Literal.class, exprStmt.expr);
        Expr.Literal literal = (Expr.Literal) exprStmt.expr;
        assertEquals(TokenType.INT, literal.token.type);
        assertEquals(42.0, literal.token.literal);
        Log.info("Single expression statement test passed.");
    }

    @Test
    void testVariableAssignment() {
        Log.info("Testing variable assignment...");
        Stmt stmt = parse("x = 42");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.AssignStmt.class, program.stmts.first());

        Stmt.AssignStmt assignStmt = (Stmt.AssignStmt) program.stmts.first();
        assertInstanceOf(Expr.Identifier.class, assignStmt.var);
        assertEquals(TokenType.ASSIGN, assignStmt.operator.type);
        assertInstanceOf(Expr.Literal.class, assignStmt.value);
        Expr.Literal valueLiteral = (Expr.Literal) assignStmt.value;
        assertEquals(TokenType.INT, valueLiteral.token.type);
        assertEquals(42.0, valueLiteral.token.literal);
        Log.info("Variable assignment test passed.");
    }

    @Test
    void testIfStatementStructure() {
        Log.info("Testing if statement structure...");
        Stmt stmt = parse("if x > 5 { y = 10 } else { y = 0 }");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.IfStmt.class, program.stmts.first());

        Stmt.IfStmt ifStmt = (Stmt.IfStmt) program.stmts.first();
        assertInstanceOf(Expr.Binary.class, ifStmt.condition);
        Expr.Binary condition = (Expr.Binary) ifStmt.condition;
        assertEquals(TokenType.GREATER, condition.operator.type);

        assertInstanceOf(Stmt.Block.class, ifStmt.thenBranch);
        Stmt.Block thenBlock = (Stmt.Block) ifStmt.thenBranch;
        assertEquals(1, thenBlock.stmts.size);
        assertInstanceOf(Stmt.AssignStmt.class, thenBlock.stmts.first());

        assertInstanceOf(Stmt.Block.class, ifStmt.elseBranch);
        Stmt.Block elseBlock = (Stmt.Block) ifStmt.elseBranch;
        assertEquals(1, elseBlock.stmts.size);
        assertInstanceOf(Stmt.AssignStmt.class, elseBlock.stmts.first());
        Log.info("If statement structure test passed.");
    }

    @Test
    void testWhileLoopStructure() {
        Log.info("Testing while loop structure...");
        Stmt stmt = parse("while x < 10 { x = x + 1 }");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.WhileStmt.class, program.stmts.first());

        Stmt.WhileStmt whileStmt = (Stmt.WhileStmt) program.stmts.first();
        assertInstanceOf(Expr.Binary.class, whileStmt.expr);
        Expr.Binary condition = (Expr.Binary) whileStmt.expr;
        assertEquals(TokenType.LESS, condition.operator.type);

        assertInstanceOf(Stmt.Block.class, whileStmt.body);
        Stmt.Block block = (Stmt.Block) whileStmt.body;
        assertEquals(1, block.stmts.size);
        assertInstanceOf(Stmt.AssignStmt.class, block.stmts.first());

        Stmt.AssignStmt assignStmt = (Stmt.AssignStmt) block.stmts.first();
        assertInstanceOf(Expr.Identifier.class, assignStmt.var);
        assertInstanceOf(Expr.Binary.class, assignStmt.value);
        Log.info("While loop structure test passed.");
    }

    @Test
    void testForLoopStructure() {
        Log.info("Testing for loop structure...");
        Stmt stmt = parse("for i in range(10) { print(i) }");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ForStmt.class, program.stmts.first());

        Stmt.ForStmt forStmt = (Stmt.ForStmt) program.stmts.first();
        assertInstanceOf(Expr.Identifier.class, forStmt.varDecl);
        assertInstanceOf(Expr.Call.class, forStmt.expr);
        assertInstanceOf(Stmt.Block.class, forStmt.body);

        Stmt.Block block = (Stmt.Block) forStmt.body;
        assertEquals(1, block.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, block.stmts.first());
        Log.info("For loop structure test passed.");
    }

    @Test
    void testFunctionDeclarationStructure() {
        Log.info("Testing function declaration structure...");
        Stmt stmt = parse("fn add(x, y) -> int { return x + y }");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.FnStmt.class, program.stmts.first());

        Stmt.FnStmt fnStmt = (Stmt.FnStmt) program.stmts.first();
        assertEquals("add", fnStmt.name.literal);
        assertEquals(2, fnStmt.parameters.size);
        assertEquals(1, fnStmt.results.size);
        assertInstanceOf(Stmt.Block.class, fnStmt.body);

        Stmt.Block block = (Stmt.Block) fnStmt.body;
        assertEquals(1, block.stmts.size);
        assertInstanceOf(Stmt.ReturnStmt.class, block.stmts.first());

        Stmt.ReturnStmt returnStmt = (Stmt.ReturnStmt) block.stmts.first();
        assertInstanceOf(Expr.Binary.class, returnStmt.expr);
        Log.info("Function declaration structure test passed.");
    }

    @Test
    void testUnaryExpressionStructure() {
        Log.info("Testing unary expression structure...");
        Stmt stmt = parse("-x");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, program.stmts.first());

        Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) program.stmts.first();
        assertInstanceOf(Expr.Unary.class, exprStmt.expr);
        Expr.Unary unary = (Expr.Unary) exprStmt.expr;
        assertEquals(TokenType.MINUS, unary.operator.type);
        assertInstanceOf(Expr.Identifier.class, unary.expr);
        Log.info("Unary expression structure test passed.");
    }

    @Test
    void testBinaryExpressionStructure() {
        Log.info("Testing binary expression structure...");
        Stmt stmt = parse("a + b * c");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, program.stmts.first());

        Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) program.stmts.first();
        assertInstanceOf(Expr.Binary.class, exprStmt.expr);
        Expr.Binary binary = (Expr.Binary) exprStmt.expr;
        assertEquals(TokenType.PLUS, binary.operator.type);
        assertInstanceOf(Expr.Identifier.class, binary.left);
        assertInstanceOf(Expr.Binary.class, binary.right);

        Expr.Binary rightBinary = (Expr.Binary) binary.right;
        assertEquals(TokenType.STAR, rightBinary.operator.type);
        assertInstanceOf(Expr.Identifier.class, rightBinary.left);
        assertInstanceOf(Expr.Identifier.class, rightBinary.right);
        Log.info("Binary expression structure test passed.");
    }

    @Test
    void testArrayExpressionStructure() {
        Log.info("Testing array expression structure...");
        Stmt stmt = parse("({1, 2, 3})");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, program.stmts.first());

        Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) program.stmts.first();
        assertInstanceOf(Expr.Array.class, exprStmt.expr);
        Expr.Array array = (Expr.Array) exprStmt.expr;
        assertEquals(3, array.elements.size);

        for(int i = 0; i < array.elements.size; i++) {
            assertInstanceOf(Expr.Literal.class, array.elements.get(i));
            Expr.Literal literal = (Expr.Literal) array.elements.get(i);
            assertEquals(TokenType.INT, literal.token.type);
        }
        Log.info("Array expression structure test passed.");
    }

    @Test
    void testIndexExpressionStructure() {
        Log.info("Testing index expression structure...");
        Stmt stmt = parse("arr[5]");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, program.stmts.first());

        Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) program.stmts.first();
        assertInstanceOf(Expr.Index.class, exprStmt.expr);
        Expr.Index index = (Expr.Index) exprStmt.expr;
        assertInstanceOf(Expr.Identifier.class, index.list);
        assertInstanceOf(Expr.Literal.class, index.index);
        Log.info("Index expression structure test passed.");
    }

    @Test
    void testNestedBlockStructure() {
        Log.info("Testing nested block structure...");
        Stmt stmt = parse("{ x = 1; { y = 2; } }");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.Block.class, program.stmts.first());

        Stmt.Block outerBlock = (Stmt.Block) program.stmts.first();
        assertEquals(2, outerBlock.stmts.size);
        assertInstanceOf(Stmt.AssignStmt.class, outerBlock.stmts.get(0));
        assertInstanceOf(Stmt.Block.class, outerBlock.stmts.get(1));

        Stmt.Block innerBlock = (Stmt.Block) outerBlock.stmts.get(1);
        assertEquals(1, innerBlock.stmts.size);
        assertInstanceOf(Stmt.AssignStmt.class, innerBlock.stmts.first());
        Log.info("Nested block structure test passed.");
    }

    @Test
    void testComplexMixedStatements() {
        Log.info("Testing complex mixed statements...");
        String input = """
                x = 10
                if x > 5 {
                    y = x * 2
                } else {
                    y = x / 2
                }
                
                for i in range(y) {
                    print(i)
                }
                
                fn square(n) -> int {
                    return n * n
                }
                
                result = square(5)
                """;

        Stmt stmt = parse(input);
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertTrue(program.stmts.size >= 4); // At least 4 statements

        // Check that all statement types are correctly parsed
        boolean hasAssign = false, hasIf = false, hasFor = false, hasFn = false;
        for(Stmt s : program.stmts) {
            if(s instanceof Stmt.AssignStmt) hasAssign = true;
            else if(s instanceof Stmt.IfStmt) hasIf = true;
            else if(s instanceof Stmt.ForStmt) hasFor = true;
            else if(s instanceof Stmt.FnStmt) hasFn = true;
        }

        assertTrue(hasAssign);
        assertTrue(hasIf);
        assertTrue(hasFor);
        assertTrue(hasFn);
        Log.info("Complex mixed statements test passed.");
    }

    @Test
    void testErrorRecoveryStructure() {
        Log.info("Testing error recovery structure...");
        String input = """
                x = 10
                invalid syntax here
                y = 20
                if true {
                    z = 30
                }
                """;

        Stmt stmt = parse(input);
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        // Even with errors, should still have some valid statements
        assertTrue(program.stmts.size >= 2); // x=10 and if statement should be parsed
        Log.info("Error recovery structure test completed.");
    }

    @Test
    void testOperatorPrecedenceStructure() {
        Log.info("Testing operator precedence structure...");
        Stmt stmt = parse("a + b * c - d / e");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, program.stmts.first());

        Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) program.stmts.first();
        assertInstanceOf(Expr.Binary.class, exprStmt.expr);
        Expr.Binary root = (Expr.Binary) exprStmt.expr;
        assertEquals(TokenType.MINUS, root.operator.type);

        // Left side: a + (b * c)
        assertInstanceOf(Expr.Binary.class, root.left);
        Expr.Binary left = (Expr.Binary) root.left;
        assertEquals(TokenType.PLUS, left.operator.type);

        assertInstanceOf(Expr.Identifier.class, left.left);
        assertInstanceOf(Expr.Binary.class, left.right);
        Expr.Binary mult = (Expr.Binary) left.right;
        assertEquals(TokenType.STAR, mult.operator.type);

        // Right side: d / e
        assertInstanceOf(Expr.Binary.class, root.right);
        Expr.Binary div = (Expr.Binary) root.right;
        assertEquals(TokenType.SLASH, div.operator.type);
        Log.info("Operator precedence structure test passed.");
    }

    @Test
    void testComparisonOperatorsStructure() {
        Log.info("Testing comparison operators structure...");
        Stmt stmt = parse("x == y && a != b || c > d");
        assertInstanceOf(Stmt.Program.class, stmt);
        Stmt.Program program = (Stmt.Program) stmt;
        assertEquals(1, program.stmts.size);
        assertInstanceOf(Stmt.ExprStmt.class, program.stmts.first());

        Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) program.stmts.first();
        assertInstanceOf(Expr.Binary.class, exprStmt.expr);
        Expr.Binary orOp = (Expr.Binary) exprStmt.expr;
        assertEquals(TokenType.OR_OR, orOp.operator.type);

        // Left side: x == y && a != b
        assertInstanceOf(Expr.Binary.class, orOp.left);
        Expr.Binary andOp = (Expr.Binary) orOp.left;
        assertEquals(TokenType.AND_AND, andOp.operator.type);

        assertInstanceOf(Expr.Binary.class, andOp.left);
        assertInstanceOf(Expr.Binary.class, andOp.right);

        // Right side: c > d
        assertInstanceOf(Expr.Binary.class, orOp.right);
        Expr.Binary compOp = (Expr.Binary) orOp.right;
        assertEquals(TokenType.GREATER, compOp.operator.type);
        Log.info("Comparison operators structure test passed.");
    }

    // Helper method to parse input and return AST
    private Stmt parse(String input) {
        SourceMapManager.SourceMap sourceMap = sourceMapManager.loadSourceMap(input);
        Lexer lexer = new Lexer(problemCollector);
        Parser parser = new Parser(lexer, problemCollector);

        Stmt result = parser.parse(sourceMap);

        if(!problemCollector.hasError()) {
            Log.info("Parsed successfully: " + input.substring(0, Math.min(input.length(), 50)));
        } else {
            Log.warning("Parse errors occurred:");
            for(var error : problemCollector.errors) {
                Log.warning(error.toString());
            }
        }

        return result;
    }
}
