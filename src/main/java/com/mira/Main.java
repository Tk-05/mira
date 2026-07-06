package com.mira;

import com.mira.repl.Repl;

public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            CliDispatcher.dispatch(args);
        } else {
            Repl.run();
        }
    }
}
