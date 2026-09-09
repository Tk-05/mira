package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.Position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class CallHierarchyProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void prepareFindsFunctionAtDeclarationSite() {
        String source = """
                fn add(a, b) {
                    return a + b;
                }
                """;
        String uri = "file:///test.mira";
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(parse(source), source, new Position(0, 4), uri,
                new WorkspaceIndex(), Map.of());
        assertEquals(1, items.size());
        assertEquals("add", items.get(0).getName());
    }

    @Test
    void prepareFindsFunctionAtCallSite() {
        String source = """
                fn add(a, b) {
                    return a + b;
                }
                fn main() {
                    return add(1, 2);
                }
                """;
        String uri = "file:///test.mira";
        // cursor on "add" inside main()'s call
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(parse(source), source, new Position(4, 12), uri,
                new WorkspaceIndex(), Map.of());
        assertEquals(1, items.size());
        assertEquals("add", items.get(0).getName());
    }

    @Test
    void prepareReturnsEmptyForNonFunctionPosition() {
        String source = """
                var x : 1;
                """;
        String uri = "file:///test.mira";
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(parse(source), source, new Position(0, 4), uri,
                new WorkspaceIndex(), Map.of());
        assertEquals(0, items.size());
    }

    @Test
    void incomingCallsFindsSameFileCaller(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    return helper();
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(index.getAst(file, Map.of()), source,
                new Position(0, 4), uri, index, Map.of());
        assertEquals(1, items.size());

        List<CallHierarchyIncomingCall> calls = CallHierarchyProvider.incomingCalls(items.get(0), index, tempDir,
                Map.of());
        assertEquals(1, calls.size());
        assertEquals("main", calls.get(0).getFrom().getName());
        assertEquals(1, calls.get(0).getFromRanges().size());
    }

    @Test
    void incomingCallsGroupsMultipleCallSitesFromSameCaller(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    helper();
                    return helper();
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(index.getAst(file, Map.of()), source,
                new Position(0, 4), uri, index, Map.of());

        List<CallHierarchyIncomingCall> calls = CallHierarchyProvider.incomingCalls(items.get(0), index, tempDir,
                Map.of());
        assertEquals(1, calls.size());
        assertEquals(2, calls.get(0).getFromRanges().size());
    }

    @Test
    void incomingCallsEmptyWhenNoCallers(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(index.getAst(file, Map.of()), source,
                new Position(0, 4), uri, index, Map.of());

        List<CallHierarchyIncomingCall> calls = CallHierarchyProvider.incomingCalls(items.get(0), index, tempDir,
                Map.of());
        assertEquals(0, calls.size());
    }

    @Test
    void outgoingCallsFindsSameFileCallee(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    return helper();
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(index.getAst(file, Map.of()), source,
                new Position(3, 4), uri, index, Map.of());

        List<CallHierarchyOutgoingCall> calls = CallHierarchyProvider.outgoingCalls(items.get(0), index, Map.of());
        assertEquals(1, calls.size());
        assertEquals("helper", calls.get(0).getTo().getName());
    }

    @Test
    void incomingCallsFindsCrossFileCallerViaNamespacedImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        Files.writeString(mainPath, """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.helper();
                }
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        List<Node> libAst = index.getAst(libPath, Map.of());
        String libUri = libPath.toUri().toString();
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(libAst, Files.readString(libPath),
                new Position(0, 8), libUri, index, Map.of());
        assertEquals(1, items.size());

        List<CallHierarchyIncomingCall> calls = CallHierarchyProvider.incomingCalls(items.get(0), index, tempDir,
                Map.of());
        assertEquals(1, calls.size());
        assertEquals("main", calls.get(0).getFrom().getName());
        assertEquals(mainPath.toUri().toString(), calls.get(0).getFrom().getUri());
    }

    @Test
    void outgoingCallsFindsCrossFileCalleeViaNamespacedImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String mainSource = """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.helper();
                }
                """;
        Files.writeString(mainPath, mainSource);

        WorkspaceIndex index = new WorkspaceIndex();
        List<Node> mainAst = index.getAst(mainPath, Map.of());
        String mainUri = mainPath.toUri().toString();
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(mainAst, mainSource, new Position(1, 4), mainUri,
                index, Map.of());
        assertEquals(1, items.size());

        List<CallHierarchyOutgoingCall> calls = CallHierarchyProvider.outgoingCalls(items.get(0), index, Map.of());
        assertEquals(1, calls.size());
        assertEquals("helper", calls.get(0).getTo().getName());
        assertEquals(libPath.toUri().toString(), calls.get(0).getTo().getUri());
    }

    @Test
    void incomingCallsFindsCrossFileCallerViaSelectiveImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        Files.writeString(mainPath, """
                import module "lib.mira" {helper};
                fn main() {
                    return helper();
                }
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        List<Node> libAst = index.getAst(libPath, Map.of());
        String libUri = libPath.toUri().toString();
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(libAst, Files.readString(libPath),
                new Position(0, 8), libUri, index, Map.of());

        List<CallHierarchyIncomingCall> calls = CallHierarchyProvider.incomingCalls(items.get(0), index, tempDir,
                Map.of());
        assertEquals(1, calls.size());
        assertEquals("main", calls.get(0).getFrom().getName());
    }

    @Test
    void doesNotResolveMethodCallsOnObjects(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                var obj : {
                    fn helper() {
                        return 1;
                    }
                };
                fn main() {
                    return obj.helper();
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();
        List<CallHierarchyItem> mainItems = CallHierarchyProvider.prepare(index.getAst(file, Map.of()), source,
                new Position(5, 4), uri, index, Map.of());
        assertEquals(1, mainItems.size());
        assertEquals("main", mainItems.get(0).getName());

        List<CallHierarchyOutgoingCall> calls = CallHierarchyProvider.outgoingCalls(mainItems.get(0), index, Map.of());
        assertTrue(calls.isEmpty(), "method calls on objects must not be resolved as plain function calls");
    }
}
