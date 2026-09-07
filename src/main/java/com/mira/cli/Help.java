package com.mira.cli;

public class Help {

    private static final String HELPBLOCK = """
        Project commands (requires mira.toml):
          mira init [--name <n>] [--project <dir>]         Create a new project
          mira build [--mode interpret|compile|package] [--slim|--full] [--watch]
                      [--project <dir>] [--no-warn] [--no-color] [-v]
                                                             Build the project
          mira run [--mode interpret|compile|package] [--slim|--full] [--watch]
                    [--project <dir>] [--no-warn] [--no-color] [--profile] [--stats] [-v] [-- <args>]
                                                             Run the project (default: mode from mira.toml)
          mira test [--project <dir>] [--no-warn] [--no-color] [--coverage] [-v]
                                                             Run all test files matching test.pattern
          mira clean [--project <dir>]                      Delete the output directory
          mira clean build [--mode ...] [--slim|--full]      Delete the output directory, then build
          mira clean release [--mode ...] [--slim|--full]    Delete the output directory, then release
          mira release [--mode ...] [--slim|--full] [--project <dir>] [--no-warn] [-v]
                                                             Full pipeline: build -> post-build -> test
          mira task [--project <dir>]                       List all tasks defined in mira.toml
          mira task <name> [--profile] [--stats]            Run the task named <name>
          mira install [--project <dir>]                    Install the project into the local registry
          mira deps [--project <dir>]                       Print the dependency tree

        `--project <dir>` (short: `-C <dir>`) works with every project command above,
        so you don't need to `cd` into the project first. `-v`/`--verbose` works
        with build/run/test/release too — see below.

        Single-file usage:
          mira <file.mira> [flags]

        On an uncaught error, Mira always prints a crash dump (Mira call stack,
        Java stack trace, and memory dump) to stderr — no flag needed.

        Flags:
          -t,  --tokens          Dump tokens
          -b,  --dump-bytecode   Dump bytecode
          -m,  --main            Use main() as entry point
          -o,  --output <dir>    Output directory for compiled bytecode
          -h,  --help            Show this help
               --version         Print the Mira version
               --check-only      Parse and static-check, then stop (don't execute)
               --no-check        Skip the static check, go straight to execution
               --imports         Show loaded imports with type and alias
          -v,  --verbose         Report progress as it happens: module cache hits/parses,
                                 dependency resolution, static-check summary, compile phase
                                 timing. Combine with --imports for extra detail there.
                                 Also available on build/run/test/release.
               --no-warn         Suppress all warnings and hints
               --no-color        Disable colored/ANSI diagnostic output (also honors NO_COLOR)
               --watch           Watch entry file and all imported modules for changes, restart on save
               --ast             Print AST
               --test            Run test() calls and print a pass/fail summary; exit 1 if any test fails
               --coverage        With --test: print a line-coverage report for the test file(s) and
                                  every module they import, transitively. Also available on `mira test`.
               --debug           Launch interactive debugger
               --profile         Print a function- and line-level timing report after execution
                                 (with --compile, only takes effect together with --run)
               --stats           Print compiler/parser stats (line, token, and AST node counts,
                                 declaration counts, warning count, type-annotation coverage,
                                 per-file timing plus a total) for the entry file and every
                                 module it imports, transitively. With --compile, also reports
                                 bytecode-generation time. Also available on build/run/test/release.
               --strict-types    Require every top-level function's parameters and return type
                                 to carry an explicit type annotation (see the double-colon
                                 syntax, e.g. 'var x : Int : 5;'). Also settable via mira.toml
                                 [build] strict-types = true, and available on build/run/test/release.
               --compile         Compile to JVM bytecode and write .class files
               --run             (with --compile) Run the compiled bytecode in memory instead of just writing it
               --package         (with --compile) Bundle compiled classes and the Mira runtime into a standalone fat JAR
               --slim            (with --package) Bundle only the classes reachable from the program, excluding the compiler/IDE toolchain
               --full            (with --package) Bundle the entire Mira distribution (default)
               -- <args>         Everything after `--` is passed to the program as arguments

        Global commands:
          --lsp                  Start the Mira Language Server (LSP) over stdin/stdout
          --dap                  Start the Mira Debug Adapter (DAP) over stdin/stdout
          --fmt <file>           Format a .mira file in-place
        """;

    public static String getHelp() {
        return HELPBLOCK;
    }
}
