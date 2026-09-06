package com.mira.integration.interpreter.statement;

import static com.mira.integration.InterpreterRunner.normNum;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractWhileTests;

public class WhileTest extends AbstractWhileTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void simpleWhile() {
        assertNull(backend.runAndGetValue("""
                var i : 0;
                while(i <= 10){ i : (i + 1); }
                """));
    }

    @Test
    void whileWithBreak() {
        assertNull(backend.runAndGetValue("""
                var x : 0;
                while(x < 100) {
                    x : (x + 1);
                    if(x == 5) { break; }
                }
                """));
    }

    @Test
    void simpleDoWhile() {
        assertNull(backend.runAndGetValue("""
                var i : 0;
                do { i : (i + 1); } while(i < 5);
                """));
    }

    @Test
    void doWhileWithBreak() {
        backend.runAndGetValue("""
                var x : 0;
                do {
                    x : (x + 1);
                    if(x == 3) { break; }
                } while(x < 100);
                """);
        assertEquals(3.0, normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }
}
