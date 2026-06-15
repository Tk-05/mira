package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.mira.error.MiraError;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.resolver.StaticCheck;
import com.mira.utils.ModuleResolver;
import com.mira.warning.Warning;
import com.mira.warning.WarningCollector;
import com.mira.warning.WarningLevel;

public class DiagnosticCollector {

    public static List<Diagnostic> collect(String source, Path filePath) {
        List<Diagnostic> result = new ArrayList<>();
        WarningCollector.clear();
        try {
            List<Token> tokens = new Tokenizer().tokenize(source, false);
            List<Node> ast = new Parser().parseTokens(tokens);
            try {
                Set<String> externalCalls = filePath != null
                        ? collectExternalCalls(ast, filePath)
                        : Set.of();
                new StaticCheck(externalCalls).check(ast);
            } catch (MultipleStaticCheckErrors mre) {
                mre.getErrors().forEach(e -> result.add(fromError(e, DiagnosticSeverity.Error)));
            }
        } catch (MultipleParserErrors mpe) {
            mpe.getErrors().forEach(e -> result.add(fromError(e, DiagnosticSeverity.Error)));
        } catch (MiraError e) {
            result.add(fromError(e, DiagnosticSeverity.Error));
        }
        for (Warning w : WarningCollector.getWarnings()) {
            result.add(fromWarning(w));
        }
        WarningCollector.clear();
        return result;
    }

    private static Set<String> collectExternalCalls(List<Node> ast, Path filePath) {
        Set<String> externalCalls = new LinkedHashSet<>();
        Path dir = filePath.getParent();
        if (dir == null) {
            return externalCalls;
        }
        try {
            Files.walk(dir)
                    .filter(p -> p.toString().endsWith(".mira") && !p.equals(filePath))
                    .forEach(callerPath -> {
                        try {
                            String src = Files.readString(callerPath);
                            List<Node> callerAst = new Parser().parseTokens(
                                    new Tokenizer().tokenize(src, false));
                            ModuleResolver.collectExternalCalls(callerAst, callerPath, filePath, externalCalls);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
        return externalCalls;
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
        int endLine = w.endLine() > 0 ? Math.max(w.endLine() - 1, 0) : line;
        Position end = endLine > line
                ? new Position(endLine + 1, 0)
                : new Position(line, col + Math.max(1, w.span()));
        Range range = new Range(new Position(line, col), end);
        DiagnosticSeverity sev = w.level() == WarningLevel.WARNING
                ? DiagnosticSeverity.Warning : DiagnosticSeverity.Information;
        return new Diagnostic(range, w.message(), sev, "mira");
    }
}
