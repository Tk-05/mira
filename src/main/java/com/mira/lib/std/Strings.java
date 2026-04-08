package com.mira.lib.std;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Strings implements Lib {

    @Override
    public void loadLib(Environment environment) {
        environment.define("charAt",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    int index = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    return str.charAt(index);
                }));

        environment.define("indexOf",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    char ch = String.valueOf(args.get(1)).charAt(0);
                    return str.indexOf(ch);
                }));

        environment.define("trim",
                new NativeFunction(1, args -> {
                    return String.valueOf(args.get(0)).trim();
                }));

        environment.define("split",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    String delimiter = String.valueOf(args.get(1));
                    return str.split(delimiter);
                }));

        environment.define("substr",
                new NativeFunction(3, args -> {
                    String str = String.valueOf(args.get(0));
                    int start = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    int end = (int) Double.parseDouble(String.valueOf(args.get(2)));
                    return str.substring(start, end);
                }));

        environment.define("strEqual",
                new NativeFunction(2, args -> {
                    String string1 = String.valueOf(args.get(0));
                    String string2 = String.valueOf(args.get(1));
                    return string1.equals(string2);
                }));

        environment.define("replace",
                new NativeFunction(3, args -> {
                    String string = String.valueOf(args.get(0));
                    char ch1 = String.valueOf(args.get(1)).charAt(0);
                    char ch2 = String.valueOf(args.get(2)).charAt(0);
                    return string.replace(ch1, ch2);
                }));
    }
}
