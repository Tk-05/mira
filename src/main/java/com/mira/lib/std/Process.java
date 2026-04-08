package com.mira.lib.std;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Process implements Lib {

    private static final Map<Double, java.lang.Process> processes = new ConcurrentHashMap<>();
    private static double nextId = 1;

    private static DumbExpression wrap(String val) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, val, 0, 0));
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("processStart", new NativeFunction(1, args -> {
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
                double id = nextId++;
                processes.put(id, process);
                return id;
            } catch (IOException e) {
                throw new RuntimeException("processStart failed: " + e.getMessage());
            }
        }));

        environment.define("processAlive", new NativeFunction(1, args -> {
            double id = Double.parseDouble(String.valueOf(args.get(0)));
            java.lang.Process p = processes.get(id);
            if (p == null) {
                throw new RuntimeException("No process with id: " + id);
            }
            return p.isAlive();
        }));

        environment.define("processWait", new NativeFunction(1, args -> {
            double id = Double.parseDouble(String.valueOf(args.get(0)));
            java.lang.Process p = processes.get(id);
            if (p == null) {
                throw new RuntimeException("No process with id: " + id);
            }
            try {
                return (double) p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("processWait interrupted");
            }
        }));

        environment.define("processKill", new NativeFunction(1, args -> {
            double id = Double.parseDouble(String.valueOf(args.get(0)));
            java.lang.Process p = processes.get(id);
            if (p == null) {
                throw new RuntimeException("No process with id: " + id);
            }
            p.destroy();
            processes.remove(id);
            return null;
        }));

        environment.define("processOutput", new NativeFunction(1, args -> {
            double id = Double.parseDouble(String.valueOf(args.get(0)));
            java.lang.Process p = processes.get(id);
            if (p == null) {
                throw new RuntimeException("No process with id: " + id);
            }
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while (reader.ready() && (line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                return sb.toString().stripTrailing();
            } catch (IOException e) {
                throw new RuntimeException("processOutput failed: " + e.getMessage());
            }
        }));

        environment.define("processExitCode", new NativeFunction(1, args -> {
            double id = Double.parseDouble(String.valueOf(args.get(0)));
            java.lang.Process p = processes.get(id);
            if (p == null) {
                throw new RuntimeException("No process with id: " + id);
            }
            if (p.isAlive()) {
                throw new RuntimeException("Process is still running");
            }
            return (double) p.exitValue();
        }));

        environment.define("pid", new NativeFunction(0, args -> {
            return (double) ProcessHandle.current().pid();
        }));

        environment.define("listProcesses", new NativeFunction(0, args -> {
            List<com.mira.parser.nodes.expression.Expression> pids = new ArrayList<>();
            ProcessHandle.allProcesses().forEach(ph -> pids.add(wrap(String.valueOf(ph.pid()))));
            return new ListExpression(pids);
        }));

        environment.define("processInfo", new NativeFunction(1, args -> {
            long pid = (long) Double.parseDouble(String.valueOf(args.get(0)));
            return ProcessHandle.of(pid)
                    .flatMap(ph -> ph.info().command())
                    .orElse("unknown");
        }));

        environment.define("sleep", new NativeFunction(1, args -> {
            long ms = (long) Double.parseDouble(String.valueOf(args.get(0)));
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
    }
}
