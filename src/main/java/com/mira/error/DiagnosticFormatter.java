package com.mira.error;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import com.mira.cli.Flags;
import com.mira.error.resolver.StaticCheckError;
import com.mira.error.runtime.RuntimeError;
import com.mira.warning.Warning;
import com.mira.warning.WarningLevel;

public final class DiagnosticFormatter {

    private DiagnosticFormatter() {
    }

    private static boolean colorsEnabled() {
        return !Flags.noColor && System.getenv("NO_COLOR") == null;
    }

    private static String ansi(String code) {
        return colorsEnabled() ? code : "";
    }

    private static String red() {
        return ansi("[31m");
    }

    private static String bold() {
        return ansi("[1m");
    }

    private static String cyan() {
        return ansi("[36m");
    }

    private static String dim() {
        return ansi("[2m");
    }

    private static String reset() {
        return ansi("[0m");
    }

    private static String green() {
        return ansi("[32m");
    }

    public static String format(Throwable t) {
        if (t instanceof MiraError error) {
            return formatMiraError(error);
        }

        String msg = t.getMessage();
        String label = msg != null && !msg.isBlank() ? msg : t.getClass().getName();
        return red() + bold() + "[internal error]" + reset() + ": " + label;
    }

    public static String formatError(String message) {
        return red() + bold() + "[error]" + reset() + ": " + message;
    }

    public static String formatInfo(String message) {
        return cyan() + bold() + "[info]" + reset() + ": " + message;
    }

    public static String formatPass(String message) {
        return green() + bold() + "[pass]" + reset() + " " + message;
    }

    public static String formatFail(String message) {
        return red() + bold() + "[fail]" + reset() + " " + message;
    }

    public static String formatFileError(Path path, IOException e) {
        boolean notFound = e instanceof NoSuchFileException || !Files.exists(path);
        if (notFound) {
            return red() + bold() + "[error]" + reset() + ": file not found: " + path;
        }
        return red() + bold() + "[error]" + reset() + ": cannot read file: " + path + " — " + e.getMessage();
    }

    public static String formatWarning(Warning warning) {
        boolean isHint = warning.level() == WarningLevel.HINT;
        String warnColor = isHint ? cyan() : ansi("[33m");
        String tag = warnColor + bold() + "[" + warning.level().name().toLowerCase() + "]" + reset();

        StringBuilder sb = new StringBuilder();
        sb.append(tag).append(": ").append(bold()).append(warning.message()).append(reset()).append("\n");

        int line = warning.line();
        int col = warning.column();
        String fileName = Flags.fileName != null ? Flags.fileName : "<input>";

        if (line > 0) {
            sb.append(cyan()).append("  --> ").append(reset()).append(fileName).append(":").append(line).append(":")
                    .append(col).append("\n");

            String[] sourceLines = Flags.sourceLines;
            if (sourceLines != null && line <= sourceLines.length) {
                String srcLine = sourceLines[line - 1];

                if (line >= 2) {
                    String prevLine = sourceLines[line - 2];
                    String prevLabel = String.format("%4d", line - 1);
                    sb.append(dim()).append(prevLabel).append(" |").append(reset()).append(" ").append(prevLine)
                            .append("\n");
                } else {
                    sb.append(dim()).append("     |").append(reset()).append("\n");
                }

                String lineLabel = String.format("%4d", line);
                sb.append(lineLabel).append(" | ").append(srcLine).append("\n");

                sb.append(dim()).append("     |").append(reset()).append(" ");
                int caretPos = Math.max(0, col - 1);
                for (int i = 0; i < caretPos; i++) {
                    sb.append(srcLine.length() > i && srcLine.charAt(i) == '\t' ? '\t' : ' ');
                }
                sb.append(warnColor).append(bold());
                sb.append("^".repeat(Math.max(1, warning.span())));
                sb.append(reset()).append("\n");
                sb.append(dim()).append("     |").append(reset()).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private static String formatMiraError(MiraError error) {
        StringBuilder sb = new StringBuilder();

        String phaseLabel = error instanceof StaticCheckError
                ? "static error"
                : error instanceof RuntimeError ? "runtime error" : "error";

        sb.append(red()).append(bold()).append("[").append(phaseLabel).append("]");
        if (error.getErrorCode() != null) {
            sb.append("[").append(error.getErrorCode()).append("]");
        }
        sb.append(reset()).append(": ").append(bold()).append(error.getMessage()).append(reset()).append("\n");

        int line = error.getLine();
        int col = error.getColumn();

        String sourceFile = error.getSourceFile();
        String fileName = sourceFile != null ? sourceFile : (Flags.fileName != null ? Flags.fileName : "<input>");

        if (line > 0) {
            sb.append(cyan()).append("  --> ").append(reset()).append(fileName).append(":").append(line).append(":")
                    .append(col).append("\n");

            String[] sourceLines = Flags.sourceLines;
            if (sourceLines != null && line <= sourceLines.length && sourceFile == null) {
                String srcLine = sourceLines[line - 1];

                if (line >= 2) {
                    String prevLine = sourceLines[line - 2];
                    String prevLabel = String.format("%4d", line - 1);
                    sb.append(dim()).append(prevLabel).append(" |").append(reset()).append(" ").append(prevLine)
                            .append("\n");
                } else {
                    sb.append(dim()).append("     |").append(reset()).append("\n");
                }

                String lineLabel = String.format("%4d", line);
                sb.append(lineLabel).append(" | ").append(srcLine).append("\n");

                sb.append(dim()).append("     |").append(reset()).append(" ");
                int caretPos = Math.max(0, col - 1);
                for (int i = 0; i < caretPos; i++) {
                    sb.append(srcLine.length() > i && srcLine.charAt(i) == '\t' ? '\t' : ' ');
                }
                int caretLen = error.getSpan();
                sb.append(red()).append(bold());
                sb.append("^".repeat(caretLen));
                sb.append(reset()).append("\n");
                sb.append(dim()).append("     |").append(reset()).append("\n");
            }
        } else {
            sb.append(cyan()).append("  --> ").append(reset()).append(fileName).append("\n");
        }

        java.util.List<String> chain = error.getImportChain();
        if (!chain.isEmpty()) {
            sb.append(cyan()).append("     = ").append(reset()).append("imported via: ")
                    .append(String.join(" → ", chain)).append("\n");
        }

        if (error.getHint() != null) {
            sb.append(cyan()).append("     = ").append(reset()).append("hint: ").append(error.getHint()).append("\n");
        }

        return sb.toString().stripTrailing();
    }
}
