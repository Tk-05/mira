package com.mira.lsp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.utils.ModuleResolver;

public class WorkspaceIndex {

    private final Map<Path, List<Node>> astCache = new ConcurrentHashMap<>();
    private final Map<Path, String> sourceCache = new ConcurrentHashMap<>();
    private volatile List<Path> miraFiles;
    private volatile Path indexedRoot;

    public List<Node> getAst(Path path, Map<String, String> openOverlayByUri) {
        String overlay = openOverlayByUri != null ? openOverlayByUri.get(path.toUri().toString()) : null;
        if (overlay != null) {
            String cachedSource = sourceCache.get(path);
            if (cachedSource == null || !cachedSource.equals(overlay)) {
                List<Node> ast = parse(overlay);
                astCache.put(path, ast);
                sourceCache.put(path, overlay);
                return ast;
            }
            return astCache.get(path);
        }
        return astCache.computeIfAbsent(path, p -> {
            try {
                String src = Files.readString(p);
                sourceCache.put(p, src);
                return parse(src);
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    public String getSource(Path path, Map<String, String> openOverlayByUri) {
        getAst(path, openOverlayByUri);
        return sourceCache.getOrDefault(path, "");
    }

    public void invalidate(Path path) {
        astCache.remove(path);
        sourceCache.remove(path);
    }

    public List<Path> allMiraFiles(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return List.of();
        }
        List<Path> cached = miraFiles;
        if (cached != null && workspaceRoot.equals(indexedRoot)) {
            return cached;
        }
        List<Path> found = ModuleResolver.findAllMiraFiles(workspaceRoot);
        miraFiles = found;
        indexedRoot = workspaceRoot;
        return found;
    }

    public void invalidateFileList() {
        miraFiles = null;
    }

    private static List<Node> parse(String source) {
        try {
            return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        } catch (Exception e) {
            return List.of();
        }
    }
}
