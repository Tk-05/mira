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
            """;

    public static String getHelp() {
        return helpBlock;
    }
}
