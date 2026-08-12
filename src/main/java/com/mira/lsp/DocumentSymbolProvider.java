package com.mira.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;

public class DocumentSymbolProvider {

    public static List<DocumentSymbol> provide(List<Node> ast, String content) {
        return walk(ast, content);
    }

    static void collectFlat(List<Node> nodes, String uri, String content, List<SymbolInformation> out) {
        for (DocumentSymbol ds : walk(nodes, content)) {
            flatten(ds, uri, null, out);
        }
    }

    private static void flatten(DocumentSymbol ds, String uri, String containerName, List<SymbolInformation> out) {
        SymbolInformation si = new SymbolInformation(ds.getName(), ds.getKind(),
                new Location(uri, ds.getRange()), containerName);
        out.add(si);
        if (ds.getChildren() != null) {
            for (DocumentSymbol child : ds.getChildren()) {
                flatten(child, uri, ds.getName(), out);
            }
        }
    }

    static List<DocumentSymbol> walk(List<Node> nodes, String content) {
        List<DocumentSymbol> out = new ArrayList<>();
        for (Node n : nodes) {
            collect(n, content, out);
        }
        return out;
    }

    private static void collect(Node n, String content, List<DocumentSymbol> out) {
        if (n instanceof FuncDecl f) {
            out.add(symbol(f.getName(), SymbolKind.Function, content, f.line, f.nameColumn, f.endLine,
                    walk(f.getBody(), content)));
        } else if (n instanceof VarDecl v) {
            out.add(varSymbol(v, content));
        } else if (n instanceof VarDestructure vd) {
            List<String> names = vd.getNames();
            for (int i = 0; i < names.size(); i++) {
                out.add(symbol(names.get(i), SymbolKind.Variable, content,
                        vd.line, vd.getNameColumns().get(i), vd.line, List.of()));
            }
        } else if (n instanceof EnumDecl ed) {
            out.add(enumSymbol(ed, content));
        } else if (n instanceof If s) {
            out.addAll(walk(s.getThenBody(), content));
            if (s.getElseBody() != null) {
                out.addAll(walk(s.getElseBody(), content));
            }
        } else if (n instanceof Loop s) {
            if (!s.isForeach()) {
                out.addAll(walk(s.getVarDecls(), content));
            }
            out.addAll(walk(s.getBody(), content));
        } else if (n instanceof While s) {
            out.addAll(walk(s.getBody(), content));
        } else if (n instanceof Block s) {
            out.addAll(walk(s.getBody(), content));
        } else if (n instanceof Switch s) {
            for (Switch.SwitchCase sc : s.getCases()) {
                out.addAll(walk(sc.getBody(), content));
            }
            if (s.getDefaultBody() != null) {
                out.addAll(walk(s.getDefaultBody(), content));
            }
        } else if (n instanceof TryCatch s) {
            out.addAll(walk(s.getTryBody(), content));
            for (TryCatch.CatchClause cc : s.getCatchClauses()) {
                out.addAll(walk(cc.getBody(), content));
            }
            if (s.getFinallyBody() != null) {
                out.addAll(walk(s.getFinallyBody(), content));
            }
        } else if (n instanceof Lock s) {
            out.addAll(walk(s.getBody(), content));
        } else if (n instanceof ComptimeBlock s) {
            out.addAll(walk(s.getBody(), content));
        }
    }

    private static DocumentSymbol varSymbol(VarDecl v, String content) {
        List<DocumentSymbol> children = new ArrayList<>();
        SymbolKind kind = v.isConst() ? SymbolKind.Constant : SymbolKind.Variable;
        if (v.getInitializer() instanceof ObjectExpression obj) {
            kind = SymbolKind.Object;
            children.addAll(memberSymbols(obj.getVarDecls(), obj.getMethods(), content));
        } else if (v.getInitializer() instanceof StructExpression st) {
            kind = SymbolKind.Struct;
            children.addAll(memberSymbols(st.getVarDecls(), st.getMethods(), content));
        }
        return symbol(v.getName(), kind, content, v.line, v.nameColumn, v.endLine, children);
    }

    private static List<DocumentSymbol> memberSymbols(List<VarDecl> fields, List<FuncDecl> methods, String content) {
        List<DocumentSymbol> children = new ArrayList<>();
        for (VarDecl f : fields) {
            SymbolKind k = f.isConst() ? SymbolKind.Constant : SymbolKind.Field;
            children.add(symbol(f.getName(), k, content, f.line, f.nameColumn, f.endLine, List.of()));
        }
        for (FuncDecl m : methods) {
            children.add(symbol(m.getName(), SymbolKind.Method, content, m.line, m.nameColumn, m.endLine, List.of()));
        }
        return children;
    }

    private static DocumentSymbol enumSymbol(EnumDecl ed, String content) {
        List<DocumentSymbol> children = new ArrayList<>();
        for (String key : ed.getValues().keySet()) {
            children.add(symbol(key, SymbolKind.EnumMember, content, ed.line, 0, ed.endLine, List.of()));
        }
        return symbol(ed.getIdentifier(), SymbolKind.Enum, content, ed.line, 0, ed.endLine, children);
    }

    private static DocumentSymbol symbol(String name, SymbolKind kind, String content,
            int line, int nameColumn, int endLine, List<DocumentSymbol> children) {
        Range selection = LspPositions.nameRange(content, line, nameColumn, name);
        Range full = LspPositions.fullRange(content, line, endLine, selection);
        DocumentSymbol ds = new DocumentSymbol(name, kind, full, selection);
        if (!children.isEmpty()) {
            ds.setChildren(children);
        }
        return ds;
    }
}
