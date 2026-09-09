package com.mira.build.dependency;

import java.util.List;

import com.mira.build.dependency.DependencyGraphInspector.DepNode;

/**
 * Renders a {@link DepNode} tree as produced by
 * {@link DependencyGraphInspector} for "mira deps".
 */
public final class DependencyTreePrinter {

    private DependencyTreePrinter() {
    }

    public static void print(DepNode root) {
        System.out.println(root.name() + " (" + root.spec() + ")");
        printChildren(root.children(), "");
    }

    private static void printChildren(List<DepNode> children, String prefix) {
        for (int i = 0; i < children.size(); i++) {
            DepNode child = children.get(i);
            boolean last = i == children.size() - 1;
            String connector = last ? "└─ " : "├─ ";
            String status = child.available() ? "✓ available" : "✗ missing";
            String detail = child.detail() != null ? " (" + child.detail() + ")" : "";

            System.out.println(prefix + connector + child.name() + " [" + child.kind() + "] " + child.spec() + "  "
                    + status + detail);

            String childPrefix = prefix + (last ? "   " : "│  ");
            printChildren(child.children(), childPrefix);
        }
    }
}
