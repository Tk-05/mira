package com.mira.lib.std;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Term implements Lib {

    private static String ansi(int code, String text) {
        return "\033[" + code + "m" + text + "\033[0m";
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("red", new NativeFunction(1, "text", args -> ansi(31, String.valueOf(args.get(0)))));
        environment.define("green", new NativeFunction(1, "text", args -> ansi(32, String.valueOf(args.get(0)))));
        environment.define("yellow", new NativeFunction(1, "text", args -> ansi(33, String.valueOf(args.get(0)))));
        environment.define("blue", new NativeFunction(1, "text", args -> ansi(34, String.valueOf(args.get(0)))));
        environment.define("magenta", new NativeFunction(1, "text", args -> ansi(35, String.valueOf(args.get(0)))));
        environment.define("cyan", new NativeFunction(1, "text", args -> ansi(36, String.valueOf(args.get(0)))));
        environment.define("white", new NativeFunction(1, "text", args -> ansi(37, String.valueOf(args.get(0)))));

        environment.define("bold", new NativeFunction(1, "text", args -> ansi(1, String.valueOf(args.get(0)))));
        environment.define("dim", new NativeFunction(1, "text", args -> ansi(2, String.valueOf(args.get(0)))));
        environment.define("italic", new NativeFunction(1, "text", args -> ansi(3, String.valueOf(args.get(0)))));
        environment.define("underline", new NativeFunction(1, "text", args -> ansi(4, String.valueOf(args.get(0)))));

        environment.define("stripAnsi", new NativeFunction(1, "text", args
                -> String.valueOf(args.get(0)).replaceAll("\033\\[[0-9;]*m", "")));

        environment.define("clear", new NativeFunction(0, args -> "\033[2J\033[H"));
    }
}
