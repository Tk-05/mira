package com.mira.lib.std;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.utils.FileLoader;

public class IO implements Lib {

    @Override
    public void loadLib(Environment environment) {
        environment.define("readFile",
                new NativeFunction(1, args -> {
                    try {
                        return FileLoader.readFileFromPath(String.valueOf(args.get(0)));
                    } catch (IOException e) {
                        throw new RuntimeException("readFile failed: " + e.getMessage());
                    }
                }));

        environment.define("writeFile",
                new NativeFunction(2, args -> {
                    try {
                        Path path = Path.of(String.valueOf(args.get(0)));
                        if (path.getParent() != null) {
                            Files.createDirectories(path.getParent());
                        }
                        Files.writeString(path, String.valueOf(args.get(1)), StandardCharsets.UTF_8);
                        return null;
                    } catch (IOException e) {
                        throw new RuntimeException("writeFile failed: " + e.getMessage());
                    }
                }));
    }
}
