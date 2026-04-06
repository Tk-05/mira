package com.mira.error;

import com.mira.Flags;

public final class DiagnosticFormatter {

    private static final String RED = "\u001B[31m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String DIM = "\u001B[2m";
    private static final String RESET = "\u001B[0m";

    private DiagnosticFormatter() {
    }

    public static String format(Throwable t) {
        if (t instanceof MiraError error) {
            return formatMiraError(error);
        }

        String msg = t.getMessage();
        return RED + BOLD + "internal error" + RESET + ": " + (msg != null ? msg : t.getClass().getSimpleName());
    }

    private static String formatMiraError(MiraError error) {
        StringBuilder sb = new StringBuilder();

        sb.append(RED).append(BOLD).append("error");
        if (error.getErrorCode() != null) {
            sb.append("[").append(error.getErrorCode()).append("]");
        }
        sb.append(RESET).append(": ").append(BOLD).append(error.getMessage()).append(RESET).append("\n");

        int line = error.getLine();
        int col = error.getColumn();

        String fileName = Flags.fileName != null ? Flags.fileName : "<input>";

        if (line > 0) {
            sb.append(CYAN).append("  --> ").append(RESET)
                    .append(fileName).append(":").append(line).append(":").append(col).append("\n");

            String[] sourceLines = Flags.sourceLines;
            if (sourceLines != null && line <= sourceLines.length) {
                String srcLine = sourceLines[line - 1];
                String lineLabel = String.format("%4d", line);

                sb.append(DIM).append("     |").append(RESET).append("\n");
                sb.append(DIM).append(lineLabel).append(" |").append(RESET)
                        .append(" ").append(srcLine).append("\n");

                sb.append(DIM).append("     |").append(RESET).append(" ");
                int caretPos = Math.max(0, col - 1);
                for (int i = 0; i < caretPos; i++) {
                    sb.append(srcLine.length() > i && srcLine.charAt(i) == '\t' ? '\t' : ' ');
                }
                sb.append(RED).append(BOLD).append("^").append(RESET).append("\n");
                sb.append(DIM).append("     |").append(RESET).append("\n");
            }
        } else {
            sb.append(CYAN).append("  --> ").append(RESET).append(fileName).append("\n");
        }

        if (error.getHint() != null) {
            sb.append(CYAN).append("     = ").append(RESET)
                    .append("hint: ").append(error.getHint()).append("\n");
        }

        return sb.toString().stripTrailing();
    }
}
