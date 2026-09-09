package com.mira.lsp;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
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
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
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
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;

import com.mira.error.MiraError;
import com.mira.error.lexer.MultipleLexerErrors;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.format.AstFormatter;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class DocumentService implements TextDocumentService {

    private static final long DIAGNOSTICS_DEBOUNCE_MS = 300;

    private final LspServer server;
    private final WorkspaceIndex workspaceIndex;
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, List<Node>> astCache = new ConcurrentHashMap<>();
    private volatile Path workspaceRoot;

    private final ScheduledExecutorService diagnosticsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mira-diagnostics");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> pendingDiagnostics;

    public DocumentService(LspServer server, WorkspaceIndex workspaceIndex) {
        this.server = server;
        this.workspaceIndex = workspaceIndex;
    }

    public void shutdown() {
        diagnosticsScheduler.shutdownNow();
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
        scheduleReanalysis(0);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getContentChanges().get(0).getText();
        documents.put(uri, content);
        updateAstCache(uri, content);
        invalidateWorkspaceEntry(uri);
        scheduleReanalysis(DIAGNOSTICS_DEBOUNCE_MS);
    }

    /**
     * Cancels any not-yet-run reanalysis and schedules a fresh one after
     * {@code delayMs} on the single diagnostics thread - so a burst of edits
     * collapses into one check after the user actually pauses, instead of one
     * full workspace check per keystroke.
     */
    private void scheduleReanalysis(long delayMs) {
        ScheduledFuture<?> pending = pendingDiagnostics;
        if (pending != null) {
            pending.cancel(false);
        }
        pendingDiagnostics = diagnosticsScheduler.schedule(this::reanalyzeAll, delayMs, TimeUnit.MILLISECONDS);
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
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Path docPath = uriToPath(uri);
        List<CodeLens> lenses = CodeLensProvider.provide(ast, content, uri, docPath, workspaceIndex, workspaceRoot,
                documents);
        return CompletableFuture.completedFuture(lenses);
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
        String content = documents.getOrDefault(uri, "");
        List<CompletionItem> items = CompletionProvider.provide(ast, uri, content, params.getPosition(),
                uriToPath(uri));
        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Hover hover = HoverProvider.provide(ast, content, params.getPosition(), uriToPath(uri),
                workspaceIndex, documents);
        return CompletableFuture.completedFuture(hover);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        return CompletableFuture.completedFuture(SemanticTokenProvider.provide(ast));
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        return CompletableFuture.completedFuture(SemanticTokenProvider.provide(ast, params.getRange()));
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        List<CallHierarchyItem> items = CallHierarchyProvider.prepare(ast, content, params.getPosition(), uri,
                workspaceIndex, documents);
        return CompletableFuture.completedFuture(items);
    }

    @Override
    public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
            CallHierarchyIncomingCallsParams params) {
        List<CallHierarchyIncomingCall> calls = CallHierarchyProvider.incomingCalls(params.getItem(),
                workspaceIndex, workspaceRoot, documents);
        return CompletableFuture.completedFuture(calls);
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
            CallHierarchyOutgoingCallsParams params) {
        List<CallHierarchyOutgoingCall> calls = CallHierarchyProvider.outgoingCalls(params.getItem(),
                workspaceIndex, documents);
        return CompletableFuture.completedFuture(calls);
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
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        List<DocumentHighlight> result = DocumentHighlightProvider.provide(ast, content, params.getPosition(), uri);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        return CompletableFuture.completedFuture(FoldingRangeProvider.provide(ast));
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
        Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> result
                = range != null ? Either3.forFirst(range) : null;
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        Path docPath = uriToPath(uri);
        try {
            WorkspaceEdit edit = RenameProvider.rename(ast, content, params.getPosition(), uri,
                    docPath, workspaceIndex, workspaceRoot, documents, params.getNewName());
            return CompletableFuture.completedFuture(edit);
        } catch (RenameProvider.RenameRejectedException e) {
            CompletableFuture<WorkspaceEdit> failed = new CompletableFuture<>();
            failed.completeExceptionally(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.RequestFailed, e.getMessage(), null)));
            return failed;
        }
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        String uri = params.getTextDocument().getUri();
        List<Node> ast = astCache.getOrDefault(uri, List.of());
        String content = documents.getOrDefault(uri, "");
        return CompletableFuture.completedFuture(InlayHintProvider.provide(ast, content, params.getRange()));
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
        } catch (MiraError | MultipleLexerErrors | MultipleParserErrors ignored) {
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
