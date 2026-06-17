package com.mira.error;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import com.mira.Flags;
import com.mira.error.resolver.StaticCheckError;
import com.mira.error.runtime.RuntimeError;
import com.mira.warning.Warning;
import com.mira.warning.WarningLevel;

public final class DiagnosticFormatter {

    private static final String RED = "\u001B[31m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String DIM = "\u001B[2m";
    private static final String RESET = "\u001B[0m";

    private static final String GREEN = "[32m";

    private DiagnosticFormatter() {
    }

    public static String format(Throwable t) {
        if (t instanceof MiraError error) {
            return formatMiraError(error);
        }

        String msg = t.getMessage();
        String label = msg != null && !msg.isBlank() ? msg : t.getClass().getName();
        return RED + BOLD + "[internal error]" + RESET + ": " + label;
    }

    public static String formatError(String message) {
        return RED + BOLD + "[error]" + RESET + ": " + message;
    }

    public static String formatInfo(String message) {
        return CYAN + BOLD + "[info]" + RESET + ": " + message;
    }

    public static String formatPass(String message) {
        return GREEN + BOLD + "[pass]" + RESET + " " + message;
    }

    public static String formatFail(String message) {
        return RED + BOLD + "[fail]" + RESET + " " + message;
    }

    public static String formatFileError(Path path, IOException e) {
        boolean notFound = e instanceof NoSuchFileException || !Files.exists(path);
        if (notFound) {
            return RED + BOLD + "[error]" + RESET + ": file not found: " + path;
        }
        return RED + BOLD + "[error]" + RESET + ": cannot read file: " + path + " — " + e.getMessage();
    }

    public static String formatWarning(Warning warning) {
        boolean isHint = warning.level() == WarningLevel.HINT;
        String color = isHint ? CYAN : "[33m";
        String tag = color + BOLD + "[" + warning.level().name().toLowerCase() + "]" + RESET;

        StringBuilder sb = new StringBuilder();
        sb.append(tag).append(": ").append(BOLD).append(warning.message()).append(RESET).append("\n");

        int line = warning.line();
        int col = warning.column();
        String fileName = Flags.fileName != null ? Flags.fileName : "<input>";

        if (line > 0) {
            sb.append(CYAN).append("  --> ").append(RESET)
                    .append(fileName).append(":").append(line).append(":").append(col).append("\n");

            String[] sourceLines = Flags.sourceLines;
            if (sourceLines != null && line <= sourceLines.length) {
                String srcLine = sourceLines[line - 1];

                if (line >= 2) {
                    String prevLine = sourceLines[line - 2];
                    String prevLabel = String.format("%4d", line - 1);
                    sb.append(DIM).append(prevLabel).append(" |").append(RESET)
                            .append(" ").append(prevLine).append("\n");
                } else {
                    sb.append(DIM).append("     |").append(RESET).append("\n");
                }

                String lineLabel = String.format("%4d", line);
                sb.append(lineLabel).append(" | ").append(srcLine).append("\n");

                sb.append(DIM).append("     |").append(RESET).append(" ");
                int caretPos = Math.max(0, col - 1);
                for (int i = 0; i < caretPos; i++) {
                    sb.append(srcLine.length() > i && srcLine.charAt(i) == '\t' ? '\t' : ' ');
                }
                sb.append(color).append(BOLD);
                sb.append("^".repeat(Math.max(1, warning.span())));
                sb.append(RESET).append("\n");
                sb.append(DIM).append("     |").append(RESET).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private static String formatMiraError(MiraError error) {
        StringBuilder sb = new StringBuilder();

        String phaseLabel = error instanceof StaticCheckError ? "static error"
                : error instanceof RuntimeError ? "runtime error"
                        : "error";

        sb.append(RED).append(BOLD).append("[").append(phaseLabel).append("]");
        if (error.getErrorCode() != null) {
            sb.append("[").append(error.getErrorCode()).append("]");
        }
        sb.append(RESET).append(": ").append(BOLD).append(error.getMessage()).append(RESET).append("\n");

        int line = error.getLine();
        int col = error.getColumn();

        String sourceFile = error.getSourceFile();
        String fileName = sourceFile != null ? sourceFile
                : (Flags.fileName != null ? Flags.fileName : "<input>");

        if (line > 0) {
            sb.append(CYAN).append("  --> ").append(RESET)
                    .append(fileName).append(":").append(line).append(":").append(col).append("\n");

            String[] sourceLines = Flags.sourceLines;
            if (sourceLines != null && line <= sourceLines.length && sourceFile == null) {
                String srcLine = sourceLines[line - 1];

                if (line >= 2) {
                    String prevLine = sourceLines[line - 2];
                    String prevLabel = String.format("%4d", line - 1);
                    sb.append(DIM).append(prevLabel).append(" |").append(RESET)
                            .append(" ").append(prevLine).append("\n");
                } else {
                    sb.append(DIM).append("     |").append(RESET).append("\n");
                }

                String lineLabel = String.format("%4d", line);
                sb.append(lineLabel).append(" | ").append(srcLine).append("\n");

                sb.append(DIM).append("     |").append(RESET).append(" ");
                int caretPos = Math.max(0, col - 1);
                for (int i = 0; i < caretPos; i++) {
                    sb.append(srcLine.length() > i && srcLine.charAt(i) == '\t' ? '\t' : ' ');
                }
                int caretLen = error.getSpan();
                sb.append(RED).append(BOLD);
                sb.append("^".repeat(caretLen));
                sb.append(RESET).append("\n");
                sb.append(DIM).append("     |").append(RESET).append("\n");
            }
        } else {
            sb.append(CYAN).append("  --> ").append(RESET).append(fileName).append("\n");
        }

        java.util.List<String> chain = error.getImportChain();
        if (!chain.isEmpty()) {
            sb.append(CYAN).append("     = ").append(RESET)
                    .append("imported via: ").append(String.join(" → ", chain)).append("\n");
        }

        if (error.getHint() != null) {
            sb.append(CYAN).append("     = ").append(RESET)
                    .append("hint: ").append(error.getHint()).append("\n");
        }

        return sb.toString().stripTrailing();
    }
}
