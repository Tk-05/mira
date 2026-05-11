package com.mira;

public class Help {

    private static final String HELPBLOCK = """
        Usage [src filepath] ...
        Flags:
        -t              Dump tokens
        -b              Dump bytecode
        -e              Exit before interpreter
        -m              Use main() as entry point
        -args {a,b,...} Pass arguments to the program
        -crash          On crash: print Mira call stack and memory dump
        -crashFull      On crash: same as -crash plus Java stack trace
        -watch          Watch entry file and all imported modules for changes, restart on save
        -lint           Lint the code before execution or compilation
        -ast            Print AST
        -test           Run test() calls and print a pass/fail summary; exit 1 if any test fails
        -debug          Launch interactive debugger
        -o <dir>        Output directory for compiled bytecode
        -compile        Compile to JVM bytecode and write .class files
        -compile-run    Compile to JVM bytecode and run in memory
        --lsp           Start the Mira Language Server (LSP) over stdin/stdout
        """;

    public static String getHelp() {
        return HELPBLOCK;
    }
}
