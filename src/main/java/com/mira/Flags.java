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
    public static boolean hotReload = false;
    public static boolean crashDump = false;
    public static boolean printAsts = false;
    public static String[] args = null;
    public static String fileName = null;
    public static String[] sourceLines = null;

    public static boolean compile = false;
    public static boolean compileAndRun = false;
    public static boolean dumpByteCode = false;
    public static Path outputDir = null;
}
