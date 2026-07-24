package com.mira.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.mira.cli.Flags;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.runtime.interpreter.Interpreter;

public class ComptimeExecutor {

    private static final String PREFIX = "[comptime] ";

    public Map<String, Object> execute(List<Node> ast) {
        List<Node> body = ast.stream()
                .filter(n -> n instanceof ComptimeBlock)
                .flatMap(n -> ((ComptimeBlock) n).getBody().stream())
                .toList();

        if (body.isEmpty()) {
            return Collections.emptyMap();
        }

        Interpreter interpreter = new Interpreter();
        boolean prevMainFunction = Flags.mainFunction;
        Flags.mainFunction = false;
        PrintStream originalOut = System.out;
        System.setOut(prefixedStream(originalOut));
        try {
            interpreter.run(body, false);
        } finally {
            System.out.flush();
            System.setOut(originalOut);
            Flags.mainFunction = prevMainFunction;
        }
        return interpreter.getGlobalEnvironment().getLocalValues();
    }

    private static PrintStream prefixedStream(PrintStream target) {
        return new PrintStream(new OutputStream() {
            private boolean atLineStart = true;

            @Override
            public void write(int b) throws IOException {
                if (atLineStart) {
                    target.print(PREFIX);
                    atLineStart = false;
                }
                target.write(b);
                if (b == '\n') {
                    atLineStart = true;
                }
            }

            @Override
            public void flush() throws IOException {
                target.flush();
            }
        }, true);
    }
}
