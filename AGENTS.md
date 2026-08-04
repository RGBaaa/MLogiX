# AGENTS — Guidance for automated coding agents

Purpose: give an AI code-writing or code-understanding agent the minimal, actionable knowledge to be immediately
productive in this repository.

Quick start (Windows / PowerShell)

- Build & run the CLI compile task (recommended):
    - .\gradlew.bat compile # runs mlogix.Main with arg 'c'
    - .\gradlew.bat compile-debug# runs mlogix.Main with args 'c' 'd' (extra debug flag)
- Run unit tests:
    - .\gradlew.bat test
    - Run a single test class: .\gradlew.bat test --tests "mlogix.compiler.LexerTest"
- JAR output: build/libs/MLogiX.jar (Gradle produces this artifact)

Big picture architecture

- This repo is a small language front-end (lexer → parser → AST → problem reporting).
- Runtime/entrypoint: `src/mlogix/Main.java` — interprets CLI args and calls `Compiler`.
- Compiler orchestration: `src/mlogix/compiler/Compiler.kt` — creates `Lexer`, `Parser`, `ProblemCollector`,
  `SourceMapManager` and runs phases. Treat it as the canonical pipeline when adding features.
- Lexing: `src/mlogix/compiler/Lexer.kt` — tokenizes input, produces `Token` objects with `Span` locations.
- Parsing / AST: `src/mlogix/compiler/Parser.kt` and `src/mlogix/compiler/ast/` — builds `Expr`/`Stmt` nodes.
  `ASTPrinter.kt` is used for debugging/printing ASTs.
- Source mapping & errors: `src/mlogix/compiler/SourceMapManager.kt`, `src/mlogix/problem/Problem.kt`,
  `ProblemCollector.kt` — errors and warnings are associated with `Span` and printed with contextual lines.

Project-specific conventions and important patterns

- Single-source layout: both Java and Kotlin sources live under `src/` (Gradle configured to use `src` for java and
  kotlin). Tests live under `test/`.
- Language positions: use `Span` (in `src/mlogix/span/Span.kt`) across AST nodes and problems. When changing AST nodes,
  ensure spans are correct (use Span.between or propagate token.span).
- Problem reporting: create `Problem` instances with a `SourceMap` and then call `ProblemCollector.printError()`; the
  collector is the single place tests and the Compiler inspect for failure counts.
- File abstraction: code uses a Fi-like file abstraction (see usage in `Main.java` and `SourceMapManager.kt`) — prefer
  using the same abstraction when adding file IO.
- Tests use JUnit 5 (see `build.gradle` test configuration). Test examples:
    - `test/mlogix/compiler/LexerTest.kt` shows how to instantiate `Lexer` and assert token sequences.
    - `test/mlogix/compiler/ParserTest.kt` shows parser usage: `parser.parse("2 + 3")`.

Integration points & external dependencies

- Dependencies declared in `build.gradle`: arc-core and Mindustry core from custom Maven endpoints. The project pins a
  `mindustryVersion` and enforces Arc versions via a resolution strategy — when modifying dependencies preserve this
  pattern (see lines 35–59 in `build.gradle`).
- Gradle toolchain: Java 17 and Kotlin JVM toolchain set in `build.gradle` — CI or agents must target JDK 17.

Developer workflows important to automation

- Adding sources: place new Kotlin/Java files under `src/mlogix/…` (packages are `mlogix`), tests under `test/mlogix/…`.
- Running the project in-process: use Gradle JavaExec tasks `compile` and `compile-debug` (these are the intended
  execution shortcuts instead of a `run` task).
- Test artifacts & results are in `build/test-results/` and `build/reports/tests/` — CI agents should inspect these for
  failures.

Recommended extension tasks for agents (when implementing features)

- When adding a new token type: update `Token` definitions, `Lexer.scanToken()` in `Lexer.kt`, update `Parser` to
  consume it, and add unit tests in `test/mlogix/compiler/*`.
- When adding AST node types: update `compiler/ast/*.kt`, `ASTPrinter.kt` for debug printing, and adjust parser (s) in
  `Parser.kt` to create the node. Ensure spans are assigned.

Where to read language behaviour and design rationale

- User-facing grammar and quick guides: `docs/grammar/index.md` and `docs/grammar/fast-learning.md`.
- Design notes / parser recovery: `src/mlogix/compiler/TODO-scope_stack.md` — explains error recovery rationale used by
  the parser.
- High-level roadmap and component breakdown: `plan.md`.

Searchable anchors for an agent

- Entrypoint: `src/mlogix/Main.java`
- Pipeline: `src/mlogix/compiler/Compiler.kt`
- Lexer: `src/mlogix/compiler/Lexer.kt`
- Parser: `src/mlogix/compiler/Parser.kt`
- AST: `src/mlogix/compiler/ast/*.kt`
- Problems: `src/mlogix/problem/*.kt`

If you need more context

- Prefer reading `Compiler.kt` first to see how phases are wired. Then read `SourceMapManager.kt`, `ProblemCollector.kt`
  and tests to understand expected behavior and error formats.

License & docs: see `README.md` and `docs/` for language-user docs.

