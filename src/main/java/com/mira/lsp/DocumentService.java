package com.mira.lsp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import com.mira.error.MiraError;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class DocumentService implements TextDocumentService {

    private final LspServer server;
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, List<Node>> astCache = new ConcurrentHashMap<>();

    public DocumentService(LspServer server) {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getTextDocument().getText();
        documents.put(uri, content);
        updateAstCache(uri, content);
        server.publishDiagnostics(uri, analyze(content));
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getContentChanges().get(0).getText();
        documents.put(uri, content);
        updateAstCache(uri, content);
        server.publishDiagnostics(uri, analyze(content));
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        astCache.remove(uri);
        server.publishDiagnostics(uri, List.of());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        String uri = params.getTextDocument().getUri();
        String content = documents.getOrDefault(uri, "");
        String formatted = Formatter.format(content);
        int lineCount = content.split("\n", -1).length;
        Range fullRange = new Range(new Position(0, 0), new Position(lineCount, 0));
        return CompletableFuture.completedFuture(List.of(new TextEdit(fullRange, formatted)));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        return CompletableFuture.completedFuture(Either.forLeft(CompletionProvider.provide(ast, uri)));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Hover hover = HoverProvider.provide(ast, content, params.getPosition());
        return CompletableFuture.completedFuture(hover);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Location loc = DefinitionProvider.provide(ast, content, uri, params.getPosition());
        List<Location> result = loc != null ? List.of(loc) : List.of();
        return CompletableFuture.completedFuture(Either.forLeft(result));
    }

    private void updateAstCache(String uri, String content) {
        try {
            List<Token> tokens = new Tokenizer().tokenize(content, false);
            List<Node> ast = new Parser().parseTokens(tokens);
            astCache.put(uri, ast);
        } catch (MiraError ignored) {
        }
    }

    private List<Diagnostic> analyze(String content) {
        return DiagnosticCollector.collect(content);
    }
}
