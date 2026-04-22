package com.mira;

public class Help {

    private static final String helpBlock = """
        Usage [src filepath] ...
        Flags:
        -t Dump tokens
        -e Exit before interpreter
        -c Load file from classpath
        -m For main function entry point
        -args {arg0, arg1...}
        -crash On crash: print Mira call stack, Java stack trace, and interpreter memory dump
            """;

    public static String getHelp() {
        return helpBlock;
    }
}
