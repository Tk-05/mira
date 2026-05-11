package com.mira.lsp;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.statement.Statement;

public class HoverProvider {

    private static final Map<String, String> KEYWORD_DOCS = Map.ofEntries(
            Map.entry("fn", "**fn** — Function declaration"),
            Map.entry("var", "**var** — Mutable variable declaration"),
            Map.entry("const", "**const** — Immutable constant declaration"),
            Map.entry("pure", "**pure fn** — Pure function (result is cached for same arguments)"),
            Map.entry("async", "**async fn** — Asynchronous function"),
            Map.entry("spawn", "**spawn(fn)** — Starts an async task, returns a `Promise`"),
            Map.entry("await", "**await(promise)** — Waits for the result of an async task"),
            Map.entry("lock", "**lock(mutex) { ... }** — Exclusive access via mutex"),
            Map.entry("return", "**return** — Returns a value from a function"),
            Map.entry("if", "**if** — Conditional statement"),
            Map.entry("else", "**else** — Alternative branch of an if statement"),
            Map.entry("while", "**while** — Loop while condition is true"),
            Map.entry("for", "**for** — C-style loop"),
            Map.entry("foreach", "**foreach** — Iterate over a collection"),
            Map.entry("in", "**in** — Used in foreach to iterate over a collection"),
            Map.entry("break", "**break** — Exit the current loop"),
            Map.entry("continue", "**continue** — Skip to the next loop iteration"),
            Map.entry("switch", "**switch** — Pattern matching on a value"),
            Map.entry("case", "**case** — A branch in a switch statement"),
            Map.entry("default", "**default** — Default branch in a switch statement"),
            Map.entry("try", "**try** — Try block for error handling"),
            Map.entry("catch", "**catch** — Catch block for error handling"),
            Map.entry("finally", "**finally** — Always-executed block after try/catch"),
            Map.entry("throw", "**throw** — Throw an exception"),
            Map.entry("import", "**import** — Import a module or stdlib"),
            Map.entry("module", "**module** — Declare the module name for this file"),
            Map.entry("as", "**as** — Alias for an import"),
            Map.entry("enum", "**enum** — Declare an enumeration"),
            Map.entry("typeof", "**typeof(value)** — Returns the type of a value as a string"),
            Map.entry("true", "**true** — Boolean literal"),
            Map.entry("false", "**false** — Boolean literal"),
            Map.entry("null", "**null** — Null value")
    );

    public static Hover provide(List<Node> ast, String content, Position pos) {
        String word = wordAt(content, pos);
        if (word == null || word.isBlank()) return null;

        String stripped = word.startsWith("$") ? word.substring(1) : word;

        for (Node n : ast) {
            if (n instanceof Statement.FuncDecl f && f.getName().equals(stripped)) {
                String params = f.getParameters().stream()
                        .map(Parameter::name)
                        .collect(Collectors.joining(", "));
                String prefix = (f.isAsync() ? "async " : "") + (f.isPure() ? "pure " : "");
                String sig = prefix + "fn " + f.getName() + "(" + params + ")";
                return hover("```mira\n" + sig + "\n```");
            }
            if (n instanceof Statement.VarDecl v && v.getName().equals(stripped)) {
                String kind = v.isConst() ? "const" : "var";
                return hover("```mira\n" + kind + " $" + v.getName() + "\n```");
            }
        }

        String kwDoc = KEYWORD_DOCS.get(word);
        if (kwDoc != null) {
            return hover(kwDoc);
        }

        return null;
    }

    static String wordAt(String content, Position pos) {
        String[] lines = content.split("\n", -1);
        if (pos.getLine() >= lines.length) return null;
        String line = lines[pos.getLine()];
        int col = pos.getCharacter();
        if (col > line.length()) col = line.length();

        int start = col;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        if (start > 0 && line.charAt(start - 1) == '$') start--;

        int end = col;
        while (end < line.length() && isWordChar(line.charAt(end))) end++;

        if (start >= end) return null;
        return line.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static Hover hover(String markdown) {
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, markdown));
    }
}
