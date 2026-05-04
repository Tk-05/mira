package com.mira.runtime.functions;

import java.util.concurrent.CompletableFuture;

public class Promise {

    private final CompletableFuture<Object> future;

    public Promise(CompletableFuture<Object> future) {
        this.future = future;
    }

    public CompletableFuture<Object> getFuture() {
        return future;
    }

    @Override
    public String toString() {
        if (future.isDone()) {
            try {
                return "<promise:resolved(" + future.get() + ")>";
            } catch (Exception e) {
                return "<promise:rejected>";
            }
        }
        return "<promise:pending>";
    }
}
