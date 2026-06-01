package com.mira.build;

import java.util.Set;

public class BuildDispatcher {

    private static final Set<String> SUBCOMMANDS = Set.of("init", "build", "run", "test", "clean");

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
                default ->
                    throw new BuildException("Unknown command: " + cmd);
            }
        } catch (BuildException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }
}
