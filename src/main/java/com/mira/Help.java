package com.mira;

public class Help {

    private static final String HELPBLOCK = """
        Project commands (requires mira.toml):
          mira init [--name <n>]                   Create a new project in the current directory
          mira build [--mode interpret|compile|package] [--watch]  Build the project
          mira run [--mode interpret|compile] [-- <args>]  Run the project (default: mode from mira.toml)
          mira test                                Run all test files matching test.pattern
          mira clean                               Delete the output directory
          mira release                             Full pipeline: build → post-build → test
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
          -crash          On crash: print Mira call stack and memory dump
          -crashFull      On crash: same as -crash plus Java stack trace
          -watch          Watch entry file and all imported modules for changes, restart on save
          -ast            Print AST
          -test           Run test() calls and print a pass/fail summary; exit 1 if any test fails
          -debug          Launch interactive debugger
          -o <dir>        Output directory for compiled bytecode
          -compile        Compile to JVM bytecode and write .class files
          -package        (used with -compile) Bundle compiled classes and the Mira runtime into a standalone fat JAR
          -compile-run    Compile to JVM bytecode and run in memory
          --lsp           Start the Mira Language Server (LSP) over stdin/stdout
        """;

    public static String getHelp() {
        return HELPBLOCK;
    }
}
