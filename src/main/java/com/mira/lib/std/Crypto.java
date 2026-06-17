package com.mira.lib.std;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Crypto implements Lib {

    private static String digest(String algorithm, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Crypto: " + algorithm + " not available");
        }
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("md5", new NativeFunction(1, args
                -> digest("MD5", String.valueOf(args.get(0)))));

        environment.define("sha1", new NativeFunction(1, args
                -> digest("SHA-1", String.valueOf(args.get(0)))));

        environment.define("sha256", new NativeFunction(1, args
                -> digest("SHA-256", String.valueOf(args.get(0)))));

        environment.define("sha512", new NativeFunction(1, args
                -> digest("SHA-512", String.valueOf(args.get(0)))));

        environment.define("hmacSha256", new NativeFunction(2, args -> {
            try {
                String key = String.valueOf(args.get(0));
                String message = String.valueOf(args.get(1));
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] result = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(result);
            } catch (Exception e) {
                throw new RuntimeException("hmacSha256 failed: " + e.getMessage());
            }
        }));

        environment.define("uuid", new NativeFunction(0, args
                -> UUID.randomUUID().toString()));

        environment.define("uuidNoDashes", new NativeFunction(0, args
                -> UUID.randomUUID().toString().replace("-", "")));
    }
}
