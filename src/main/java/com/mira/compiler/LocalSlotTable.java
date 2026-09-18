package com.mira.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalSlotTable {

    private int nextSlot;
    private final Deque<Map<String, Integer>> scopeStack = new ArrayDeque<>();

    private final Deque<Integer> isolationFloors = new ArrayDeque<>();

    public LocalSlotTable(int startSlot) {
        this.nextSlot = startSlot;
        scopeStack.push(new LinkedHashMap<>());
    }

    public void enterScope() {
        scopeStack.push(new LinkedHashMap<>());
    }

    public void exitScope() {
        if (scopeStack.size() > 1) {
            scopeStack.pop();
        }
    }

    public void enterIsolatedScope() {
        isolationFloors.push(scopeStack.size());
        enterScope();
    }

    public void exitIsolatedScope() {
        exitScope();
        isolationFloors.pop();
    }

    public boolean isIsolated() {
        return !isolationFloors.isEmpty();
    }

    private int visibleScopeCount() {
        return isolationFloors.isEmpty() ? scopeStack.size() : scopeStack.size() - isolationFloors.peek();
    }

    public int allocate(String name) {
        int slot = nextSlot++;
        scopeStack.peek().put(name, slot);
        return slot;
    }

    public int allocateWide(String name) {
        int slot = nextSlot;
        nextSlot += 2;
        scopeStack.peek().put(name, slot);
        return slot;
    }

    public Integer slotOf(String name) {
        int visible = visibleScopeCount();
        int i = 0;
        for (Map<String, Integer> scope : scopeStack) {
            if (i >= visible) {
                break;
            }
            Integer slot = scope.get(name);
            if (slot != null) {
                return slot;
            }
            i++;
        }
        return null;
    }

    public int allocateTemp() {
        return nextSlot++;
    }

    public int current() {
        return nextSlot;
    }

    public List<String> getCaptureList() {
        int visible = visibleScopeCount();
        List<Map.Entry<String, Integer>> all = new ArrayList<>();
        int i = 0;
        for (Map<String, Integer> scope : scopeStack) {
            if (i >= visible) {
                break;
            }
            for (Map.Entry<String, Integer> e : scope.entrySet()) {
                if (!e.getKey().startsWith("$$")) {
                    all.add(e);
                }
            }
            i++;
        }
        all.sort(Map.Entry.comparingByValue());
        return all.stream().map(Map.Entry::getKey).toList();
    }
}
