package com.mira.cli;

public class Help {

    private static final String HELPBLOCK = """
        Project commands (requires mira.toml):
          mira init [--name <n>] [--project <dir>]         Create a new project
          mira build [--mode interpret|compile|package] [--slim|--full] [--watch]
                      [--project <dir>] [--no-warn] [--no-color]
                                                             Build the project
          mira run [--mode interpret|compile|package] [--slim|--full] [--watch]
                    [--project <dir>] [--no-warn] [--no-color] [--profile] [-- <args>]
                                                             Run the project (default: mode from mira.toml)
          mira test [--project <dir>] [--no-warn] [--no-color]
                                                             Run all test files matching test.pattern
          mira clean [--project <dir>]                      Delete the output directory
          mira clean build [--mode ...] [--slim|--full]      Delete the output directory, then build
          mira clean release [--mode ...] [--slim|--full]    Delete the output directory, then release
          mira release [--mode ...] [--slim|--full] [--project <dir>] [--no-warn]
                                                             Full pipeline: build -> post-build -> test
          mira task [--project <dir>]                       List all tasks defined in mira.toml
          mira task <name> [--profile]                      Run the task named <name>
          mira install [--project <dir>]                    Install the project into the local registry
          mira deps [--project <dir>]                       Print the dependency tree

        `--project <dir>` (short: `-C <dir>`) works with every project command above,
        so you don't need to `cd` into the project first.

        Single-file usage:
          mira <file.mira> [flags]

        Flags:
          -t,  --tokens          Dump tokens
          -b,  --dump-bytecode   Dump bytecode
          -m,  --main            Use main() as entry point
          -o,  --output <dir>    Output directory for compiled bytecode
          -h,  --help            Show this help
          -v,  --version         Print the Mira version
               --check-only      Parse and static-check, then stop (don't execute)
               --no-check        Skip the static check, go straight to execution
               --imports         Show loaded imports with type and alias
               --verbose         More detail — combine with --imports or --crash-dump
               --no-warn         Suppress all warnings and hints
               --no-color        Disable colored/ANSI diagnostic output (also honors NO_COLOR)
               --crash-dump      On crash: print Mira call stack and memory dump
               --watch           Watch entry file and all imported modules for changes, restart on save
               --ast             Print AST
               --test            Run test() calls and print a pass/fail summary; exit 1 if any test fails
               --debug           Launch interactive debugger
               --profile         Print a function- and line-level timing report after execution
                                 (with --compile, only takes effect together with --run)
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
