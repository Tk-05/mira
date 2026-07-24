package com.mira.build;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.runtime.FileRunner;

public class TaskRunner {

    public static void listTasks(ProjectConfig config) {
        if (config.tasks().isEmpty()) {
            System.out.println(DiagnosticFormatter.formatInfo("no tasks defined in mira.toml"));
            return;
        }
        System.out.println(DiagnosticFormatter.formatInfo("tasks for " + config.name() + ":"));
        int maxLen = config.tasks().keySet().stream().mapToInt(String::length).max().orElse(0);
        for (TaskConfig task : config.tasks().values()) {
            String desc = task.description() != null ? task.description() : "";
            String type = task.isCmd() ? "[cmd]" : "[script]";
            System.out.printf("  %-" + maxLen + "s  %s  %s%n", task.name(), type, desc);
        }
    }

    public static void runTask(BuildContext ctx, String name) {
        ProjectConfig config = ctx.config();
        TaskConfig task = config.tasks().get(name);
        if (task == null) {
            throw new BuildException("task '" + name + "' is not defined in mira.toml\n"
                    + "Run 'mira task' to see all available tasks.");
        }

        System.out.println(DiagnosticFormatter.formatInfo("running task '" + name + "'..."));
        long start = System.currentTimeMillis();

        try {
            if (task.isCmd()) {
                runCmd(task.cmd());
            } else {
                runScript(ctx, task.script());
            }
            System.out.println(DiagnosticFormatter.formatPass(
                    "task '" + name + "' finished in " + (System.currentTimeMillis() - start) + " ms"));
        } catch (BuildException e) {
            System.out.println(DiagnosticFormatter.formatFail(
                    "task '" + name + "' failed after " + (System.currentTimeMillis() - start) + " ms"));
            throw e;
        }
    }

    private static void runCmd(String cmd) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = isWindows
                ? new ProcessBuilder("cmd.exe", "/c", cmd)
                : new ProcessBuilder("sh", "-c", cmd);
        pb.inheritIO();
        try {
            int exitCode = pb.start().waitFor();
            if (exitCode != 0) {
                throw new BuildException("command exited with code " + exitCode + ": " + cmd);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BuildException("failed to execute command: " + cmd + " — " + e.getMessage());
        }
    }

    private static void runScript(BuildContext ctx, String scriptPath) {
        ctx.applyFlags(ProjectConfig.BuildMode.INTERPRET);
        Flags.inputPath.set(Paths.get(scriptPath).isAbsolute()
                ? Paths.get(scriptPath).normalize()
                : ctx.config().projectRoot().resolve(scriptPath).normalize());
        Flags.mainFunction = false;
        Flags.testMode = false;
        FileRunner.runFile(new AtomicBoolean(false));
    }
}
