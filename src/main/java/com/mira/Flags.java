package com.mira;

import java.nio.file.Path;

public class Flags {

    public static final ThreadLocal<Path> inputPath = new ThreadLocal<>();
    public static boolean dumpTokens = false;
    public static boolean exitBeforeInterpreter = false;
    public static boolean mainFunction = false;
    public static boolean libInfo = false;
    public static boolean debug = false;
    public static boolean lint = false;
    public static String[] args = null;
    public static String fileName = null;
    public static String[] sourceLines = null;
}
