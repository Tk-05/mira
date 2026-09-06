package com.mira.lsp;

import java.util.List;

import org.eclipse.lsp4j.SemanticTokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class SemanticTokenProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
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
}
