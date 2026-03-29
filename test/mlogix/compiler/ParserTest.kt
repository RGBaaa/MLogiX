package mlogix.compiler

import mlogix.mlogix.ast.Expr
import mlogix.mlogix.ast.Stmt
import mlogix.mlogix.ast.Stmt.*
import mlogix.mlogix.token.TokenType
import mlogix.problem.ProblemCollector
import mlogix.util.Log
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.min

class ParserTest {
    private var sourceMapManager: SourceMapManager? = null
    private var problemCollector: ProblemCollector? = null

    @BeforeEach
    fun setUp() {
        sourceMapManager = SourceMapManager()
        problemCollector = ProblemCollector()
        Log.setLevel(Log.LogType.INFO)
    }

    @Test
    fun testEmptyProgram() {
        Log.info("Testing empty program parsing...")
        val stmt = parse("")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertNotNull(program.stmts)
        Assertions.assertEquals(0, program.stmts.size)
        Log.info("Empty program test passed.")
    }

    @Test
    fun testSingleExpressionStatement() {
        Log.info("Testing single expression statement...")
        val stmt = parse("42")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, program.stmts.first())

        val exprStmt = program.stmts.first() as ExprStmt
        Assertions.assertInstanceOf(Expr.Literal::class.java, exprStmt.expr)
        val literal = exprStmt.expr as Expr.Literal
        Assertions.assertEquals(TokenType.INT, literal.token.type)
        Assertions.assertEquals(42.0, literal.token.literal)
        Log.info("Single expression statement test passed.")
    }

    @Test
    fun testVariableAssignment() {
        Log.info("Testing variable assignment...")
        val stmt = parse("x = 42")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(AssignStmt::class.java, program.stmts.first())

        val assignStmt = program.stmts.first() as AssignStmt
        Assertions.assertInstanceOf(Expr.Identifier::class.java, assignStmt.`var`)
        Assertions.assertEquals(TokenType.ASSIGN, assignStmt.operator.type)
        Assertions.assertInstanceOf(Expr.Literal::class.java, assignStmt.value)
        val valueLiteral = assignStmt.value as Expr.Literal
        Assertions.assertEquals(TokenType.INT, valueLiteral.token.type)
        Assertions.assertEquals(42.0, valueLiteral.token.literal)
        Log.info("Variable assignment test passed.")
    }

    @Test
    fun testIfStatementStructure() {
        Log.info("Testing if statement structure...")
        val stmt = parse("if x > 5 { y = 10 } else { y = 0 }")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(IfStmt::class.java, program.stmts.first())

        val ifStmt = program.stmts.first() as IfStmt
        Assertions.assertInstanceOf(Expr.Binary::class.java, ifStmt.condition)
        val condition = ifStmt.condition as Expr.Binary
        Assertions.assertEquals(TokenType.GREATER, condition.operator.type)

        Assertions.assertInstanceOf(BlockStmt::class.java, ifStmt.thenBranch)
        val thenBlock = ifStmt.thenBranch as BlockStmt
        Assertions.assertEquals(1, thenBlock.stmts.size)
        Assertions.assertInstanceOf(AssignStmt::class.java, thenBlock.stmts.first())

        Assertions.assertInstanceOf(BlockStmt::class.java, ifStmt.elseBranch)
        val elseBlock = ifStmt.elseBranch as BlockStmt
        Assertions.assertEquals(1, elseBlock.stmts.size)
        Assertions.assertInstanceOf(AssignStmt::class.java, elseBlock.stmts.first())
        Log.info("If statement structure test passed.")
    }

    @Test
    fun testWhileLoopStructure() {
        Log.info("Testing while loop structure...")
        val stmt = parse("while x < 10 { x = x + 1 }")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(WhileStmt::class.java, program.stmts.first())

        val whileStmt = program.stmts.first() as WhileStmt
        Assertions.assertInstanceOf(Expr.Binary::class.java, whileStmt.expr)
        val condition = whileStmt.expr as Expr.Binary
        Assertions.assertEquals(TokenType.LESS, condition.operator.type)

        Assertions.assertInstanceOf(BlockStmt::class.java, whileStmt.body)
        val blockStmt = whileStmt.body as BlockStmt
        Assertions.assertEquals(1, blockStmt.stmts.size)
        Assertions.assertInstanceOf(AssignStmt::class.java, blockStmt.stmts.first())

        val assignStmt = blockStmt.stmts.first() as AssignStmt
        Assertions.assertInstanceOf(Expr.Identifier::class.java, assignStmt.`var`)
        Assertions.assertInstanceOf(Expr.Binary::class.java, assignStmt.value)
        Log.info("While loop structure test passed.")
    }

    @Test
    fun testForLoopStructure() {
        Log.info("Testing for loop structure...")
        val stmt = parse("for i in range(10) { print(i) }")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ForStmt::class.java, program.stmts.first())

        val forStmt = program.stmts.first() as ForStmt
        Assertions.assertInstanceOf(Expr.Identifier::class.java, forStmt.varDecl)
        Assertions.assertInstanceOf(Expr.Call::class.java, forStmt.expr)
        Assertions.assertInstanceOf(BlockStmt::class.java, forStmt.body)

        val blockStmt = forStmt.body as BlockStmt
        Assertions.assertEquals(1, blockStmt.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, blockStmt.stmts.first())
        Log.info("For loop structure test passed.")
    }

    @Test
    fun testFunctionDeclarationStructure() {
        Log.info("Testing function declaration structure...")
        val stmt = parse("fn add(x, y) -> int { return x + y }")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(FnStmt::class.java, program.stmts.first())

        val fnStmt = program.stmts.first() as FnStmt
        Assertions.assertEquals("add", fnStmt.name!!.literal)
        Assertions.assertEquals(2, fnStmt.parameters!!.size)
        Assertions.assertEquals(1, fnStmt.results!!.size)
        Assertions.assertInstanceOf(BlockStmt::class.java, fnStmt.body)

        val blockStmt = fnStmt.body as BlockStmt
        Assertions.assertEquals(1, blockStmt.stmts.size)
        Assertions.assertInstanceOf(ReturnStmt::class.java, blockStmt.stmts.first())

        val returnStmt = blockStmt.stmts.first() as ReturnStmt
        Assertions.assertInstanceOf(Expr.Binary::class.java, returnStmt.expr)
        Log.info("Function declaration structure test passed.")
    }

    @Test
    fun testUnaryExpressionStructure() {
        Log.info("Testing unary expression structure...")
        val stmt = parse("-x")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, program.stmts.first())

        val exprStmt = program.stmts.first() as ExprStmt
        Assertions.assertInstanceOf(Expr.Unary::class.java, exprStmt.expr)
        val unary = exprStmt.expr as Expr.Unary
        Assertions.assertEquals(TokenType.MINUS, unary.operator.type)
        Assertions.assertInstanceOf(Expr.Identifier::class.java, unary.expr)
        Log.info("Unary expression structure test passed.")
    }

    @Test
    fun testBinaryExpressionStructure() {
        Log.info("Testing binary expression structure...")
        val stmt = parse("a + b * c")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, program.stmts.first())

        val exprStmt = program.stmts.first() as ExprStmt
        Assertions.assertInstanceOf(Expr.Binary::class.java, exprStmt.expr)
        val binary = exprStmt.expr as Expr.Binary
        Assertions.assertEquals(TokenType.PLUS, binary.operator.type)
        Assertions.assertInstanceOf(Expr.Identifier::class.java, binary.left)
        Assertions.assertInstanceOf(Expr.Binary::class.java, binary.right)

        val rightBinary = binary.right as Expr.Binary
        Assertions.assertEquals(TokenType.STAR, rightBinary.operator.type)
        Assertions.assertInstanceOf(Expr.Identifier::class.java, rightBinary.left)
        Assertions.assertInstanceOf(Expr.Identifier::class.java, rightBinary.right)
        Log.info("Binary expression structure test passed.")
    }

    @Test
    fun testArrayExpressionStructure() {
        Log.info("Testing array expression structure...")
        val stmt = parse("({1, 2, 3})")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, program.stmts.first())

        val exprStmt = program.stmts.first() as ExprStmt
        Assertions.assertInstanceOf(Expr.Array::class.java, exprStmt.expr)
        val array = exprStmt.expr as Expr.Array
        Assertions.assertEquals(3, array.elements.size)

        for (i in 0..<array.elements.size) {
            Assertions.assertInstanceOf(Expr.Literal::class.java, array.elements.get(i))
            val literal = array.elements.get(i) as Expr.Literal
            Assertions.assertEquals(TokenType.INT, literal.token.type)
        }
        Log.info("Array expression structure test passed.")
    }

    @Test
    fun testIndexExpressionStructure() {
        Log.info("Testing index expression structure...")
        val stmt = parse("arr[5]")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, program.stmts.first())

        val exprStmt = program.stmts.first() as ExprStmt
        Assertions.assertInstanceOf(Expr.Index::class.java, exprStmt.expr)
        val index = exprStmt.expr as Expr.Index
        Assertions.assertInstanceOf(Expr.Identifier::class.java, index.list)
        Assertions.assertInstanceOf(Expr.Literal::class.java, index.index)
        Log.info("Index expression structure test passed.")
    }

    @Test
    fun testNestedBlockStructure() {
        Log.info("Testing nested block structure...")
        val stmt = parse("{ x = 1; { y = 2; } }")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(BlockStmt::class.java, program.stmts.first())

        val outerBlock = program.stmts.first() as BlockStmt
        Assertions.assertEquals(2, outerBlock.stmts.size)
        Assertions.assertInstanceOf(AssignStmt::class.java, outerBlock.stmts.get(0))
        Assertions.assertInstanceOf(BlockStmt::class.java, outerBlock.stmts.get(1))

        val innerBlock = outerBlock.stmts.get(1) as BlockStmt
        Assertions.assertEquals(1, innerBlock.stmts.size)
        Assertions.assertInstanceOf(AssignStmt::class.java, innerBlock.stmts.first())
        Log.info("Nested block structure test passed.")
    }

    @Test
    fun testComplexMixedStatements() {
        Log.info("Testing complex mixed statements...")
        val input = """
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
                
                """.trimIndent()

        val stmt = parse(input)
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertTrue(program.stmts.size >= 4) // At least 4 statements

        // Check that all statement types are correctly parsed
        var hasAssign = false
        var hasIf = false
        var hasFor = false
        var hasFn = false
        for (s in program.stmts) {
            when (s) {
                is AssignStmt -> hasAssign = true
                is IfStmt -> hasIf = true
                is ForStmt -> hasFor = true
                is FnStmt -> hasFn = true
            }
        }

        Assertions.assertTrue(hasAssign)
        Assertions.assertTrue(hasIf)
        Assertions.assertTrue(hasFor)
        Assertions.assertTrue(hasFn)
        Log.info("Complex mixed statements test passed.")
    }

    @Test
    fun testErrorRecoveryStructure() {
        Log.info("Testing error recovery structure...")
        val input = """
                x = 10
                invalid syntax here
                y = 20
                if true {
                    z = 30
                }
                
                """.trimIndent()

        val stmt = parse(input)
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        // Even with errors, should still have some valid statements
        Assertions.assertTrue(program.stmts.size >= 2) // x=10 and if statement should be parsed
        Log.info("Error recovery structure test completed.")
    }

    @Test
    fun testOperatorPrecedenceStructure() {
        Log.info("Testing operator precedence structure...")
        val stmt = parse("a + b * c - d / e")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, program.stmts.first())

        val exprStmt = program.stmts.first() as ExprStmt
        Assertions.assertInstanceOf(Expr.Binary::class.java, exprStmt.expr)
        val root = exprStmt.expr as Expr.Binary
        Assertions.assertEquals(TokenType.MINUS, root.operator.type)

        // Left side: a + (b * c)
        Assertions.assertInstanceOf(Expr.Binary::class.java, root.left)
        val left = root.left as Expr.Binary
        Assertions.assertEquals(TokenType.PLUS, left.operator.type)

        Assertions.assertInstanceOf(Expr.Identifier::class.java, left.left)
        Assertions.assertInstanceOf(Expr.Binary::class.java, left.right)
        val right = left.right as Expr.Binary
        Assertions.assertEquals(TokenType.STAR, right.operator.type)

        // Right side: d / e
        Assertions.assertInstanceOf(Expr.Binary::class.java, root.right)
        val div = root.right as Expr.Binary
        Assertions.assertEquals(TokenType.SLASH, div.operator.type)
        Log.info("Operator precedence structure test passed.")
    }

    @Test
    fun testComparisonOperatorsStructure() {
        Log.info("Testing comparison operators structure...")
        val stmt = parse("x == y && a != b || c > d")
        Assertions.assertInstanceOf(Program::class.java, stmt)
        val program = stmt as Program
        Assertions.assertEquals(1, program.stmts.size)
        Assertions.assertInstanceOf(ExprStmt::class.java, program.stmts.first())

        val exprStmt = program.stmts.first() as ExprStmt
        Assertions.assertInstanceOf(Expr.Binary::class.java, exprStmt.expr)
        val orOp = exprStmt.expr as Expr.Binary
        Assertions.assertEquals(TokenType.OR_OR, orOp.operator.type)

        // Left side: x == y && a != b
        Assertions.assertInstanceOf(Expr.Binary::class.java, orOp.left)
        val andOp = orOp.left as Expr.Binary
        Assertions.assertEquals(TokenType.AND_AND, andOp.operator.type)

        Assertions.assertInstanceOf(Expr.Binary::class.java, andOp.left)
        Assertions.assertInstanceOf(Expr.Binary::class.java, andOp.right)

        // Right side: c > d
        Assertions.assertInstanceOf(Expr.Binary::class.java, orOp.right)
        val compOp = orOp.right as Expr.Binary
        Assertions.assertEquals(TokenType.GREATER, compOp.operator.type)
        Log.info("Comparison operators structure test passed.")
    }

    // Helper method to parse input and return AST
    private fun parse(input: String): Stmt {
        val sourceMap = sourceMapManager!!.loadSourceMap(input)
        val lexer = Lexer(problemCollector)
        val parser = Parser(lexer, problemCollector!!)

        val result = parser.parse(sourceMap)

        if (!problemCollector!!.hasError()) {
            Log.info("Parsed successfully: " + input.substring(0, min(input.length, 50)))
        } else {
            Log.warning("Parse errors occurred:")
            for (error in problemCollector!!.errors) {
                Log.warning(error.toString())
            }
        }

        return result
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = ParserTest()
            test.setUp()

            Log.info("Running MLogiX Parser Structure Tests...")

            try {
                test.testEmptyProgram()
                test.testSingleExpressionStatement()
                test.testVariableAssignment()
                test.testIfStatementStructure()
                test.testWhileLoopStructure()
                test.testForLoopStructure()
                test.testFunctionDeclarationStructure()
                test.testUnaryExpressionStructure()
                test.testBinaryExpressionStructure()
                test.testArrayExpressionStructure()
                test.testIndexExpressionStructure()
                test.testNestedBlockStructure()
                test.testComplexMixedStatements()
                test.testErrorRecoveryStructure()
                test.testOperatorPrecedenceStructure()
                test.testComparisonOperatorsStructure()
            } catch (e: Exception) {
                Log.error("Test execution failed: " + e.message)
                e.printStackTrace()
            }

            Log.info("All parser structure tests completed.")
        }
    }
}
