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

    public int allocate(String name) {
        int slot = nextSlot++;
        scopeStack.peek().put(name, slot);
        return slot;
    }

    public Integer slotOf(String name) {
        for (Map<String, Integer> scope : scopeStack) {
            Integer slot = scope.get(name);
            if (slot != null) {
                return slot;
            }
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
        List<Map.Entry<String, Integer>> all = new ArrayList<>();
        for (Map<String, Integer> scope : scopeStack) {
            for (Map.Entry<String, Integer> e : scope.entrySet()) {
                if (!e.getKey().startsWith("$$")) {
                    all.add(e);
                }
            }
        }
        all.sort(Map.Entry.comparingByValue());
        return all.stream().map(Map.Entry::getKey).toList();
    }
}
