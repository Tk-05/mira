package com.mira.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.build.BuildDispatcher;
import com.mira.debugger.Debugger;
import com.mira.format.AstFormatter;
import com.mira.lsp.Launcher;
import com.mira.runtime.FileRunner;
import com.mira.runtime.HotReloader;
import com.mira.utils.FileLoader;

public class CliDispatcher {

    public static void dispatch(String[] args) {
        if (args[0].equals("--lsp")) {
            try {
                Launcher.launch();
            } catch (Exception e) {
                System.err.println("LSP server error: " + e.getMessage());
            }
            return;
        }

        if (args[0].equals("--dap")) {
            try {
                com.mira.dap.DapLauncher.launch();
            } catch (Exception e) {
                System.err.println("DAP server error: " + e.getMessage());
            }
            return;
        }

        if (args[0].equals("--fmt")) {
            if (args.length < 2) {
                System.err.println("Usage: mira --fmt <file.mira>");
                System.exit(1);
                return;
            }
            try {
                Path fmtPath = Paths.get(args[1]).toAbsolutePath().normalize();
                String fmtSource = FileLoader.readFileFromPath(fmtPath.toString());
                String fmtResult = AstFormatter.format(fmtSource);
                Files.writeString(fmtPath, fmtResult);
                System.out.println("Formatted: " + fmtPath);
            } catch (Exception e) {
                System.err.println("Format error: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        if (BuildDispatcher.isSubcommand(args[0])) {
            BuildDispatcher.dispatch(args);
            return;
        }

        Flags.parse(args);

        if (Flags.debug) {
            Debugger.run();
            return;
        }

        if (Flags.hotReload) {
            new HotReloader(Flags.inputPath.get()).run();
            return;
        }

        FileRunner.runFile(new AtomicBoolean(false));
    }
}
