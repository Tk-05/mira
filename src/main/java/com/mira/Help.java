package com.mira;

public class Help {

    private static final String HELPBLOCK = """
        Usage [src filepath] ...
        Flags:
        -t Dump tokens
        -b Dump bytecode
        -e Exit before interpreter
        -c Load file from classpath
        -m For main function entry point
        -args {arg0, arg1...}
        -crash On crash: print Mira call stack, Java stack trace, and interpreter memory dump
        -watch Watch for reload of input file
        -lint Lint the code before execution or compilation
        -ast Print ast
        -o Specify Output directory in case of compilation
        -compile Compile and write to java bytecode
        -compile-run Compile to bytecode but run in memory
        """;

    public static String getHelp() {
        return HELPBLOCK;
    }
}
