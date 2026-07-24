package com.mira.build;

import java.util.Set;

import com.mira.error.DiagnosticFormatter;

public class BuildDispatcher {

    private static final Set<String> SUBCOMMANDS =
            Set.of("init", "build", "run", "test", "clean", "task", "release", "install", "deps");

    public static boolean isSubcommand(String arg) {
        return SUBCOMMANDS.contains(arg);
    }

    public static void dispatch(String[] args) {
        String cmd = args[0];
        try {
            switch (cmd) {
                case "init" ->
                    Commands.init(args);
                case "build" ->
                    Commands.build(args);
                case "run" ->
                    Commands.run(args);
                case "test" ->
                    Commands.test(args);
                case "clean" ->
                    Commands.clean(args);
                case "task" ->
                    Commands.task(args);
                case "release" ->
                    Commands.release(args);
                case "install" ->
                    Commands.install(args);
                case "deps" ->
                    Commands.deps(args);
                default ->
                    throw new BuildException("Unknown command: " + cmd);
            }
        } catch (BuildException e) {
            System.err.println(DiagnosticFormatter.formatError(e.getMessage()));
            System.exit(1);
        }
    }
}
