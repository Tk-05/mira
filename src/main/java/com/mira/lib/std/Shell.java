package com.mira.lib.std;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Shell implements Lib {

    @Override
    public void loadLib(Environment environment) {

        environment.define("execute", new NativeFunction(1, args -> {
            String command = String.valueOf(args.get(0));
            try {
                ProcessBuilder pb = new ProcessBuilder();

                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    pb.command("cmd.exe", "/c", command);
                } else {
                    pb.command("sh", "-c", command);
                }

                pb.redirectErrorStream(true);
                java.lang.Process process = pb.start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                process.waitFor();
                return output.toString().stripTrailing();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("exec failed: " + e.getMessage());
            }
        }));

        environment.define("executeCode", new NativeFunction(1, args -> {
            String command = String.valueOf(args.get(0));
            try {
                ProcessBuilder pb = new ProcessBuilder();

                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    pb.command("cmd.exe", "/c", command);
                } else {
                    pb.command("sh", "-c", command);
                }

                pb.redirectErrorStream(true);
                java.lang.Process process = pb.start();
                process.waitFor();
                return (double) process.exitValue();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("execCode failed: " + e.getMessage());
            }
        }));

        environment.define("getenv", new NativeFunction(1, args -> {
            String name = String.valueOf(args.get(0));
            String value = System.getenv(name);
            return value != null ? value : "";
        }));

        environment.define("hasenv", new NativeFunction(1, args -> {
            String name = String.valueOf(args.get(0));
            return System.getenv(name) != null;
        }));

        environment.define("osName", new NativeFunction(0, args -> {
            return System.getProperty("os.name");
        }));

        environment.define("isWindows", new NativeFunction(0, args -> {
            return System.getProperty("os.name").toLowerCase().contains("win");
        }));

        environment.define("isLinux", new NativeFunction(0, args -> {
            return System.getProperty("os.name").toLowerCase().contains("linux");
        }));

        environment.define("isMac", new NativeFunction(0, args -> {
            return System.getProperty("os.name").toLowerCase().contains("mac");
        }));

        environment.define("cwd", new NativeFunction(0, args -> {
            return System.getProperty("user.dir");
        }));

        environment.define("username", new NativeFunction(0, args -> {
            return System.getProperty("user.name");
        }));

        environment.define("homedir", new NativeFunction(0, args -> {
            return System.getProperty("user.home");
        }));
    }
}
