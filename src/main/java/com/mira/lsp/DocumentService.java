package com.mira.lsp;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;

import com.mira.error.MiraError;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.format.AstFormatter;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class DocumentService implements TextDocumentService {

    private final LspServer server;
    private final WorkspaceIndex workspaceIndex;
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, List<Node>> astCache = new ConcurrentHashMap<>();
    private volatile Path workspaceRoot;

    public DocumentService(LspServer server, WorkspaceIndex workspaceIndex) {
        this.server = server;
        this.workspaceIndex = workspaceIndex;
    }

    public void setWorkspaceRoot(Path root) {
        this.workspaceRoot = root;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public WorkspaceIndex getWorkspaceIndex() {
        return workspaceIndex;
    }

    public Map<String, String> getOpenDocuments() {
        return documents;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getTextDocument().getText();
        documents.put(uri, content);
        updateAstCache(uri, content);
        invalidateWorkspaceEntry(uri);
        reanalyzeAll();
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getContentChanges().get(0).getText();
        documents.put(uri, content);
        updateAstCache(uri, content);
        invalidateWorkspaceEntry(uri);
        reanalyzeAll();
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        astCache.remove(uri);
        invalidateWorkspaceEntry(uri);
        server.publishDiagnostics(uri, List.of());
    }

    private void invalidateWorkspaceEntry(String uri) {
        Path path = uriToPath(uri);
        if (path != null) {
            workspaceIndex.invalidate(path);
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        String uri = params.getTextDocument().getUri();
        String content = documents.getOrDefault(uri, "");
        String formatted = AstFormatter.format(content);
        int lineCount = content.split("\n", -1).length;
        Range fullRange = new Range(new Position(0, 0), new Position(lineCount, 0));
        return CompletableFuture.completedFuture(List.of(new TextEdit(fullRange, formatted)));
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Path docPath = uriToPath(uri);
        List<Either<Command, CodeAction>> actions = CodeActionProvider.provide(params, ast, uri, content,
                docPath, workspaceIndex, workspaceRoot, documents);
        return CompletableFuture.completedFuture(actions);
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Path docPath = uriToPath(uri);
        SignatureHelp help = SignatureHelpProvider.provide(ast, content, params.getPosition(),
                docPath, workspaceIndex, documents);
        return CompletableFuture.completedFuture(help);
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
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        return CompletableFuture.completedFuture(SemanticTokenProvider.provide(ast));
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

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Path docPath = uriToPath(uri);
        boolean includeDeclaration = params.getContext() != null && params.getContext().isIncludeDeclaration();
        List<Location> result = ReferenceProvider.provide(ast, content, params.getPosition(), uri,
                docPath, workspaceIndex, workspaceRoot, documents, includeDeclaration);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Path docPath = uriToPath(uri);
        Range range = RenameProvider.prepareRename(ast, content, params.getPosition(), uri,
                docPath, workspaceIndex, workspaceRoot, documents);
        Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> result =
                range != null ? Either3.forFirst(range) : null;
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Path docPath = uriToPath(uri);
        WorkspaceEdit edit = RenameProvider.rename(ast, content, params.getPosition(), uri,
                docPath, workspaceIndex, workspaceRoot, documents, params.getNewName());
        return CompletableFuture.completedFuture(edit);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        List<Either<SymbolInformation, DocumentSymbol>> result = DocumentSymbolProvider.provide(ast, content).stream()
                .map(Either::<SymbolInformation, DocumentSymbol>forRight)
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    private void updateAstCache(String uri, String content) {
        try {
            List<Token> tokens = new Tokenizer().tokenize(content, false);
            List<Node> ast = new Parser().parseTokens(tokens);
            astCache.put(uri, ast);
        } catch (MiraError | MultipleParserErrors ignored) {
            // Parsing failed for the text just stored in `documents` — remove any
            // previously cached AST rather than leaving it paired with the new
            // (unparseable) text. Every position-based feature reads both maps
            // together, and a stale AST against fresh text computes offsets
            // against the wrong tree. Callers already treat a missing entry as
            // "no result" via getOrDefault(uri, List.of()).
            astCache.remove(uri);
        }
    }

    private void reanalyzeAll() {
        Map<Path, String> openDocuments = openDocumentsByPath();
        documents.forEach((docUri, docContent)
                -> server.publishDiagnostics(docUri,
                        DiagnosticCollector.collect(docContent, uriToPath(docUri), openDocuments,
                                workspaceIndex, workspaceRoot)));
    }

    Map<Path, String> openDocumentsByPath() {
        Map<Path, String> openDocuments = new HashMap<>();
        documents.forEach((docUri, docContent) -> {
            Path p = uriToPath(docUri);
            if (p != null) {
                openDocuments.put(p, docContent);
            }
        });
        return openDocuments;
    }

    static Path uriToPath(String uri) {
        try {
            return java.nio.file.Paths.get(new java.net.URI(uri));
        } catch (Exception e) {
            return null;
        }
    }
}
