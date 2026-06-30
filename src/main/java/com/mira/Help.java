package com.mira;

public class Help {

    private static final String HELPBLOCK = """
        Project commands (requires mira.toml):
          mira init [--name <n>]                   Create a new project in the current directory
          mira build [--mode interpret|compile|package] [--slim|--full] [--watch]  Build the project
          mira run [--mode interpret|compile|package] [--slim|--full] [-- <args>]  Run the project (default: mode from mira.toml)
          mira test                                Run all test files matching test.pattern
          mira clean                               Delete the output directory
          mira clean build [--mode ...] [--slim|--full]    Delete the output directory, then build
          mira clean release [--mode ...] [--slim|--full]  Delete the output directory, then release
          mira release [--mode ...] [--slim|--full]  Full pipeline: build → post-build → test
          mira task                                List all tasks defined in mira.toml
          mira task <name>                         Run the task named <name>

        Single-file usage:
          mira <file.mira> [flags]

        Flags:
          -t              Dump tokens
          -b              Dump bytecode
          -e              Exit before interpreter
          -m              Use main() as entry point
          -args {a,b,...} Pass arguments to the program
          -li             Show loaded imports with type and alias
          -liFull         Like -li, but also lists every exported symbol per import
          -nsc            Skip the static check
          -no-warn        Suppress all warnings and hints
          -crash          On crash: print Mira call stack and memory dump
          -crashFull      On crash: same as -crash plus Java stack trace
          -watch          Watch entry file and all imported modules for changes, restart on save
          -ast            Print AST
          -test           Run test() calls and print a pass/fail summary; exit 1 if any test fails
          -debug          Launch interactive debugger
          -o <dir>        Output directory for compiled bytecode
          -compile        Compile to JVM bytecode and write .class files
          -package        (used with -compile) Bundle compiled classes and the Mira runtime into a standalone fat JAR
          --slim          (with mode=package) Bundle only the classes reachable from the program, excluding the compiler/IDE toolchain
          --full          (with mode=package) Bundle the entire Mira distribution (previous default behavior)
          -compile-run    Compile to JVM bytecode and run in memory
          --lsp           Start the Mira Language Server (LSP) over stdin/stdout
          --fmt <file>    Format a .mira file in-place
        """;

    public static String getHelp() {
        return HELPBLOCK;
    }
}
