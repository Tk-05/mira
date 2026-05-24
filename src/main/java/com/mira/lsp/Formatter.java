package com.mira.lsp;

public class Formatter {

    private static final int INDENT_SIZE = 4;

    public static String format(String source) {
        String[] lines = source.split("\n", -1);
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean inMultiLineString = false;
        boolean inBlockComment = false;

        for (String line : lines) {
            String stripped = line.strip();

            if (inMultiLineString) {
                result.append(line).append("\n");
                if (countOccurrences(stripped, "\"\"\"") % 2 == 1) {
                    inMultiLineString = false;
                }
                continue;
            }

            if (countOccurrences(stripped, "\"\"\"") % 2 == 1) {
                result.append(" ".repeat(Math.max(0, indent) * INDENT_SIZE)).append(stripped).append("\n");
                inMultiLineString = true;
                continue;
            }

            if (stripped.isEmpty()) {
                result.append("\n");
                continue;
            }

            int leadingClose = countLeadingClose(stripped);
            int thisIndent = Math.max(0, indent - leadingClose);

            result.append(" ".repeat(thisIndent * INDENT_SIZE)).append(stripped).append("\n");

            int net = countNetBraces(stripped, inBlockComment);

            inBlockComment = updatedBlockCommentState(stripped, inBlockComment);

            indent = Math.max(0, indent + net);
        }

        return result.toString().stripTrailing() + "\n";
    }

    private static int countLeadingClose(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '}') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static int countNetBraces(String line, boolean startInBlockComment) {
        int net = 0;
        boolean inString = false;
        boolean inBlockComment = startInBlockComment;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            if (inBlockComment) {
                if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            if (inString) {
                if (c == '\\') {
                    i += 2;
                } else if (c == '"') {
                    inString = false;
                    i++;
                } else {
                    i++;
                }
                continue;
            }

            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                break;
            }

            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                inBlockComment = true;
                i += 2;
                continue;
            }

            if (c == '"' && i + 2 < line.length() && line.charAt(i + 1) == '"' && line.charAt(i + 2) == '"') {
                i += 3;
                continue;
            }

            if (c == '"') {
                inString = true;
                i++;
                continue;
            }

            if (c == '{') {
                net++;
            } else if (c == '}') {
                net--;
            }

            i++;
        }

        return net;
    }

    private static boolean updatedBlockCommentState(String line, boolean startInBlockComment) {
        boolean inBlockComment = startInBlockComment;
        boolean inString = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (inBlockComment) {
                if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
            } else if (inString) {
                if (c == '\\') {
                    i += 2;
                } else if (c == '"') {
                    inString = false;
                    i++;
                } else {
                    i++;
                }
            } else {
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    break;
                }
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i += 2;
                } else if (c == '"') {
                    inString = true;
                    i++;
                } else {
                    i++;
                }
            }
        }
        return inBlockComment;
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
