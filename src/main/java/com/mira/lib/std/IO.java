package com.mira.lib.std;

import java.io.IOException;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.utils.FileLoader;

public class IO implements Lib {

    @Override
    public void loadLib(Environment environment) {
        //functions
        environment.define("readFile",
                new NativeFunction(1, args -> {
                    try {
                        return FileLoader.readFileFromPath(String.valueOf(args.get(0)));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }));
    }
}
