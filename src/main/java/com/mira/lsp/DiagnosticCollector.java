package com.mira.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.mira.error.MiraError;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.linter.Linter;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.warning.Warning;
import com.mira.warning.WarningCollector;
import com.mira.warning.WarningLevel;

public class DiagnosticCollector {

    public static List<Diagnostic> collect(String source) {
        List<Diagnostic> result = new ArrayList<>();
        WarningCollector.clear();
        try {
            List<Token> tokens = new Tokenizer().tokenize(source, false);
            List<Node> ast = new Parser().parseTokens(tokens);
            try {
                new Linter().lint(ast);
            } catch (MiraError e) {
                result.add(fromError(e, DiagnosticSeverity.Error));
            }
        } catch (MiraError e) {
            result.add(fromError(e, DiagnosticSeverity.Error));
        }
        for (Warning w : WarningCollector.getWarnings()) {
            result.add(fromWarning(w));
        }
        WarningCollector.clear();
        return result;
    }

    private static Diagnostic fromError(MiraError e, DiagnosticSeverity severity) {
        int line = Math.max(e.getLine() - 1, 0);
        int col = Math.max(e.getColumn() - 1, 0);
        int endCol = col + Math.max(1, e.getSpan());
        Range range = new Range(new Position(line, col), new Position(line, endCol));
        String message = e.getMessage();
        if (e.getHint() != null) {
            message += "\n💡 " + e.getHint();
        }
        Diagnostic d = new Diagnostic(range, message, severity, "mira");
        if (e.getErrorCode() != null) {
            d.setCode(e.getErrorCode());
        }
        return d;
    }

    private static Diagnostic fromWarning(Warning w) {
        int line = Math.max(w.line() - 1, 0);
        int col = Math.max(w.column() - 1, 0);
        Range range = new Range(new Position(line, col), new Position(line, col + 1));
        DiagnosticSeverity sev = w.level() == WarningLevel.WARNING
                ? DiagnosticSeverity.Warning : DiagnosticSeverity.Information;
        return new Diagnostic(range, w.message(), sev, "mira");
    }
}
