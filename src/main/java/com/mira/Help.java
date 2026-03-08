package com.mira;

public class Help {

    private static final String helpBlock = """
        Usage [src filepath] ...
        Flags:
        -t Dump tokens
        -e Exit before interpreter
        -c Load file from classpath
            """;

    public static String getHelp() {
        return helpBlock;
    }
}
