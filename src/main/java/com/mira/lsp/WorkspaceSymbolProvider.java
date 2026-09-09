package com.mira.lsp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.SymbolInformation;

public class WorkspaceSymbolProvider {

    private static final int MAX_RESULTS = 200;

    public static List<SymbolInformation> provide(String query, Path workspaceRoot, WorkspaceIndex workspaceIndex,
            Map<String, String> openDocumentsByUri) {
        if (workspaceRoot == null) {
            return List.of();
        }
        String q = query == null ? "" : query.trim();
        List<Scored> scored = new ArrayList<>();
        for (Path file : workspaceIndex.allMiraFiles(workspaceRoot)) {
            for (SymbolInformation si : workspaceIndex.getSymbols(file, openDocumentsByUri)) {
                int score = q.isEmpty() ? 0 : fuzzyScore(q, si.getName());
                if (score >= 0) {
                    scored.add(new Scored(si, score));
                }
            }
        }

        scored.sort(Comparator.<Scored>comparingInt(s -> -s.score).thenComparing(s -> s.symbol.getName()));
        List<SymbolInformation> result = new ArrayList<>(Math.min(scored.size(), MAX_RESULTS));
        for (int i = 0; i < scored.size() && i < MAX_RESULTS; i++) {
            result.add(scored.get(i).symbol);
        }
        return result;
    }

    private record Scored(SymbolInformation symbol, int score) {

    }

    private static int fuzzyScore(String query, String target) {
        String q = query.toLowerCase();
        String t = target.toLowerCase();
        int qi = 0;
        int score = 0;
        int consecutiveRun = 0;
        for (int ti = 0; ti < t.length() && qi < q.length(); ti++) {
            if (t.charAt(ti) != q.charAt(qi)) {
                consecutiveRun = 0;
                continue;
            }
            consecutiveRun++;
            int bonus = 1 + Math.min(consecutiveRun, 5);
            if (ti == 0 || isWordBoundary(target, ti)) {
                bonus += 8;
            }
            score += bonus;
            qi++;
        }
        return qi == q.length() ? score : -1;
    }

    private static boolean isWordBoundary(String s, int i) {
        char prev = s.charAt(i - 1);
        if (prev == '_' || prev == '.') {
            return true;
        }
        return Character.isLowerCase(prev) && Character.isUpperCase(s.charAt(i));
    }
}
