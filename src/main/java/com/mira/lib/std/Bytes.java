package com.mira.lib.std;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.BytesValue;
import com.mira.runtime.values.NullValue;

public class Bytes implements Lib {

    private static BytesValue toBytes(Object arg) {
        if (arg instanceof BytesValue bv) {
            return bv;
        }
        throw new RuntimeException("Expected bytes, got: " + arg.getClass().getSimpleName());
    }

    private static int toIndex(Object arg) {
        return (int) Double.parseDouble(String.valueOf(arg));
    }

    private static Expression wrapDouble(double d) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(d), 0, 0));
    }

    private static List<Object> toNumberList(Object arg) {
        List<Expression> members = switch (arg) {
            case ArrayExpression a ->
                a.getMembers();
            case ListExpression l ->
                l.getMembers();
            default ->
                throw new RuntimeException("Expected list, got: " + arg.getClass().getSimpleName());
        };
        List<Object> out = new ArrayList<>(members.size());
        for (Expression e : members) {
            out.add(Double.parseDouble(String.valueOf(
                    e instanceof DumbExpression d ? d.getValue() : e)));
        }
        return out;
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("newBytes", new NativeFunction(1, args -> {
            int size = toIndex(args.get(0));
            return new BytesValue(size);
        }));

        environment.define("fromString", new NativeFunction(1, args -> {
            String s = String.valueOf(args.get(0));
            return new BytesValue(s.getBytes(StandardCharsets.UTF_8));
        }));

        environment.define("fromList", new NativeFunction(1, args -> {
            List<Object> nums = toNumberList(args.get(0));
            byte[] data = new byte[nums.size()];
            for (int i = 0; i < nums.size(); i++) {
                data[i] = (byte) ((Double) nums.get(i)).intValue();
            }
            return new BytesValue(data);
        }));

        environment.define("fromHex", new NativeFunction(1, args -> {
            String hex = String.valueOf(args.get(0));
            return new BytesValue(HexFormat.of().parseHex(hex));
        }));

        environment.define("fromBase64", new NativeFunction(1, args -> {
            String encoded = String.valueOf(args.get(0));
            return new BytesValue(Base64.getDecoder().decode(encoded));
        }));

        environment.define("size", new NativeFunction(1, args -> {
            return (double) toBytes(args.get(0)).size();
        }));

        environment.define("get", new NativeFunction(2, args -> {
            byte[] data = toBytes(args.get(0)).getData();
            int index = toIndex(args.get(1));
            return (double) (data[index] & 0xFF);
        }));

        environment.define("set", new NativeFunction(3, args -> {
            byte[] data = toBytes(args.get(0)).getData().clone();
            int index = toIndex(args.get(1));
            data[index] = (byte) toIndex(args.get(2));
            return new BytesValue(data);
        }));

        environment.define("slice", new NativeFunction(3, args -> {
            byte[] data = toBytes(args.get(0)).getData();
            int start = toIndex(args.get(1));
            int end = toIndex(args.get(2));
            byte[] slice = new byte[end - start];
            System.arraycopy(data, start, slice, 0, end - start);
            return new BytesValue(slice);
        }));

        environment.define("concat", new NativeFunction(2, args -> {
            byte[] a = toBytes(args.get(0)).getData();
            byte[] b = toBytes(args.get(1)).getData();
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return new BytesValue(result);
        }));

        environment.define("copy", new NativeFunction(1, args -> {
            return new BytesValue(toBytes(args.get(0)).getData());
        }));

        environment.define("fill", new NativeFunction(2, args -> {
            byte[] data = toBytes(args.get(0)).getData().clone();
            byte val = (byte) toIndex(args.get(1));
            for (int i = 0; i < data.length; i++) {
                data[i] = val;
            }
            return new BytesValue(data);
        }));

        environment.define("toString", new NativeFunction(1, args -> {
            return new String(toBytes(args.get(0)).getData(), StandardCharsets.UTF_8);
        }));

        environment.define("toList", new NativeFunction(1, args -> {
            byte[] data = toBytes(args.get(0)).getData();
            List<Expression> members = new ArrayList<>(data.length);
            for (byte b : data) {
                members.add(wrapDouble((double) (b & 0xFF)));
            }
            return new ListExpression(members);
        }));

        environment.define("toHex", new NativeFunction(1, args -> {
            return HexFormat.of().formatHex(toBytes(args.get(0)).getData());
        }));

        environment.define("toBase64", new NativeFunction(1, args -> {
            return Base64.getEncoder().encodeToString(toBytes(args.get(0)).getData());
        }));

        environment.define("readFile", new NativeFunction(1, args -> {
            try {
                byte[] data = Files.readAllBytes(Paths.get(String.valueOf(args.get(0))));
                return new BytesValue(data);
            } catch (IOException e) {
                throw new RuntimeException("bytes.readFile failed: " + e.getMessage());
            }
        }));

        environment.define("writeFile", new NativeFunction(2, args -> {
            try {
                Files.write(Paths.get(String.valueOf(args.get(0))),
                        toBytes(args.get(1)).getData());
                return NullValue.INSTANCE;
            } catch (IOException e) {
                throw new RuntimeException("bytes.writeFile failed: " + e.getMessage());
            }
        }));
    }
}
