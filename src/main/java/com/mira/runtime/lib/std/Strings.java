package com.mira.runtime.lib.std;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.lib.Lib;

public class Strings implements Lib {

    @Override
    public void loadLib(Environment environment) {
        //functions
        environment.define("charAt",
                new NativeFunction(2, args -> {
                    int index = (int) Double.parseDouble(String.valueOf(args.get(0)));
                    return String.valueOf(args.get(1)).charAt(index);
                }));

        environment.define("indexOf",
                new NativeFunction(2, args -> {
                    char ch = String.valueOf(args.get(0)).charAt(0);
                    return String.valueOf(args.get(1)).indexOf(ch);
                }));

        environment.define("trim",
                new NativeFunction(1, args -> {
                    return String.valueOf(args.get(0)).trim();
                }));

        environment.define("split",
                new NativeFunction(2, args -> {
                    return String.valueOf(args.get(1)).split(String.valueOf(args.get(0)));
                }));

        environment.define("substring",
                new NativeFunction(3, args -> {
                    int start = (int) Double.parseDouble(String.valueOf(args.get(0)));
                    int end = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    return String.valueOf(args.get(2)).substring(start, end);
                }));
    }
}
