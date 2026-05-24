package com.mira.lib.std;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
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

        environment.define("fileExists",
                new NativeFunction(1, args -> Files.exists(Path.of(String.valueOf(args.get(0))))));

        environment.define("appendFile",
                new NativeFunction(2, args -> {
                    try {
                        Path path = Path.of(String.valueOf(args.get(0)));
                        if (path.getParent() != null) {
                            Files.createDirectories(path.getParent());
                        }
                        Files.writeString(path, String.valueOf(args.get(1)),
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        return null;
                    } catch (IOException e) {
                        throw new RuntimeException("appendFile failed: " + e.getMessage());
                    }
                }));

        environment.define("listDir",
                new NativeFunction(1, args -> {
                    try {
                        Path dir = Path.of(String.valueOf(args.get(0)));
                        List<Expression> members = new ArrayList<>();
                        Files.list(dir).map(p -> p.getFileName().toString()).forEach(name
                                -> members.add(new DumbExpression(new Token(TokenType.EXPRESSION, name, 0, 0))));
                        return new ListExpression(members);
                    } catch (IOException e) {
                        throw new RuntimeException("listDir failed: " + e.getMessage());
                    }
                }));

        environment.define("mkdir",
                new NativeFunction(1, args -> {
                    try {
                        Files.createDirectories(Path.of(String.valueOf(args.get(0))));
                        return null;
                    } catch (IOException e) {
                        throw new RuntimeException("mkdir failed: " + e.getMessage());
                    }
                }));

        environment.define("deleteFile",
                new NativeFunction(1, args -> {
                    try {
                        Files.deleteIfExists(Path.of(String.valueOf(args.get(0))));
                        return null;
                    } catch (IOException e) {
                        throw new RuntimeException("deleteFile failed: " + e.getMessage());
                    }
                }));
    }
}
