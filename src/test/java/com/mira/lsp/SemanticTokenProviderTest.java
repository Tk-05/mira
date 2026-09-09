package com.mira.lsp;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class SemanticTokenProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    private static boolean hasTokenOfType(SemanticTokens tokens, String typeName) {
        int typeIndex = SemanticTokenProvider.TOKEN_TYPES.indexOf(typeName);
        List<Integer> data = tokens.getData();
        for (int i = 3; i < data.size(); i += 5) {
            if (data.get(i) == typeIndex) {
                return true;
            }
        }
        return false;
    }

    @Test
    void emitsTokensForDestructuredNames() {
        String source = """
                var (a, b) : {1, 2};
                """;
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        // 5 ints per token (deltaLine, deltaStart, length, tokenType, tokenModifiers)
        assertEquals(2, tokens.getData().size() / 5);
    }

    @Test
    void emitsFunctionTokenForDeclaredFunctionName() {
        String source = """
                fn add(a, b) {
                    return a + b;
                }
                """;
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        int functionTypeIndex = SemanticTokenProvider.TOKEN_TYPES.indexOf("function");
        List<Integer> data = tokens.getData();
        boolean hasFunctionToken = false;
        for (int i = 3; i < data.size(); i += 5) {
            if (data.get(i) == functionTypeIndex) {
                hasFunctionToken = true;
            }
        }
        assertTrue(hasFunctionToken);
    }

    @Test
    void emitsPropertyTokenForObjectField() {
        String source = """
                var obj : {
                    var count : 0;
                };
                """;
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        int propertyTypeIndex = SemanticTokenProvider.TOKEN_TYPES.indexOf("property");
        List<Integer> data = tokens.getData();
        boolean hasPropertyToken = false;
        for (int i = 3; i < data.size(); i += 5) {
            if (data.get(i) == propertyTypeIndex) {
                hasPropertyToken = true;
            }
        }
        assertTrue(hasPropertyToken);
    }

    @Test
    void emitsTypeTokenForExplicitVarDeclType() {
        String source = "var x : Number : 5;\n";
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        assertTrue(hasTokenOfType(tokens, "type"));
    }

    @Test
    void emitsTypeTokenForFunctionParameterAndReturnType() {
        String source = """
                fn add(a : Number, b : Number) -> Number {
                    return a + b;
                }
                """;
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        assertTrue(hasTokenOfType(tokens, "type"));
    }

    @Test
    void emitsTypeTokenForTypeAliasTarget() {
        String source = "type Id : Number;\n";
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        assertTrue(hasTokenOfType(tokens, "type"));
    }

    @Test
    void noTypeTokenWhenNoAnnotationsPresent() {
        String source = """
                fn add(a, b) {
                    return a + b;
                }
                """;
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        assertFalse(hasTokenOfType(tokens, "type"));
    }

    @Test
    void emitsNamespaceTokenForImportAlias() {
        String source = "import module \"lib.mira\" as lib;\n";
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        assertTrue(hasTokenOfType(tokens, "namespace"));
    }

    @Test
    void emitsFunctionTokenForNamespaceCall() {
        String source = """
                import module "lib.mira" as lib;
                lib.helper();
                """;
        SemanticTokens tokens = SemanticTokenProvider.provide(parse(source));
        assertTrue(hasTokenOfType(tokens, "function"));
    }

    @Test
    void rangeFilteredProvideOnlyIncludesTokensWithinRange() {
        String source = """
                var a : 1;
                var b : 2;
                var c : 3;
                """;
        List<Node> ast = parse(source);
        SemanticTokens full = SemanticTokenProvider.provide(ast);
        assertEquals(3, full.getData().size() / 5);

        Range onlyMiddleLine = new Range(new Position(1, 0), new Position(1, 99));
        SemanticTokens ranged = SemanticTokenProvider.provide(ast, onlyMiddleLine);
        assertEquals(1, ranged.getData().size() / 5);
    }
}
