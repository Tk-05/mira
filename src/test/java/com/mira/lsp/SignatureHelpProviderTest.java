package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class SignatureHelpProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void firstParameterActive() {
        String source = """
                fn add(a, b, c) {
                    return a;
                }
                fn main() {
                    return add(1, 2, 3);
                }
                """;
        Position pos = new Position(4, 15);
        SignatureHelp help = SignatureHelpProvider.provide(parse(source), source, pos, null, null, Map.of());
        assertEquals(1, help.getSignatures().size());
        assertEquals("add(a, b, c)", help.getSignatures().get(0).getLabel());
        assertEquals(0, help.getActiveParameter());
    }

    @Test
    void secondParameterActiveAfterComma() {
        String source = """
                fn add(a, b, c) {
                    return a;
                }
                fn main() {
                    return add(1, 2, 3);
                }
                """;
        Position pos = new Position(4, 18);
        SignatureHelp help = SignatureHelpProvider.provide(parse(source), source, pos, null, null, Map.of());
        assertEquals(1, help.getActiveParameter());
    }

    @Test
    void stdlibFallback() {
        String source = """
                import string;
                fn main() {
                    return string.charAt(x, 1);
                }
                """;
        Position pos = new Position(2, 26);
        SignatureHelp help = SignatureHelpProvider.provide(parse(source), source, pos, null, null, Map.of());
        assertEquals(1, help.getSignatures().size());
        assertTrue(help.getSignatures().get(0).getLabel().startsWith("charAt("));
    }

    @Test
    void objectMethodCall() {
        String source = """
                var obj : {
                    fn increment(step) {
                        return step;
                    }
                };
                fn main() {
                    return obj.increment(1);
                }
                """;
        Position pos = new Position(6, 26);
        SignatureHelp help = SignatureHelpProvider.provide(parse(source), source, pos, null, null, Map.of());
        assertEquals(1, help.getSignatures().size());
        assertEquals("increment(step)", help.getSignatures().get(0).getLabel());
    }

    @Test
    void resolvesParamsForBareCallFromSelectiveModuleImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet(name) {
                    return name;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" {greet};
                fn main() {
                    return greet(1);
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(2, 17); // inside "greet(1)"'s parens
        SignatureHelp help = SignatureHelpProvider.provide(parse(source), source, pos, mainPath, null, Map.of());

        assertEquals(1, help.getSignatures().size());
        assertEquals("greet(name)", help.getSignatures().get(0).getLabel());
    }
}
