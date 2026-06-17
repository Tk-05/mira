package com.mira.lib.std;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.NullValue;

public class Log implements Lib {

    private static final int LEVEL_DEBUG = 0;
    private static final int LEVEL_INFO = 1;
    private static final int LEVEL_WARN = 2;
    private static final int LEVEL_ERROR = 3;

    private int currentLevel = LEVEL_DEBUG;
    private PrintWriter fileWriter = null;

    private void emit(int level, String prefix, String message, boolean stderr) {
        if (level < currentLevel) {
            return;
        }
        String line = prefix + " " + message;
        if (fileWriter != null) {
            fileWriter.println(line);
            fileWriter.flush();
        } else {
            PrintStream out = stderr ? System.err : System.out;
            out.println(line);
        }
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("debug", new NativeFunction(1, args -> {
            emit(LEVEL_DEBUG, "[DEBUG]", String.valueOf(args.get(0)), false);
            return NullValue.INSTANCE;
        }));

        environment.define("info", new NativeFunction(1, args -> {
            emit(LEVEL_INFO, "[INFO]", String.valueOf(args.get(0)), false);
            return NullValue.INSTANCE;
        }));

        environment.define("warn", new NativeFunction(1, args -> {
            emit(LEVEL_WARN, "[WARN]", String.valueOf(args.get(0)), true);
            return NullValue.INSTANCE;
        }));

        environment.define("error", new NativeFunction(1, args -> {
            emit(LEVEL_ERROR, "[ERROR]", String.valueOf(args.get(0)), true);
            return NullValue.INSTANCE;
        }));

        environment.define("setLevel", new NativeFunction(1, args -> {
            currentLevel = switch (String.valueOf(args.get(0)).toLowerCase()) {
                case "debug" ->
                    LEVEL_DEBUG;
                case "info" ->
                    LEVEL_INFO;
                case "warn" ->
                    LEVEL_WARN;
                case "error" ->
                    LEVEL_ERROR;
                default ->
                    throw new RuntimeException("log.setLevel: unknown level '" + args.get(0) + "'");
            };
            return NullValue.INSTANCE;
        }));

        environment.define("toFile", new NativeFunction(1, args -> {
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                }
                fileWriter = new PrintWriter(new FileWriter(Path.of(String.valueOf(args.get(0))).toFile(), true));
                return NullValue.INSTANCE;
            } catch (IOException e) {
                throw new RuntimeException("log.toFile failed: " + e.getMessage());
            }
        }));
    }
}
