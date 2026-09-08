package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class CodeActionProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void offersQuickFixForUnusedVariable() {
        String source = """
                module main;
                fn main() {
                    var unused : 5;
                    return 0;
                }
                """;
        String uri = "file:///test.mira";
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, null, Map.of());
        assertTrue(diagnostics.stream().anyMatch(d -> d.getMessage().contains("unused")),
                "expected an unused-variable diagnostic, got: " + diagnostics);

        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(2, 8), new Position(2, 14)),
                new CodeActionContext(diagnostics));

        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                null, new WorkspaceIndex(), null, Map.of());
        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertEquals("Remove unused 'unused'", action.getTitle());
        List<TextEdit> edits = action.getEdit().getChanges().get(uri);
        assertEquals(1, edits.size());
        assertEquals(new Range(new Position(2, 0), new Position(3, 0)), edits.get(0).getRange());
    }

    @Test
    void noActionsWithoutMatchingDiagnostics() {
        String uri = "file:///test.mira";
        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(0, 0)),
                new CodeActionContext(List.of()));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse("module main;\n"),
                uri, "module main;\n", null, new WorkspaceIndex(), null, Map.of());
        assertEquals(0, actions.size());
    }

    @Test
    void offersQuickFixForConstReassignment() {
        String source = """
                module main;
                fn main() {
                    const x : 1;
                    x : 2;
                    return x;
                }
                """;
        String uri = "file:///test.mira";
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, null, Map.of());
        assertTrue(diagnostics.stream().anyMatch(d -> d.getMessage().contains("reassign constant 'x'")),
                "expected a const-reassignment diagnostic, got: " + diagnostics);

        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(3, 4), new Position(3, 6)),
                new CodeActionContext(diagnostics));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                null, new WorkspaceIndex(), null, Map.of());

        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertEquals("Change 'const x' to 'var'", action.getTitle());
        List<TextEdit> edits = action.getEdit().getChanges().get(uri);
        assertEquals(1, edits.size());
        assertEquals("var", edits.get(0).getNewText());
        assertEquals(new Range(new Position(2, 4), new Position(2, 9)), edits.get(0).getRange());
    }

    @Test
    void offersQuickFixForMissingModuleDeclaration() {
        String source = """
                fn main() {
                    return 0;
                }
                """;
        String uri = "file:///test.mira";
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, null, Map.of());
        assertTrue(diagnostics.stream().anyMatch(d -> d.getMessage().contains("missing a 'module' declaration")),
                "expected a missing-module-declaration diagnostic, got: " + diagnostics);

        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(0, 0)),
                new CodeActionContext(diagnostics));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                null, new WorkspaceIndex(), null, Map.of());

        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertEquals("Add 'module test;' declaration", action.getTitle());
        List<TextEdit> edits = action.getEdit().getChanges().get(uri);
        assertEquals(1, edits.size());
        assertEquals("module test;\n", edits.get(0).getNewText());
        assertEquals(new Range(new Position(0, 0), new Position(0, 0)), edits.get(0).getRange());
    }

    @Test
    void offersQuickFixForUndeclaredVariable() {
        String source = """
                module main;
                fn main() {
                    return y;
                }
                """;
        String uri = "file:///test.mira";
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, null, Map.of());
        assertTrue(diagnostics.stream().anyMatch(d -> d.getMessage().contains("never declared")),
                "expected an undeclared-variable diagnostic, got: " + diagnostics);

        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(2, 11), new Position(2, 12)),
                new CodeActionContext(diagnostics));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                null, new WorkspaceIndex(), null, Map.of());

        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertEquals("Declare 'var y;'", action.getTitle());
        List<TextEdit> edits = action.getEdit().getChanges().get(uri);
        assertEquals(1, edits.size());
        assertEquals("    var y;\n", edits.get(0).getNewText());
        assertEquals(new Range(new Position(2, 0), new Position(2, 0)), edits.get(0).getRange());
    }

    @Test
    void unusedFixRenamesInsteadOfDeletingSharedCommaLine() {
        String source = """
                module main;
                fn main() {
                    var a, b : 2;
                    return a;
                }
                """;
        String uri = "file:///test.mira";
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, null, Map.of());
        Diagnostic unusedB = diagnostics.stream()
                .filter(d -> d.getMessage().contains("'b' is declared but never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected unused 'b' diagnostic, got: " + diagnostics));

        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                unusedB.getRange(),
                new CodeActionContext(List.of(unusedB)));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                null, new WorkspaceIndex(), null, Map.of());

        assertEquals(1, actions.size());
        TextEdit edit = actions.get(0).getRight().getEdit().getChanges().get(uri).get(0);
        assertEquals("_", edit.getNewText());
        assertEquals(edit.getRange().getStart().getLine(), edit.getRange().getEnd().getLine(),
                "fix must not span/delete the whole shared line, which would also remove 'a'");
    }

    @Test
    void unusedFixRenamesInsteadOfDeletingSharedDestructureLine() {
        String source = """
                module main;
                fn main() {
                    var (a, b) : {1, 2};
                    return a;
                }
                """;
        String uri = "file:///test.mira";
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, null, Map.of());
        Diagnostic unusedB = diagnostics.stream()
                .filter(d -> d.getMessage().contains("'b' is declared but never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected unused 'b' diagnostic, got: " + diagnostics));

        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                unusedB.getRange(),
                new CodeActionContext(List.of(unusedB)));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                null, new WorkspaceIndex(), null, Map.of());

        assertEquals(1, actions.size());
        TextEdit edit = actions.get(0).getRight().getEdit().getChanges().get(uri).get(0);
        assertEquals("_", edit.getNewText());
        assertEquals(edit.getRange().getStart().getLine(), edit.getRange().getEnd().getLine(),
                "fix must not span/delete the whole shared line, which would also remove 'a'");
    }

    @Test
    void unusedFixEditsOnlyTheSelectiveImportEntry(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet() {
                    return 1;
                }
                pub fn unused() {
                    return 2;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                module main;
                import module "lib.mira" {greet, unused};
                fn main() {
                    return greet();
                }
                """;
        Files.writeString(mainPath, source);

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, mainPath, Map.of());
        Diagnostic unusedImport = diagnostics.stream()
                .filter(d -> d.getMessage().contains("'unused' is imported but never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected unused 'unused' import diagnostic, got: "
                + diagnostics));

        String uri = mainPath.toUri().toString();
        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                unusedImport.getRange(),
                new CodeActionContext(List.of(unusedImport)));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                mainPath, new WorkspaceIndex(), tempDir, Map.of());

        assertEquals(1, actions.size());
        TextEdit edit = actions.get(0).getRight().getEdit().getChanges().get(uri).get(0);
        assertEquals("{greet}", edit.getNewText());
        assertEquals(edit.getRange().getStart().getLine(), edit.getRange().getEnd().getLine(),
                "fix must not span/delete the whole import line, which would also remove 'greet'");
    }

    @Test
    void offersQuickFixForPrivateAccessAcrossFiles(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                module lib;
                fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        Files.writeString(mainPath, """
                module main;
                import module "lib.mira" as lib;
                fn main() {
                    return lib.helper();
                }
                """);

        String mainSource = Files.readString(mainPath);
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(mainSource, mainPath, Map.of());
        assertTrue(diagnostics.stream().anyMatch(d -> d.getMessage().contains("is private in module")),
                "expected a private-access diagnostic, got: " + diagnostics);

        String mainUri = mainPath.toUri().toString();
        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(mainUri),
                new Range(new Position(3, 11), new Position(3, 24)),
                new CodeActionContext(diagnostics));

        WorkspaceIndex index = new WorkspaceIndex();
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(mainSource), mainUri,
                mainSource, mainPath, index, tempDir, Map.of());

        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertEquals("Mark 'helper' as 'pub' in lib.mira", action.getTitle());
        String libUri = libPath.toUri().toString();
        List<TextEdit> edits = action.getEdit().getChanges().get(libUri);
        assertEquals(1, edits.size());
        assertEquals("pub ", edits.get(0).getNewText());
        assertEquals(new Range(new Position(1, 0), new Position(1, 0)), edits.get(0).getRange());
    }

    @Test
    void offersQuickFixToImportUndefinedFunctionFromAnotherFile(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                module main;
                fn main() {
                    return helper();
                }
                """;
        Files.writeString(mainPath, source);

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, mainPath, Map.of());
        Diagnostic undefined = diagnostics.stream()
                .filter(d -> d.getMessage().contains("is called but never defined"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected undefined-function diagnostic, got: " + diagnostics));

        String uri = mainPath.toUri().toString();
        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                undefined.getRange(),
                new CodeActionContext(List.of(undefined)));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                mainPath, new WorkspaceIndex(), tempDir, Map.of());

        assertEquals(1, actions.size());
        CodeAction action = actions.get(0).getRight();
        assertEquals("Import 'helper' from 'lib.mira'", action.getTitle());
        List<TextEdit> edits = action.getEdit().getChanges().get(uri);
        assertEquals(1, edits.size());
        assertEquals("import module \"lib.mira\" {helper};\n", edits.get(0).getNewText());
        // right after the module declaration (line 0), since there are no
        // existing imports to group with
        assertEquals(new Range(new Position(1, 0), new Position(1, 0)), edits.get(0).getRange());
    }

    @Test
    void importFixInsertsAfterExistingImports(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                module main;
                import string;
                fn main() {
                    return helper();
                }
                """;
        Files.writeString(mainPath, source);

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, mainPath, Map.of());
        Diagnostic undefined = diagnostics.stream()
                .filter(d -> d.getMessage().contains("is called but never defined"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected undefined-function diagnostic, got: " + diagnostics));

        String uri = mainPath.toUri().toString();
        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                undefined.getRange(),
                new CodeActionContext(List.of(undefined)));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                mainPath, new WorkspaceIndex(), tempDir, Map.of());

        assertEquals(1, actions.size());
        TextEdit edit = actions.get(0).getRight().getEdit().getChanges().get(uri).get(0);
        // right after the existing "import string;" on line 1 (0-indexed)
        assertEquals(new Range(new Position(2, 0), new Position(2, 0)), edit.getRange());
    }

    @Test
    void noImportFixWhenNoFileExportsTheName(@TempDir Path tempDir) throws IOException {
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                module main;
                fn main() {
                    return helper();
                }
                """;
        Files.writeString(mainPath, source);

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, mainPath, Map.of());
        Diagnostic undefined = diagnostics.stream()
                .filter(d -> d.getMessage().contains("is called but never defined"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected undefined-function diagnostic, got: " + diagnostics));

        String uri = mainPath.toUri().toString();
        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                undefined.getRange(),
                new CodeActionContext(List.of(undefined)));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                mainPath, new WorkspaceIndex(), tempDir, Map.of());

        assertEquals(0, actions.size());
    }

    @Test
    void noImportFixForNonPublicMatchInAnotherFile(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                module main;
                fn main() {
                    return helper();
                }
                """;
        Files.writeString(mainPath, source);

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, mainPath, Map.of());
        Diagnostic undefined = diagnostics.stream()
                .filter(d -> d.getMessage().contains("is called but never defined"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected undefined-function diagnostic, got: " + diagnostics));

        String uri = mainPath.toUri().toString();
        CodeActionParams params = new CodeActionParams(
                new TextDocumentIdentifier(uri),
                undefined.getRange(),
                new CodeActionContext(List.of(undefined)));
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, parse(source), uri, source,
                mainPath, new WorkspaceIndex(), tempDir, Map.of());

        assertEquals(0, actions.size());
    }
}
